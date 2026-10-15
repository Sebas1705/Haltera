package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.domain.usecases.stats.HeatCell
import es.sebas1705.ironlog.domain.usecases.stats.Heatmap
import es.sebas1705.ironlog.domain.usecases.stats.Heatmaps
import es.sebas1705.ironlog.feature.progress.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private val MAX_CELL = 20.dp
private val MIN_CELL = 11.dp
private val CELL_GAP = 3.dp
private val DAY_GUTTER = 30.dp
private val MONTH_ROW = 16.dp
private val LEVEL_ALPHAS = floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f)

/** Colour of a heatmap level: the surface tint for none, then the primary colour at growing strength. */
@Composable
internal fun heatColors(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    return remember(
        primary,
        empty
    ) { LEVEL_ALPHAS.mapIndexed { i, a -> if (i == 0) empty else primary.copy(alpha = a) } }
}

/** Cell geometry of a heatmap, in pixels. */
private data class HeatGeometry(val cell: Float, val step: Float, val left: Float, val top: Float) {
    fun origin(column: Int, row: Int) = Offset(left + column * step, top + row * step)

    fun hit(heatmap: Heatmap, at: Offset): HeatCell? {
        val column = ((at.x - left) / step).toInt()
        val row = ((at.y - top) / step).toInt()
        if (at.x < left || at.y < top) return null
        return heatmap.weeks.getOrNull(column)?.getOrNull(row)?.takeIf { !it.future }
    }
}

/**
 * GitHub-style training calendar: one column per week (Monday on top), months above, a tooltip for the
 * tapped day. Scrolls sideways (starting at today) when the weeks do not fit.
 */
@Composable
internal fun HeatmapChart(
    heatmap: Heatmap,
    tooltip: (HeatCell) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val labels = HeatLabels(
        stringArrayResource(Res.array.progress_weekdays_short),
        stringArrayResource(Res.array.progress_months_short)
    )
    val colors = heatColors()
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(heatmap) { mutableStateOf<HeatCell?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val weeks = heatmap.weeks.size
        val cell = ((maxWidth - DAY_GUTTER) / weeks - CELL_GAP).coerceIn(MIN_CELL, MAX_CELL)
        val scroll = rememberScrollState()
        LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        val density = LocalDensity.current
        val geometry = with(
            density
        ) { HeatGeometry(cell.toPx(), (cell + CELL_GAP).toPx(), DAY_GUTTER.toPx(), MONTH_ROW.toPx()) }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .width(DAY_GUTTER + (cell + CELL_GAP) * weeks)
                    .height(MONTH_ROW + (cell + CELL_GAP) * DAYS + LEGEND_ROOM)
                    .pointerInput(heatmap, geometry) {
                        detectTapGestures { tap ->
                            val hit = geometry.hit(heatmap, tap)
                            selected = if (hit == selected) null else hit
                        }
                    },
            ) {
                drawHeatmap(heatmap, geometry, colors, labels, measurer, style)
                selected?.let { day ->
                    val column = heatmap.weeks.indexOfFirst { w -> w.any { it.day == day.day } }
                    val row = heatmap.weeks[column].indexOfFirst { it.day == day.day }
                    val center = geometry.origin(column, row) + Offset(geometry.cell / 2, geometry.cell / 2)
                    drawCircle(style.tooltipBackground, radius = geometry.cell / 4, center = center)
                    drawTooltip(tooltip(day), center, measurer, style)
                }
            }
        }
        HeatLegend(colors, Modifier.align(Alignment.BottomEnd))
    }
}

private class HeatLabels(val weekdays: List<String>, val months: List<String>)

private const val DAYS = 7
private val LEGEND_ROOM = 36.dp
private val LABELLED_ROWS = listOf(0, 2, 4)
private const val MIN_LABEL_COLUMNS = 3

private fun DrawScope.drawHeatmap(
    heatmap: Heatmap,
    geometry: HeatGeometry,
    colors: List<Color>,
    labels: HeatLabels,
    measurer: TextMeasurer,
    style: ChartStyle,
) {
    LABELLED_ROWS.forEach { row ->
        val layout = measurer.measure(labels.weekdays[row], style.labelStyle)
        drawText(
            layout,
            style.axisText,
            Offset(0f, geometry.origin(0, row).y + (geometry.cell - layout.size.height) / 2)
        )
    }
    val months = heatmap.weeks.map { LocalDate.fromEpochDays(it.first().day.toLong()).month.ordinal }
    months.forEachIndexed { column, month ->
        val starts = column == 0 || months[column - 1] != month
        val roomy = (column + 1 until minOf(column + MIN_LABEL_COLUMNS, months.size)).all { months[it] == month }
        if (starts && roomy) {
            drawText(
                measurer.measure(labels.months[month], style.labelStyle),
                style.axisText,
                Offset(geometry.origin(column, 0).x, 0f)
            )
        }
    }
    val size = Size(geometry.cell, geometry.cell)
    heatmap.weeks.forEachIndexed { column, week ->
        week.forEachIndexed { row, day ->
            if (!day.future) {
                drawRoundRect(
                    colors[day.level.coerceIn(0, Heatmaps.MAX_LEVEL)],
                    topLeft = geometry.origin(column, row),
                    size = size,
                    cornerRadius = CornerRadius(CELL_RADIUS_DP.dp.toPx()),
                )
            }
        }
    }
}

private const val CELL_RADIUS_DP = 2

@Composable
private fun HeatLegend(colors: List<Color>, modifier: Modifier) {
    Row(
        modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        LegendText(stringResource(Res.string.progress_heatmap_less))
        colors.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
        LegendText(stringResource(Res.string.progress_heatmap_more))
    }
}

@Composable
private fun LegendText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Horizontal bars of this period (solid) over the previous one (outline) for one row of a comparison
 * list, e.g. a muscle's weekly sets. [max] is the largest value of the whole list.
 */
@Composable
internal fun ComparisonBarRow(
    label: String,
    current: Double,
    previous: Double?,
    max: Double,
    value: String,
    change: String?,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val colors = MaterialTheme.colorScheme
    val description = listOfNotNull(label, value, change).joinToString(", ")
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Bar(current / max, colors.primary, barHeight)
        if (previous != null) Bar(previous / max, colors.outline.copy(alpha = 0.5f), barHeight / 2)
        change?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bar(fraction: Double, color: Color, height: Dp) {
    val f = if (fraction.isNaN()) 0f else fraction.toFloat().coerceIn(0f, 1f)
    Box(
        Modifier
            .padding(top = 3.dp)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (f > 0f) {
            Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(height / 2)).background(color))
        }
    }
}
