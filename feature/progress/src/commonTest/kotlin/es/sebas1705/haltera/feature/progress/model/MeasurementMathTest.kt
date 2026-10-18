package es.sebas1705.haltera.feature.progress.model

import es.sebas1705.haltera.core.units.LengthUnit
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.MeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementMathTest {
    private val day = 86_400_000L

    private fun m(id: String, at: Long, type: MeasurementType, value: Long, name: String = "") =
        BodyMeasurement(id, at, type, value, name)

    @Test
    fun `no measurements give no series`() {
        assertTrue(MeasurementMath.series(emptyList()).isEmpty())
        assertTrue(MeasurementMath.customNames(emptyList()).isEmpty())
        assertNull(MeasurementMath.change(emptyList()))
    }

    @Test
    fun `one series per type and custom name, fixed types first in their order, customs by name`() {
        val all = listOf(
            m("1", 10, MeasurementType.BODY_FAT, 185),
            m("2", 20, MeasurementType.CUSTOM, 370, "calf"),
            m("3", 30, MeasurementType.WAIST, 820),
            m("4", 40, MeasurementType.CUSTOM, 371, "  Calf "), // same series as "calf"
            m("5", 50, MeasurementType.CUSTOM, 200, "Antebrazo"),
            m("6", 60, MeasurementType.WAIST, 815),
        )
        val series = MeasurementMath.series(all)
        assertEquals(
            listOf("WAIST", "BODY_FAT", "CUSTOM:antebrazo", "CUSTOM:calf"),
            series.map { it.key },
        )
        val calf = series.single { it.key == "CUSTOM:calf" }
        assertEquals("  Calf ", calf.customName) // the latest spelling (the repository stores it normalised)
        assertEquals(listOf("4", "2"), calf.entries.map { it.id })
        val waist = series.first()
        assertEquals(listOf(30L, 60L), waist.points.map { it.x }) // oldest first
        assertEquals(listOf(820.0, 815.0), waist.points.map { it.y })
        assertEquals(815L, waist.latest.value)
        assertEquals(listOf("Antebrazo", "  Calf "), MeasurementMath.customNames(all))
    }

    @Test
    fun `same instant entries keep a stable order`() {
        val all = listOf(m("b", 10, MeasurementType.ARM, 351), m("a", 10, MeasurementType.ARM, 350))
        val arm = MeasurementMath.series(all).single()
        assertEquals(listOf("a", "b"), arm.entries.map { it.id })
        assertEquals(listOf("b", "a"), arm.points.map { p -> all.first { it.value.toDouble() == p.y }.id })
        assertEquals(MeasurementMath.series(all.reversed()), MeasurementMath.series(all))
    }

    @Test
    fun `the 30-day change is the latest minus the oldest entry within 30 days before it`() {
        val now = 100 * day
        val entries = listOf(
            m("old", now - 31 * day, MeasurementType.WAIST, 900), // outside the window
            m("edge", now - 30 * day, MeasurementType.WAIST, 850), // exactly 30 days: inside
            m("mid", now - 10 * day, MeasurementType.WAIST, 830),
            m("now", now, MeasurementType.WAIST, 820),
        )
        assertEquals(-30L, MeasurementMath.change(entries))
        assertNull(MeasurementMath.change(entries.take(1)))
        assertNull(MeasurementMath.change(listOf(entries[0], entries[3]))) // only one older than 30 days
        assertEquals(
            0L,
            MeasurementMath.change(
                listOf(m("a", now, MeasurementType.NECK, 400), m("b", now, MeasurementType.NECK, 400))
            )
        )
    }

    @Test
    fun `values are parsed in the shown unit and checked against the type`() {
        assertEquals(825L, MeasurementMath.parseValue("82,5", MeasurementType.WAIST, LengthUnit.CM))
        assertEquals(826L, MeasurementMath.parseValue("32.5", MeasurementType.WAIST, LengthUnit.IN))
        assertEquals(185L, MeasurementMath.parseValue("18,5", MeasurementType.BODY_FAT, LengthUnit.IN)) // % in any unit
        assertEquals(5_000L, MeasurementMath.parseValue("500", MeasurementType.CUSTOM, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("500.1", MeasurementType.CUSTOM, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("0", MeasurementType.ARM, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("0.04", MeasurementType.ARM, LengthUnit.CM)) // rounds to 0 mm
        assertNull(MeasurementMath.parseValue("-5", MeasurementType.ARM, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("100.1", MeasurementType.BODY_FAT, LengthUnit.CM))
        assertEquals(1_000L, MeasurementMath.parseValue("100", MeasurementType.BODY_FAT, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("", MeasurementType.NECK, LengthUnit.CM))
        assertNull(MeasurementMath.parseValue("abc", MeasurementType.NECK, LengthUnit.CM))
    }

    @Test
    fun `the input text of a stored value saves back to the same value`() {
        val cases = listOf(MeasurementType.WAIST, MeasurementType.BODY_FAT).flatMap { type ->
            LengthUnit.entries.flatMap { unit -> listOf("en", "es").map { language -> Triple(type, unit, language) } }
        }
        for ((type, unit, language) in cases) {
            for (value in listOf(1L, 9L, 10L, 825L, 999L, 1_000L)) {
                val text = MeasurementMath.inputText(value, type, unit, language)
                assertEquals(value, MeasurementMath.parseValue(text, type, unit), "$value $type $unit $language: $text")
            }
        }
        assertEquals("82,5", MeasurementMath.inputText(825, MeasurementType.WAIST, LengthUnit.CM, "es"))
        assertEquals("32.48", MeasurementMath.inputText(825, MeasurementType.WAIST, LengthUnit.IN, "en"))
        assertEquals("1000", MeasurementMath.inputText(10_000, MeasurementType.WAIST, LengthUnit.CM, "en"))
        assertEquals("18.5", MeasurementMath.inputText(185, MeasurementType.BODY_FAT, LengthUnit.IN, "en"))
    }

    @Test
    fun `charts show centimetres, inches or percent`() {
        assertEquals(82.5, MeasurementMath.toShown(825.0, MeasurementType.WAIST, LengthUnit.CM))
        assertEquals(10.0, MeasurementMath.toShown(254.0, MeasurementType.WAIST, LengthUnit.IN), 1e-9)
        assertEquals(18.5, MeasurementMath.toShown(185.0, MeasurementType.BODY_FAT, LengthUnit.IN))
    }
}
