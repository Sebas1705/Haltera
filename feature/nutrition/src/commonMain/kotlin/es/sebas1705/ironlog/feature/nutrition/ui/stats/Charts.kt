package es.sebas1705.ironlog.feature.nutrition.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.domain.usecases.nutrition.DatedValue
import es.sebas1705.ironlog.domain.usecases.nutrition.GoalCalculator
import es.sebas1705.ironlog.domain.usecases.nutrition.MacroSplit
import es.sebas1705.ironlog.domain.usecases.nutrition.NutritionStats
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.ui.components.MacroColors
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import es.sebas1705.ironlog.feature.nutrition.ui.components.shortDate
import kotlinx.datetime.LocalDate
import kotlin.math.max

private val CHART_HEIGHT = 180.dp
private const val GRID_LINES = 4
private const val BAR_FILL = 0.7f

@Composable
private fun AxisLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.tabularFigures(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        AxisLabel(label)
    }
}

private fun DrawScope.grid(color: Color) {
    for (i in 0..GRID_LINES) {
        val y = size.height * i / GRID_LINES
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/** X axis: first and last date of the range. */
@Composable
private fun DateAxis(dates: List<LocalDate>) {
    if (dates.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(start = 40.dp)) {
        AxisLabel(shortDate(dates.first()))
        Spacer(Modifier.weight(1f))
        AxisLabel(shortDate(dates.last()))
    }
}

/**
 * Daily kcal as bars, one slot per date of the range (days without entries stay empty), with the goal as a dashed
 * line. Bars within ±10 % of the goal are green.
 */
@Composable
internal fun KcalBarsChart(dates: List<LocalDate>, kcal: Map<LocalDate, Double>, goal: Int?, description: String) {
    val maxValue = max(kcal.values.maxOrNull() ?: 0.0, (goal ?: 0) * 1.15).coerceAtLeast(1.0)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bar = MacroColors.kcal
    val within = MacroColors.withinGoal
    val goalColor = MaterialTheme.colorScheme.onSurface
    Row {
        Column(Modifier.height(CHART_HEIGHT).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(Amounts.format(maxValue, 0))
            AxisLabel("0")
        }
        Canvas(Modifier.weight(1f).height(CHART_HEIGHT).semantics { contentDescription = description }) {
            grid(grid)
            val slot = size.width / dates.size.coerceAtLeast(1)
            dates.forEachIndexed { i, date ->
                val v = kcal[date] ?: return@forEachIndexed
                val h = (v / maxValue * size.height).toFloat()
                val color = if (goal != null && NutritionStats.withinGoal(v, goal)) within else bar
                drawRoundRect(
                    color,
                    topLeft = Offset(i * slot + slot * (1 - BAR_FILL) / 2, size.height - h),
                    size = Size(slot * BAR_FILL, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            if (goal != null) {
                val y = (size.height - goal / maxValue * size.height).toFloat()
                drawLine(
                    goalColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
        }
    }
    DateAxis(dates)
}

/** Energy from protein, carbohydrate and fat per day as stacked bars (4/4/9 kcal per gram). */
@Composable
internal fun StackedMacroChart(dates: List<LocalDate>, nutrients: Map<LocalDate, Nutrients>, description: String) {
    fun energy(n: Nutrients) = Triple(
        n.protein * GoalCalculator.KCAL_PER_G_PROTEIN,
        n.carbs * GoalCalculator.KCAL_PER_G_CARBS,
        n.fat * GoalCalculator.KCAL_PER_G_FAT,
    )
    val maxValue = nutrients.values.maxOfOrNull { energy(it).let { (p, c, f) -> p + c + f } }?.coerceAtLeast(1.0) ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    val colors = listOf(MacroColors.protein, MacroColors.carbs, MacroColors.fat)
    Row {
        Column(Modifier.height(CHART_HEIGHT).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(Amounts.format(maxValue, 0))
            AxisLabel("0")
        }
        Canvas(Modifier.weight(1f).height(CHART_HEIGHT).semantics { contentDescription = description }) {
            grid(grid)
            val slot = size.width / dates.size.coerceAtLeast(1)
            dates.forEachIndexed { i, date ->
                val n = nutrients[date] ?: return@forEachIndexed
                val (p, c, f) = energy(n)
                var bottom = size.height
                listOf(p, c, f).forEachIndexed { k, value ->
                    val h = (value / maxValue * size.height).toFloat()
                    drawRect(
                        colors[k],
                        topLeft = Offset(i * slot + slot * (1 - BAR_FILL) / 2, bottom - h),
                        size = Size(slot * BAR_FILL, h)
                    )
                    bottom -= h
                }
            }
        }
    }
    DateAxis(dates)
}

/** Average macro energy split as a donut with the percentages beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MacroDonut(split: MacroSplit, labels: List<String>, description: String) {
    val colors = listOf(MacroColors.protein, MacroColors.carbs, MacroColors.fat)
    val values = listOf(split.proteinPct, split.carbsPct, split.fatPct)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(132.dp).semantics { contentDescription = description }) {
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
            val lang = language()
            labels.forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colors[i]))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$label  ${Amounts.format(values[i], 0, lang)} %",
                        style = MaterialTheme.typography.bodyMedium.tabularFigures()
                    )
                }
            }
        }
    }
}

/**
 * Two series on their own scales over the same dates: kcal on the left axis, body weight on the right. Both are
 * 7-day moving averages.
 */
@Suppress("CyclomaticComplexMethod") // Two scaled series with their axes and legend.
@Composable
internal fun DualAxisChart(
    kcal: List<DatedValue>,
    weight: List<DatedValue>,
    kcalLabel: String,
    weightLabel: String,
    description: String,
) {
    val dates = (kcal + weight).map { it.date }.distinct().sorted()
    if (dates.isEmpty()) return
    val first = dates.first()
    val span = (dates.last().toEpochDays() - first.toEpochDays()).coerceAtLeast(1)
    val kcalColor = MacroColors.kcal
    val weightColor = MacroColors.withinGoal
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lang = language()
    fun bounds(points: List<DatedValue>): Pair<Double, Double>? {
        if (points.isEmpty()) return null
        val lo = points.minOf { it.value }
        val hi = points.maxOf { it.value }
        val pad = ((hi - lo) * 0.1).coerceAtLeast(if (hi == lo) 1.0 else 0.0)
        return (lo - pad) to (hi + pad)
    }
    val kb = bounds(kcal)
    val wb = bounds(weight)
    Row {
        Column(Modifier.height(CHART_HEIGHT).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(kb?.second?.let { Amounts.format(it, 0) } ?: "")
            AxisLabel(kb?.first?.let { Amounts.format(it, 0) } ?: "")
        }
        Canvas(Modifier.weight(1f).height(CHART_HEIGHT).semantics { contentDescription = description }) {
            grid(grid)
            fun line(points: List<DatedValue>, b: Pair<Double, Double>?, color: Color) {
                if (b == null || points.isEmpty()) return
                val offsets = points.map {
                    val x = (it.date.toEpochDays() - first.toEpochDays()).toFloat() / span * size.width
                    val y = (size.height - (it.value - b.first) / (b.second - b.first) * size.height).toFloat()
                    Offset(x, y)
                }
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                offsets.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
            }
            line(kcal, kb, kcalColor)
            line(weight, wb, weightColor)
        }
        Column(
            Modifier.height(CHART_HEIGHT).width(44.dp).padding(start = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AxisLabel(wb?.second?.let { Amounts.format(it, 1, lang) } ?: "")
            AxisLabel(wb?.first?.let { Amounts.format(it, 1, lang) } ?: "")
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 44.dp)) {
        AxisLabel(shortDate(dates.first()))
        Spacer(Modifier.weight(1f))
        AxisLabel(shortDate(dates.last()))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
        LegendDot(kcalColor, kcalLabel)
        LegendDot(weightColor, weightLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LegendRow(items: List<Pair<Color, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (color, label) -> LegendDot(color, label) }
    }
}
