package es.sebas1705.haltera.photos

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ExifDatesTest {
    private val madrid = TimeZone.of("Europe/Madrid")

    private fun at(zone: TimeZone, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime(y, mo, d, h, mi, s).toInstant(zone).toEpochMilliseconds()

    @Test
    fun `a camera date is local time of the device`() {
        assertEquals(at(madrid, 2026, 10, 14, 18, 30, 5), ExifDates.parse("2026:10:14 18:30:05", timeZone = madrid))
        assertEquals(
            at(TimeZone.UTC, 2026, 10, 14, 18, 30, 5),
            ExifDates.parse("2026:10:14 18:30:05", timeZone = TimeZone.UTC)
        )
    }

    @Test
    fun `an offset written by the camera wins over the device time zone`() {
        assertEquals(
            at(TimeZone.UTC, 2026, 10, 14, 16, 30, 5),
            ExifDates.parse("2026:10:14 18:30:05", "+02:00", madrid)
        )
        assertEquals(
            at(TimeZone.UTC, 2026, 10, 14, 23, 30, 5),
            ExifDates.parse("2026:10:14 18:30:05", "-05:00", madrid)
        )
        // A broken offset falls back to the device zone.
        assertEquals(at(madrid, 2026, 10, 14, 18, 30, 5), ExifDates.parse("2026:10:14 18:30:05", "+99:00", madrid))
        assertEquals(at(madrid, 2026, 10, 14, 18, 30, 5), ExifDates.parse("2026:10:14 18:30:05", "junk", madrid))
    }

    @Test
    fun `missing, blank, zeroed or impossible dates give nothing`() {
        assertNull(ExifDates.parse(null))
        assertNull(ExifDates.parse(""))
        assertNull(ExifDates.parse("   "))
        assertNull(ExifDates.parse("0000:00:00 00:00:00"))
        assertNull(ExifDates.parse("2026:02:30 10:00:00"))
        assertNull(ExifDates.parse("2026:13:01 10:00:00"))
        assertNull(ExifDates.parse("2026-10-14"))
        assertNull(ExifDates.parse("2026:10:14 25:00:00"))
    }

    @Test
    fun `a time inside the daylight saving gap still gives an instant`() {
        // 29 March 2026, 02:30 does not exist in Madrid; it is still read (moved by the gap), never dropped.
        val parsed = ExifDates.parse("2026:03:29 02:30:00", timeZone = madrid)
        assertEquals(true, parsed != null)
    }
}
