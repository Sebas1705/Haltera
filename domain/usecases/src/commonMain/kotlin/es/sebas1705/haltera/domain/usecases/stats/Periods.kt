package es.sebas1705.ironlog.domain.usecases.stats

import kotlin.math.abs

/**
 * Calendar days are epoch days (days since 1970-01-01) in the user's time zone. The caller converts
 * timestamps to days (so the domain stays free of time zones); weeks start on Monday.
 */
object EpochDays {
    const val DAYS_PER_WEEK = 7

    /** 1970-01-01 was a Thursday: shifting by 3 makes Monday the first day of the week. */
    private const val THURSDAY_OFFSET = 3

    /** The Monday of [day]'s week. */
    fun weekStart(day: Int): Int = day - dayOfWeekIndex(day)

    /** 0 = Monday … 6 = Sunday. */
    fun dayOfWeekIndex(day: Int): Int = (day + THURSDAY_OFFSET).mod(DAYS_PER_WEEK)
}

/** An inclusive range of epoch days. */
data class DayWindow(val first: Int, val last: Int) {
    init {
        require(last >= first) { "last ($last) is before first ($first)" }
    }

    val length: Int get() = last - first + 1

    /** Length in weeks (may be fractional for the "all" period). */
    val weeks: Double get() = length.toDouble() / EpochDays.DAYS_PER_WEEK

    operator fun contains(day: Int): Boolean = day in first..last
}

/** Period choices of the statistics screens. [days] is null for "all time". */
enum class StatsPeriod(val days: Int?) {
    FOUR_WEEKS(28),
    THREE_MONTHS(91),
    TWELVE_MONTHS(364),
    ALL(null),
}

/**
 * Period windows (Metric.PERCENT_CHANGE). A period is the last N days ending today (today included);
 * the comparison period is the N days right before it, so both have exactly the same length and the
 * same number of each weekday. "All time" starts on the first logged day and has no comparison.
 */
object Periods {
    /** Buckets of the "all" period become 4 weeks long once it is longer than this many days. */
    const val LONG_SPAN_DAYS = 364
    const val WEEK_BUCKET_DAYS = 7
    const val MONTH_BUCKET_DAYS = 28

    fun current(period: StatsPeriod, today: Int, firstLoggedDay: Int?): DayWindow {
        val days = period.days
        return if (days == null) {
            DayWindow((firstLoggedDay ?: today).coerceAtMost(today), today)
        } else {
            DayWindow(today - days + 1, today)
        }
    }

    /** The equal-length window before [current]; null for "all time". */
    fun previous(period: StatsPeriod, current: DayWindow): DayWindow? =
        if (period.days == null) null else DayWindow(current.first - current.length, current.first - 1)

    /** 7-day buckets, or 28-day buckets when the window is longer than [LONG_SPAN_DAYS]. */
    fun bucketDays(window: DayWindow): Int = if (window.length > LONG_SPAN_DAYS) MONTH_BUCKET_DAYS else WEEK_BUCKET_DAYS

    /**
     * Splits [window] into consecutive buckets of [size] days counted back from its last day, oldest
     * first. The oldest bucket is shorter when the length is not a multiple of [size].
     */
    fun buckets(window: DayWindow, size: Int = bucketDays(window)): List<DayWindow> {
        require(size > 0) { "size must be positive" }
        val result = ArrayList<DayWindow>()
        var last = window.last
        while (last >= window.first) {
            val first = (last - size + 1).coerceAtLeast(window.first)
            result += DayWindow(first, last)
            last = first - 1
        }
        return result.reversed()
    }
}

/**
 * A value of this period next to the same value of the comparison period. [previous] is null when
 * there is no comparison period ("all time").
 */
data class Comparison(val current: Double, val previous: Double?) {
    val delta: Double? get() = previous?.let { current - it }

    /**
     * Relative change in percent: (current − previous) / previous × 100. Null without a comparison or
     * when the previous value is 0 (any change from nothing is not a percentage).
     */
    val percent: Double?
        get() = previous?.takeIf { abs(it) > EPSILON }?.let { (current - it) / abs(it) * PERCENT }

    val direction: Direction
        get() {
            val d = delta ?: return Direction.NONE
            return when {
                abs(d) <= EPSILON -> Direction.SAME
                d > 0 -> Direction.UP
                else -> Direction.DOWN
            }
        }

    enum class Direction { UP, DOWN, SAME, NONE }

    private companion object {
        const val EPSILON = 1e-9
        const val PERCENT = 100.0
    }
}
