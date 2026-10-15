package es.sebas1705.ironlog.domain.usecases.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeatmapAndRegressionTest {

    @Test
    fun `heatmap columns are calendar weeks ending with this week`() {
        val map = Heatmaps.build(emptyList(), TODAY, weeks = 13)
        assertEquals(13, map.weeks.size)
        assertTrue(map.weeks.all { it.size == 7 })
        val monday = EpochDays.weekStart(TODAY)
        assertEquals(monday - 12 * 7, map.weeks.first().first().day)
        assertEquals(monday + 6, map.weeks.last().last().day)
        // Wednesday is today: Thursday to Sunday are in the future.
        assertEquals(listOf(false, false, false, true, true, true, true), map.weeks.last().map { it.future })
        assertEquals(0, map.trainingDays)
        assertFailsWith<IllegalArgumentException> { Heatmaps.build(emptyList(), TODAY, weeks = 0) }
    }

    @Test
    fun `heatmap buckets sessions by day and levels by quartile`() {
        fun sets(n: Int) = Array(n) { lift(50.0, 10) }
        val sessions = listOf(
            dated("a", TODAY, move("x", *sets(4))),
            dated("b", TODAY - 1, move("x", *sets(8))),
            dated("c", TODAY - 1, move("x", *sets(4))), // same day: 12 sets, 2 sessions
            dated("d", TODAY - 2, move("x", *sets(16))),
            dated("e", TODAY - 3, move("x", lift(50.0, 10, completed = false))), // no effective set
            dated("f", TODAY - 200, move("x", *sets(30))), // outside the map
            dated("g", TODAY - 4, move("x", *sets(30)), minutes = null), // in progress
        )
        val map = Heatmaps.build(sessions, TODAY)
        val cells = map.weeks.flatten().associateBy { it.day }
        assertEquals(listOf(4, 12, 16), map.thresholds) // quartiles of 4, 12, 16
        assertEquals(1, cells.getValue(TODAY).level)
        assertEquals(2, cells.getValue(TODAY - 1).sessions)
        assertEquals(12, cells.getValue(TODAY - 1).effectiveSets)
        assertEquals(2, cells.getValue(TODAY - 1).level)
        assertEquals(3, cells.getValue(TODAY - 2).level)
        assertEquals(1, cells.getValue(TODAY - 3).level)
        assertEquals(0, cells.getValue(TODAY - 4).level)
        assertEquals(4, map.trainingDays)
    }

    @Test
    fun `quartiles and levels`() {
        assertEquals(emptyList(), Heatmaps.quartiles(emptyList()))
        assertEquals(listOf(2, 4, 6), Heatmaps.quartiles(listOf(8, 6, 4, 2)))
        assertEquals(listOf(5, 5, 5), Heatmaps.quartiles(listOf(5)))
        assertEquals(4, Heatmaps.level(1, 9, listOf(2, 4, 6)))
        assertEquals(0, Heatmaps.level(0, 9, listOf(2, 4, 6)))
        assertEquals(13, Heatmaps.weeksFor(StatsPeriod.FOUR_WEEKS))
        assertEquals(13, Heatmaps.weeksFor(StatsPeriod.THREE_MONTHS))
        assertEquals(52, Heatmaps.weeksFor(StatsPeriod.TWELVE_MONTHS))
        assertEquals(53, Heatmaps.weeksFor(StatsPeriod.ALL))
    }

    @Test
    fun `least squares fits a line`() {
        val trend = assertNotNull(LinearRegression.fit(listOf(0.0 to 1.0, 1.0 to 3.0, 2.0 to 5.0)))
        assertEquals(2.0, trend.slope, 1e-9)
        assertEquals(1.0, trend.intercept, 1e-9)
        assertEquals(1.0, trend.r2, 1e-9)
        assertEquals(7.0, trend.at(3.0), 1e-9)

        val noisy = assertNotNull(LinearRegression.fit(listOf(0.0 to 0.0, 1.0 to 2.0, 2.0 to 1.0, 3.0 to 3.0)))
        assertEquals(0.8, noisy.slope, 1e-9)
        assertEquals(0.64, noisy.r2, 1e-9)

        val flat = assertNotNull(LinearRegression.fit(listOf(0.0 to 4.0, 5.0 to 4.0)))
        assertEquals(0.0, flat.slope, 1e-9)
        assertEquals(1.0, flat.r2)

        assertNull(LinearRegression.fit(listOf(1.0 to 2.0)))
        assertNull(LinearRegression.fit(listOf(1.0 to 2.0, 1.0 to 3.0)))
    }
}
