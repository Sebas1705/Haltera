package es.sebas1705.ironlog.domain.usecases.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodsTest {

    @Test
    fun `weeks start on monday`() {
        assertEquals(2, EpochDays.dayOfWeekIndex(TODAY)) // Wednesday
        assertEquals(TODAY - 2, EpochDays.weekStart(TODAY))
        assertEquals(TODAY - 2, EpochDays.weekStart(TODAY - 2))
        assertEquals(TODAY + 5, EpochDays.weekStart(TODAY + 5)) // next Monday
        assertEquals(-3, EpochDays.weekStart(0)) // 1970-01-01 was a Thursday
        assertEquals(-3, EpochDays.weekStart(-1))
    }

    @Test
    fun `fixed periods end today and the previous one has the same length`() {
        val current = Periods.current(StatsPeriod.FOUR_WEEKS, TODAY, firstLoggedDay = 0)
        assertEquals(DayWindow(TODAY - 27, TODAY), current)
        assertEquals(28, current.length)
        assertEquals(DayWindow(TODAY - 55, TODAY - 28), Periods.previous(StatsPeriod.FOUR_WEEKS, current))
        assertEquals(91, Periods.current(StatsPeriod.THREE_MONTHS, TODAY, null).length)
        assertEquals(364, Periods.current(StatsPeriod.TWELVE_MONTHS, TODAY, null).length)
    }

    @Test
    fun `all time starts on the first logged day and has no comparison`() {
        val all = Periods.current(StatsPeriod.ALL, TODAY, firstLoggedDay = TODAY - 9)
        assertEquals(DayWindow(TODAY - 9, TODAY), all)
        assertNull(Periods.previous(StatsPeriod.ALL, all))
        assertEquals(DayWindow(TODAY, TODAY), Periods.current(StatsPeriod.ALL, TODAY, null))
        // A session logged in the future does not move the start after today.
        assertEquals(DayWindow(TODAY, TODAY), Periods.current(StatsPeriod.ALL, TODAY, TODAY + 3))
    }

    @Test
    fun `buckets count back from the last day and the oldest may be short`() {
        val buckets = Periods.buckets(DayWindow(1, 10), size = 7)
        assertEquals(listOf(DayWindow(1, 3), DayWindow(4, 10)), buckets)
        assertEquals(4, Periods.buckets(DayWindow(TODAY - 27, TODAY)).size)
        assertTrue(Periods.buckets(DayWindow(TODAY - 27, TODAY)).all { it.length == 7 })
    }

    @Test
    fun `long all time spans use 4-week buckets`() {
        assertEquals(7, Periods.bucketDays(DayWindow(0, 363)))
        assertEquals(28, Periods.bucketDays(DayWindow(0, 364)))
        assertFailsWith<IllegalArgumentException> { Periods.buckets(DayWindow(0, 3), size = 0) }
    }

    @Test
    fun `comparison deltas and percentages`() {
        val up = Comparison(12.0, 10.0)
        assertEquals(2.0, up.delta)
        assertEquals(20.0, up.percent!!, 1e-9)
        assertEquals(Comparison.Direction.UP, up.direction)

        val down = Comparison(5.0, 10.0)
        assertEquals(-50.0, down.percent!!, 1e-9)
        assertEquals(Comparison.Direction.DOWN, down.direction)

        assertEquals(Comparison.Direction.SAME, Comparison(3.0, 3.0).direction)
        // From nothing there is a delta but no percentage.
        assertEquals(4.0, Comparison(4.0, 0.0).delta)
        assertNull(Comparison(4.0, 0.0).percent)
        // Without a comparison period there is neither.
        assertNull(Comparison(4.0, null).delta)
        assertNull(Comparison(4.0, null).percent)
        assertEquals(Comparison.Direction.NONE, Comparison(4.0, null).direction)
    }

    @Test
    fun `day windows reject inverted ranges`() {
        assertFailsWith<IllegalArgumentException> { DayWindow(5, 4) }
        assertEquals(1.0, DayWindow(0, 6).weeks)
    }
}
