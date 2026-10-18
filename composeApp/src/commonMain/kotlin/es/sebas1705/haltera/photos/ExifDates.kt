package es.sebas1705.haltera.photos

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/** EXIF dates of a picked photo ("2026:10:14 18:30:05", local time of the camera, optional offset "+02:00"). */
@OptIn(ExperimentalTime::class)
internal object ExifDates {
    private val DATE = Regex("""(\d{4}):(\d{2}):(\d{2})[ T](\d{2}):(\d{2}):(\d{2})""")
    private val OFFSET = Regex("""([+-])(\d{2}):(\d{2})""")
    private const val MAX_OFFSET_HOURS = 18
    private const val DATE_PARTS = 3

    /**
     * Epoch millis of [text] (null when it is missing, blank, zeroed or not a real date). Without [offset] the time is
     * read in [timeZone], the device's: the camera wrote local time.
     */
    fun parse(text: String?, offset: String? = null, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long? {
        val match = DATE.matchEntire(text?.trim().orEmpty()) ?: return null
        val numbers = match.groupValues.drop(1).map { it.toInt() }
        val date = numbers.take(DATE_PARTS)
        val time = numbers.drop(DATE_PARTS)
        val local = runCatching {
            LocalDateTime(LocalDate(date[0], date[1], date[2]), LocalTime(time[0], time[1], time[2]))
        }.getOrNull() ?: return null
        val utcOffset = offset?.trim()?.let(OFFSET::matchEntire)?.let { m ->
            val (sign, oh, om) = m.destructured
            val hours = oh.toInt()
            if (hours > MAX_OFFSET_HOURS) return@let null
            val factor = if (sign == "-") -1 else 1
            runCatching { UtcOffset(hours = factor * hours, minutes = factor * om.toInt()) }.getOrNull()
        }
        return if (utcOffset != null) {
            local.toInstant(utcOffset).toEpochMilliseconds()
        } else {
            local.toInstant(timeZone).toEpochMilliseconds()
        }
    }
}
