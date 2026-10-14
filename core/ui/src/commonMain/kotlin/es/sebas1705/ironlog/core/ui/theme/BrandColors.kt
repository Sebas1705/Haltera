package es.sebas1705.ironlog.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The brand colours of one theme, named as in `brand/tokens.json` (docs/BRAND.md).
 * BrandTokensTest fails when these and the JSON disagree.
 *
 * - [accent] is iron orange for fills (logo, rest timer, prominent containers); [onAccent] goes on it.
 * - [accentText] is the accent for text and Material `primary` (darkened on light for WCAG AA).
 * - [prGold] marks personal records and progression; [prGoldContainer] is the badge fill.
 */
@Immutable
data class BrandColors(
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val onAccentText: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val graphite: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val prGold: Color,
    val prGoldContainer: Color,
    val onPrGoldContainer: Color,
)

val LightBrandColors = BrandColors(
    accent = Color(0xFFFF5A1F),
    onAccent = Color(0xFF1E2126),
    accentText = Color(0xFFC2410C),
    onAccentText = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFFFDBCC),
    onAccentContainer = Color(0xFF3A1000),
    graphite = Color(0xFF1E2126),
    background = Color(0xFFF4F4F5),
    onBackground = Color(0xFF1E2126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2126),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF4B5059),
    outline = Color(0xFF767B84),
    outlineVariant = Color(0xFFC9CBD0),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    error = Color(0xFFB91C1C),
    prGold = Color(0xFF854D0E),
    prGoldContainer = Color(0xFFFEF3C7),
    onPrGoldContainer = Color(0xFF713F12),
)

val DarkBrandColors = BrandColors(
    accent = Color(0xFFFF5A1F),
    onAccent = Color(0xFF1E2126),
    accentText = Color(0xFFFF5A1F),
    onAccentText = Color(0xFF1E2126),
    accentContainer = Color(0xFF6B2307),
    onAccentContainer = Color(0xFFFFDBCC),
    graphite = Color(0xFF1E2126),
    background = Color(0xFF141619),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF1E2126),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF2D3138),
    onSurfaceVariant = Color(0xFFB6BAC1),
    outline = Color(0xFF8B9098),
    outlineVariant = Color(0xFF3F444C),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    prGold = Color(0xFFFACC15),
    prGoldContainer = Color(0xFF422006),
    onPrGoldContainer = Color(0xFFFEF08A),
)
