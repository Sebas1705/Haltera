package es.sebas1705.haltera.core.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LengthsTest {
    @Test
    fun `the length unit follows the weight unit`() {
        assertEquals(LengthUnit.CM, LengthUnit.forWeightUnit(WeightUnit.KG))
        assertEquals(LengthUnit.IN, LengthUnit.forWeightUnit(WeightUnit.LB))
    }

    @Test
    fun `centimetres and inches convert to millimetres`() {
        assertEquals(825L, Lengths.toMillimetres(82.5, LengthUnit.CM))
        assertEquals(254L, Lengths.toMillimetres(10.0, LengthUnit.IN))
        assertEquals(826L, Lengths.toMillimetres(32.5, LengthUnit.IN)) // 825.5 rounds up
        assertEquals(82.5, Lengths.fromMillimetres(825, LengthUnit.CM))
        assertEquals(10.0, Lengths.fromMillimetres(254, LengthUnit.IN), 1e-9)
        assertEquals(0L, Lengths.toMillimetres(0.0, LengthUnit.CM))
    }

    @Test
    fun `format shows one decimal in cm and two in inches without trailing zeros`() {
        assertEquals("82.5", Lengths.format(825, LengthUnit.CM, language = "en"))
        assertEquals("82,5", Lengths.format(825, LengthUnit.CM, language = "es"))
        assertEquals("80", Lengths.format(800, LengthUnit.CM, language = "en"))
        assertEquals("32.48", Lengths.format(825, LengthUnit.IN, language = "en"))
        assertEquals("32,48", Lengths.format(825, LengthUnit.IN, language = "es"))
        assertEquals("10", Lengths.format(254, LengthUnit.IN, language = "en"))
        assertEquals("1,000", Lengths.format(10_000, LengthUnit.CM, language = "en"))
        assertEquals("1000", Lengths.format(10_000, LengthUnit.CM, language = "en", grouping = false))
    }

    @Test
    fun `every millimetre shown and typed back is the same millimetre in both units and languages`() {
        for (unit in LengthUnit.entries) {
            for (language in listOf("en", "es")) {
                for (mm in 0L..5_000L) {
                    val text = Lengths.format(mm, unit, language = language, grouping = false)
                    assertEquals(mm, Lengths.parse(text, unit), "$mm mm in $unit ($language) shown as $text")
                }
            }
        }
    }

    @Test
    fun `parse accepts a decimal comma or point and rejects text`() {
        assertEquals(825L, Lengths.parse("82,5", LengthUnit.CM))
        assertEquals(825L, Lengths.parse(" 82.5 ", LengthUnit.CM))
        assertEquals(826L, Lengths.parse("32,5", LengthUnit.IN))
        assertNull(Lengths.parse("", LengthUnit.CM))
        assertNull(Lengths.parse("abc", LengthUnit.IN))
        assertNull(Lengths.parse("Infinity", LengthUnit.CM))
        assertNull(Lengths.parse("NaN", LengthUnit.CM))
        assertEquals(-10L, Lengths.parse("-1", LengthUnit.CM)) // range checks belong to the caller
    }

    @Test
    fun `body fat is stored in tenths of a percent`() {
        assertEquals(185L, BodyFat.toTenths(18.5))
        assertEquals(185L, BodyFat.parse("18,5"))
        assertEquals(185L, BodyFat.parse("18.54"))
        assertEquals(186L, BodyFat.parse("18.55"))
        assertEquals(1000L, BodyFat.parse("100"))
        assertEquals(18.5, BodyFat.toPercent(185))
        assertEquals("18.5", BodyFat.format(185, language = "en"))
        assertEquals("18,5", BodyFat.format(185, language = "es"))
        assertEquals("20", BodyFat.format(200, language = "en"))
        assertEquals("0.1", BodyFat.format(1, language = "en"))
        assertNull(BodyFat.parse(""))
        assertNull(BodyFat.parse("%"))
        for (tenths in 0L..1_000L) {
            for (language in listOf("en", "es")) assertEquals(tenths, BodyFat.parse(BodyFat.format(tenths, language)))
        }
    }
}
