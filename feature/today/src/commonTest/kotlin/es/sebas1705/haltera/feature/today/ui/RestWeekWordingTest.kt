package es.sebas1705.haltera.feature.today.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The weekly rest line never says "4 of 3" (QA alpha.4): over the target it states the target apart. */
class RestWeekWordingTest {
    private fun wording(used: Int, planned: Int) = RestWeekUi(used, planned, restToday = false).wording

    @Test
    fun `within the target it counts against it`() {
        assertEquals(RestWeekWording.OF_TARGET, wording(0, 3))
        assertEquals(RestWeekWording.OF_TARGET, wording(1, 3))
        assertEquals(RestWeekWording.OF_TARGET, wording(3, 3))
    }

    @Test
    fun `over the target it says how many and the target`() {
        assertEquals(RestWeekWording.OVER_TARGET, wording(4, 3))
        assertEquals(RestWeekWording.OVER_TARGET, wording(6, 1))
    }

    @Test
    fun `a routine without planned rest days never shows of 0`() {
        assertEquals(RestWeekWording.NONE_PLANNED, wording(0, 0))
        assertEquals(RestWeekWording.UNPLANNED, wording(2, 0))
    }
}
