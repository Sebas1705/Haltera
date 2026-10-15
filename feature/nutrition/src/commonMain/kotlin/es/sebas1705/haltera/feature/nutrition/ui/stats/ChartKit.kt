package es.sebas1705.haltera.feature.nutrition.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.units.Numbers
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// Chart helpers following the feature:progress conventions (ChartScale / ChartCanvas there). Features cannot
// depend on each other, so this is a small copy: nice axis scales with a unit, thinned x labels, tooltips.

/** A y axis from [min] to [max] with a tick every [step]. */
internal data class AxisScale(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double>
        get() = (0..((max - min) / step).roundToInt()).map { min + it * step }

    /** Position of [value] between min (0) and max (1). */
    fun fraction(value: Double): Double = if (max == min) HALF else (value - min) / (max - min)

    private companion object {
        const val HALF = 0.5
    }
}

/** "Nice" ticks (1, 2, 2.5 or 5 × 10ⁿ) and label thinning. */
internal object ChartScale {
    private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)
    private const val FLAT_PADDING = 0.1
    private const val DEFAULT_TICKS = 4
    private const val TEN = 10.0
    private const val EPSILON = 1e-9
    private const val TINY = 1e-12

    fun nice(minValue: Double, maxValue: Double, targetTicks: Int = DEFAULT_TICKS, includeZero: Boolean = false): AxisScale {
        var low = if (includeZero) minOf(0.0, minValue) else minValue
        var high = if (includeZero) maxOf(0.0, maxValue) else maxValue
        if (high - low < EPSILON) {
            val pad = if (abs(high) < EPSILON) 1.0 else abs(high) * FLAT_PADDING
            high += pad
            low = if (includeZero && low >= 0) 0.0 else low - pad
        }
        val rough = (high - low) / targetTicks.coerceAtLeast(1)
        val magnitude = TEN.pow(floor(log10(rough)))
        val step = NICE_STEPS.first { it * magnitude >= rough - TINY } * magnitude
        return AxisScale(floor(low / step + EPSILON) * step, ceil(high / step - EPSILON) * step, step)
    }

    /** Indices of at most [maxLabels] evenly spread labels of [count] items, always with the first and last. */
    fun labelIndices(count: Int, maxLabels: Int): List<Int> {
        if (count <= 0 || maxLabels <= 0) return emptyList()
        if (count <= maxLabels) return (0 until count).toList()
        if (maxLabels == 1) return listOf(count - 1)
        val step = (count - 1).toDouble() / (maxLabels - 1)
        return (0 until maxLabels).map { (it * step).roundToInt() }.distinct()
    }

    /** Axis tick text: grouped integers, or one decimal for small steps. */
    fun tick(value: Double, step: Double): String = Numbers.format(value, maxDecimals = if (step < 1) 1 else 0)
}

/** Colours and text styles a chart draws with, resolved from the theme. */
internal data class ChartStyle(
    val grid: Color,
    val axisText: Color,
    val tooltipBackground: Color,
    val tooltipText: Color,
    val labelStyle: TextStyle,
    val tooltipStyle: TextStyle,
)

@Composable
internal fun rememberChartStyle(): ChartStyle {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colors, typography) {
        ChartStyle(
            grid = colors.outlineVariant,
            axisText = colors.onSurfaceVariant,
            tooltipBackground = colors.inverseSurface,
            tooltipText = colors.inverseOnSurface,
            labelStyle = typography.labelSmall,
            tooltipStyle = typography.labelMedium,
        )
    }
}

/** Where the plot sits inside the canvas once the axis labels are measured. */
internal data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun y(scale: AxisScale, value: Double): Float = (bottom - scale.fraction(value) * height).toFloat()
}

/** The last drawn plot area, read by tap handlers (drawing must not write state). */
internal class PlotHolder {
    var area: PlotArea? = null
}

private const val GAP_DP = 6
private const val TOOLTIP_PADDING_DP = 6
private const val TOOLTIP_RADIUS_DP = 6
private const val TOOLTIP_OFFSET_DP = 10

/**
 * Lays out the plot: y tick labels on the left with [unit] above them (and, with [right], a second axis on the
 * right with [rightUnit]), room for one line of x labels below. Draws the grid and the labels.
 */
@Suppress("LongParameterList")
internal fun DrawScope.drawAxes(
    scale: AxisScale,
    unit: String,
    measurer: TextMeasurer,
    style: ChartStyle,
    right: AxisScale? = null,
    rightUnit: String? = null,
): PlotArea {
    val gap = GAP_DP.dp.toPx()
    fun measure(text: String) = measurer.measure(text, style.labelStyle)
    val labels = scale.ticks.map { it to measure(ChartScale.tick(it, scale.step)) }
    val rightLabels = right?.ticks?.map { it to measure(ChartScale.tick(it, right.step)) }.orEmpty()
    val unitLayout = measure(unit)
    val rightUnitLayout = rightUnit?.let(::measure)
    val labelHeight = unitLayout.size.height.toFloat()
    val left = maxOf(labels.maxOf { it.second.size.width }, unitLayout.size.width).toFloat() + gap
    val rightLabelWidth = rightLabels.maxOfOrNull { it.second.size.width } ?: 0
    val rightWidth = if (right == null) 0f else maxOf(rightLabelWidth, rightUnitLayout?.size?.width ?: 0) + gap
    // Unit line on top, then half a label so the top tick label is not cut.
    val top = labelHeight + labelHeight / 2 + gap / 2
    val area = PlotArea(left, top, size.width - rightWidth, size.height - labelHeight - gap)
    drawText(unitLayout, style.axisText, Offset(0f, 0f))
    rightUnitLayout?.let { drawText(it, style.axisText, Offset(size.width - it.size.width, 0f)) }
    labels.forEach { (value, layout) ->
        val y = area.y(scale, value)
        drawLine(style.grid, Offset(area.left, y), Offset(area.right, y), strokeWidth = 1.dp.toPx())
        drawText(layout, style.axisText, Offset(left - gap - layout.size.width, y - layout.size.height / 2f))
    }
    if (right != null) {
        rightLabels.forEach { (value, layout) ->
            val y = area.y(right, value)
            drawText(layout, style.axisText, Offset(area.right + gap, y - layout.size.height / 2f))
        }
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

/** A rounded bubble with [text] above [anchor] (below when there is no room), kept inside the canvas. */
internal fun DrawScope.drawTooltip(text: String, anchor: Offset, measurer: TextMeasurer, style: ChartStyle) {
    val padding = TOOLTIP_PADDING_DP.dp.toPx()
    val layout = measurer.measure(text, style.tooltipStyle)
    val box = Size(layout.size.width + padding * 2, layout.size.height + padding * 2)
    val offset = TOOLTIP_OFFSET_DP.dp.toPx()
    val left = (anchor.x - box.width / 2).coerceIn(0f, (size.width - box.width).coerceAtLeast(0f))
    val above = anchor.y - offset - box.height
    val top = if (above >= 0f) above else (anchor.y + offset).coerceAtMost((size.height - box.height).coerceAtLeast(0f))
    drawRoundRect(style.tooltipBackground, Offset(left, top), box, CornerRadius(TOOLTIP_RADIUS_DP.dp.toPx()))
    drawText(layout, style.tooltipText, Offset(left + padding, top + padding))
}
