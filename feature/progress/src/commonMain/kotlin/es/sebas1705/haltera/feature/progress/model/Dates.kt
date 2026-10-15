package es.sebas1705.ironlog.feature.progress.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val MONTHS_PER_YEAR = 12

/** Current time and time zone, injectable so tests are deterministic. */
@OptIn(ExperimentalTime::class)
internal class ProgressClock(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun now(): Long = nowMillis()

    fun today(): LocalDate = now().toLocalDate(timeZone)
}

@OptIn(ExperimentalTime::class)
internal fun Long.toLocalDate(timeZone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date

@OptIn(ExperimentalTime::class)
internal fun LocalDate.startOfDayMillis(timeZone: TimeZone): Long = atStartOfDayIn(timeZone).toEpochMilliseconds()

/** The Monday of this date's week. */
internal fun LocalDate.weekStart(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** A calendar month; [month] is 1-based. */
internal data class MonthKey(val year: Int, val month: Int) {
    val firstDay: LocalDate get() = LocalDate(year, month, 1)

    /** Number of days in the month. */
    val length: Int get() = plus(1).firstDay.minus(1, DateTimeUnit.DAY).day

    /** Blank cells before day 1 in a Monday-first week grid. */
    val leadingBlanks: Int get() = firstDay.dayOfWeek.isoDayNumber - 1

    fun plus(months: Int): MonthKey {
        val index = year * MONTHS_PER_YEAR + (month - 1) + months
        return MonthKey(index.floorDiv(MONTHS_PER_YEAR), index.mod(MONTHS_PER_YEAR) + 1)
    }

    operator fun contains(date: LocalDate): Boolean = date.year == year && date.month.number == month

    fun day(day: Int): LocalDate = LocalDate(year, month, day)

    companion object {
        fun of(date: LocalDate) = MonthKey(date.year, date.month.number)
    }
}
