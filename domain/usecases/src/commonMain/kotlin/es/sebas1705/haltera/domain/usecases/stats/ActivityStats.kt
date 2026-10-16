package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.HealthDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A value on a date. */
data class DatedValue(val date: LocalDate, val value: Long)

/**
 * Steps of one Monday-to-Sunday week.
 *
 * @property daysWithData Days of the week with a step count (the average is over them).
 */
data class WeeklySteps(val weekStart: LocalDate, val total: Long, val daysWithData: Int, val average: Double?)

/**
 * Activity statistics from the daily totals read from the health platform (Progress → Activity). Days without
 * data are "unknown", never zero: they are left out of averages and break streaks. Today is still in progress:
 * it is left out of averages and only extends a streak once it reaches the goal.
 */
@Suppress("TooManyFunctions") // One function per statistic of the Activity tab.
object ActivityStats {
    private const val WEEK = 7

    /** Every date in [from, to] with its steps, null where there is no data. */
    fun dailySteps(days: List<HealthDay>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Long?>> {
        val byDate = days.associate { it.date to it.steps }
        return dates(from, to).map { it to byDate[it] }
    }

    /** Mean of [selector] over the [count] complete days before [today] that have data; null when none has. */
    fun averageBefore(
        days: List<HealthDay>,
        today: LocalDate,
        count: Int = WEEK,
        selector: (HealthDay) -> Number?
    ): Double? {
        val from = today.minus(DatePeriod(days = count))
        val values = days.filter { it.date >= from && it.date < today }.mapNotNull { selector(it)?.toDouble() }
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    /** Mean steps of the 7 complete days before [today] (Metric.STEPS_AVERAGE). */
    fun sevenDayAverage(days: List<HealthDay>, today: LocalDate): Double? = averageBefore(days, today) { it.steps }

    /** The day with the most steps in [from, to] (ties: the latest), or null without data (Metric.STEPS_BEST). */
    fun best(days: List<HealthDay>, from: LocalDate, to: LocalDate): DatedValue? =
        days.filter { it.date in from..to && it.steps != null }
            .maxWithOrNull(compareBy<HealthDay> { it.steps }.thenBy { it.date })
            ?.let { DatedValue(it.date, it.steps!!) }

    /** Days in [from, to] that reached [goal] steps. */
    fun daysAtGoal(days: List<HealthDay>, from: LocalDate, to: LocalDate, goal: Int): Int =
        days.count { it.date in from..to && (it.steps ?: 0) >= goal }

    /**
     * Consecutive days that reached [goal], ending today if today already did, else yesterday (Metric.STEPS_STREAK).
     * A day without data ends the streak.
     */
    fun currentStreak(days: List<HealthDay>, today: LocalDate, goal: Int): Int {
        val reached = days.filter { (it.steps ?: 0) >= goal }.map { it.date }.toSet()
        var day = if (today in reached) today else today.minus(ONE_DAY)
        var streak = 0
        while (day in reached) {
            streak++
            day = day.minus(ONE_DAY)
        }
        return streak
    }

    /** The longest run of consecutive days that reached [goal]. */
    fun longestStreak(days: List<HealthDay>, goal: Int): Int {
        val reached = days.filter { (it.steps ?: 0) >= goal }.map { it.date }.sorted()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in reached) {
            run = if (previous != null && previous.plus(ONE_DAY) == date) run + 1 else 1
            longest = maxOf(longest, run)
            previous = date
        }
        return longest
    }

    /**
     * Step totals of the [weeks] weeks up to the one containing [today], latest first (Metric.STEPS_WEEKLY).
     * The current week is partial.
     */
    fun weeklyTotals(days: List<HealthDay>, today: LocalDate, weeks: Int): List<WeeklySteps> {
        val thisWeek = weekStart(today)
        return (0 until weeks).map { i ->
            val start = thisWeek.minus(DatePeriod(days = WEEK * i))
            val end = start.plus(DatePeriod(days = WEEK - 1))
            val values = days.filter { it.date in start..end }.mapNotNull { it.steps }
            WeeklySteps(start, values.sum(), values.size, values.takeIf { it.isNotEmpty() }?.average())
        }
    }

    /** Values of [selector] by date, oldest first, days without data left out (trend charts). */
    fun series(days: List<HealthDay>, selector: (HealthDay) -> Number?): List<Pair<LocalDate, Double>> =
        days.sortedBy { it.date }.mapNotNull { d -> selector(d)?.let { d.date to it.toDouble() } }

    /** The Monday of [date]'s week. */
    fun weekStart(date: LocalDate): LocalDate =
        date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber))

    private fun dates(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plus(ONE_DAY) }.takeWhile { it <= to }.toList()

    private val ONE_DAY = DatePeriod(days = 1)
}

/**
 * Calories eaten (Nutrition log) against calories burned (health platform) per day. Only days with both are
 * compared, so a day without a log or without platform data does not look like a deficit.
 */
object EnergyBalance {
    /** One day with both values; [balance] = eaten − burned (negative = more burned). */
    data class Day(val date: LocalDate, val eatenKcal: Double, val burnedKcal: Double) {
        val balance: Double get() = eatenKcal - burnedKcal
    }

    /** Average of the compared days. */
    data class Summary(val days: Int, val averageEaten: Double, val averageBurned: Double) {
        val averageBalance: Double get() = averageEaten - averageBurned
    }

    /** Days where something was eaten and the platform reported total calories burned, oldest first. */
    fun days(eaten: Map<LocalDate, Double>, health: List<HealthDay>): List<Day> =
        health.mapNotNull { h ->
            val burned = h.totalKcal?.takeIf { it > 0 } ?: return@mapNotNull null
            val food = eaten[h.date]?.takeIf { it > 0 } ?: return@mapNotNull null
            Day(h.date, food, burned)
        }.sortedBy { it.date }

    fun summary(days: List<Day>): Summary? = days.takeIf { it.isNotEmpty() }?.let { d ->
        Summary(d.size, d.map { it.eatenKcal }.average(), d.map { it.burnedKcal }.average())
    }
}
