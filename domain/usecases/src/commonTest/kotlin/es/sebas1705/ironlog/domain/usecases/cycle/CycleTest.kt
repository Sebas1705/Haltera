package es.sebas1705.ironlog.domain.usecases.cycle

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
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
}
