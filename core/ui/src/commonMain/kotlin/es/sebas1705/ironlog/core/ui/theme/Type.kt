package es.sebas1705.ironlog.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import es.sebas1705.ironlog.core.ui.resources.Res
import es.sebas1705.ironlog.core.ui.resources.barlow_semicondensed_semibold
import es.sebas1705.ironlog.core.ui.resources.inter_variable
import org.jetbrains.compose.resources.Font

/** OpenType feature for tabular (equal-width) figures. */
private const val TABULAR_FIGURES = "tnum"

/**
 * Tabular figures: every digit has the same width, so loads, reps and timers do not shift as they
 * change. Use it on numbers: `MaterialTheme.typography.headlineMedium.tabularFigures()`.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

/** Barlow Semi Condensed 600: display, headline and title styles, and big numbers. */
@Composable
fun displayFontFamily(): FontFamily = FontFamily(Font(Res.font.barlow_semicondensed_semibold, FontWeight.SemiBold))

/** Inter 400 and 500 (one variable font file, Google Fonts): body and label styles. */
@Composable
fun textFontFamily(): FontFamily = FontFamily(
    Font(
        Res.font.inter_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        Res.font.inter_variable,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
)

/** Material 3 type scale: Barlow Semi Condensed for display, headline and title; Inter for the rest. */
@Composable
fun appTypography(): Typography {
    val display = displayFontFamily()
    val text = textFontFamily()
    return remember(display, text) { brandTypography(display, text) }
}

private fun brandTypography(display: FontFamily, text: FontFamily): Typography = Typography().run {
    fun TextStyle.display() = copy(fontFamily = display, fontWeight = FontWeight.SemiBold)
    fun TextStyle.text() = copy(fontFamily = text)
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.display(),
        headlineSmall = headlineSmall.display(),
        titleLarge = titleLarge.display(),
        titleMedium = titleMedium.display(),
        titleSmall = titleSmall.display(),
        bodyLarge = bodyLarge.text(),
        bodyMedium = bodyMedium.text(),
        bodySmall = bodySmall.text(),
        labelLarge = labelLarge.text(),
        labelMedium = labelMedium.text(),
        labelSmall = labelSmall.text(),
    )
}
