package es.sebas1705.ironlog.feature.routines.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.domain.usecases.routinescore.MuscleGroup
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_analysis_chart_legend
import es.sebas1705.ironlog.feature.routines.resources.routines_analysis_chart_title
import es.sebas1705.ironlog.feature.routines.resources.routines_analysis_chart_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.max

private const val BAND_LOW = 10.0
private const val BAND_HIGH = 20.0
private const val FLAG_LOW = 6.0
private const val FLAG_HIGH = 25.0
private const val MIN_AXIS = 30.0
private const val AXIS_STEP = 10.0

/** A horizontal 0–100 bar filled in the score colour. */
@Composable
internal fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = scoreColor(score)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * score.coerceIn(0, 100) / 100f
        if (width > 0f) drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
    }
}

/**
 * Weekly sets per muscle group as horizontal bars drawn on a Compose Canvas, with the 10–20 sets target band
 * shaded. Major groups always show (zero included); minor groups only when trained.
 */
@Composable
internal fun WeeklySetsCard(weekly: Map<MuscleGroup, Double>, language: String) {
    val groups = MuscleGroup.entries.filter { it.major || (weekly[it] ?: 0.0) > 0.0 }
    val axisMax = ceil(max(MIN_AXIS, (weekly.values.maxOrNull() ?: 0.0) * 1.05) / AXIS_STEP) * AXIS_STEP
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.routines_analysis_chart_title), style = MaterialTheme.typography.titleMedium)
            // No gap between rows, so the target band reads as one column.
            Column {
                groups.forEach { group ->
                    val sets = weekly[group] ?: 0.0
                    val label = groupLabel(group)
                    val description =
                        stringResource(Res.string.routines_analysis_chart_value, label, formatOne(sets, language))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (group.major) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(LABEL_WIDTH),
                        )
                        SetsBar(sets, axisMax, group.major, Modifier.weight(1f).height(26.dp))
                        Text(
                            formatOne(sets, language),
                            style = MaterialTheme.typography.bodySmall.tabularFigures(),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(VALUE_WIDTH),
                        )
                    }
                }
            }
            AxisTicks(axisMax)
            Text(
                stringResource(Res.string.routines_analysis_chart_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetsBar(sets: Double, axisMax: Double, major: Boolean, modifier: Modifier) {
    val band = IronlogTheme.colors.success.copy(alpha = 0.18f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        !major -> MaterialTheme.colorScheme.outline
        sets in BAND_LOW..BAND_HIGH -> IronlogTheme.colors.success
        sets < FLAG_LOW || sets > FLAG_HIGH -> IronlogTheme.colors.warning
        else -> IronlogTheme.colors.success.copy(alpha = 0.55f)
    }
    Canvas(modifier) {
        fun x(value: Double) = (size.width * (value / axisMax)).toFloat().coerceIn(0f, size.width)
        val barHeight = size.height * 0.45f
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2, barHeight / 2)
        drawRoundRect(
            track,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius
        )
        drawRect(band, topLeft = Offset(x(BAND_LOW), 0f), size = Size(x(BAND_HIGH) - x(BAND_LOW), size.height))
        val width = x(sets)
        if (width > 0f) {
            drawRoundRect(
                color,
                topLeft = Offset(0f, top),
                size = Size(max(width, barHeight), barHeight),
                cornerRadius = radius
            )
        }
    }
}

/** 0, 10, 20… under the bars, aligned with them. */
@Composable
private fun AxisTicks(axisMax: Double) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row {
        Canvas(Modifier.padding(start = LABEL_WIDTH, end = VALUE_WIDTH).weight(1f).height(16.dp)) {
            var value = 0.0
            while (value <= axisMax) {
                val layout = measurer.measure(value.toInt().toString(), style)
                val x = (size.width * (value / axisMax)).toFloat()
                // Centred on its tick. The last label may run into the value column on the right (the
                // canvas does not clip) rather than being pushed left onto the previous one.
                val left = (x - layout.size.width / 2f).coerceAtLeast(0f)
                drawText(layout, topLeft = Offset(left, 0f))
                value += AXIS_STEP
            }
        }
    }
}

private val LABEL_WIDTH = 104.dp
private val VALUE_WIDTH = 40.dp
