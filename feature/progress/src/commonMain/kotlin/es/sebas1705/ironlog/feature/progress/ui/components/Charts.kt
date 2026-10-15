package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.feature.progress.model.AxisScale
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.model.ChartScale
import kotlin.math.abs

private const val BAR_GAP_FRACTION = 0.25f
private const val GHOST_ALPHA = 0.35f

/** Ghost outline opacity on dark surfaces (on-surface-variant at 80 %). */
private const val GHOST_ALPHA_DARK = 0.8f

/** Surfaces darker than this luminance are treated as the dark theme. */
private const val DARK_LUMINANCE = 0.5f
private const val DIM_ALPHA = 0.6f
private const val MAX_X_LABELS = 4
private const val DOT_FACTOR = 1.4f

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

/**
 * Vertical bars from 0, one per bucket, with an optional [ghost] value per bar (the comparison period,
 * drawn as an outline behind). Tapping a bar shows [tooltip] for it; tapping again hides it.
 * [description] summarises the chart for screen readers.
 */
@Composable
internal fun BarChart(
    values: List<Double>,
    labels: List<String>,
    format: (Double) -> String,
    tooltip: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    ghost: List<Double>? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightLast: Boolean = false,
    maxLabels: Int = MAX_X_LABELS,
    height: Dp = 180.dp,
) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val plot = remember { PlotHolder() }
    // Dashed "previous period" outlines: on a dark card the faint outline colour is hard to see, so use the
    // stronger on-surface-variant there.
    val scheme = MaterialTheme.colorScheme
    val ghostColor = if (scheme.surface.luminance() < DARK_LUMINANCE) {
        scheme.onSurfaceVariant.copy(alpha = GHOST_ALPHA_DARK)
    } else {
        scheme.outline.copy(alpha = GHOST_ALPHA)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { tap ->
                    val area = plot.area ?: return@detectTapGestures
                    val slot = area.width / values.size.coerceAtLeast(1)
                    val index = ((tap.x - area.left) / slot).toInt().coerceIn(0, values.lastIndex)
                    selected = if (selected == index) null else index
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val scale = ChartScale.nice(0.0, (values + ghost.orEmpty()).max(), includeZero = true)
        val area = drawYAxis(scale, format, unit, measurer, style)
        plot.area = area
        val bars = BarColors(color, ghostColor, highlightLast, selected)
        drawBars(values, ghost, scale, area, bars)
        val slot = area.width / values.size
        ChartScale.labelIndices(labels.size, maxLabels).forEach { i ->
            drawXLabel(labels[i], area.left + i * slot + slot / 2, measurer, style)
        }
        selected?.let { i ->
            val top = area.y(scale, maxOf(values[i], ghost?.getOrNull(i) ?: 0.0))
            drawTooltip(tooltip(i), Offset(area.left + i * slot + slot / 2, top), measurer, style)
        }
    }
}

/** How bars are coloured: dimmed when another bar is selected, or all but the last with [highlightLast]. */
private class BarColors(val color: Color, val ghost: Color, val highlightLast: Boolean, val selected: Int?)

private fun DrawScope.drawBars(values: List<Double>, ghost: List<Double>?, scale: AxisScale, area: PlotArea, colors: BarColors) {
    val slot = area.width / values.size
    val barWidth = slot * (1 - BAR_GAP_FRACTION)
    val radius = CornerRadius(minOf(barWidth / 2, BAR_RADIUS_DP.dp.toPx()))
    val dash = PathEffect.dashPathEffect(floatArrayOf(GHOST_DASH_ON, GHOST_DASH_OFF))
    values.forEachIndexed { i, value ->
        val left = area.left + i * slot + (slot - barWidth) / 2
        ghost?.getOrNull(i)?.takeIf { it > 0 }?.let { g ->
            val top = area.y(scale, g)
            drawRoundRect(
                colors.ghost,
                topLeft = Offset(left, top),
                size = Size(barWidth, area.bottom - top),
                cornerRadius = radius,
                style = Stroke(width = GHOST_STROKE_DP.dp.toPx(), pathEffect = dash),
            )
        }
        if (value > 0) {
            val top = area.y(scale, value)
            val dim = (colors.highlightLast && i != values.lastIndex) || (colors.selected != null && colors.selected != i)
            drawRoundRect(
                if (dim) colors.color.copy(alpha = DIM_ALPHA) else colors.color,
                topLeft = Offset(left, top),
                size = Size(barWidth, area.bottom - top),
                cornerRadius = radius,
            )
        }
    }
}

/** One line of a [LineChart]. */
internal data class LineSeries(
    val points: List<ChartPoint>,
    val color: Color,
    val label: String? = null,
    val dots: Boolean = true,
    val width: Dp = 2.5.dp,
    val dashed: Boolean = false,
    /** Whether tapping can select its points (helper lines like trends are not selectable). */
    val selectable: Boolean = true,
)

/** Maps times to x positions inside a plot. */
private class TimeAxis(val minX: Long, val maxX: Long) {
    /** Horizontal room kept at both ends so the first and last dots are not cut. */
    var inset = 0f

    fun x(value: Long, area: PlotArea): Float =
        if (maxX == minX) {
            area.left + area.width / 2
        } else {
            area.left + inset + ((value - minX).toFloat() / (maxX - minX)) * (area.width - 2 * inset)
        }
}

/**
 * A time chart (x = epoch millis) with nice y ticks in [unit], first, middle and last dates below and a
 * tap-to-inspect tooltip on the nearest point of a selectable series ([tooltip] gets series and point
 * index).
 */
@Composable
internal fun LineChart(
    series: List<LineSeries>,
    format: (Double) -> String,
    dateLabel: (Long) -> String,
    tooltip: (series: Int, point: Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    height: Dp = 180.dp,
    includeZero: Boolean = false,
) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(series) { mutableStateOf<Pair<Int, Int>?>(null) }
    val guide = MaterialTheme.colorScheme.outline
    val plot = remember { PlotHolder() }
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val time = TimeAxis(all.minOf { it.x }, all.maxOf { it.x })
    val scale = ChartScale.nice(all.minOf { it.y }, all.maxOf { it.y }, includeZero = includeZero)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(series) {
                detectTapGestures { tap ->
                    val area = plot.area ?: return@detectTapGestures
                    val hit = nearestPoint(series, time, area, tap.x)
                    selected = if (hit == selected) null else hit
                }
            },
    ) {
        val area = drawYAxis(scale, format, unit, measurer, style)
        time.inset = LINE_INSET_DP.dp.toPx()
        plot.area = area
        series.forEach { line -> drawSeries(line, line.points.map { Offset(time.x(it.x, area), area.y(scale, it.y)) }) }
        // The middle label snaps to the data point closest to the centre, so every label sits under a point.
        val middle = all.minBy { abs(it.x - (time.minX + time.maxX) / 2) }.x
        listOf(time.minX, middle, time.maxX).distinct()
            .forEach { drawXLabel(dateLabel(it), time.x(it, area), measurer, style) }
        selected?.let { (s, p) ->
            val point = series.getOrNull(s)?.points?.getOrNull(p) ?: return@let
            val anchor = Offset(time.x(point.x, area), area.y(scale, point.y))
            drawSelectionGuide(anchor.x, area, guide)
            drawCircle(series[s].color, radius = series[s].width.toPx() * SELECTED_DOT_FACTOR, center = anchor)
            drawTooltip(tooltip(s, p), anchor, measurer, style)
        }
    }
}

/** (series, point) of the selectable point closest to [x] horizontally. */
private fun nearestPoint(series: List<LineSeries>, time: TimeAxis, area: PlotArea, x: Float): Pair<Int, Int>? =
    series.withIndex().filter { it.value.selectable }
        .flatMap { (s, line) -> line.points.mapIndexed { p, point -> Triple(s, p, abs(time.x(point.x, area) - x)) } }
        .minByOrNull { it.third }
        ?.let { it.first to it.second }

private fun DrawScope.drawSeries(line: LineSeries, offsets: List<Offset>) {
    if (offsets.size > 1) {
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val dash = if (line.dashed) {
            PathEffect.dashPathEffect(floatArrayOf(LINE_DASH_ON_DP.dp.toPx(), LINE_DASH_OFF_DP.dp.toPx()))
        } else {
            null
        }
        drawPath(
            path,
            line.color,
            style = Stroke(line.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)
        )
    }
    if (line.dots || offsets.size == 1) {
        offsets.forEach { drawCircle(line.color, radius = line.width.toPx() * DOT_FACTOR, center = it) }
    }
}

private const val BAR_RADIUS_DP = 3
private const val GHOST_STROKE_DP = 1.5f
private const val GHOST_DASH_ON = 6f
private const val GHOST_DASH_OFF = 4f
private const val LINE_DASH_ON_DP = 8
private const val LINE_DASH_OFF_DP = 6
private const val SELECTED_DOT_FACTOR = 2.2f
private const val LINE_INSET_DP = 8

/** Colour keys of a chart's series. */
@Composable
internal fun ChartLegend(
    items: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
    outlinedLast: Boolean = false
) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val swatch = Modifier.size(10.dp).clip(CircleShape)
                if (outlinedLast && index == items.lastIndex) {
                    Box(swatch.border(1.5.dp, color, CircleShape))
                } else {
                    Box(swatch.background(color))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A tinted strip of text explaining what a chart will show once there is data. */
@Composable
internal fun ChartEmpty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
