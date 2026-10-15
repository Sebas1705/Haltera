package es.sebas1705.ironlog.feature.nutrition.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.domain.usecases.nutrition.DatedValue
import es.sebas1705.ironlog.domain.usecases.nutrition.MacroSplit
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.ui.components.MacroColors
import kotlinx.datetime.LocalDate
import kotlin.math.abs

private val CHART_HEIGHT = 200.dp
private const val BAR_FILL = 0.7f
private const val DIM_ALPHA = 0.55f
private const val MAX_X_LABELS = 4
private const val GOAL_HEADROOM = 1.1

/** One slot of a [DailyBarsChart]: its x label and stacked values (null: no data that day). */
internal data class BarSlot(val label: String, val values: List<Double>?)

/**
 * One bar per date of the range, stacked from [colors] (one colour per value), on a nice 0-based axis with
 * [unit]. Optional [goal] dashed line; [withinGoal] bars (single series) use [withinColor]. Tapping a bar shows
 * [tooltip]; [description] summarises the chart for screen readers.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun DailyBarsChart(
    slots: List<BarSlot>,
    colors: List<Color>,
    unit: String,
    tooltip: (Int) -> String,
    description: String,
    goal: Double? = null,
    withinGoal: ((Double) -> Boolean)? = null,
    withinColor: Color = MacroColors.withinGoal,
) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    val goalColor = MaterialTheme.colorScheme.onSurface
    var selected by remember(slots) { mutableStateOf<Int?>(null) }
    val plot = remember { PlotHolder() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(slots) {
                detectTapGestures { tap ->
                    val area = plot.area ?: return@detectTapGestures
                    val slot = area.width / slots.size.coerceAtLeast(1)
                    val index = ((tap.x - area.left) / slot).toInt().coerceIn(0, slots.lastIndex)
                    selected = if (selected == index || slots[index].values == null) null else index
                }
            },
    ) {
        if (slots.isEmpty()) return@Canvas
        val max = maxOf(slots.maxOf { it.values?.sum() ?: 0.0 }, (goal ?: 0.0) * GOAL_HEADROOM)
        val scale = ChartScale.nice(0.0, max, includeZero = true)
        val area = drawAxes(scale, unit, measurer, style)
        plot.area = area
        val slot = area.width / slots.size
        val barWidth = slot * BAR_FILL
        slots.forEachIndexed { i, s ->
            val values = s.values ?: return@forEachIndexed
            val dim = selected != null && selected != i
            var stacked = 0.0
            values.forEachIndexed { k, v ->
                val bottom = area.y(scale, stacked)
                stacked += v
                val top = area.y(scale, stacked)
                val base = if (values.size == 1 && withinGoal?.invoke(v) == true) withinColor else colors[k % colors.size]
                drawRoundRect(
                    if (dim) base.copy(alpha = DIM_ALPHA) else base,
                    topLeft = Offset(area.left + i * slot + (slot - barWidth) / 2, top),
                    size = Size(barWidth, bottom - top),
                    cornerRadius = CornerRadius(if (k == values.lastIndex) 2.dp.toPx() else 0f),
                )
            }
        }
        if (goal != null) {
            val y = area.y(scale, goal)
            drawLine(
                goalColor,
                Offset(area.left, y),
                Offset(area.right, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
        ChartScale.labelIndices(slots.size, MAX_X_LABELS).forEach { i ->
            drawXLabel(slots[i].label, area.left + i * slot + slot / 2, measurer, style)
        }
        selected?.let { i ->
            val total = slots[i].values?.sum() ?: return@let
            drawTooltip(tooltip(i), Offset(area.left + i * slot + slot / 2, area.y(scale, total)), measurer, style)
        }
    }
}

/** Average macro energy split as a donut with the percentages beside it. */
@Composable
internal fun MacroDonut(split: MacroSplit, labels: List<String>, description: String) {
    val colors = listOf(MacroColors.protein, MacroColors.carbs, MacroColors.fat)
    val values = listOf(split.proteinPct, split.carbsPct, split.fatPct)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 22.dp.toPx()
            var start = -90f
            values.forEachIndexed { i, pct ->
                val sweep = (pct / 100.0 * 360.0).toFloat()
                drawArc(
                    colors[i],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colors[i]))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$label  ${Amounts.percent(values[i])}",
                        style = MaterialTheme.typography.bodyMedium.tabularFigures()
                    )
                }
            }
        }
    }
}

/**
 * Two series on their own nice scales over the same dates: kcal on the left axis, body weight on the right
 * (both 7-day moving averages). Tapping shows both values of the nearest date.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun DualAxisChart(
    kcal: List<DatedValue>,
    weight: List<DatedValue>,
    kcalUnit: String,
    weightUnit: String,
    kcalLegend: String,
    weightLegend: String,
    dateLabel: (LocalDate) -> String,
    tooltip: (LocalDate) -> String,
    description: String,
) {
    val dates = (kcal + weight).map { it.date }.distinct().sorted()
    if (dates.isEmpty()) return
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    val kcalColor = MacroColors.kcal
    val weightColor = MacroColors.withinGoal
    val guide = MaterialTheme.colorScheme.outline
    val labels = dates.associateWith(dateLabel)
    var selected by remember(dates) { mutableStateOf<LocalDate?>(null) }
    val plot = remember { PlotHolder() }
    val first = dates.first().toEpochDays()
    val span = (dates.last().toEpochDays() - first).coerceAtLeast(1)
    fun x(date: LocalDate, area: PlotArea) = area.left + (date.toEpochDays() - first).toFloat() / span * area.width
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(dates) {
                detectTapGestures { tap ->
                    val area = plot.area ?: return@detectTapGestures
                    val nearest = dates.minBy { abs(x(it, area) - tap.x) }
                    selected = if (selected == nearest) null else nearest
                }
            },
    ) {
        val kScale = ChartScale.nice(kcal.minOfOrNull { it.value } ?: 0.0, kcal.maxOfOrNull { it.value } ?: 1.0)
        val wScale = weight.takeIf { it.isNotEmpty() }?.let { w ->
            weightScale(
                w.minOf { it.value },
                w.maxOf { it.value }
            )
        }
        val area = drawAxes(kScale, kcalUnit, measurer, style, right = wScale, rightUnit = wScale?.let { weightUnit })
        plot.area = area
        fun line(points: List<DatedValue>, scale: AxisScale?, color: Color) {
            if (scale == null || points.isEmpty()) return
            val offsets = points.map { Offset(x(it.date, area), area.y(scale, it.value)) }
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            offsets.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
        }
        line(kcal, kScale, kcalColor)
        line(weight, wScale, weightColor)
        ChartScale.labelIndices(dates.size, MAX_X_LABELS).forEach { i ->
            drawXLabel(labels.getValue(dates[i]), x(dates[i], area), measurer, style)
        }
        selected?.let { date ->
            val sx = x(date, area)
            drawSelectionGuide(sx, area, guide)
            drawTooltip(tooltip(date), Offset(sx, area.top), measurer, style)
        }
    }
    LegendRow(listOf(kcalColor to kcalLegend, weightColor to weightLegend))
}

/** A weight axis spanning at least [MIN_WEIGHT_SPAN_KG], so one entry or a flat week does not stretch to ±10 %. */
private fun weightScale(low: Double, high: Double): AxisScale {
    val pad = ((MIN_WEIGHT_SPAN_KG - (high - low)) / 2).coerceAtLeast(0.0)
    return ChartScale.nice(low - pad, high + pad)
}

private const val MIN_WEIGHT_SPAN_KG = 2.0

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LegendRow(items: List<Pair<Color, String>>) {
    FlowRow(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (color, label) -> LegendDot(color, label) }
    }
}

/** A tinted strip explaining what a chart will show once there is data. */
@Composable
internal fun ChartEmpty(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
