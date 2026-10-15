package es.sebas1705.haltera.feature.progress.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.feature.progress.model.AxisScale

/** Colours and text styles a chart draws with, resolved from the theme in composition. */
internal data class ChartStyle(
    val grid: Color,
    val axisText: Color,
    val tooltipBackground: Color,
    val tooltipText: Color,
    val labelStyle: TextStyle,
    val tooltipStyle: TextStyle,
)

/** Where the plot sits inside the canvas once the axis labels are measured. */
internal data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun y(scale: AxisScale, value: Double): Float = (bottom - scale.fraction(value) * height).toFloat()
}

private const val GAP_DP = 6
private const val TOOLTIP_PADDING_DP = 6
private const val TOOLTIP_RADIUS_DP = 6
private const val TOOLTIP_OFFSET_DP = 10

/**
 * Lays out the plot: y tick labels on the left, the [unit] above them and room for one line of x labels
 * below. Draws the horizontal grid and the tick labels; returns the plot rectangle.
 */
internal fun DrawScope.drawYAxis(
    scale: AxisScale,
    format: (Double) -> String,
    unit: String?,
    measurer: TextMeasurer,
    style: ChartStyle,
    hasXLabels: Boolean = true,
): PlotArea {
    val gap = GAP_DP.dp.toPx()
    val labels = scale.ticks.map { it to measurer.measure(format(it), style.labelStyle) }
    val unitLayout = unit?.let { measurer.measure(it, style.labelStyle) }
    val labelHeight = labels.firstOrNull()?.second?.size?.height?.toFloat() ?: 0f
    val left = (labels.maxOfOrNull { it.second.size.width } ?: 0).toFloat()
        .coerceAtLeast(unitLayout?.size?.width?.toFloat() ?: 0f) + gap
    val top = (unitLayout?.size?.height?.toFloat() ?: 0f) + labelHeight / 2 + gap / 2
    val bottom = size.height - if (hasXLabels) labelHeight + gap else labelHeight / 2
    val area = PlotArea(left, top, size.width, bottom)
    unitLayout?.let { drawText(it, style.axisText, Offset(0f, 0f)) }
    labels.forEach { (value, layout) ->
        val y = area.y(scale, value)
        drawLine(style.grid, Offset(area.left, y), Offset(area.right, y), strokeWidth = 1.dp.toPx())
        drawText(layout, style.axisText, Offset(left - gap - layout.size.width, y - layout.size.height / 2f))
    }
    return area
}

/** One x label centred on [x], kept inside the canvas. */
internal fun DrawScope.drawXLabel(text: String, x: Float, measurer: TextMeasurer, style: ChartStyle) {
    val layout = measurer.measure(text, style.labelStyle)
    val left = (x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, style.axisText, Offset(left, size.height - layout.size.height))
}

/** A vertical guide at the selected [x]. */
internal fun DrawScope.drawSelectionGuide(x: Float, area: PlotArea, color: Color) {
    drawLine(
        color,
        Offset(x, area.top),
        Offset(x, area.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

/** A rounded bubble with [text] above [anchor] (below it when there is no room), kept inside the canvas. */
internal fun DrawScope.drawTooltip(text: String, anchor: Offset, measurer: TextMeasurer, style: ChartStyle) {
    val padding = TOOLTIP_PADDING_DP.dp.toPx()
    val layout: TextLayoutResult = measurer.measure(text, style.tooltipStyle)
    val box = Size(layout.size.width + padding * 2, layout.size.height + padding * 2)
    val offset = TOOLTIP_OFFSET_DP.dp.toPx()
    val left = (anchor.x - box.width / 2).coerceIn(0f, (size.width - box.width).coerceAtLeast(0f))
    val above = anchor.y - offset - box.height
    val top = if (above >= 0f) above else (anchor.y + offset).coerceAtMost((size.height - box.height).coerceAtLeast(0f))
    val rect = Rect(Offset(left, top), box)
    drawRoundRect(
        style.tooltipBackground,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(TOOLTIP_RADIUS_DP.dp.toPx()),
    )
    drawText(layout, style.tooltipText, Offset(left + padding, top + padding))
}

/** The last drawn plot area, read by tap handlers (a plain holder: drawing must not write state). */
internal class PlotHolder {
    var area: PlotArea? = null
}
