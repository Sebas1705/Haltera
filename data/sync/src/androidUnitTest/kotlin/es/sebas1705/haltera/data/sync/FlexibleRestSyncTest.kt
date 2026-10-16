package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.sync.SyncCollections.ROUTINES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rest policy in the routines payload (docs/designs/2026-10-16-flexible-rest-days.md). */
class FlexibleRestSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    private fun Device.restOf(id: String) = db.routineEntityQueries.selectById(id).executeAsOne()
        .let { Triple(it.restMode, it.restDaysPerWeek, it.restWeekdays) }

    @Test
    fun `a flexible routine round-trips between two devices`() = runTest {
        d1.routine()
        d1.db.routineEntityQueries.setRestPolicy("FLEXIBLE", 2, 0b100_0100, 5, "r1")
        d1.engine.syncNow()
        val payload = Json.parseToJsonElement(cloud.doc("uid-1", ROUTINES, "r1")!!.payload).jsonObject
        assertEquals(JsonPrimitive("FLEXIBLE"), payload["restMode"])
        assertEquals(JsonPrimitive(2L), payload["restDaysPerWeek"])
        assertEquals(JsonPrimitive(68L), payload["restWeekdays"])

        d2.engine.syncNow()
        assertEquals(Triple("FLEXIBLE", 2L, 68L), d2.restOf("r1"))
        assertEquals(d1.read(ROUTINES, "r1"), d2.read(ROUTINES, "r1"))
        // Applied data does not echo back.
        assertEquals(emptyList(), d2.dirty())
    }

    @Test
    fun `changing only the rest policy is uploaded`() = runTest {
        d1.routine()
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals(Triple("FIXED", 0L, 0L), d2.restOf("r1"))

        d1.db.routineEntityQueries.setRestPolicy("FLEXIBLE", 3, 0, 10, "r1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals(Triple("FLEXIBLE", 3L, 0L), d2.restOf("r1"))

        // And back to fixed from the other device.
        d2.db.routineEntityQueries.setRestPolicy("FIXED", 0, 0, 20, "r1")
        d2.engine.syncNow()
        d1.engine.syncNow()
        assertEquals(Triple("FIXED", 0L, 0L), d1.restOf("r1"))
    }

    @Test
    fun `a document written by an older version decodes as a fixed routine`() = runTest {
        d1.routine()
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", ROUTINES, "r1")!!
        // Strip the new fields as an older app would have written the document.
        val old =
            JsonObject(
                Json.parseToJsonElement(doc.payload).jsonObject - setOf("restMode", "restDaysPerWeek", "restWeekdays")
            )
        d2.db.routineEntityQueries.insert("r1", "Local", "", null, null, 1, 1)
        d2.db.routineEntityQueries.setRestPolicy("FLEXIBLE", 2, 0, 1, "r1")
        cloud.put("uid-1", doc.copy(updatedAt = doc.updatedAt + 1_000_000, payload = old.toString()))

        d2.engine.syncNow()
        assertEquals(Triple("FIXED", 0L, 0L), d2.restOf("r1"))
        assertEquals("Push Pull Legs", d2.db.routineEntityQueries.selectById("r1").executeAsOne().name)
    }

    @Test
    fun `a payload from a newer version with an unknown mode is stored and read leniently`() = runTest {
        d1.routine()
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", ROUTINES, "r1")!!
        val newer = JsonObject(
            Json.parseToJsonElement(doc.payload).jsonObject + mapOf("restMode" to JsonPrimitive("ROTATING"), "future" to JsonPrimitive(1)),
        )
        cloud.put("uid-1", doc.copy(updatedAt = doc.updatedAt + 1_000_000, payload = newer.toString()))
        d2.engine.syncNow()
        assertEquals("ROTATING", d2.restOf("r1").first)
        assertEquals(RestMode.FIXED, RestMode.fromDb(d2.restOf("r1").first))
    }
}
