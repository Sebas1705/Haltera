package es.sebas1705.ironlog.data.repository

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.sql.HistoryLoader
import es.sebas1705.ironlog.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.ironlog.data.repository.sql.SqlRoutineRepository
import es.sebas1705.ironlog.data.repository.sql.SqlSessionRepository
import es.sebas1705.ironlog.data.routines.FamousRoutines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock()

    private class Repos(val routines: SqlRoutineRepository, val sessions: SqlSessionRepository)

    private fun TestScope.repos(): Repos {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return Repos(
            SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock),
            SqlSessionRepository(db, HistoryLoader(db), dispatcher, clock),
        )
    }

    /** Routine A (squat + flexible bench), Rest, B (plank). */
    private suspend fun Repos.routine(): String {
        val id = routines.createRoutine("R")
        val a = routines.addDay(id, "A")
        routines.addSlot(a, SlotSpec(SlotTarget.Fixed("squat.barbell")))
        routines.addSlot(
            a,
            SlotSpec(SlotTarget.Flexible("bench-press", listOf("bench-press.dumbbell", "bench-press.barbell")))
        )
        routines.addDay(id, "Rest", DayKind.REST)
        val b = routines.addDay(id, "B")
        routines.addSlot(b, SlotSpec(SlotTarget.Fixed("plank.bodyweight")))
        return id
    }

    @Test
    fun `starting a routine day creates one exercise per slot`() = runTest {
        val r = repos()
        val routineId = r.routine()

        val sessionId = r.sessions.startFromRoutineDay(routineId, 0)

        val session = r.sessions.getSession(sessionId)!!
        assertTrue(session.inProgress)
        assertEquals("A", session.dayName)
        assertEquals(0, session.routineDayIndex)
        assertEquals(listOf("squat.barbell", "bench-press.dumbbell"), session.exercises.map { it.variantId })
        assertTrue(session.exercises.all { it.slotId != null })
    }

    @Test
    fun `a flexible slot reuses the variant last used in it`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val first = r.sessions.startFromRoutineDay(routineId, 0)
        val bench = r.sessions.getSession(first)!!.exercises[1]
        r.sessions.substituteExercise(bench.id, "bench-press.barbell")
        r.sessions.finish(first)
        clock.millis += 1_000

        val second = r.sessions.startFromRoutineDay(routineId, 0)

        assertEquals("bench-press.barbell", r.sessions.getSession(second)!!.exercises[1].variantId)
    }

    @Test
    fun `only one session is in progress and it can be resumed`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val id = r.sessions.startFree()

        assertEquals(id, r.sessions.startFree())
        assertEquals(id, r.sessions.startFromRoutineDay(routineId, 0))
        assertEquals(id, r.sessions.getInProgress()?.id)
        r.sessions.finish(id)
        assertNull(r.sessions.getInProgress())
    }

    @Test
    fun `log, update and delete sets`() = runTest {
        val r = repos()
        val session = r.sessions.startFree()
        val e = r.sessions.addExercise(session, "squat.barbell")
        clock.millis = 2_000
        val warmup = r.sessions.logSet(e, SetInput(kind = SetKind.WARMUP, reps = 5, loadGrams = 60_000))
        val work = r.sessions.logSet(
            e,
            SetInput(reps = 5, loadGrams = 100_000, loadDetail = "{\"bar\":20000}", completed = false)
        )

        var sets = r.sessions.getSession(session)!!.exercises.single().sets
        assertEquals(listOf(SetKind.WARMUP, SetKind.WORKING), sets.map { it.kind })
        assertEquals(2_000L, sets[0].completedAt)
        assertNull(sets[1].completedAt)
        assertEquals("{\"bar\":20000}", sets[1].loadDetail)

        clock.millis = 3_000
        r.sessions.updateSet(work, SetInput(reps = 4, loadGrams = 100_000, rpe = 9.5))
        r.sessions.deleteSet(warmup)
        sets = r.sessions.getSession(session)!!.exercises.single().sets
        assertEquals(1, sets.size)
        assertEquals(0, sets[0].position)
        assertEquals(4, sets[0].reps)
        assertEquals(9.5, sets[0].rpe)
        assertEquals(3_000L, sets[0].completedAt)
    }

    @Test
    fun `add, move and remove exercises`() = runTest {
        val r = repos()
        val session = r.sessions.startFree()
        val squat = r.sessions.addExercise(session, "squat.barbell")
        val bench = r.sessions.addExercise(session, "bench-press.barbell")
        val plank = r.sessions.addExercise(session, "plank.bodyweight")

        r.sessions.moveExercise(plank, 0)
        r.sessions.removeExercise(squat)
        r.sessions.updateExerciseNotes(bench, "pause reps")

        val exercises = r.sessions.getSession(session)!!.exercises
        assertEquals(listOf(plank, bench), exercises.map { it.id })
        assertEquals(listOf(0, 1), exercises.map { it.position })
        assertEquals("pause reps", exercises[1].notes)
    }

    @Test
    fun `finishing advances the cycle pointer and wraps around`() = runTest {
        val r = repos()
        val routineId = r.routine()

        r.sessions.finish(r.sessions.startFromRoutineDay(routineId, 0))
        assertEquals(1, r.routines.getRoutine(routineId)!!.nextDayIndex)
        r.sessions.finish(r.sessions.startFromRoutineDay(routineId, 2))
        assertEquals(0, r.routines.getRoutine(routineId)!!.nextDayIndex)
    }

    @Test
    fun `finishing a free session does not touch routines and finishing twice keeps the end time`() = runTest {
        val r = repos()
        val id = r.sessions.startFree()
        clock.millis = 5_000
        r.sessions.finish(id)
        clock.millis = 9_000
        r.sessions.finish(id)
        assertEquals(5_000L, r.sessions.getSession(id)!!.endedAt)
    }

    @Test
    fun `history, date range and last performance for a slot`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val days = listOf(1_000L, 2_000L, 3_000L)
        days.forEach { at ->
            clock.millis = at
            val id = r.sessions.startFromRoutineDay(routineId, 0)
            val squat = r.sessions.getSession(id)!!.exercises.first()
            r.sessions.logSet(squat.id, SetInput(reps = 5, loadGrams = at * 100))
            r.sessions.finish(id)
        }

        assertEquals(listOf(3_000L, 2_000L, 1_000L), r.sessions.observeHistory().first().map { it.startedAt })
        assertEquals(listOf(2_000L), r.sessions.observeSessionsBetween(1_500, 3_000).first().map { it.startedAt })
        val slotId = r.routines.getRoutine(routineId)!!.days.first().slots.first().id
        val last = r.sessions.lastForSlotAndVariant(slotId, "squat.barbell", limit = 2)
        assertEquals(listOf(300_000L, 200_000L), last.map { it.exercise.sets.single().loadGrams })
        assertEquals(3, r.sessions.lastForVariant("squat.barbell").size)
    }

    @Test
    fun `observing the session in progress follows set changes`() = runTest {
        val r = repos()
        r.sessions.observeInProgress().test {
            assertNull(awaitItem())
            val id = r.sessions.startFree()
            val e = r.sessions.addExercise(id, "squat.barbell")
            r.sessions.logSet(e, SetInput(reps = 5, loadGrams = 100_000))
            assertEquals(1, expectMostRecentItem()!!.exercises.single().sets.size)
            r.sessions.deleteSession(id)
            assertNull(expectMostRecentItem())
        }
    }

    @Test
    fun `deleting a routine keeps its sessions`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val id = r.sessions.startFromRoutineDay(routineId, 0)
        r.sessions.finish(id)

        r.routines.deleteRoutine(routineId)

        val session = assertNotNull(r.sessions.getSession(id))
        assertNull(session.routineId)
        assertTrue(session.exercises.all { it.slotId == null })
    }

    @Test
    fun `body weight entries`() = runTest {
        val repo = SqlBodyWeightRepository(db, UnconfinedTestDispatcher(testScheduler), clock)
        clock.millis = 10
        val first = repo.add(80_000)
        repo.add(79_500, measuredAt = 20)

        assertEquals(79_500L, repo.observeLatest().first()?.weightGrams)
        assertEquals(listOf(20L, 10L), repo.observeAll().first().map { it.measuredAt })
        assertEquals(listOf(10L), repo.observeBetween(0, 15).first().map { it.measuredAt })
        repo.update(repo.observeAll().first().last().copy(weightGrams = 81_000))
        assertEquals(81_000L, repo.observeAll().first().first { it.id == first }.weightGrams)
        repo.delete(first)
        assertEquals(1, repo.observeAll().first().size)
    }
}
