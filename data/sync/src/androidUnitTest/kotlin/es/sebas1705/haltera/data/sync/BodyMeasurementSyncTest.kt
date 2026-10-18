package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.BODY_MEASUREMENTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun Device.measurement(
    id: String = "bm1",
    type: String = "WAIST",
    value: Long = 825,
    customName: String = "",
    notes: String = "after breakfast",
    measuredAt: Long = 1_000,
) = db.bodyMeasurementEntityQueries.upsert(id, measuredAt, type, customName, value, notes)

private fun Device.row(id: String = "bm1") = db.bodyMeasurementEntityQueries.selectById(id).executeAsOneOrNull()

/** Body measurements (schema 9) through the sync contract: collection `bodyMeasurements`, one document per row. */
class BodyMeasurementSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    @Test
    fun `the collection is synced, after body weight and before foods`() {
        val all = SyncCollections.ALL
        assertTrue(BODY_MEASUREMENTS in all)
        assertEquals(all.indexOf(SyncCollections.BODY_WEIGHT) + 1, all.indexOf(BODY_MEASUREMENTS))
    }

    @Test
    fun `a measurement round-trips to another phone with every field`() = runTest {
        d1.measurement()
        d1.measurement("bm2", type = "CUSTOM", value = 371, customName = "Gemelo izquierdo, ñ", notes = "")
        d1.measurement("bm3", type = "BODY_FAT", value = 185, notes = "")
        d1.engine.syncNow()
        val doc = assertNotNull(cloud.doc("uid-1", BODY_MEASUREMENTS, "bm1"))
        assertEquals(
            BodyMeasurementPayload("bm1", 1_000, "WAIST", 825, "", "after breakfast"),
            SyncJson.decodeFromString(BodyMeasurementPayload.serializer(), doc.payload),
        )
        d2.engine.syncNow()
        assertEquals(d1.row(), d2.row())
        assertEquals("Gemelo izquierdo, ñ", d2.row("bm2")?.customName)
        assertEquals(185L, d2.row("bm3")?.value_)
        // Applied remote rows are clean on the receiver: nothing bounces back.
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `edits and deletes reach the other phone`() = runTest {
        d1.measurement()
        d1.engine.syncNow()
        d2.engine.syncNow()
        tick()
        d1.measurement(value = 810, notes = "")
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals(810L, d2.row()?.value_)

        d1.db.bodyMeasurementEntityQueries.delete("bm1")
        d1.engine.syncNow()
        val tombstone = assertNotNull(cloud.doc("uid-1", BODY_MEASUREMENTS, "bm1"))
        assertTrue(tombstone.deleted)
        assertEquals("", tombstone.payload)
        d2.engine.syncNow()
        assertNull(d2.row())
        assertEquals(1L, d2.state(BODY_MEASUREMENTS, "bm1")?.deleted)
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `applying the same document twice is idempotent`() {
        val doc = SyncDocument(
            BODY_MEASUREMENTS,
            "bm1",
            updatedAt = 50,
            deleted = false,
            payload = SyncJson.encodeToString(
                BodyMeasurementPayload.serializer(),
                BodyMeasurementPayload("bm1", 1_000, "CHEST", 1_020, "", "x"),
            ),
        )
        d2.store.apply(doc)
        val first = d2.row()
        d2.store.apply(doc)
        assertEquals(first, d2.row())
        assertEquals(1, d2.db.bodyMeasurementEntityQueries.selectAll().executeAsList().size)
        assertEquals(50L, d2.state(BODY_MEASUREMENTS, "bm1")?.updatedAt)
        assertTrue(d2.dirty().isEmpty())
        // Reading it back gives the same payload it was applied from.
        assertEquals(doc.payload, d2.read(BODY_MEASUREMENTS, "bm1"))
    }

    @Test
    fun `an older or equal document does not overwrite a newer local edit`() = runTest {
        d1.measurement(value = 900)
        val local = assertNotNull(d1.state(BODY_MEASUREMENTS, "bm1"))
        d1.engine.receive(
            BODY_MEASUREMENTS,
            listOf(
                SyncDocument(
                    BODY_MEASUREMENTS,
                    "bm1",
                    local.updatedAt,
                    deleted = false,
                    payload = SyncJson.encodeToString(
                        BodyMeasurementPayload.serializer(),
                        BodyMeasurementPayload("bm1", 1_000, "WAIST", 700),
                    ),
                ),
            ),
        )
        assertEquals(900L, d1.row()?.value_)
    }

    @Test
    fun `documents without the optional fields decode with defaults, and unknown keys are ignored`() {
        val payload = """{"id":"bm1","measuredAt":5,"type":"NECK","value":390,"futureField":true}"""
        d2.store.apply(SyncDocument(BODY_MEASUREMENTS, "bm1", 10, deleted = false, payload = payload))
        val row = assertNotNull(d2.row())
        assertEquals(Triple("NECK", "", ""), Triple(row.type, row.customName, row.notes))
    }

    @Test
    fun `a type from a newer version is kept as it is through sync`() = runTest {
        d1.measurement(type = "LEFT_CALF", value = 370)
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals("LEFT_CALF", d2.row()?.type)
    }

    @Test
    fun `measurements logged before sync are backfilled, and a merge uploads them again`() = runTest {
        d1.db.metaQueries.put(META_SYNC_APPLYING, "1") // written without tracking
        d1.measurement()
        d1.db.metaQueries.put(META_SYNC_APPLYING, "0")
        assertNull(d1.state(BODY_MEASUREMENTS, "bm1"))
        d1.engine.syncNow() // first start: backfill
        assertNotNull(cloud.doc("uid-1", BODY_MEASUREMENTS, "bm1"))

        d1.engine.markAllDirty()
        assertEquals(1L, d1.state(BODY_MEASUREMENTS, "bm1")?.dirty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `with the cloud copy off nothing is uploaded until it is turned on`() = runTest {
        val io = UnconfinedTestDispatcher(testScheduler)
        val phone = Device(cloud, io, cloudOn = MutableStateFlow(false))
        phone.measurement()
        phone.engine.start(backgroundScope)
        runCurrent()
        phone.engine.syncNow()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, cloud.pushes)
        assertNull(cloud.doc("uid-1", BODY_MEASUREMENTS, "bm1"))

        phone.cloudOn.value = true
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertNotNull(cloud.doc("uid-1", BODY_MEASUREMENTS, "bm1"))
    }
}
