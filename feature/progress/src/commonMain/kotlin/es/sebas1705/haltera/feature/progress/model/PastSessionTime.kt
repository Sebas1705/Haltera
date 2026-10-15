package es.sebas1705.haltera.feature.progress.model

import es.sebas1705.haltera.core.ui.components.PastSessionForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Conversions between the past-session dialog (a calendar date as UTC midnight plus a local time, as the
 * Material pickers use) and epoch millis in the device time zone.
 */
@OptIn(ExperimentalTime::class)
internal object PastSessionTime {
    private const val MINUTE_MS = 60_000L
    private const val QUARTER_MINUTES = 15
    private const val EVENING_HOUR = 18
    private const val NOON_HOUR = 12

    /** Start of the session described by [form], in [timeZone]. */
    fun startMillis(form: PastSessionForm, timeZone: TimeZone): Long =
        LocalDateTime(dateOf(form.dateUtcMillis), LocalTime(form.hour, form.minute)).toInstant(timeZone)
            .toEpochMilliseconds()

    fun durationMillis(form: PastSessionForm): Long = form.durationMinutes * MINUTE_MS

    /** [date] as the pickers want it: its UTC midnight. */
    fun utcMillis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    fun dateOf(utcMillis: Long): LocalDate = Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date

    fun localDate(millis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date

    /** Today as the date picker wants it: its UTC midnight (also the latest selectable date). */
    fun todayUtcMillis(now: Long, timeZone: TimeZone): Long = utcMillis(localDate(now, timeZone))

    /**
     * When a body-weight entry dated [dateUtcMillis] was measured: [now] if that date is today, noon of
     * that day otherwise.
     */
    fun measuredAt(dateUtcMillis: Long, now: Long, timeZone: TimeZone): Long {
        val date = dateOf(dateUtcMillis)
        if (date == localDate(now, timeZone)) return now
        return LocalDateTime(date, LocalTime(NOON_HOUR, 0)).toInstant(timeZone).toEpochMilliseconds()
    }

    /**
     * A new past session on [date]: today it starts an hour ago (on the quarter), any other day at 18:00;
     * it lasts [durationMinutes].
     */
    fun newForm(
        date: LocalDate,
        now: Long,
        timeZone: TimeZone,
        durationMinutes: Int,
        choiceKey: String?
    ): PastSessionForm {
        val anHourAgo = Instant.fromEpochMilliseconds(now - durationMinutes * MINUTE_MS).toLocalDateTime(timeZone)
        val (hour, minute) = when {
            date != localDate(now, timeZone) -> EVENING_HOUR to 0
            anHourAgo.date != date -> 0 to 0
            else -> anHourAgo.hour to anHourAgo.minute / QUARTER_MINUTES * QUARTER_MINUTES
        }
        return PastSessionForm(utcMillis(date), hour, minute, durationMinutes, choiceKey)
    }

    /** The dialog values of a saved session (an unfinished one gets [defaultMinutes]). */
    fun editForm(startedAt: Long, endedAt: Long?, timeZone: TimeZone, defaultMinutes: Int): PastSessionForm {
        val start = Instant.fromEpochMilliseconds(startedAt).toLocalDateTime(timeZone)
        val minutes = endedAt?.let { ((it - startedAt).coerceAtLeast(0) + MINUTE_MS / 2) / MINUTE_MS }?.toInt()
        return PastSessionForm(utcMillis(start.date), start.hour, start.minute, minutes ?: defaultMinutes, null)
    }
}
