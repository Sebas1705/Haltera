package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.SESSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The "Both hands" snapshot of logged exercises in the sessions payload (schema 8, core design §3.3). */
class BothHandsSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    /** Turns "Both hands" on or off for slot r1-s1 (bench press, see [Device.routine]). */
    private fun Device.setSlotBilateral(on: Boolean) = db.routineSlotEntityQueries.update(
        target = """{"kind":"fixed","variantId":"bench-press.barbell"}""", sets = 3, warmupSets = 1, prescription = "{}",
        restBetweenSetsS = 90, restAfterS = 120, supersetGroup = null, bilateral = if (on) 1 else 0, notes = "",
        progression = "{}", id = "r1-s1",
    )

    private fun Device.flags(sessionId: String): Map<String, Long> =
        db.sessionExerciseEntityQueries.selectBySession(sessionId).executeAsList().associate { it.id to it.bilateral }

    private fun exercisesOf(payload: String): List<JsonObject> =
        Json.parseToJsonElement(payload).jsonObject.getValue("exercises").jsonArray.map { it.jsonObject }

    @Test
    fun `the snapshot round-trips and outlives the slot`() = runTest {
        d1.routine()
        d1.userExercise()
        d1.setSlotBilateral(true)
        d1.session()
        d1.engine.syncNow()

        val exercises = exercisesOf(cloud.doc("uid-1", SESSIONS, "w1")!!.payload)
        assertEquals(listOf(JsonPrimitive(1L), JsonPrimitive(0L)), exercises.map { it["bilateral"] })

        d2.engine.syncNow()
        assertEquals(mapOf("w1-e1" to 1L, "w1-e2" to 0L), d2.flags("w1"))
        assertEquals(d1.read(SESSIONS, "w1"), d2.read(SESSIONS, "w1"))
        assertEquals(emptyList(), d2.dirty())

        // The slot is removed on the first phone: the link goes, the snapshot stays on both phones.
        cloud.tick()
        d1.db.routineSlotEntityQueries.delete("r1-s1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        val e1 = d2.db.sessionExerciseEntityQueries.selectById("w1-e1").executeAsOne()
        assertNull(e1.slotId)
        assertEquals(1L, e1.bilateral)
    }

    @Test
    fun `a document written by an older version takes the flag of the receiver's slot`() = runTest {
        d1.routine()
        d1.userExercise()
        d1.setSlotBilateral(true)
        d1.session()
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", SESSIONS, "w1")!!
        // Strip the new field from every exercise, as alpha.4 (schema 6/7) writes the document.
        val root = Json.parseToJsonElement(doc.payload).jsonObject
        val old = JsonObject(
            root + ("exercises" to JsonArray(exercisesOf(doc.payload).map { JsonObject(it - "bilateral") })),
        )
        cloud.put("uid-1", doc.copy(updatedAt = doc.updatedAt + 1_000_000, payload = old.toString()))

        d2.engine.syncNow()
        // e1 came from the "Both hands" slot, which d2 has (routines apply before sessions); e2 had no slot.
        assertEquals(mapOf("w1-e1" to 1L, "w1-e2" to 0L), d2.flags("w1"))
    }

    @Test
    fun `an older document without the slot here counts one implement`() = runTest {
        d1.routine()
        d1.userExercise()
        d1.setSlotBilateral(true)
        d1.session()
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", SESSIONS, "w1")!!
        val root = Json.parseToJsonElement(doc.payload).jsonObject
        val old = JsonObject(
            root + ("exercises" to JsonArray(exercisesOf(doc.payload).map { JsonObject(it - "bilateral") })),
        )
        // Only the session reaches the other phone (its routine was deleted meanwhile), so the slot is unknown there.
        val d3 = Device(
            FakeCloud().also { it.put("uid-1", doc.copy(payload = old.toString())) },
            Dispatchers.Unconfined
        )
        d3.userExercise()
        d3.engine.syncNow()
        assertEquals(mapOf("w1-e1" to 0L, "w1-e2" to 0L), d3.flags("w1"))
        assertNull(d3.db.sessionExerciseEntityQueries.selectById("w1-e1").executeAsOne().slotId)
    }
}
