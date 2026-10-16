package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.HealthDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityStatsTest {
    // Friday 16 October 2026.
    private val today = LocalDate(2026, 10, 16)

    private fun daysAgo(n: Int) = today.minus(DatePeriod(days = n))

    /** Steps for today, yesterday, … (index = days ago); null = no data. */
    private fun steps(vararg values: Long?): List<HealthDay> =
        values.mapIndexed { i, v -> HealthDay(daysAgo(i), steps = v) }

    @Test
    fun `daily steps lists every date and keeps gaps as null`() {
        val days = listOf(HealthDay(daysAgo(2), steps = 100), HealthDay(today, steps = 5))
        assertEquals(
            listOf(daysAgo(2) to 100L, daysAgo(1) to null, today to 5L),
            ActivityStats.dailySteps(days, daysAgo(2), today),
        )
    }

    @Test
    fun `the 7-day average skips today and days without data`() {
        // Today 20 000 (ignored), then 7 complete days with one missing and one 8 days ago (ignored).
        val days = steps(20_000, 6_000, 8_000, null, 10_000, 4_000, 6_000, 8_000, 100_000)
        assertEquals(7_000.0, ActivityStats.sevenDayAverage(days, today))
    }

    @Test
    fun `no data means no average, not zero`() {
        assertNull(ActivityStats.sevenDayAverage(emptyList(), today))
        assertNull(ActivityStats.sevenDayAverage(steps(5_000, null, null), today))
        assertEquals(0.0, ActivityStats.sevenDayAverage(steps(5_000, 0), today))
    }

    @Test
    fun `best day is the highest, the latest on a tie, inside the range`() {
        val days = steps(3_000, 9_000, 2_000, 9_000, 50_000)
        assertEquals(DatedValue(daysAgo(1), 9_000), ActivityStats.best(days, daysAgo(3), today))
        assertEquals(DatedValue(daysAgo(4), 50_000), ActivityStats.best(days, daysAgo(10), today))
        assertNull(ActivityStats.best(steps(null, null), daysAgo(3), today))
    }

    @Test
    fun `days at goal count the days that reached it`() {
        val days = steps(8_000, 7_999, null, 12_000)
        assertEquals(2, ActivityStats.daysAtGoal(days, daysAgo(6), today, 8_000))
        assertEquals(1, ActivityStats.daysAtGoal(days, daysAgo(2), today, 8_000))
    }

    @Test
    fun `the current streak ends yesterday while today is still below the goal`() {
        val days = steps(1_000, 9_000, 8_000, 10_000, 3_000, 9_000)
        assertEquals(3, ActivityStats.currentStreak(days, today, 8_000))
    }

    @Test
    fun `today extends the streak once it reaches the goal`() {
        assertEquals(4, ActivityStats.currentStreak(steps(8_500, 9_000, 8_000, 10_000, 3_000), today, 8_000))
        assertEquals(1, ActivityStats.currentStreak(steps(8_500, 2_000), today, 8_000))
    }

    @Test
    fun `a day without data breaks the streak`() {
        assertEquals(1, ActivityStats.currentStreak(steps(0, 9_000, null, 9_000, 9_000), today, 8_000))
        assertEquals(0, ActivityStats.currentStreak(steps(0, 7_000), today, 8_000))
        assertEquals(0, ActivityStats.currentStreak(emptyList(), today, 8_000))
    }

    @Test
    fun `the longest streak finds the longest run of consecutive dates`() {
        val days = steps(9_000, 9_000, null, 9_000, 9_000, 9_000, 1, 9_000)
        assertEquals(3, ActivityStats.longestStreak(days, 8_000))
        assertEquals(0, ActivityStats.longestStreak(steps(1, 2), 8_000))
        // Unsorted input with a gap of one day.
        val gaps = listOf(HealthDay(daysAgo(0), steps = 9_000), HealthDay(daysAgo(2), steps = 9_000))
        assertEquals(1, ActivityStats.longestStreak(gaps, 8_000))
    }

    @Test
    fun `weekly totals are Monday to Sunday, latest first, the current week partial`() {
        // Monday 12 October .. Friday 16 October is the current week.
        val days = listOf(
            HealthDay(LocalDate(2026, 10, 12), steps = 1_000),
            HealthDay(LocalDate(2026, 10, 16), steps = 3_000),
            HealthDay(LocalDate(2026, 10, 11), steps = 7_000), // Sunday of the week before.
            HealthDay(LocalDate(2026, 10, 5), steps = 5_000),
            HealthDay(LocalDate(2026, 10, 6), steps = null),
        )
        val weeks = ActivityStats.weeklyTotals(days, today, 3)
        assertEquals(LocalDate(2026, 10, 12), weeks[0].weekStart)
        assertEquals(WeeklySteps(LocalDate(2026, 10, 12), 4_000, 2, 2_000.0), weeks[0])
        assertEquals(WeeklySteps(LocalDate(2026, 10, 5), 12_000, 2, 6_000.0), weeks[1])
        assertEquals(WeeklySteps(LocalDate(2026, 9, 28), 0, 0, null), weeks[2])
    }

    @Test
    fun `the week starts on Monday, also from a Sunday or a Monday`() {
        assertEquals(LocalDate(2026, 10, 12), ActivityStats.weekStart(LocalDate(2026, 10, 18)))
        assertEquals(LocalDate(2026, 10, 12), ActivityStats.weekStart(LocalDate(2026, 10, 12)))
    }

    @Test
    fun `series skip days without the value and are oldest first`() {
        val days = listOf(
            HealthDay(today, restingHeartRate = 55),
            HealthDay(daysAgo(1), restingHeartRate = null),
            HealthDay(daysAgo(2), restingHeartRate = 60),
        )
        assertEquals(listOf(daysAgo(2) to 60.0, today to 55.0), ActivityStats.series(days) { it.restingHeartRate })
    }

    @Test
    fun `average before works for sleep and heart rate`() {
        val days = listOf(
            HealthDay(today, sleepMinutes = 600),
            HealthDay(daysAgo(1), sleepMinutes = 420),
            HealthDay(daysAgo(2), sleepMinutes = 480),
        )
        assertEquals(450.0, ActivityStats.averageBefore(days, today) { it.sleepMinutes })
    }

    @Test
    fun `energy balance compares only days with both food and burned calories`() {
        val eaten = mapOf(daysAgo(0) to 2_500.0, daysAgo(1) to 1_800.0, daysAgo(3) to 2_000.0, daysAgo(4) to 0.0)
        val health = listOf(
            HealthDay(daysAgo(0), totalKcal = 2_300.0),
            HealthDay(daysAgo(1), totalKcal = 2_400.0),
            HealthDay(daysAgo(2), totalKcal = 2_200.0), // Nothing logged.
            HealthDay(daysAgo(3), totalKcal = null), // No platform data.
            HealthDay(daysAgo(4), totalKcal = 2_000.0), // Logged 0: not a real log.
        )
        val days = EnergyBalance.days(eaten, health)
        assertEquals(listOf(daysAgo(1), daysAgo(0)), days.map { it.date })
        assertEquals(200.0, days.last().balance)
        assertEquals(-600.0, days.first().balance)
        val summary = EnergyBalance.summary(days)!!
        assertEquals(2, summary.days)
        assertEquals(2_150.0, summary.averageEaten)
        assertEquals(2_350.0, summary.averageBurned)
        assertEquals(-200.0, summary.averageBalance)
        assertNull(EnergyBalance.summary(emptyList()))
    }
}
