package es.sebas1705.haltera.data.repository

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.export.ByteArrayOutput
import es.sebas1705.haltera.data.repository.export.ExportFormat
import es.sebas1705.haltera.data.repository.export.SqlDataExporter
import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.HistoryFileParser
import es.sebas1705.haltera.data.repository.importer.ImportOptions
import es.sebas1705.haltera.data.repository.importer.ParseOptions
import es.sebas1705.haltera.data.repository.importer.SqlHistoryImporter
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlBodyMeasurementRepository
import es.sebas1705.haltera.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlNutritionRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Export (version 2) → import keeps routine sessions (docs/EXPORT-FORMAT.md, docs/IMPORT-FORMATS.md): on the same
 * device the sessions are linked to their routine, day and slot again, with their "Both hands" snapshot; on a fresh
 * device they keep their names as the title and their volume, and no routine is invented.
 */
class HalteraRoutineRoundTripTest {
    private val clock = TestClock(at(day = 20))
    private val utc = ParseOptions(TimeZone.UTC)

    private class Device(
        val db: AppDatabase,
        val routines: SqlRoutineRepository,
        val sessions: SqlSessionRepository,
        val exercises: SqlExerciseRepository,
        val importer: SqlHistoryImporter,
        val exporter: SqlDataExporter,
    )

    private fun TestScope.device(db: AppDatabase = inMemoryDatabase().withTestCatalog()): Device {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val history = HistoryLoader(db)
        val exercises = SqlExerciseRepository(db, history, dispatcher, clock)
        val routines = SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock)
        val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })
        val nutrition = SqlNutritionRepository(
            db,
            FoodImporter(db, now = clock),
            OpenFoodFactsClient(http, "1.0", now = clock),
            KeyValueSettingsRepository(MapSettings()),
            dispatcher,
            clock,
        )
        val exporter = SqlDataExporter(
            db,
            history,
            exercises,
            nutrition,
            SqlBodyWeightRepository(db, dispatcher, clock),
            SqlBodyMeasurementRepository(db, dispatcher, clock),
            routines,
            DefaultRoutineSharing(routines, exercises, clock),
            dispatcher,
        )
        return Device(
            db,
            routines,
            SqlSessionRepository(db, history, dispatcher, clock),
            exercises,
            SqlHistoryImporter(db, exercises, dispatcher),
            exporter,
        )
    }

    private class Logbook(val ppl: String, val flex: String, val press: String, val row: String, val bench: String)

    /**
     * "PPL" (fixed): A = dumbbell press (both hands) + one-arm row (one hand), B = barbell bench. "Flex" (flexible
     * rest days): one day with the press (both hands). Sessions: PPL A (day 10), PPL B (11), free (12), Flex (13),
     * PPL A (14). Then the press slot of PPL is removed: its snapshot stays, its link goes.
     */
    private suspend fun Device.seed(): Logbook {
        val ppl = routines.createRoutine("PPL")
        val a = routines.addDay(ppl, "A")
        val press = routines.addSlot(a, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = true))
        val row = routines.addSlot(a, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = false))
        val b = routines.addDay(ppl, "B")
        val bench = routines.addSlot(b, SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        val flex = routines.createRoutine("Flex")
        val f = routines.addDay(flex, "Full")
        routines.addSlot(f, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = true))
        routines.setRestPolicy(flex, RestPolicy.flexible(2))
        routines.setActive(ppl)

        logRoutine(ppl, 0, at(day = 10))
        logRoutine(ppl, 1, at(day = 11))
        val free = sessions.startFree(startedAt = at(day = 12))
        sessions.finish(free, endedAt = at(day = 12) + HOUR)
        val freeExercise = sessions.addExercise(free, "bench-press.dumbbell")
        repeat(2) { sessions.logSet(freeExercise, SetInput(reps = 10, loadGrams = 20_000)) }
        logRoutine(flex, 0, at(day = 13))
        logRoutine(ppl, 0, at(day = 14))
        routines.deleteSlot(press)
        return Logbook(ppl, flex, press, row, bench)
    }

    private suspend fun Device.logRoutine(routineId: String, day: Int, startedAt: Long) {
        val id = sessions.startFromRoutineDay(routineId, day, startedAt = startedAt)
        sessions.finish(id, endedAt = startedAt + HOUR)
        sessions.getSession(id)!!.exercises.forEach { e ->
            repeat(2) { sessions.logSet(e.id, SetInput(reps = 10, loadGrams = 20_000)) }
        }
    }

    private suspend fun Device.export(): ByteArray {
        val out = ByteArrayOutput()
        exporter.export(out, "1.0.0", clock.millis, TimeZone.UTC)
        return out.toByteArray()
    }

    private suspend fun Device.all(): List<WorkoutSession> = sessions.observeHistory(
        100
    ).first().sortedBy { it.startedAt }

    /** TrainingMath.volume (domain:usecases is not visible here): load × reps of completed sets, × 2 for both hands. */
    private fun WorkoutSession.volume(): Long = exercises.sumOf { e ->
        e.sets.filter { it.completed }.sumOf { (it.loadGrams ?: 0) * (it.reps ?: 0) } * if (e.bilateral) 2 else 1
    }

    /** What must survive a round trip on the same device. */
    private data class Shape(
        val id: String,
        val routineId: String?,
        val dayIndex: Int?,
        val dayName: String?,
        val slots: List<String?>,
        val bothHands: List<Boolean>,
        val volume: Long,
        val routineName: String?,
        val setIds: List<String>,
    )

    private fun List<WorkoutSession>.shape() = map { s ->
        val exercises = s.exercises.sortedBy { it.position }
        Shape(
            s.id,
            s.routineId,
            s.routineDayIndex,
            s.dayName,
            exercises.map { it.slotId },
            exercises.map { it.bilateral },
            s.volume(),
            s.routineName,
            exercises.flatMap { e -> e.sets.sortedBy { it.position }.map { it.id } },
        )
    }

    private suspend fun Device.wipeSessions() = all().forEach { sessions.deleteSession(it.id) }

    @Test
    fun `the export carries the routine, the day, the slot and the both-hands snapshot`() = runTest {
        val d = device()
        val book = d.seed()
        val csv = HistoryFileParser.parse(
            d.export(),
            utc
        ).let { p -> p.copy(sessions = p.sessions.sortedBy { it.startedAt }) }

        assertEquals(3, ExportFormat.VERSION)
        val first = csv.sessions.first()
        assertEquals(book.ppl, first.routineId)
        assertEquals("PPL", first.routineName)
        assertEquals(0, first.routineDayIndex)
        assertEquals("A", first.title)
        // The press slot was removed after logging: no slot id, but both hands.
        assertEquals(listOf(null, book.row), first.exercises.map { it.slotId })
        assertEquals(listOf(true, false), first.exercises.map { it.bilateral })
        val free = csv.sessions[2]
        assertNull(free.routineId)
        assertEquals(listOf(false), free.exercises.map { it.bilateral })
    }

    @Test
    fun `re-importing on the same device with skip or replace keeps links, titles and volume`() = runTest {
        val d = device()
        val book = d.seed()
        val before = d.all().shape()
        val zip = d.export()
        val parsed = HistoryFileParser.parse(zip, utc)

        // Skip: everything is there already.
        assertEquals(0, d.importer.import(parsed, ImportOptions()).sessionsWritten)
        assertEquals(before, d.all().shape())

        // Replace: rewritten from the file, linked again.
        val replaced = d.importer.import(parsed, ImportOptions(existing = ExistingPolicy.REPLACE))
        assertEquals(5, replaced.sessionsReplaced)
        assertEquals(before.map { it.copy() }, d.all().shape())

        // Lost sessions imported back (skip): the same again.
        d.wipeSessions()
        assertEquals(5, d.importer.import(parsed, ImportOptions()).sessionsWritten)
        val after = d.all().shape()
        assertEquals(before, after)
        assertEquals(listOf(book.ppl, book.ppl, null, book.flex, book.ppl), after.map { it.routineId })
        assertEquals(listOf(null, book.row), after.last().slots)
        assertEquals(
            listOf(listOf(true, false), listOf(false), listOf(false), listOf(true), listOf(true, false)),
            after.map { it.bothHands }
        )

        // The next day sees the imported sessions: PPL's latest is day A (index 0) of day 14, so B comes next.
        val latest = d.sessions.observeLatestForRoutine(book.ppl).first()!!
        assertEquals(at(day = 14), latest.startedAt)
        assertEquals(0, latest.routineDayIndex)
        // The flexible routine's week counts its own linked session.
        val week = d.sessions.observeSessionsBetween(at(day = 13), at(day = 14)).first()
        assertEquals(listOf(book.flex), week.map { it.routineId })
        assertEquals(0, week.single().routineDayIndex)
    }

    @Test
    fun `importing on a fresh device keeps the names as the title and the volume, without inventing routines`() = runTest {
        val source = device()
        source.seed()
        val before = source.all().shape()
        val parsed = HistoryFileParser.parse(source.export(), utc)

        val fresh = device()
        fresh.importer.import(parsed, ImportOptions())

        val after = fresh.all().shape()
        assertEquals(listOf("A", "B", null, "Full", "A"), after.map { it.dayName })
        // The title is "<routine name> · <day>" from the name the session kept.
        assertEquals(listOf("PPL", "PPL", null, "Flex", "PPL"), after.map { it.routineName })
        assertEquals(List(5) { null }, after.map { it.routineId })
        assertEquals(List(5) { null }, after.map { it.dayIndex })
        assertEquals(before.map { it.volume }, after.map { it.volume })
        assertEquals(before.map { it.bothHands }, after.map { it.bothHands })
        assertEquals(emptyList(), fresh.routines.observeRoutines().first())
        assertEquals(after.map { s -> s.slots.map { null } }, after.map { it.slots })
    }

    @Test
    fun `a deleted routine keeps its name on the sessions, in the export and after importing`() = runTest {
        val d = device()
        val book = d.seed()
        d.routines.updateRoutine(book.flex, "Flex 2", "")
        d.routines.deleteRoutine(book.flex)

        val flex = d.all().single { it.startedAt == at(day = 13) }
        assertNull(flex.routineId)
        assertNull(flex.routineDayIndex)
        assertEquals("Flex 2", flex.routineName)
        assertEquals("Full", flex.dayName)

        val parsed = HistoryFileParser.parse(d.export(), utc)
        val exported = parsed.sessions.single { it.startedAt == at(day = 13) }
        assertNull(exported.routineId)
        assertNull(exported.routineDayIndex)
        assertEquals("Flex 2", exported.routineName)

        // Replace on the same device gives back exactly what was there, set ids included.
        val before = d.all().shape()
        d.importer.import(parsed, ImportOptions(existing = ExistingPolicy.REPLACE))
        assertEquals(before, d.all().shape())
        val fresh = device()
        fresh.importer.import(parsed, ImportOptions())
        assertEquals("Flex 2", fresh.all().single { it.startedAt == at(day = 13) }.routineName)
    }

    @Test
    fun `a renamed routine renames its sessions`() = runTest {
        val d = device()
        val book = d.seed()
        d.routines.updateRoutine(book.ppl, "Push Pull", "")
        assertEquals(listOf("Push Pull", "Push Pull", null, "Flex", "Push Pull"), d.all().map { it.routineName })
    }

    @Test
    fun `a slot or day that no longer matches the routine is not linked`() = runTest {
        val d = device()
        val book = d.seed()
        val parsed = HistoryFileParser.parse(d.export(), utc)
        d.wipeSessions()
        // The row slot is gone and day B was removed since the export.
        d.routines.deleteSlot(book.row)
        d.routines.deleteDay(d.routines.getRoutine(book.ppl)!!.days[1].id)

        d.importer.import(parsed, ImportOptions())

        val shape = d.all().shape()
        assertEquals(listOf(0, null, null, 0, 0), shape.map { it.dayIndex })
        assertEquals(listOf(book.ppl, book.ppl, null, book.flex, book.ppl), shape.map { it.routineId })
        assertEquals(listOf(null, null), shape.first().slots)
        // The snapshot still decides the volume: press both hands, row one hand.
        assertEquals(listOf(true, false), shape.first().bothHands)
    }

    @Test
    fun `a version 1 export still imports unlinked, titled with the day name and one implement`() = runTest {
        val source = device()
        source.seed()
        val v2 = HistoryFileParser.parse(source.export(), utc)
        // Version 1 had no routine, slot or both-hands columns.
        val v1 = v2.copy(
            sessions = v2.sessions.map { s ->
                s.copy(
                    routineId = null,
                    routineName = null,
                    routineDayIndex = null,
                    exercises = s.exercises.map { it.copy(slotId = null, bilateral = null) },
                )
            },
        )
        val d = device()
        d.importer.import(v1, ImportOptions())

        val shape = d.all().shape()
        assertEquals(listOf("A", "B", null, "Full", "A"), shape.map { it.dayName })
        assertEquals(List(5) { null }, shape.map { it.routineName })
        assertEquals(List(5) { null }, shape.map { it.routineId })
        assertEquals(List(5) { false }, shape.map { s -> s.bothHands.any { it } })
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        /** 10:00 UTC of October [day], 2026. */
        fun at(day: Int): Long = 1_790_812_800_000L + (day - 1) * DAY + 10 * HOUR
    }
}
