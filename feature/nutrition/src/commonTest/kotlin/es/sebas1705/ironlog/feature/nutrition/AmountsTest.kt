package es.sebas1705.ironlog.feature.nutrition

import es.sebas1705.ironlog.feature.nutrition.model.AmountMode
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.model.ParsedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountsTest {
    @Test
    fun `amounts accept dot or comma and must be positive`() {
        assertEquals(150.0, Amounts.parse(" 150 "))
        assertEquals(1.5, Amounts.parse("1,5"))
        assertEquals(0.25, Amounts.parse("0.25"))
        assertNull(Amounts.parse("0"))
        assertNull(Amounts.parse("-3"))
        assertNull(Amounts.parse("abc"))
        assertNull(Amounts.parse("1000000"))
    }

    @Test
    fun `optional values distinguish blank from invalid`() {
        assertEquals(ParsedValue.Blank, Amounts.parseOptional("  "))
        assertEquals(ParsedValue.Value(0.0), Amounts.parseOptional("0"))
        assertEquals(ParsedValue.Value(2.5), Amounts.parseOptional("2,5"))
        assertEquals(ParsedValue.Invalid, Amounts.parseOptional("-1"))
        assertEquals(ParsedValue.Invalid, Amounts.parseOptional("x"))
    }

    @Test
    fun `servings convert with the serving size`() {
        assertEquals(375.0, Amounts.grams(1.5, AmountMode.SERVINGS, 250.0))
        assertEquals(80.0, Amounts.grams(80.0, AmountMode.GRAMS, 250.0))
        assertNull(Amounts.grams(1.0, AmountMode.SERVINGS, null))
    }

    @Test
    fun `formatting trims zeros and uses the language separator`() {
        assertEquals("150", Amounts.format(150.0))
        assertEquals("1.5", Amounts.format(1.5))
        assertEquals("1,5", Amounts.format(1.5, language = "es"))
        assertEquals("0.33", Amounts.format(1.0 / 3, decimals = 2))
        assertEquals("2", Amounts.format(1.96))
        assertEquals("12", Amounts.format(12.4, decimals = 0))
    }
}
