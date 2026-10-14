package es.sebas1705.ironlog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Material roles come from the brand colours; only the neutral ladders below are hand-tuned.
private val LightColors: ColorScheme = with(LightBrandColors) {
    lightColorScheme(
        primary = accentText,
        onPrimary = onAccentText,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = Color(0xFF555B64),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDE1E8),
        onSecondaryContainer = Color(0xFF151A21),
        tertiary = Color(0xFF8A5100),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDDB8),
        onTertiaryContainer = Color(0xFF2C1600),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surface,
        surfaceContainerLow = Color(0xFFEFEFF1),
        surfaceContainer = Color(0xFFEAEAEC),
        surfaceContainerHigh = Color(0xFFE4E4E7),
        surfaceContainerHighest = Color(0xFFDEDEE1),
        error = error,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF450A0A),
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

private val DarkColors: ColorScheme = with(DarkBrandColors) {
    darkColorScheme(
        primary = accentText,
        onPrimary = onAccentText,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = Color(0xFFC1C7D0),
        onSecondary = Color(0xFF2B3138),
        secondaryContainer = Color(0xFF3A3F47),
        onSecondaryContainer = Color(0xFFDDE1E8),
        tertiary = Color(0xFFFFB95C),
        onTertiary = Color(0xFF482900),
        tertiaryContainer = Color(0xFF673D00),
        onTertiaryContainer = Color(0xFFFFDDB8),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = Color(0xFF0F1113),
        surfaceContainerLow = Color(0xFF1A1C20),
        surfaceContainer = surface,
        surfaceContainerHigh = Color(0xFF272A30),
        surfaceContainerHighest = surfaceVariant,
        error = error,
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

private val LocalBrandColors = staticCompositionLocalOf { LightBrandColors }

/** Brand values beyond Material 3 for features: iron-orange accent, PR gold, semantic colours. */
object IronlogTheme {
    /** The brand colours of the current (light or dark) theme. */
    val colors: BrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandColors.current
}

/** The Ironlog Material 3 theme: iron orange on graphite, Barlow Semi Condensed and Inter, light and dark. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrandColors else LightBrandColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
