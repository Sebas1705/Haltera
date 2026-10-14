@file:Suppress("MatchingDeclarationName") // The chart composables and their series type live together.

package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_description
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_not_enough
import org.jetbrains.compose.resources.stringResource

/** One line of a [LineChart]. */
internal data class ChartSeries(
    val points: List<ChartPoint>,
    val color: Color,
    val label: String? = null,
    val showDots: Boolean = true,
    val strokeWidth: Dp = 2.5.dp,
)

/**
 * A titled card with a time line chart drawn on a Canvas. The y range spans the values of every series
 * (with a small margin); the x range spans their times. Min and max values are shown on the left and
 * the first and last dates below.
 */
@Composable
internal fun LineChartCard(
    title: String,
    series: List<ChartSeries>,
    formatValue: @Composable (Double) -> String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val main = series.firstOrNull()?.points.orEmpty()
            Text(title, style = MaterialTheme.typography.titleMedium)
            main.lastOrNull()?.let { last ->
                Text(
                    formatValue(last.y),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            if (main.size < 2) {
                Text(stringResource(Res.string.progress_chart_not_enough), style = MaterialTheme.typography.bodyMedium)
            } else {
                ChartBody(title, series, main, formatValue)
            }
        }
    }
}

@Composable
private fun ChartBody(
    title: String,
    series: List<ChartSeries>,
    main: List<ChartPoint>,
    formatValue: @Composable (Double) -> String,
) {
    val all = series.flatMap { it.points }
    val minY = all.minOf { it.y }
    val maxY = all.maxOf { it.y }
    val description = stringResource(
        Res.string.progress_chart_description,
        title,
        formatValue(main.first().y),
        formatValue(main.last().y),
    )
    Row(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.height(160.dp).padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisLabel(formatValue(maxY))
            AxisLabel(formatValue(minY))
        }
        LineChart(series, Modifier.weight(1f).height(160.dp).semantics { contentDescription = description })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AxisLabel(formatDate(all.minOf { it.x }, withYear = true))
        AxisLabel(formatDate(all.maxOf { it.x }, withYear = true))
    }
    if (series.count { it.label != null } > 1) {
        Legend(series)
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Legend(series: List<ChartSeries>) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { s ->
            val label = s.label ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                Spacer(Modifier.width(6.dp))
                AxisLabel(label)
            }
        }
    }
}

/** The plot itself: grid lines and one polyline per series. */
@Composable
internal fun LineChart(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val all = series.flatMap { it.points }
        if (all.isEmpty()) return@Canvas
        val bounds = Bounds.of(all)
        drawGrid(gridColor)
        series.forEach { s -> drawSeries(s, bounds) }
    }
}

private data class Bounds(val minX: Long, val maxX: Long, val minY: Double, val maxY: Double) {
    fun x(value: Long, width: Float): Float =
        if (maxX == minX) width / 2 else ((value - minX).toDouble() / (maxX - minX) * width).toFloat()

    fun y(value: Double, height: Float): Float =
        if (maxY == minY) height / 2 else (height - (value - minY) / (maxY - minY) * height).toFloat()

    companion object {
        private const val MARGIN = 0.05

        fun of(points: List<ChartPoint>): Bounds {
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val margin = (maxY - minY) * MARGIN
            return Bounds(points.minOf { it.x }, points.maxOf { it.x }, minY - margin, maxY + margin)
        }
    }
}

private const val GRID_LINES = 4

private fun DrawScope.drawGrid(color: Color) {
    for (i in 0..GRID_LINES) {
        val y = size.height * i / GRID_LINES
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawSeries(series: ChartSeries, bounds: Bounds) {
    if (series.points.isEmpty()) return
    val offsets = series.points.map { Offset(bounds.x(it.x, size.width), bounds.y(it.y, size.height)) }
    val path = Path().apply {
        moveTo(offsets.first().x, offsets.first().y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = series.color,
        style = Stroke(width = series.strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    if (series.showDots) {
        offsets.forEach { drawCircle(series.color, radius = series.strokeWidth.toPx() * 1.5f, center = it) }
    }
}
