package es.sebas1705.ironlog.data.sync

import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The SQLite triggers of SyncTracking.sq: every synced table marks its aggregate root. */
class SyncTrackingTest {
    private val device = Device(FakeCloud(), Dispatchers.Unconfined)
    private val db = device.db

    private fun roots() = device.dirty().map { "${it.collection}/${it.id}" }.toSet()

    private fun clear() = db.transaction {
        device.dirty().forEach {
            db.syncTrackingQueries.clearDirty(
                it.collection,
                it.id,
                it.updatedAt
            )
        }
    }

    @Test
    fun `the catalogue import marks nothing`() {
        assertTrue(device.dirty().isEmpty())
        val newer = TEST_CATALOG.replace(
            "\"catalogVersion\":1",
            "\"catalogVersion\":2"
        ).replace("Squat\"", "Back Squat\"")
        CatalogImporter(db, now = { 1L }).importIfNewer(newer)
        assertTrue(device.dirty().isEmpty())
        assertNull(device.state(SyncCollections.EXERCISES, "squat"))
    }

    @Test
    fun `routine, day and slot changes map to the routine`() {
        device.routine()
        assertEquals(setOf("routines/r1"), roots())
        clear()
        db.routineDayEntityQueries.update("Push A", "TRAINING", "r1-d1")
        assertEquals(setOf("routines/r1"), roots())
        clear()
        db.routineSlotEntityQueries.setPosition(1, "r1-s1")
        assertEquals(setOf("routines/r1"), roots())
        clear()
        db.routineSlotEntityQueries.delete("r1-s1")
        db.routineDayEntityQueries.delete("r1-d2")
        assertEquals(setOf("routines/r1"), roots())
        assertEquals(0L, device.state(SyncCollections.ROUTINES, "r1")!!.deleted)
    }

    @Test
    fun `session, session exercise and set changes map to the session`() {
        device.routine()
        device.userExercise()
        clear()
        device.session()
        assertEquals(setOf("sessions/w1"), roots())
        clear()
        db.setLogEntityQueries.setPosition(1, "w1-t1")
        assertEquals(setOf("sessions/w1"), roots())
        clear()
        db.sessionExerciseEntityQueries.updateNotes("tough", "w1-e2")
        assertEquals(setOf("sessions/w1"), roots())
        clear()
        db.setLogEntityQueries.delete("w1-t2")
        db.sessionExerciseEntityQueries.delete("w1-e2")
        assertEquals(setOf("sessions/w1"), roots())
        assertEquals(0L, device.state(SyncCollections.SESSIONS, "w1")!!.deleted)
    }

    @Test
    fun `body weight and USER exercise changes map to their roots`() {
        device.bodyWeight()
        device.userExercise()
        assertEquals(setOf("bodyWeight/bw1", "exercises/user-1"), roots())
        clear()
        db.bodyWeightEntityQueries.delete("bw1")
        assertEquals(1L, device.state(SyncCollections.BODY_WEIGHT, "bw1")!!.deleted)
        assertEquals(setOf("bodyWeight/bw1"), roots())
    }

    @Test
    fun `deleting a root leaves a tombstone, also through the cascade of its children`() {
        device.routine()
        device.userExercise()
        device.session()
        clear()
        db.workoutSessionEntityQueries.delete("w1")
        val session = device.state(SyncCollections.SESSIONS, "w1")
        assertEquals(1L, session!!.deleted)
        assertEquals(1L, session.dirty)
        db.routineEntityQueries.delete("r1")
        val routine = device.state(SyncCollections.ROUTINES, "r1")
        assertEquals(1L, routine!!.deleted)
        assertEquals(1L, routine.dirty)
        assertEquals(0L, db.routineDayEntityQueries.countByRoutine("r1").executeAsOne())
    }

    @Test
    fun `changing the active routine marks settings`() {
        device.routine()
        clear()
        db.routineEntityQueries.markActive("r1")
        assertTrue("settings/user" in roots())
    }

    @Test
    fun `nothing is tracked while remote changes are applied`() {
        db.metaQueries.put(META_SYNC_APPLYING, "1")
        device.routine()
        device.bodyWeight()
        assertTrue(device.dirty().isEmpty())
        db.metaQueries.put(META_SYNC_APPLYING, "0")
        db.bodyWeightEntityQueries.delete("bw1")
        assertNotNull(device.state(SyncCollections.BODY_WEIGHT, "bw1"))
    }

    @Test
    fun `updatedAt always moves forward`() {
        device.bodyWeight()
        val first = device.state(SyncCollections.BODY_WEIGHT, "bw1")!!.updatedAt
        device.bodyWeight(grams = 81_000)
        assertTrue(device.state(SyncCollections.BODY_WEIGHT, "bw1")!!.updatedAt > first)
    }
}
