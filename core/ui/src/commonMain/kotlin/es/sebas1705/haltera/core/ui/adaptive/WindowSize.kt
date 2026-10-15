package es.sebas1705.haltera.core.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material window width classes: compact < 600 dp, medium 600–840 dp, expanded ≥ 840 dp. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Material window height classes: compact < 480 dp (landscape phones), medium < 900 dp, expanded. */
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

/** The window size classes of the current window, available through [LocalWindowSize]. */
@Immutable
data class WindowSize(val width: WidthClass, val height: HeightClass) {
    /** A navigation rail instead of a bottom bar: medium/expanded width, or a landscape phone. */
    val useNavigationRail: Boolean get() = width != WidthClass.COMPACT || height == HeightClass.COMPACT

    /** Enough room for two columns of cards. */
    val isExpanded: Boolean get() = width == WidthClass.EXPANDED

    companion object {
        val MEDIUM_WIDTH = 600.dp
        val EXPANDED_WIDTH = 840.dp
        val MEDIUM_HEIGHT = 480.dp
        val EXPANDED_HEIGHT = 900.dp

        fun from(width: Dp, height: Dp): WindowSize = WindowSize(
            width = when {
                width < MEDIUM_WIDTH -> WidthClass.COMPACT
                width < EXPANDED_WIDTH -> WidthClass.MEDIUM
                else -> WidthClass.EXPANDED
            },
            height = when {
                height < MEDIUM_HEIGHT -> HeightClass.COMPACT
                height < EXPANDED_HEIGHT -> HeightClass.MEDIUM
                else -> HeightClass.EXPANDED
            },
        )
    }
}

/** The current window's size classes. Defaults to a portrait phone outside [ProvideWindowSize]. */
val LocalWindowSize = staticCompositionLocalOf { WindowSize(WidthClass.COMPACT, HeightClass.MEDIUM) }

/** Measures the window once at the app root and provides [LocalWindowSize] to [content]. */
@Composable
fun ProvideWindowSize(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowSize provides WindowSize.from(maxWidth, maxHeight)) {
            content()
        }
    }
}

/** Readable width for a column of content on wide windows (F2). */
val ContentMaxWidth = 720.dp

/** Max width of dialogs and bottom sheets on wide windows. */
val SheetMaxWidth = 640.dp

/**
 * Caps the width of a screen's content at [maxWidth] and centres it. Apply it to the root
 * LazyColumn / scrolling Column of a screen in place of `fillMaxSize()`/`fillMaxWidth()`.
 */
fun Modifier.centeredContent(maxWidth: Dp = ContentMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()
