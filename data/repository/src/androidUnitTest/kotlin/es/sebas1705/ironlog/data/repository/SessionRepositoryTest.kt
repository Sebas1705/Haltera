package es.sebas1705.ironlog.data.repository

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.sql.HistoryLoader
import es.sebas1705.ironlog.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.ironlog.data.repository.sql.SqlExerciseRepository
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
        r.logBench(first)
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
    fun `finishing leaves the manual override alone (the cycle is derived from sessions)`() = runTest {
        val r = repos()
        val routineId = r.routine()

        r.sessions.finish(r.sessions.startFromRoutineDay(routineId, 0))

        val routine = r.routines.getRoutine(routineId)!!
        assertEquals(0, routine.nextDayIndex)
        assertNull(routine.nextDayIndexSetAt)
    }

    @Test
    fun `a past start is neither blocked by nor returns the live session`() = runTest {
        val r = repos()
        val routineId = r.routine()
        clock.millis = 100_000
        val live = r.sessions.startFree()

        val pastDay = r.sessions.startFromRoutineDay(routineId, 2, startedAt = 10_000)
        val pastFree = r.sessions.startFree(startedAt = 20_000)

        assertTrue(pastDay != live && pastFree != live && pastDay != pastFree)
        assertEquals(10_000L, r.sessions.getSession(pastDay)!!.startedAt)
        assertEquals("B", r.sessions.getSession(pastDay)!!.dayName)
        assertEquals(20_000L, r.sessions.getSession(pastFree)!!.startedAt)
        // The live session is still the one resumed by live starts.
        assertEquals(live, r.sessions.getInProgress()?.id)
        assertEquals(live, r.sessions.startFromRoutineDay(routineId, 0))
    }

    @Test
    fun `finish at a given time, then change the times`() = runTest {
        val r = repos()
        val id = r.sessions.startFree(startedAt = 10_000)
        clock.millis = 500_000

        r.sessions.finish(id, endedAt = 70_000)
        assertEquals(70_000L, r.sessions.getSession(id)!!.endedAt)

        r.sessions.updateTimes(id, startedAt = 20_000, endedAt = 90_000)
        val moved = r.sessions.getSession(id)!!
        assertEquals(20_000L, moved.startedAt)
        assertEquals(90_000L, moved.endedAt)

        r.sessions.updateTimes(id, startedAt = 30_000, endedAt = 5_000)
        assertEquals(30_000L, r.sessions.getSession(id)!!.endedAt)
    }

    @Test
    fun `sets of a finished session can be edited without reopening it`() = runTest {
        val r = repos()
        val id = r.sessions.startFree(startedAt = 10_000)
        val e = r.sessions.addExercise(id, "squat.barbell")
        val set = r.sessions.logSet(e, SetInput(reps = 5, loadGrams = 100_000))
        r.sessions.finish(id, endedAt = 20_000)

        r.sessions.updateSet(set, SetInput(reps = 6, loadGrams = 105_000))
        r.sessions.logSet(e, SetInput(reps = 4, loadGrams = 105_000))
        r.sessions.addExercise(id, "bench-press.barbell")

        val session = r.sessions.getSession(id)!!
        assertEquals(20_000L, session.endedAt)
        assertEquals(listOf(6, 4), session.exercises.first().sets.map { it.reps })
        assertEquals(2, session.exercises.size)
        assertNull(r.sessions.getInProgress())
    }

    @Test
    fun `the latest session of a routine is by start time, finished or not`() = runTest {
        val r = repos()
        val routineId = r.routine()
        r.sessions.observeLatestForRoutine(routineId).test {
            assertNull(awaitItem())
            // Logged out of order: the most recent insert is the oldest session.
            r.sessions.finish(r.sessions.startFromRoutineDay(routineId, 2, startedAt = 30_000), 31_000)
            r.sessions.finish(r.sessions.startFromRoutineDay(routineId, 0, startedAt = 10_000), 11_000)
            assertEquals(30_000L, expectMostRecentItem()!!.startedAt)
            r.sessions.startFromRoutineDay(routineId, 0, startedAt = 40_000)
            val latest = expectMostRecentItem()!!
            assertEquals(40_000L, latest.startedAt)
            assertTrue(latest.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history is ordered by start time and bounded by before`() = runTest {
        val r = repos()
        val routineId = r.routine()
        // Inserted out of order.
        listOf(3_000L, 1_000L, 2_000L).forEach { at ->
            val id = r.sessions.startFromRoutineDay(routineId, 0, startedAt = at)
            val squat = r.sessions.getSession(id)!!.exercises.first()
            r.sessions.logSet(squat.id, SetInput(reps = 5, loadGrams = at * 100))
            r.sessions.finish(id, endedAt = at + 500)
        }
        val slotId = r.routines.getRoutine(routineId)!!.days.first().slots.first().id

        assertEquals(listOf(3_000L, 2_000L, 1_000L), r.sessions.observeHistory().first().map { it.startedAt })
        assertEquals(
            listOf(3_000L, 2_000L, 1_000L),
            r.sessions.lastForSlotAndVariant(slotId, "squat.barbell").map { it.sessionStartedAt },
        )
        assertEquals(
            listOf(1_000L),
            r.sessions.lastForSlotAndVariant(slotId, "squat.barbell", before = 2_000).map { it.sessionStartedAt },
        )
        assertEquals(
            listOf(2_000L, 1_000L),
            r.sessions.lastForVariant("squat.barbell", before = 2_500).map { it.sessionStartedAt },
        )
        assertTrue(r.sessions.lastForVariant("squat.barbell", before = 1_000).isEmpty())
    }

    @Test
    fun `a past start picks the flexible variant used before it`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val early = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 1_000)
        r.sessions.substituteExercise(r.sessions.getSession(early)!!.exercises[1].id, "bench-press.barbell")
        r.logBench(early)
        r.sessions.finish(early, 1_500)
        val late = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 5_000)
        r.sessions.substituteExercise(r.sessions.getSession(late)!!.exercises[1].id, "bench-press.dumbbell")
        r.logBench(late)
        r.sessions.finish(late, 5_500)

        val between = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 3_000)

        assertEquals("bench-press.barbell", r.sessions.getSession(between)!!.exercises[1].variantId)
    }

    /** A completed set on the flexible bench slot of [sessionId] (only performed exercises are history). */
    private suspend fun Repos.logBench(sessionId: String) {
        val bench = sessions.getSession(sessionId)!!.exercises[1]
        sessions.logSet(bench.id, SetInput(reps = 8, loadGrams = 30_000, completed = true))
    }

    /** A finished free session at [at] with one exercise of [variantId], its set completed or not. */
    private suspend fun Repos.freeSession(variantId: String, at: Long, completed: Boolean = true) {
        val id = sessions.startFree(startedAt = at)
        sessions.logSet(
            sessions.addExercise(id, variantId),
            SetInput(reps = 8, loadGrams = 30_000, completed = completed),
        )
        sessions.finish(id, at + 500)
    }

    @Test
    fun `a flexible slot without history in it takes the variant used last anywhere`() = runTest {
        val r = repos()
        val routineId = r.routine()
        r.freeSession("bench-press.barbell", at = 1_000)

        val sessionId = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 3_000)

        assertEquals("bench-press.barbell", r.sessions.getSession(sessionId)!!.exercises[1].variantId)
    }

    @Test
    fun `the any-session fallback respects before and ignores empty performances`() = runTest {
        val r = repos()
        val routineId = r.routine()
        r.freeSession("bench-press.barbell", at = 1_000, completed = false)
        r.freeSession("bench-press.barbell", at = 9_000)

        val sessionId = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 5_000)

        // Nothing usable before 5 000: catalogue order of the allowed variants.
        assertEquals("bench-press.dumbbell", r.sessions.getSession(sessionId)!!.exercises[1].variantId)
    }

    @Test
    fun `history in the slot wins over a later use elsewhere`() = runTest {
        val r = repos()
        val routineId = r.routine()
        val first = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 1_000)
        r.logBench(first)
        r.sessions.finish(first, 1_500) // dumbbell, the slot's default
        r.freeSession("bench-press.barbell", at = 3_000)

        val second = r.sessions.startFromRoutineDay(routineId, 0, startedAt = 5_000)

        assertEquals("bench-press.dumbbell", r.sessions.getSession(second)!!.exercises[1].variantId)
    }

    @Test
    fun `without a query the exercises logged most come first`() = runTest {
        val r = repos()
        val exercises = SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(testScheduler), clock)
        assertEquals("bench-press", exercises.search(ExerciseFilter(), "en").first().first().id)
        r.freeSession("squat.barbell", at = 1_000)
        r.freeSession("squat.barbell", at = 2_000)

        assertEquals("squat", exercises.search(ExerciseFilter(), "en").first().first().id)
        // A query keeps name matches first.
        assertEquals(
            listOf("bench-press"),
            exercises.search(ExerciseFilter(query = "bench"), "en").first().map { it.id }
        )
    }

    @Test
    fun `deleting a finished session removes it from history`() = runTest {
        val r = repos()
        val id = r.sessions.startFree(startedAt = 1_000)
        r.sessions.logSet(r.sessions.addExercise(id, "squat.barbell"), SetInput(reps = 5, loadGrams = 1))
        r.sessions.finish(id, 2_000)

        r.sessions.deleteSession(id)

        assertNull(r.sessions.getSession(id))
        assertTrue(r.sessions.observeHistory().first().isEmpty())
        assertTrue(r.sessions.lastForVariant("squat.barbell").isEmpty())
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

    @Test
    fun `dropping incomplete rows deletes pending sets and exercises left empty`() = runTest {
        val r = repos()
        val session = r.sessions.startFree()
        val squat = r.sessions.addExercise(session, "squat.barbell")
        val bench = r.sessions.addExercise(session, "bench-press.barbell")
        val plank = r.sessions.addExercise(session, "plank.bodyweight")
        r.sessions.logSet(squat, SetInput(reps = 5, loadGrams = 100_000, completed = false))
        val done = r.sessions.logSet(squat, SetInput(reps = 5, loadGrams = 100_000, completed = true))
        r.sessions.logSet(bench, SetInput(reps = 5, loadGrams = 80_000, completed = false))
        r.sessions.logSet(plank, SetInput(durationS = 60, completed = true))

        r.sessions.dropIncomplete(session)

        val exercises = r.sessions.getSession(session)!!.exercises
        assertEquals(listOf(squat, plank), exercises.map { it.id })
        assertEquals(listOf(0, 1), exercises.map { it.position })
        assertEquals(listOf(done), exercises[0].sets.map { it.id })
        assertEquals(0, exercises[0].sets.single().position)
    }

    @Test
    fun `exercises without completed sets in finished sessions stay out of history`() = runTest {
        val r = repos()
        clock.millis = 1_000
        val session = r.sessions.startFree()
        val squat = r.sessions.addExercise(session, "squat.barbell")
        val bench = r.sessions.addExercise(session, "bench-press.barbell")
        r.sessions.logSet(squat, SetInput(reps = 5, loadGrams = 100_000, completed = true))
        r.sessions.logSet(bench, SetInput(reps = 5, loadGrams = 80_000, completed = false))
        // Saved before saving pruned empty exercises: the bench row is still there.
        r.sessions.finish(session, endedAt = 2_000)

        assertEquals(listOf(squat), r.sessions.observeHistory().first().single().exercises.map { it.id })
        assertEquals(
            listOf(squat),
            r.sessions.observeSessionsBetween(0, 5_000).first().single().exercises.map { it.id }
        )
        assertTrue(r.sessions.lastForVariant("bench-press.barbell").isEmpty())
        assertEquals(1, r.sessions.lastForVariant("squat.barbell").size)
        // The session itself (editing) still shows every exercise.
        assertEquals(2, r.sessions.getSession(session)!!.exercises.size)
    }
}
