package es.sebas1705.ironlog.domain.usecases.cycle

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import es.sebas1705.ironlog.data.models.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleTest {
    private fun days(vararg kinds: DayKind) = kinds.mapIndexed { i, kind -> RoutineDay("d$i", "r", i, "Day $i", kind) }

    private val t = DayKind.TRAINING
    private val r = DayKind.REST

    @Test
    fun `pointer on a training day returns it`() {
        val next = Cycle.nextPendingDay(days(t, r, t), 0)!!
        assertEquals(0, next.dayIndex)
        assertFalse(next.planSaysRest)
    }

    @Test
    fun `rest days are skipped and reported`() {
        val next = Cycle.nextPendingDay(days(t, r, r, t), 1)!!
        assertEquals(3, next.dayIndex)
        assertEquals(listOf(1, 2), next.restDaysSkipped)
        assertTrue(next.planSaysRest)
    }

    @Test
    fun `search wraps around the cycle`() {
        val next = Cycle.nextPendingDay(days(t, t, r, r), 2)!!
        assertEquals(0, next.dayIndex)
        assertEquals(listOf(2, 3), next.restDaysSkipped)
    }

    @Test
    fun `out of range pointers are clamped after a routine edit`() {
        assertEquals(1, Cycle.nextPendingDay(days(t, t), 7)!!.dayIndex)
        assertEquals(0, Cycle.nextPendingDay(days(t, t), -3)!!.dayIndex)
        assertEquals(0, Cycle.clamp(5, 0))
    }

    @Test
    fun `no training days or no days gives null`() {
        assertNull(Cycle.nextPendingDay(days(r, r), 0))
        assertNull(Cycle.nextPendingDay(emptyList(), 0))
    }

    @Test
    fun `completing day k moves the pointer to k + 1 mod N`() {
        assertEquals(1, Cycle.pointerAfter(0, 3))
        assertEquals(0, Cycle.pointerAfter(2, 3))
        assertEquals(0, Cycle.pointerAfter(0, 0))
    }

    @Test
    fun `routine overload uses its pointer`() {
        val routine = Routine(id = "r", name = "R", days = days(r, t), nextDayIndex = 0)
        assertEquals(1, Cycle.nextPendingDay(routine)!!.dayIndex)
    }

    // Derived pointer: the day after the routine's most recent session by startedAt.

    private val routine = Routine(id = "r", name = "R", days = days(t, t, r, t))

    private fun session(id: String, dayIndex: Int, startedAt: Long, routineId: String = "r") =
        WorkoutSession(id, startedAt, endedAt = startedAt + 1, routineId = routineId, routineDayIndex = dayIndex)

    private fun next(routine: Routine, logged: List<WorkoutSession>): Int? =
        Cycle.nextPendingDay(routine, Cycle.latestOf(routine.id, logged))?.dayIndex

    @Test
    fun `logging past days in order advances the cycle`() {
        val logged = mutableListOf<WorkoutSession>()
        assertEquals(0, next(routine, logged))
        logged += session("a", 0, startedAt = 1_000)
        assertEquals(1, next(routine, logged))
        logged += session("b", 1, startedAt = 2_000)
        // Day 2 is a rest day: skipped and reported.
        val nextDay = Cycle.nextPendingDay(routine, Cycle.latestOf("r", logged))!!
        assertEquals(3, nextDay.dayIndex)
        assertTrue(nextDay.planSaysRest)
        logged += session("c", 3, startedAt = 3_000)
        assertEquals(0, next(routine, logged))
    }

    @Test
    fun `logging past days out of order follows the latest start, not the latest insert`() {
        val logged = mutableListOf(session("late", 1, startedAt = 5_000))
        assertEquals(3, next(routine, logged))
        // An older day logged afterwards does not move the cycle back.
        logged += session("early", 3, startedAt = 1_000)
        assertEquals(3, next(routine, logged))
        // Other routines and free sessions are ignored.
        logged += session("other", 0, startedAt = 9_000, routineId = "x")
        logged += WorkoutSession("free", startedAt = 9_500)
        assertEquals(3, next(routine, logged))
    }

    @Test
    fun `a manual override wins until a newer session starts`() {
        val logged = mutableListOf(session("a", 0, startedAt = 1_000))
        val overridden = routine.copy(nextDayIndex = 3, nextDayIndexSetAt = 2_000)
        assertEquals(3, next(overridden, logged))
        // A past session older than the override does not cancel it.
        logged += session("past", 1, startedAt = 1_500)
        assertEquals(3, next(overridden, logged))
        // A session started after the override takes over again.
        logged += session("new", 3, startedAt = 2_500)
        assertEquals(0, next(overridden, logged))
    }

    @Test
    fun `without sessions the override (or day 0) is used`() {
        assertEquals(0, next(routine, emptyList()))
        assertEquals(1, next(routine.copy(nextDayIndex = 1, nextDayIndexSetAt = 5), emptyList()))
    }
}
