package es.sebas1705.ironlog.core.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnitsTest {
    @Test
    fun `kg and lb convert to grams and back`() {
        assertEquals(100_000L, Weights.kgToGrams(100.0))
        assertEquals(62_500L, Weights.kgToGrams(62.5))
        assertEquals(20_412L, Weights.lbToGrams(45.0))
        assertEquals(45.0, Weights.gramsToLb(Weights.lbToGrams(45.0)), 0.01)
        assertEquals(220.462, Weights.kgToLb(100.0), 0.001)
        assertEquals(100.0, Weights.lbToKg(Weights.kgToLb(100.0)), 1e-9)
        assertEquals(Weights.lbToGrams(135.0), Weights.toGrams(135.0, WeightUnit.LB))
        assertEquals(2.5, Weights.fromGrams(2_500, WeightUnit.KG))
    }

    @Test
    fun `format trims trailing zeros`() {
        assertEquals("62.5", Weights.format(62_500, WeightUnit.KG, language = "en"))
        assertEquals("60", Weights.format(60_000, WeightUnit.KG, language = "en"))
        assertEquals("1.25", Weights.format(1_250, WeightUnit.KG, language = "en"))
        assertEquals("-10", Weights.format(-10_000, WeightUnit.KG, language = "en"))
        assertEquals("45", Weights.format(Weights.lbToGrams(45.0), WeightUnit.LB, language = "en"))
    }

    @Test
    fun `format uses the decimal comma and groups thousands in Spanish`() {
        assertEquals("79,6", Weights.format(79_600, WeightUnit.KG, decimals = 1, language = "es"))
        assertEquals("1,25", Weights.format(1_250, WeightUnit.KG, language = "es"))
        assertEquals("15 090", Weights.format(15_090_000, WeightUnit.KG, decimals = 0, language = "es"))
        assertEquals("1250", Weights.format(1_250_000, WeightUnit.KG, decimals = 0, language = "es"))
        assertEquals("15,090", Weights.format(15_090_000, WeightUnit.KG, decimals = 0, language = "en"))
        assertEquals(
            "15090",
            Weights.format(15_090_000, WeightUnit.KG, decimals = 0, language = "en", grouping = false)
        )
    }

    @Test
    fun `numbers format per language`() {
        assertEquals("0,94", Numbers.format(0.9412, maxDecimals = 2, language = "es"))
        assertEquals("0.94", Numbers.format(0.9412, maxDecimals = 2, language = "en"))
        assertEquals("129,0", Numbers.format(129.0, maxDecimals = 1, minDecimals = 1, language = "es"))
        assertEquals("−0,17", Numbers.format(-0.17, language = "es", typographicMinus = true))
        assertEquals("0", Numbers.format(-0.2, maxDecimals = 0, language = "es", typographicMinus = true))
        assertEquals("78 815", Numbers.format(78_815L, language = "es"))
        assertEquals("78,815", Numbers.format(78_815L, language = "en"))
        assertEquals("1,234.5", Numbers.format(1_234.5, maxDecimals = 1, language = "en"))
        assertEquals(',', Numbers.decimalSeparator("es-ES"))
        assertEquals('.', Numbers.decimalSeparator("en"))
    }

    @Test
    fun `percent keeps the sign with its number`() {
        assertEquals("31 %", Numbers.percent(31.0, language = "es"))
        assertEquals("−31 %", Numbers.percent(-31.0, language = "es"))
        assertEquals("31%", Numbers.percent(31.0, language = "en"))
        assertEquals("12.5%", Numbers.percent(12.5, maxDecimals = 1, language = "en"))
    }

    @Test
    fun `parse accepts comma and dot`() {
        assertEquals(1.5, Numbers.parse("1,5"))
        assertEquals(1.5, Numbers.parse(" 1.5 "))
        assertEquals(15_090.5, Numbers.parse("15 090,5"))
        assertEquals(15_090.5, Numbers.parse("15,090.5"))
        assertEquals(null, Numbers.parse(""))
        assertEquals(null, Numbers.parse("abc"))
        assertEquals(62_500L, Weights.parse("62,5", WeightUnit.KG))
    }

    @Test
    fun `unknown unit names default to kg`() {
        assertEquals(WeightUnit.LB, WeightUnit.fromName("LB"))
        assertEquals(WeightUnit.KG, WeightUnit.fromName("stone"))
        assertEquals(WeightUnit.KG, WeightUnit.fromName(null))
    }

    @Test
    fun `round to increment`() {
        assertEquals(62_500L, roundToIncrement(61_300, 2_500))
        assertEquals(60_000L, roundToIncrement(61_200, 2_500))
        assertEquals(62_500L, roundToIncrement(61_250, 2_500))
        assertEquals(60_000L, roundToIncrement(62_400, 2_500, Rounding.DOWN))
        assertEquals(62_500L, roundToIncrement(60_100, 2_500, Rounding.UP))
        assertEquals(60_000L, roundToIncrement(60_000, 2_500, Rounding.UP))
        assertEquals(-5_000L, roundToIncrement(-4_000, 2_500))
        assertEquals(61_300L, roundToIncrement(61_300, 0))
    }
}

class PlateCalculatorTest {
    private val kg = PlateCalculator.KG_PLATES

    @Test
    fun `100 kg on a 20 kg bar is 25 + 15 per side`() {
        val loadout = PlateCalculator.platesPerSide(100_000, 20_000, kg)
        assertEquals(listOf(25_000L, 15_000L), loadout.perSide)
        assertEquals(100_000L, loadout.achievedGrams)
        assertTrue(loadout.isExact)
    }

    @Test
    fun `small jumps use the light plates`() {
        val loadout = PlateCalculator.platesPerSide(62_500, 20_000, kg)
        assertEquals(listOf(20_000L, 1_250L), loadout.perSide)
        assertEquals(62_500L, loadout.achievedGrams)
    }

    @Test
    fun `target at or below the bar needs no plates`() {
        val loadout = PlateCalculator.platesPerSide(15_000, 20_000, kg)
        assertEquals(emptyList(), loadout.perSide)
        assertEquals(20_000L, loadout.achievedGrams)
        assertEquals(0L, loadout.missingGrams)
    }

    @Test
    fun `unreachable target returns the closest load below it`() {
        val loadout = PlateCalculator.platesPerSide(61_000, 20_000, kg)
        assertEquals(60_000L, loadout.achievedGrams)
        assertEquals(1_000L, loadout.missingGrams)
        assertFalse(loadout.isExact)
    }

    @Test
    fun `limited stock is respected`() {
        val stock = listOf(PlateStock(20_000, pairs = 1), PlateStock(10_000, pairs = 2), PlateStock(5_000))
        val loadout = PlateCalculator.platesPerSideFromStock(120_000, 20_000, stock)
        assertEquals(listOf(20_000L, 10_000L, 10_000L, 5_000L, 5_000L), loadout.perSide)
        assertEquals(120_000L, loadout.achievedGrams)
    }

    @Test
    fun `lb plates on a 45 lb bar`() {
        val bar = Weights.lbToGrams(45.0)
        val loadout = PlateCalculator.platesPerSide(Weights.lbToGrams(225.0), bar, PlateCalculator.LB_PLATES)
        assertEquals(listOf(Weights.lbToGrams(45.0), Weights.lbToGrams(45.0)), loadout.perSide)
        assertEquals("225", Weights.format(loadout.achievedGrams, WeightUnit.LB, decimals = 0))
    }

    @Test
    fun `total and smallest increment`() {
        assertEquals(100_000L, PlateCalculator.total(20_000, listOf(25_000, 15_000)))
        assertEquals(2_500L, PlateCalculator.smallestIncrement(kg))
    }
}
