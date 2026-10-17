package es.sebas1705.haltera.health

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

/**
 * "Connected · last read …" (QA pre-alpha.5 showed "2026-10-17 01:39"): the time of the last Health Connect read in
 * the app language, with the real en and es resource strings.
 */
@OptIn(ExperimentalTime::class)
class LastReadFormatTest {
    private val zone = TimeZone.of("Europe/Madrid")
    private val now = millis(2026, 10, 17, 9, 30)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun format(locale: String) = Resources(locale).let {
        LastReadFormat(
            months = it.array("health_months_short"),
            todayPattern = it.string("health_last_read_today"),
            yesterdayPattern = it.string("health_last_read_yesterday"),
            datePattern = it.string("health_last_read_date"),
            dateYearPattern = it.string("health_last_read_date_year"),
            timeZone = zone,
        )
    }

    private val en = format("en")
    private val es = format("es")

    @Test
    fun `today shows the time only`() {
        val at = millis(2026, 10, 17, 1, 39)
        assertEquals("today, 01:39", en.format(at, now))
        assertEquals("hoy, 01:39", es.format(at, now))
        assertEquals("Connected · last read today, 01:39", Resources("en").fill("health_last_read", en.format(at, now)))
        assertEquals(
            "Conectado · última lectura hoy, 01:39",
            Resources("es").fill("health_last_read", es.format(at, now)),
        )
    }

    @Test
    fun `yesterday is said in words`() {
        val at = millis(2026, 10, 16, 23, 5)
        assertEquals("yesterday, 23:05", en.format(at, now))
        assertEquals("ayer, 23:05", es.format(at, now))
    }

    @Test
    fun `an older day of this year shows the short date without the year`() {
        val at = millis(2026, 1, 3, 18, 0)
        assertEquals("Jan 3, 18:00", en.format(at, now))
        assertEquals("3 ene, 18:00", es.format(at, now))
    }

    @Test
    fun `another year shows the year`() {
        val at = millis(2025, 12, 30, 9, 7)
        assertEquals("Dec 30, 2025, 09:07", en.format(at, now))
        assertEquals("30 dic 2025, 09:07", es.format(at, now))
    }

    @Test
    fun `days follow the device time zone, not UTC`() {
        // 00:10 in Madrid is still the previous day in UTC.
        val at = millis(2026, 10, 17, 0, 10)
        assertEquals("today, 00:10", en.format(at, now))
        val utc = LastReadFormat(
            months = Resources("en").array("health_months_short"),
            todayPattern = "today, %1\$s",
            yesterdayPattern = "yesterday, %1\$s",
            datePattern = "%2\$s %1\$d, %3\$s",
            dateYearPattern = "%2\$s %1\$d, %3\$d, %4\$s",
            timeZone = TimeZone.UTC,
        )
        assertEquals("yesterday, 22:10", utc.format(at, now))
    }

    @Test
    fun `a read in the future (clock changed) shows its date`() {
        val at = millis(2026, 10, 18, 7, 0)
        assertEquals("Oct 18, 07:00", en.format(at, now))
        assertEquals("18 oct, 07:00", es.format(at, now))
    }

    @Test
    fun `both languages have twelve months`() {
        assertEquals(12, Resources("en").array("health_months_short").size)
        assertEquals(12, Resources("es").array("health_months_short").size)
    }
}

/** The composeApp strings of one locale ("en" = values, "es" = values-es), read from the repository. */
private class Resources(locale: String) {
    private val xml: String = run {
        val folder = if (locale == "en") "values" else "values-$locale"
        val path = "composeApp/src/commonMain/composeResources/$folder/strings.xml"
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .first { it.exists() }
            .readText()
    }

    fun string(name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>").find(xml)?.groupValues?.get(1)
            ?: error("missing string $name")

    fun array(name: String): List<String> {
        val block = xml.substringAfter("<string-array name=\"$name\">").substringBefore("</string-array>")
        return Regex("<item>(.*?)</item>").findAll(block).map { it.groupValues[1] }.toList()
    }

    fun fill(name: String, vararg args: String): String =
        args.foldIndexed(string(name)) { i, text, arg -> text.replace("%${i + 1}\$s", arg) }
}
