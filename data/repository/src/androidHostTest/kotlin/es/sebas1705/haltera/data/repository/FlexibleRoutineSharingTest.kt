package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.share.ImportPreviewResult
import es.sebas1705.haltera.data.repository.share.RoutineDecodeResult
import es.sebas1705.haltera.data.repository.share.RoutineExport
import es.sebas1705.haltera.data.repository.share.RoutineShareCode
import es.sebas1705.haltera.data.repository.share.SharedRest
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The optional `routine.rest` of the share format (docs/designs/2026-10-16-flexible-rest-days.md). */
class FlexibleRoutineSharingTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(42L)

    private class Env(val routines: RoutineRepository, val sharing: DefaultRoutineSharing)

    private fun TestScope.env(): Env {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val routines = SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock)
        val exercises = SqlExerciseRepository(db, HistoryLoader(db), dispatcher, clock)
        return Env(routines, DefaultRoutineSharing(routines, exercises, clock))
    }

    private suspend fun Env.routine(policy: RestPolicy, rest: Boolean = false): String {
        val id = routines.createRoutine("Split", "")
        listOf("Push", "Pull", "Legs").forEach {
            routines.addSlot(routines.addDay(id, it), SlotSpec(SlotTarget.Fixed("squat.barbell")))
        }
        if (rest) routines.addDay(id, "Rest", DayKind.REST)
        routines.setRestPolicy(id, policy)
        return id
    }

    private suspend fun Env.roundTrip(json: String): String {
        val preview = assertIs<ImportPreviewResult.Ready>(sharing.preview(json)).preview
        return sharing.import(preview, dropMissing = true)
    }

    @Test
    fun `a flexible routine with a number of rest days round trips through the file`() = runTest {
        val env = env()
        val id = env.routine(RestPolicy.flexible(3))
        val json = env.sharing.export(id)!!
        val rest = Json.parseToJsonElement(json).jsonObject["routine"]!!.jsonObject["rest"]!!.jsonObject
        assertEquals("\"FLEXIBLE\"", rest["mode"].toString())
        assertEquals("3", rest["daysPerWeek"].toString())

        val copy = env.routines.getRoutine(env.roundTrip(json))!!
        assertEquals(RestPolicy.flexible(3), copy.restPolicy)
        assertEquals(listOf("Push", "Pull", "Legs"), copy.days.map { it.name })
    }

    @Test
    fun `rest weekdays round trip through the QR code`() = runTest {
        val env = env()
        val id = env.routine(RestPolicy.onWeekdays(listOf(3, 7)))
        val code = env.sharing.shareCode(id)!!
        assertTrue(RoutineShareCode.isCode(code))
        val copy = env.routines.getRoutine(env.roundTrip(code))!!
        assertEquals(listOf(3, 7), copy.restPolicy.weekdays)
        assertEquals(2, copy.restPolicy.restDaysCount)
    }

    @Test
    fun `a fixed routine writes no rest key, so its file and code are unchanged`() = runTest {
        val env = env()
        val id = env.routine(RestPolicy.FIXED, rest = true)
        val json = env.sharing.export(id)!!
        assertFalse("\"rest\"" in json)
        val copy = env.routines.getRoutine(env.roundTrip(json))!!
        assertEquals(RestPolicy.FIXED, copy.restPolicy)
        assertEquals(
            listOf(DayKind.TRAINING, DayKind.TRAINING, DayKind.TRAINING, DayKind.REST),
            copy.days.map { it.kind }
        )
    }

    @Test
    fun `files and codes written before flexible routines import as fixed`() = runTest {
        val env = env()
        val old = """{"format":"haltera-routine","version":1,"exportedAt":1,"routine":{"name":"Old","description":"",
            |"days":[{"name":"A","kind":"TRAINING","slots":[]},{"name":"Rest","kind":"REST","slots":[]}]},
            |"customExercises":[]}
        """.trimMargin()
        val fromFile = env.routines.getRoutine(env.roundTrip(old))!!
        assertEquals(RestPolicy.FIXED, fromFile.restPolicy)
        assertEquals(listOf(DayKind.TRAINING, DayKind.REST), fromFile.days.map { it.kind })
        val fromV1 = env.routines.getRoutine(env.roundTrip(RoutineShareCode.encodeV1(old)))!!
        assertEquals(RestPolicy.FIXED, fromV1.restPolicy)
        val fromV2 = env.routines.getRoutine(env.roundTrip(RoutineShareCode.encode(old)))!!
        assertEquals(RestPolicy.FIXED, fromV2.restPolicy)
    }

    @Test
    fun `an unknown rest mode from a newer app imports as fixed and keeps the days`() = runTest {
        val env = env()
        val newer = """{"format":"haltera-routine","version":1,"routine":{"name":"N","days":[{"name":"A"}],
            |"rest":{"mode":"ROTATING","daysPerWeek":2,"extra":true}}}
        """.trimMargin()
        val copy = env.routines.getRoutine(env.roundTrip(newer))!!
        assertEquals(RestPolicy.FIXED, copy.restPolicy)
        assertEquals(listOf("A"), copy.days.map { it.name })
    }

    @Test
    fun `a flexible file with rest days in it imports its training days only`() = runTest {
        val env = env()
        val json = """{"format":"haltera-routine","version":1,"routine":{"name":"F","days":[{"name":"A"},
            |{"name":"Rest","kind":"REST"},{"name":"B"}],"rest":{"daysPerWeek":2}}}
        """.trimMargin()
        val copy = env.routines.getRoutine(env.roundTrip(json))!!
        assertEquals(RestPolicy.flexible(2), copy.restPolicy)
        assertEquals(listOf("A", "B"), copy.days.map { it.name })
    }

    @Test
    fun `the rest object decodes leniently`() {
        assertEquals(RestPolicy.flexible(6), SharedRest(daysPerWeek = 40).toPolicy())
        assertEquals(RestPolicy.onWeekdays(listOf(1)), SharedRest(weekdays = listOf(1, 9, 0)).toPolicy())
        assertEquals(RestPolicy.FIXED, SharedRest(mode = "FIXED", daysPerWeek = 3).toPolicy())
        assertNull(SharedRest.of(RestPolicy.FIXED))
        assertEquals(SharedRest("FLEXIBLE", 2, listOf(3, 7)), SharedRest.of(RestPolicy.onWeekdays(listOf(7, 3))))
    }

    @Test
    fun `a flexible catalogue copy is shared in full, not as a template code`() = runTest {
        val env = env()
        val id = env.routines.copyTemplate("ppl-reddit", "en")
        assertTrue(RoutineShareCode.isTemplateCode(env.sharing.shareCode(id)!!))
        env.routines.setRestPolicy(id, RestPolicy.flexible(1))
        val code = env.sharing.shareCode(id)!!
        assertFalse(RoutineShareCode.isTemplateCode(code))
        val decoded = assertIs<RoutineDecodeResult.Valid>(RoutineExport.decode(RoutineShareCode.decode(code)!!))
        assertEquals(1, decoded.file.routine.rest?.daysPerWeek)
    }
}
