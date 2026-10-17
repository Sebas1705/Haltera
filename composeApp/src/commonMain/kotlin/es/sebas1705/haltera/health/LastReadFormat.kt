package es.sebas1705.haltera.health

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.health_last_read_date
import es.sebas1705.haltera.resources.health_last_read_date_year
import es.sebas1705.haltera.resources.health_last_read_today
import es.sebas1705.haltera.resources.health_last_read_yesterday
import es.sebas1705.haltera.resources.health_months_short
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * When Health Connect was last read, in the device time zone and the app language, like the other dates of the app
 * (short month names from the resources, 24-hour time): "today, 01:39", "yesterday, 23:10", "Oct 14, 18:05" /
 * "14 oct, 18:05", and the year only when it is not the current one ("Dec 30, 2025, 09:00" / "30 dic 2025, 09:00").
 * The patterns are the raw resource strings, so this is usable (and testable) outside composition.
 */
internal class LastReadFormat(
    private val months: List<String>,
    private val todayPattern: String,
    private val yesterdayPattern: String,
    private val datePattern: String,
    private val dateYearPattern: String,
    private val timeZone: TimeZone,
) {
    @OptIn(ExperimentalTime::class)
    fun format(millis: Long, nowMillis: Long): String {
        val at = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
        val time = clock(at)
        val month = months.getOrElse(at.month.number - 1) { "" }
        return when {
            at.date == today -> fill(todayPattern, time)
            at.date == today.minus(DatePeriod(days = 1)) -> fill(yesterdayPattern, time)
            at.year == today.year -> fill(datePattern, at.day.toString(), month, time)
            else -> fill(dateYearPattern, at.day.toString(), month, at.year.toString(), time)
        }
    }

    private fun clock(at: LocalDateTime) =
        "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"

    /** Replaces `%1$s`, `%2$d`… of a raw resource pattern. */
    private fun fill(pattern: String, vararg args: String): String =
        args.foldIndexed(pattern) { i, text, arg -> text.replace("%${i + 1}\$s", arg).replace("%${i + 1}\$d", arg) }
}

@Composable
internal fun rememberLastReadFormat(): LastReadFormat {
    val months = stringArrayResource(Res.array.health_months_short)
    val today = stringResource(Res.string.health_last_read_today)
    val yesterday = stringResource(Res.string.health_last_read_yesterday)
    val date = stringResource(Res.string.health_last_read_date)
    val dateYear = stringResource(Res.string.health_last_read_date_year)
    return remember(months, today, yesterday, date, dateYear) {
        LastReadFormat(months, today, yesterday, date, dateYear, TimeZone.currentSystemDefault())
    }
}
