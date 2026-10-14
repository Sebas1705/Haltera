package es.sebas1705.ironlog.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A rounded rectangle of the logo on its [LogoGeometry.GRID]-unit grid. */
data class LogoRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float)

/** The Barbell-I logo as in `brand/tokens.json` (`logo`); BrandTokensTest checks they match. */
object LogoGeometry {
    const val GRID = 108f
    const val CORNER = 24f
    val background = Color(0xFF1E2126)
    val barColor = Color(0xFFFF5A1F)
    val beamColor = Color(0xFFF4F4F5)
    val plateColor = Color(0xFFFF5A1F)
    val bars = listOf(LogoRect(50f, 26f, 8f, 56f, 3f))
    val beams = listOf(LogoRect(30f, 20f, 48f, 10f, 5f), LogoRect(30f, 78f, 48f, 10f, 5f))
    val plates = listOf(
        LogoRect(24f, 16f, 8f, 18f, 3f),
        LogoRect(76f, 16f, 8f, 18f, 3f),
        LogoRect(24f, 74f, 8f, 18f, 3f),
        LogoRect(76f, 74f, 8f, 18f, 3f),
    )
}

/**
 * The Ironlog logo (concept A, "Barbell-I"): a vertical bar between two barbells on a graphite
 * rounded square. For the startup screen and Credits only, not inside working screens.
 */
@Composable
fun IronlogLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String? = null,
) {
    val described = contentDescription?.let { text -> Modifier.semantics { this.contentDescription = text } } ?: Modifier
    Canvas(modifier.size(size).then(described)) {
        val unit = this.size.minDimension / LogoGeometry.GRID
        drawRoundRect(
            color = LogoGeometry.background,
            size = Size(LogoGeometry.GRID * unit, LogoGeometry.GRID * unit),
            cornerRadius = CornerRadius(LogoGeometry.CORNER * unit),
        )
        // Same order as scripts/brand.mjs: the bar behind the beams, the plates on top.
        LogoGeometry.bars.forEach { drawRect(it, unit, LogoGeometry.barColor) }
        LogoGeometry.beams.forEach { drawRect(it, unit, LogoGeometry.beamColor) }
        LogoGeometry.plates.forEach { drawRect(it, unit, LogoGeometry.plateColor) }
    }
}

private fun DrawScope.drawRect(rect: LogoRect, unit: Float, color: Color) = drawRoundRect(
    color = color,
    topLeft = Offset(rect.x * unit, rect.y * unit),
    size = Size(rect.w * unit, rect.h * unit),
    cornerRadius = CornerRadius(rect.r * unit),
)
