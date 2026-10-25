package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rest policy and fixed ↔ flexible conversion (docs/designs/2026-10-16-flexible-rest-days.md). */
class RestPolicyRepositoryTest {
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

    private val squat = SlotSpec(SlotTarget.Fixed("squat.barbell"))

    /** Push · Rest · Pull · Legs · Rest, with one exercise on each training day. */
    private suspend fun Repos.ppl(): String {
        val id = routines.createRoutine("PPL")
        listOf(
            "Push" to DayKind.TRAINING,
            "Rest" to DayKind.REST,
            "Pull" to DayKind.TRAINING,
            "Legs" to DayKind.TRAINING,
            "Rest" to DayKind.REST
        )
            .forEach { (name, kind) ->
                val day = routines.addDay(id, name, kind)
                if (kind == DayKind.TRAINING) routines.addSlot(day, squat)
            }
        return id
    }

    /** A finished session of day [dayIndex] started at [at], as "Log past session" creates it. */
    private suspend fun Repos.log(routineId: String, dayIndex: Int, at: Long): String =
        sessions.startFromRoutineDay(routineId, dayIndex, at).also { sessions.finish(it, at + 1) }

    private suspend fun Repos.logFree(at: Long): String = sessions.startFree(at).also { sessions.finish(it, at + 1) }

    private suspend fun Repos.dayIndexOf(sessionId: String) = sessions.getSession(sessionId)!!.routineDayIndex

    @Test
    fun `routines are fixed by default and the policy round-trips`() = runTest {
        val repos = repos()
        val id = repos.routines.createRoutine("R")
        assertEquals(RestPolicy.FIXED, repos.routines.getRoutine(id)!!.restPolicy)

        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))
        assertEquals(RestPolicy.flexible(2), repos.routines.getRoutine(id)!!.restPolicy)
        repos.routines.setRestPolicy(id, RestPolicy.onWeekdays(listOf(3, 7)))
        val weekdays = repos.routines.getRoutine(id)!!.restPolicy
        assertEquals(listOf(3, 7), weekdays.weekdays)
        assertEquals(2, weekdays.restDaysPerWeek)
        // Observers see the change too.
        assertEquals(RestMode.FLEXIBLE, repos.routines.observeRoutine(id).first()!!.restPolicy.mode)
    }

    @Test
    fun `out of range values are normalised before they are stored`() = runTest {
        val repos = repos()
        val id = repos.routines.createRoutine("R")
        repos.routines.setRestPolicy(id, RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek = 12))
        assertEquals(6, repos.routines.getRoutine(id)!!.restPolicy.restDaysPerWeek)
        val row = db.routineEntityQueries.selectById(id).executeAsOne()
        assertEquals(6L, row.restDaysPerWeek)
        // An unknown mode written by a newer version reads as fixed.
        db.routineEntityQueries.setRestPolicy("SOMETHING_NEW", 3, 5, 9, id)
        assertEquals(RestPolicy.FIXED, repos.routines.getRoutine(id)!!.restPolicy)
    }

    @Test
    fun `fixed to flexible drops the empty rest days and keeps the training order`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))

        val routine = repos.routines.getRoutine(id)!!
        assertEquals(listOf("Push", "Pull", "Legs"), routine.days.map { it.name })
        assertEquals(listOf(0, 1, 2), routine.days.map { it.position })
        assertTrue(routine.days.all { it.kind == DayKind.TRAINING && it.slots.size == 1 })
        assertEquals(RestPolicy.flexible(2), routine.restPolicy)
    }

    @Test
    fun `a rest day that holds exercises becomes a training day instead of being lost`() = runTest {
        val repos = repos()
        val id = repos.routines.createRoutine("R")
        repos.routines.addDay(id, "A")
        val mobility = repos.routines.addDay(id, "Mobility", DayKind.REST)
        repos.routines.addSlot(mobility, squat)
        repos.routines.addDay(id, "Rest", DayKind.REST)

        repos.routines.setRestPolicy(id, RestPolicy.flexible(1))

        val days = repos.routines.getRoutine(id)!!.days
        assertEquals(listOf("A", "Mobility"), days.map { it.name })
        assertEquals(listOf(DayKind.TRAINING, DayKind.TRAINING), days.map { it.kind })
        assertEquals(1, days[1].slots.size)
    }

    @Test
    fun `history is remapped to the same days and the next day does not jump`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        // Push (0), Pull (2) and Legs (3) logged, plus a session started on the trailing rest day (4).
        val push = repos.log(id, 0, 1_000)
        val pull = repos.log(id, 2, 2_000)
        val legs = repos.log(id, 3, 3_000)
        val onRest = repos.log(id, 4, 500)
        val free = repos.logFree(4_000)

        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))

        assertEquals(0, repos.dayIndexOf(push))
        assertEquals(1, repos.dayIndexOf(pull))
        assertEquals(2, repos.dayIndexOf(legs))
        // The rest day it pointed at is gone; the session itself is kept.
        assertNull(repos.dayIndexOf(onRest))
        assertTrue(repos.sessions.getSession(onRest) != null)
        assertNull(repos.dayIndexOf(free))
        // Day names of the sessions are snapshots and stay.
        assertEquals("Legs", repos.sessions.getSession(legs)!!.dayName)
        // The latest session of the routine is still Legs, now at index 2 of the training days.
        assertEquals(2, repos.sessions.observeLatestForRoutine(id).first()!!.routineDayIndex)
    }

    @Test
    fun `the manual override follows its day, and one on a rest day moves to the next training day`() = runTest {
        val repos = repos()
        val onPull = repos.ppl()
        repos.routines.setNextDayIndex(onPull, 2)
        repos.routines.setRestPolicy(onPull, RestPolicy.flexible(1))
        assertEquals(1, repos.routines.getRoutine(onPull)!!.nextDayIndex)

        val onRest = repos.ppl()
        repos.routines.setNextDayIndex(onRest, 1)
        val setAt = repos.routines.getRoutine(onRest)!!.nextDayIndexSetAt
        repos.routines.setRestPolicy(onRest, RestPolicy.flexible(1))
        assertEquals(1, repos.routines.getRoutine(onRest)!!.nextDayIndex) // Pull
        // The time it was chosen is kept.
        assertEquals(setAt, repos.routines.getRoutine(onRest)!!.nextDayIndexSetAt)

        val onLastRest = repos.ppl()
        repos.routines.setNextDayIndex(onLastRest, 4)
        repos.routines.setRestPolicy(onLastRest, RestPolicy.flexible(1))
        assertEquals(0, repos.routines.getRoutine(onLastRest)!!.nextDayIndex) // wraps to Push
    }

    @Test
    fun `flexible to fixed appends the rest days and moves nothing`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))
        val legs = repos.log(id, 2, 1_000)

        repos.routines.setRestPolicy(id, RestPolicy.FIXED, appendRestDays = 2, restDayName = "Descanso")

        val routine = repos.routines.getRoutine(id)!!
        assertEquals(listOf("Push", "Pull", "Legs", "Descanso", "Descanso"), routine.days.map { it.name })
        assertEquals(
            listOf(DayKind.TRAINING, DayKind.TRAINING, DayKind.TRAINING, DayKind.REST, DayKind.REST),
            routine.days.map { it.kind }
        )
        assertEquals(listOf(0, 1, 2, 3, 4), routine.days.map { it.position })
        assertEquals(RestPolicy.FIXED, routine.restPolicy)
        assertEquals(2, repos.dayIndexOf(legs))
    }

    @Test
    fun `flexible to fixed without rest days keeps only the training days`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))
        repos.routines.setRestPolicy(id, RestPolicy.FIXED)
        assertEquals(listOf("Push", "Pull", "Legs"), repos.routines.getRoutine(id)!!.days.map { it.name })
    }

    @Test
    fun `changing the policy within flexible changes no day`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))
        val days = repos.routines.getRoutine(id)!!.days
        repos.routines.setRestPolicy(id, RestPolicy.onWeekdays(listOf(1, 4)))
        repos.routines.setRestPolicy(id, RestPolicy.flexible(3), appendRestDays = 5)
        assertEquals(days, repos.routines.getRoutine(id)!!.days)
        // Setting fixed on a fixed routine adds nothing either.
        val fixed = repos.ppl()
        repos.routines.setRestPolicy(fixed, RestPolicy.FIXED, appendRestDays = 3)
        assertEquals(5, repos.routines.getRoutine(fixed)!!.days.size)
    }

    @Test
    fun `a round trip fixed - flexible - fixed keeps every training day and its exercises`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        val before = repos.routines.getRoutine(id)!!.days.filter { it.kind == DayKind.TRAINING }
        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))
        repos.routines.setRestPolicy(id, RestPolicy.FIXED, appendRestDays = 2)
        val after = repos.routines.getRoutine(id)!!.days.filter { it.kind == DayKind.TRAINING }
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { d -> d.slots.map { it.id } }, after.map { d -> d.slots.map { it.id } })
    }

    @Test
    fun `a conversion is tracked for sync, sessions it remaps too`() = runTest {
        val repos = repos()
        val id = repos.ppl()
        val pull = repos.log(id, 2, 2_000)
        db.syncTrackingQueries.putState("routines", id, 1L, 0L, 0L)
        db.syncTrackingQueries.putState("sessions", pull, 1L, 0L, 0L)

        repos.routines.setRestPolicy(id, RestPolicy.flexible(2))

        assertEquals(1L, db.syncTrackingQueries.selectState("routines", id).executeAsOne().dirty)
        assertEquals(1L, db.syncTrackingQueries.selectState("sessions", pull).executeAsOne().dirty)
    }

    @Test
    fun `an unknown routine is ignored`() = runTest {
        repos().routines.setRestPolicy("missing", RestPolicy.flexible(2))
        assertNull(db.routineEntityQueries.selectById("missing").executeAsOneOrNull())
    }
}
