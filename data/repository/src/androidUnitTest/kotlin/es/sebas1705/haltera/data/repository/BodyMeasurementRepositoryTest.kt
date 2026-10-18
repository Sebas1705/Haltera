package es.sebas1705.haltera.data.repository

import app.cash.turbine.test
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.data.repository.sql.SqlBodyMeasurementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BodyMeasurementRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = TestClock(5_000)

    private fun TestScope.repo() = SqlBodyMeasurementRepository(db, UnconfinedTestDispatcher(testScheduler), clock)

    @Test
    fun `empty log has no measurements`() = runTest {
        assertEquals(emptyList(), repo().observeAll().first())
    }

    @Test
    fun `adds, edits and deletes measurements, latest first`() = runTest {
        val repo = repo()
        val waist = repo.add(MeasurementType.WAIST, 825, notes = "  after breakfast ")
        val fat = repo.add(MeasurementType.BODY_FAT, 185, measuredAt = 9_000)
        val all = repo.observeAll().first()
        assertEquals(listOf(fat, waist), all.map { it.id })
        val stored = all.last()
        assertEquals(5_000L, stored.measuredAt) // defaults to now
        assertEquals("after breakfast", stored.notes)
        assertEquals("", stored.customName)

        repo.update(stored.copy(value = 830, measuredAt = 1_000))
        assertEquals(830L, repo.observeAll().first().single { it.id == waist }.value)
        repo.delete(waist)
        assertEquals(listOf(fat), repo.observeAll().first().map { it.id })
        repo.delete("missing") // no-op
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `same day duplicates are kept, as in the logbook`() = runTest {
        val repo = repo()
        repo.add(MeasurementType.ARM, 350, measuredAt = 10)
        repo.add(MeasurementType.ARM, 352, measuredAt = 10)
        assertEquals(listOf(350L, 352L).sorted(), repo.observeAll().first().map { it.value }.sorted())
    }

    @Test
    fun `values outside the range are rejected and nothing is written`() = runTest {
        val repo = repo()
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.WAIST, 0) }
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.WAIST, -1) }
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.CHEST, BodyMeasurements.MAX_LENGTH_MM + 1) }
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.BODY_FAT, 1_001) }
        assertEquals(emptyList(), repo.observeAll().first())
        // The boundaries themselves are fine.
        repo.add(MeasurementType.CHEST, BodyMeasurements.MAX_LENGTH_MM)
        repo.add(MeasurementType.BODY_FAT, BodyMeasurements.MAX_BODY_FAT_TENTHS)
        repo.add(MeasurementType.NECK, 1)
        assertEquals(3, repo.observeAll().first().size)
    }

    @Test
    fun `custom measurements need a name, which is normalised, and fixed types drop it`() = runTest {
        val repo = repo()
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.CUSTOM, 380) }
        assertFailsWith<IllegalArgumentException> { repo.add(MeasurementType.CUSTOM, 380, customName = "   ") }
        repo.add(MeasurementType.CUSTOM, 380, customName = "  Gemelo   izquierdo ")
        repo.add(MeasurementType.CUSTOM, 381, customName = "x".repeat(100))
        repo.add(MeasurementType.HIPS, 990, customName = "ignored")
        val names = repo.observeAll().first().associate { it.value to it.customName }
        assertEquals("Gemelo izquierdo", names[380])
        assertEquals(BodyMeasurements.MAX_NAME_LENGTH, names.getValue(381).length)
        assertEquals("", names[990])
        // Editing a fixed type into a custom one without a name is refused too.
        val hips = repo.observeAll().first().single { it.type == MeasurementType.HIPS }
        assertFailsWith<IllegalArgumentException> { repo.update(hips.copy(type = MeasurementType.CUSTOM)) }
        assertEquals(MeasurementType.HIPS, repo.observeAll().first().single { it.id == hips.id }.type)
    }

    @Test
    fun `an edit replaces the row in place and re-emits`() = runTest {
        val repo = repo()
        val id = repo.add(MeasurementType.THIGH, 600)
        repo.observeAll().test {
            assertEquals(600L, awaitItem().single().value)
            repo.update(BodyMeasurement(id, 7_000, MeasurementType.THIGH, 610))
            assertEquals(610L, awaitItem().single().value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a type written by a newer version reads as a custom measurement with its own series`() = runTest {
        db.bodyMeasurementEntityQueries.upsert("future", 1, "LEFT_CALF", "", 370, "")
        db.bodyMeasurementEntityQueries.upsert("future2", 2, "SHOULDERS", "Shoulders", 1_200, "")
        val all = repo().observeAll().first().associateBy { it.id }
        assertEquals(MeasurementType.CUSTOM, all.getValue("future").type)
        assertEquals("left calf", all.getValue("future").customName)
        assertEquals("Shoulders", all.getValue("future2").customName)
        assertTrue(all.getValue("future").seriesKey != all.getValue("future2").seriesKey)
        // Reading never rewrites the row.
        assertEquals("LEFT_CALF", db.bodyMeasurementEntityQueries.selectById("future").executeAsOne().type)
    }

    @Test
    fun `writes are tracked for sync and deletes leave a tombstone`() = runTest {
        val repo = repo()
        val id = repo.add(MeasurementType.WAIST, 800)
        assertEquals(1L, db.syncTrackingQueries.selectState("bodyMeasurements", id).executeAsOne().dirty)
        repo.delete(id)
        assertEquals(1L, db.syncTrackingQueries.selectState("bodyMeasurements", id).executeAsOne().deleted)
    }
}
