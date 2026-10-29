package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.SESSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The routine name a session keeps (schema 8) in the sessions payload. */
class RoutineNameSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    private fun Device.row(id: String = "w1") = db.workoutSessionEntityQueries.selectById(id).executeAsOne()

    private fun Device.logbook() {
        routine()
        userExercise()
        session()
    }

    @Test
    fun `the name round-trips and survives the routine on both phones`() = runTest {
        d1.logbook()
        d1.engine.syncNow()
        val payload = Json.parseToJsonElement(cloud.doc("uid-1", SESSIONS, "w1")!!.payload).jsonObject
        assertEquals(JsonPrimitive("Push Pull Legs"), payload["routineName"])

        d2.engine.syncNow()
        assertEquals("Push Pull Legs", d2.row().routineName)

        // Deleted on the first phone: the session loses the routine and its day, keeps the name, everywhere.
        cloud.tick()
        d1.db.routineEntityQueries.delete("r1")
        assertNull(d1.row().routineDayIndex)
        d1.engine.syncNow()
        d2.engine.syncNow()
        val s = d2.row()
        assertNull(s.routineId)
        assertNull(s.routineDayIndex)
        assertEquals("Push Pull Legs", s.routineName)
    }

    @Test
    fun `an older document takes the routine name here, or keeps the one this phone had`() = runTest {
        d1.logbook()
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", SESSIONS, "w1")!!
        val old = JsonObject(Json.parseToJsonElement(doc.payload).jsonObject - "routineName").toString()
        cloud.put("uid-1", doc.copy(updatedAt = doc.updatedAt + 1_000_000, payload = old))
        d2.engine.syncNow()
        assertEquals("Push Pull Legs", d2.row().routineName)

        // An older phone writes the session again after the routine is gone (no routine id, no name): the name stays.
        val orphan = JsonObject(
            Json.parseToJsonElement(old).jsonObject - "routineName" + ("routineId" to JsonNull),
        ).toString()
        d2.db.routineEntityQueries.delete("r1")
        cloud.put("uid-1", doc.copy(updatedAt = doc.updatedAt + 2_000_000, payload = orphan))
        d2.engine.syncNow()
        val s = d2.row()
        assertNull(s.routineId)
        assertNull(s.routineDayIndex)
        assertEquals("Push Pull Legs", s.routineName)
    }
}
