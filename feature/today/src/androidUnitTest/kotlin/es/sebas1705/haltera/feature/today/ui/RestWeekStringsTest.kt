package es.sebas1705.haltera.feature.today.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The weekly rest line of a flexible routine with the real en and es strings (QA pre-alpha.5: "5 rest days" next to
 * a week strip with 3 trained days read as a contradiction). Every sentence names the routine, and the info line
 * says that free sessions and other routines do not count as its training days.
 */
class RestWeekStringsTest {
    private val en = Strings("values")
    private val es = Strings("values-es")

    @Test
    fun `every sentence names the routine in both languages`() {
        assertEquals("Rest days from “PPL” this week: 1 of 3", en.string("today_rest_week", 1, 3, "PPL"))
        assertEquals("Días de descanso de «PPL» esta semana: 1 de 3", es.string("today_rest_week", 1, 3, "PPL"))

        assertEquals("5 rest days from “PPL” this week (target 4)", en.plural("today_rest_week_over", 5, 5, 4, "PPL"))
        assertEquals("1 rest day from “PPL” this week (target 0)", en.plural("today_rest_week_over", 1, 1, 0, "PPL"))
        assertEquals(
            "5 días de descanso de «PPL» esta semana (objetivo: 4)",
            es.plural("today_rest_week_over", 5, 5, 4, "PPL"),
        )

        assertEquals(
            "2 rest days from “PPL” this week (none planned)",
            en.plural("today_rest_week_unplanned", 2, 2, "PPL")
        )
        assertEquals(
            "1 día de descanso de «PPL» esta semana (no tenías ninguno previsto)",
            es.plural("today_rest_week_unplanned", 1, 1, "PPL"),
        )

        assertEquals("No rest days from “PPL” planned this week", en.string("today_rest_week_none", "PPL"))
        assertEquals("Sin días de descanso de «PPL» previstos esta semana", es.string("today_rest_week_none", "PPL"))
    }

    @Test
    fun `days with other training are said apart`() {
        assertEquals(
            "You trained outside this routine on 2 of those days.",
            en.plural("today_rest_week_other_training", 2, 2),
        )
        assertEquals(
            "En 1 de esos días entrenaste fuera de esta rutina.",
            es.plural("today_rest_week_other_training", 1, 1)
        )
    }

    @Test
    fun `the info line explains what a rest day of the routine is`() {
        val info = en.string("today_rest_week_info")
        assertTrue("free sessions and other routines do not count" in info, info)
        val infoEs = es.string("today_rest_week_info")
        assertTrue("las sesiones libres y las de otras rutinas no cuentan" in infoEs, infoEs)
    }

    @Test
    fun `both languages use the same placeholders`() {
        val keys = listOf(
            "today_rest_week",
            "today_rest_week_over",
            "today_rest_week_unplanned",
            "today_rest_week_none",
            "today_rest_week_other_training",
            "today_rest_week_info",
        )
        keys.forEach { key -> assertEquals(en.placeholders(key), es.placeholders(key), key) }
    }
}

/** The feature:today strings of one resource folder, read from the repository (module directory or root). */
private class Strings(folder: String) {
    private val xml: String = run {
        val path = "src/commonMain/composeResources/$folder/strings.xml"
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, path), File(it, "feature/today/$path")) }
            .first { it.exists() }
            .readText()
    }

    private fun raw(name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>").find(xml)?.groupValues?.get(1)
            ?: error("missing string $name")

    /** The `one` item for a count of 1, else `other` (en and es plural rules). */
    private fun rawPlural(name: String, count: Int): String {
        val block = xml.substringAfter("<plurals name=\"$name\">").substringBefore("</plurals>")
        val quantity = if (count == 1) "one" else "other"
        return Regex("<item quantity=\"$quantity\">(.*?)</item>").find(block)?.groupValues?.get(1)
            ?: error("missing plural $name/$quantity")
    }

    private fun fill(pattern: String, args: Array<out Any>): String =
        args.foldIndexed(pattern) { i, text, arg ->
            text.replace("%${i + 1}\$s", arg.toString()).replace("%${i + 1}\$d", arg.toString())
        }

    fun string(name: String, vararg args: Any): String = fill(raw(name), args)

    fun plural(name: String, count: Int, vararg args: Any): String = fill(rawPlural(name, count), args)

    /** The placeholders of a string or of every item of a plural, sorted. */
    fun placeholders(name: String): List<String> {
        val text = if ("<plurals name=\"$name\">" in xml) {
            xml.substringAfter("<plurals name=\"$name\">").substringBefore("</plurals>")
        } else {
            raw(name)
        }
        return Regex("%\\d\\$[sd]").findAll(text).map { it.value }.toList().sorted()
    }
}
