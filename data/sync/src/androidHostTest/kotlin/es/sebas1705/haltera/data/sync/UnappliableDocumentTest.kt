package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.EXERCISES
import es.sebas1705.haltera.data.sync.SyncCollections.SESSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression: a remote document that cannot be applied here (a session whose USER exercise variant has not
 * arrived, so a foreign key fails) used to `break` out of the whole batch. Every newer document of the collection
 * was skipped, and the cursor stayed before the culprit, so with a dependency that never arrives the collection
 * was blocked on this device for good. The document is now held back and retried; the rest goes through.
 */
class UnappliableDocumentTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)

    /** Lets a test hold back the exercises collection, as if the document defining the variant were not there yet. */
    private class GatedRemote(private val cloud: FakeCloud) : RemoteStore by cloud {
        val exercisesVisible = MutableStateFlow(false)

        override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> =
            if (collection == EXERCISES && !exercisesVisible.value) {
                flowOf(emptyList())
            } else {
                cloud.changes(uid, collection, sinceUpdatedAt)
            }
    }

    private val gate = GatedRemote(cloud)
    private val d2 = Device(cloud, Dispatchers.Unconfined, remote = gate)

    private fun Device.catalogueSession(id: String) {
        db.workoutSessionEntityQueries.insert(id, 20, null, null, null)
        db.sessionExerciseEntityQueries.insert("$id-e1", id, 0, "bench-press.barbell", null)
    }

    private fun Device.document(collection: String, id: String) =
        SyncDocument(
            collection,
            id,
            state(collection, id)!!.updatedAt,
            deleted = false,
            payload = read(collection, id)!!,
            pushedAt = state(collection, id)!!.updatedAt // uploaded when it was changed: the cursor follows this
        )

    private fun Device.cursor(collection: String) =
        db.syncTrackingQueries.selectCursor(collection).executeAsOneOrNull()

    /** w1 (older) needs the USER variant of user-1; w2 (newer) only uses the bundled catalogue. */
    private fun seedAndUpload() {
        d1.userExercise()
        d1.routine()
        d1.session("w1")
        cloud.tick()
        d1.catalogueSession("w2")
    }

    @Test
    fun `a document that fails on a foreign key does not block the newer ones of its collection`() = runTest {
        seedAndUpload()
        d1.engine.syncNow()

        d2.engine.syncNow() // w1 cannot be applied: user-1.barbell never arrives

        assertNull(d2.db.workoutSessionEntityQueries.selectById("w1").executeAsOneOrNull())
        assertNotNull(d2.db.workoutSessionEntityQueries.selectById("w2").executeAsOneOrNull(), "w2 was blocked")
        assertEquals(d1.read(SESSIONS, "w2"), d2.read(SESSIONS, "w2"))
        assertTrue(d2.dirty().isEmpty(), d2.dirty().toString())
    }

    @Test
    fun `the held document is retried and applied once its dependency arrives`() = runTest {
        seedAndUpload()
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertNull(d2.db.workoutSessionEntityQueries.selectById("w1").executeAsOneOrNull())

        gate.exercisesVisible.value = true
        d2.engine.syncNow()

        assertEquals(d1.read(SESSIONS, "w1"), d2.read(SESSIONS, "w1"))
        assertEquals(d1.read(EXERCISES, "user-1"), d2.read(EXERCISES, "user-1"))
    }

    @Test
    fun `the cursor stays before the failing document across later batches so it is retried after a restart`() = runTest {
        seedAndUpload()
        d1.engine.syncNow()
        val w1 = d1.document(SESSIONS, "w1")
        val w2 = d1.document(SESSIONS, "w2")

        d2.engine.receive(SESSIONS, listOf(w1, w2))
        assertEquals(w1.updatedAt - 1, d2.cursor(SESSIONS))

        // A later live batch must not push the cursor past the held document either.
        d1.catalogueSession("w3")
        d2.engine.receive(SESSIONS, listOf(d1.document(SESSIONS, "w3")))
        assertEquals(w1.updatedAt - 1, d2.cursor(SESSIONS))
        assertNotNull(d2.db.workoutSessionEntityQueries.selectById("w3").executeAsOneOrNull())
    }

    @Test
    fun `a held document is applied as soon as the document it depends on is received`() = runTest {
        seedAndUpload()
        d1.engine.syncNow()
        val w1 = d1.document(SESSIONS, "w1")
        d2.engine.receive(SESSIONS, listOf(w1))
        assertNull(d2.db.workoutSessionEntityQueries.selectById("w1").executeAsOneOrNull())

        d2.engine.receive(EXERCISES, listOf(d1.document(EXERCISES, "user-1")))

        assertEquals(2, d2.db.sessionExerciseEntityQueries.selectBySession("w1").executeAsList().size)
        assertEquals(w1.updatedAt, d2.cursor(SESSIONS)) // nothing held any more: the cursor moves on
    }
}
