package es.sebas1705.haltera.core.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowSizeTest {

    @Test
    fun widthBreakpoints() {
        assertEquals(WidthClass.COMPACT, WindowSize.from(320.dp, 640.dp).width)
        assertEquals(WidthClass.COMPACT, WindowSize.from(599.dp, 800.dp).width)
        assertEquals(WidthClass.MEDIUM, WindowSize.from(600.dp, 800.dp).width)
        assertEquals(WidthClass.MEDIUM, WindowSize.from(839.dp, 800.dp).width)
        assertEquals(WidthClass.EXPANDED, WindowSize.from(840.dp, 800.dp).width)
        assertEquals(WidthClass.EXPANDED, WindowSize.from(1280.dp, 800.dp).width)
    }

    @Test
    fun heightBreakpoints() {
        assertEquals(HeightClass.COMPACT, WindowSize.from(800.dp, 360.dp).height)
        assertEquals(HeightClass.MEDIUM, WindowSize.from(360.dp, 480.dp).height)
        assertEquals(HeightClass.EXPANDED, WindowSize.from(800.dp, 1280.dp).height)
    }

    @Test
    fun portraitPhoneUsesBottomBar() {
        val phone = WindowSize.from(360.dp, 640.dp)
        assertFalse(phone.useNavigationRail)
        assertFalse(phone.isExpanded)
    }

    @Test
    fun landscapePhoneUsesRail() {
        // 640 x 360 dp: medium width, compact height.
        assertTrue(WindowSize.from(640.dp, 360.dp).useNavigationRail)
        // A narrow landscape window with a compact height still gets the rail.
        assertTrue(WindowSize.from(560.dp, 320.dp).useNavigationRail)
    }

    @Test
    fun tabletUsesRailAndTwoColumns() {
        val landscape = WindowSize.from(1280.dp, 800.dp)
        assertTrue(landscape.useNavigationRail)
        assertTrue(landscape.isExpanded)
        val portrait = WindowSize.from(800.dp, 1280.dp)
        assertTrue(portrait.useNavigationRail)
        assertFalse(portrait.isExpanded)
    }
}
