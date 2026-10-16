package es.sebas1705.haltera.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestPolicyTest {
    @Test
    fun `the default is fixed with nothing flexible`() {
        val fixed = RestPolicy()
        assertEquals(RestMode.FIXED, fixed.mode)
        assertFalse(fixed.isFlexible)
        assertFalse(fixed.knowsWeekdays)
        assertEquals(0, fixed.restDaysCount)
        assertNull(fixed.trainingDaysPerWeek)
        assertEquals(emptyList(), fixed.weekdays)
        assertEquals(RestPolicy.FIXED, Routine("r", "R").restPolicy)
    }

    @Test
    fun `fixed ignores stray flexible fields`() {
        val odd = RestPolicy(RestMode.FIXED, restDaysPerWeek = 3, restWeekdays = 0b101)
        assertEquals(0, odd.restDaysCount)
        assertFalse(odd.isRestWeekday(1))
        assertEquals(RestPolicy.FIXED, odd.normalized())
    }

    @Test
    fun `a number of rest days is kept between 0 and 6`() {
        assertEquals(3, RestPolicy.flexible(3).restDaysCount)
        assertEquals(4, RestPolicy.flexible(3).trainingDaysPerWeek)
        assertEquals(6, RestPolicy.flexible(9).restDaysCount)
        assertEquals(0, RestPolicy.flexible(-2).restDaysCount)
        assertEquals(7, RestPolicy.flexible(0).trainingDaysPerWeek)
        assertFalse(RestPolicy.flexible(3).knowsWeekdays)
        assertFalse(RestPolicy.flexible(3).isRestWeekday(3))
    }

    @Test
    fun `rest weekdays are a Monday-first bitmask and give the count`() {
        val wedSun = RestPolicy.onWeekdays(listOf(3, 7))
        assertEquals(0b100_0100, wedSun.restWeekdays)
        assertTrue(wedSun.knowsWeekdays)
        assertEquals(2, wedSun.restDaysCount)
        assertEquals(2, wedSun.restDaysPerWeek)
        assertEquals(5, wedSun.trainingDaysPerWeek)
        assertEquals(listOf(3, 7), wedSun.weekdays)
        assertTrue(wedSun.isRestWeekday(3))
        assertTrue(wedSun.isRestWeekday(7))
        assertFalse(wedSun.isRestWeekday(1))
        assertFalse(wedSun.isRestWeekday(0))
        assertFalse(wedSun.isRestWeekday(8))
    }

    @Test
    fun `at most six rest weekdays are kept, the first ones in the week`() {
        val all = RestPolicy.onWeekdays((1..7).toList())
        assertEquals(6, all.restDaysCount)
        assertEquals((1..6).toList(), all.weekdays)
    }

    @Test
    fun `weekdays outside 1 to 7 are ignored and none at all falls back to the number`() {
        assertEquals(0b1, RestPolicy.maskOf(listOf(0, 1, 8, -1)))
        val none = RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek = 2, restWeekdays = 0).normalized()
        assertFalse(none.knowsWeekdays)
        assertEquals(2, none.restDaysCount)
        // Bits above Sunday are dropped.
        val high = RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek = 1, restWeekdays = 0b1000_0000).normalized()
        assertEquals(0, high.restWeekdays)
        assertEquals(1, high.restDaysCount)
    }

    @Test
    fun `normalising sets the number to the weekday count and is idempotent`() {
        val raw = RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek = 5, restWeekdays = RestPolicy.maskOf(listOf(2, 4)))
        val normal = raw.normalized()
        assertEquals(2, normal.restDaysPerWeek)
        assertEquals(normal, normal.normalized())
        assertEquals(listOf(2, 4), RestPolicy.weekdaysOf(normal.restWeekdays))
    }

    @Test
    fun `rest modes are read leniently`() {
        assertEquals(RestMode.FLEXIBLE, RestMode.fromDb("FLEXIBLE"))
        assertEquals(RestMode.FIXED, RestMode.fromDb("FIXED"))
        assertEquals(RestMode.FIXED, RestMode.fromDb("SOMETHING_NEWER"))
        assertEquals(RestMode.FIXED, RestMode.fromDb(null))
    }
}
