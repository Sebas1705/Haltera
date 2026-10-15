package es.sebas1705.ironlog.feature.progress.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.domain.usecases.stats.RepRange
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.resources.*
import es.sebas1705.ironlog.feature.progress.ui.components.BarChart
import es.sebas1705.ironlog.feature.progress.ui.components.ChartEmpty
import es.sebas1705.ironlog.feature.progress.ui.components.ChartLegend
import es.sebas1705.ironlog.feature.progress.ui.components.DateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.LineChart
import es.sebas1705.ironlog.feature.progress.ui.components.LineSeries
import es.sebas1705.ironlog.feature.progress.ui.components.StatCard
import es.sebas1705.ironlog.feature.progress.ui.components.UnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.barsDescription
import es.sebas1705.ironlog.feature.progress.ui.components.formatNumber
import es.sebas1705.ironlog.feature.progress.ui.components.lineDescription
import es.sebas1705.ironlog.feature.progress.ui.components.variantLabel
import org.jetbrains.compose.resources.stringResource

/** Grams series in the user's unit. */
private fun List<ChartPoint>.inUnit(units: UnitFormat) = map { ChartPoint(it.x, units.toUnit(it.y)) }

@Composable
internal fun OneRmCard(
    state: ExerciseProgressState,
    units: UnitFormat,
    dates: DateFormat,
    onCompare: (String?) -> Unit
) {
    val title = stringResource(Res.string.progress_chart_one_rm)
    val main = state.charts.oneRm.inUnit(units)
    val compare = state.compareOneRm.inUnit(units)
    val trendLine = state.trend?.let { t ->
        listOf(t.firstAt, t.lastAt).map { at -> ChartPoint(at, units.toUnit(t.trend.at(at / MILLIS_PER_DAY))) }
    }
    val colors = MaterialTheme.colorScheme
    val selectedName = variantLabel(state.selectedVariant)
    val compareName = state.compareVariant?.let { variantLabel(it) }
    val trendName = stringResource(Res.string.progress_chart_trend_line)
    val format: (Double) -> String = { "${formatNumber(it, 1)} ${units.label}" }
    StatCard(
        title,
        metric = Metric.ESTIMATED_ONE_RM,
        subtitle = stringResource(Res.string.progress_chart_one_rm_subtitle)
    ) {
        if (main.size < 2 && compare.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_chart_one_rm_empty))
        } else {
            val series = listOfNotNull(
                LineSeries(main, colors.primary, selectedName),
                trendLine?.let {
                    LineSeries(
                        it,
                        colors.outline,
                        trendName,
                        dots = false,
                        width = 1.5.dp,
                        dashed = true,
                        selectable = false
                    )
                },
                compareName?.let { LineSeries(compare, colors.tertiary, it) },
            )
            LineChart(
                series = series,
                format = units::axis,
                unit = units.label,
                dateLabel = { dates.short(it) },
                tooltip = { s, p ->
                    "${series[s].label.orEmpty()}\n${dates.short(
                        series[s].points[p].x
                    )}: ${format(series[s].points[p].y)}"
                },
                description = lineDescription(
                    title,
                    dates.short(main.firstOrNull()?.x ?: 0),
                    dates.short(main.lastOrNull()?.x ?: 0),
                    main.map { it.y },
                    format,
                ),
            )
            ChartLegend(series.mapNotNull { s -> s.label?.let { it to s.color } })
        }
        CompareMenu(state, onCompare)
    }
}

@Composable
private fun CompareMenu(state: ExerciseProgressState, onCompare: (String?) -> Unit) {
    if (state.compareOptions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                state.compareVariant?.let { stringResource(Res.string.progress_compare_selected, variantLabel(it)) }
                    ?: stringResource(Res.string.progress_compare_with),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.compareVariantId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.progress_compare_none)) },
                    onClick = {
                        expanded = false
                        onCompare(null)
                    },
                )
            }
            state.compareOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(variantLabel(option)) },
                    onClick = {
                        expanded = false
                        onCompare(option.variantId)
                    },
                )
            }
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000.0

@Composable
internal fun TopSetCard(state: ExerciseProgressState, units: UnitFormat, dates: DateFormat) {
    val title = stringResource(Res.string.progress_chart_top_set)
    val points = state.charts.topSet.inUnit(units)
    val format: (Double) -> String = { "${formatNumber(it, 1)} ${units.label}" }
    StatCard(title, metric = Metric.TOP_SET, subtitle = stringResource(Res.string.progress_chart_top_set_subtitle)) {
        if (points.size < 2) {
            ChartEmpty(stringResource(Res.string.progress_chart_not_enough_sessions))
            return@StatCard
        }
        LineChart(
            series = listOf(LineSeries(points, MaterialTheme.colorScheme.secondary)),
            format = units::axis,
            unit = units.label,
            dateLabel = { dates.short(it) },
            tooltip = { _, p -> "${dates.short(points[p].x)}\n${format(points[p].y)}" },
            description = lineDescription(
                title,
                dates.short(points.first().x),
                dates.short(points.last().x),
                points.map { it.y },
                format
            ),
        )
    }
}

@Composable
internal fun SessionVolumeCard(state: ExerciseProgressState, units: UnitFormat, dates: DateFormat) {
    val title = stringResource(Res.string.progress_chart_volume)
    val points = state.charts.volume.takeLast(MAX_VOLUME_BARS)
    val values = points.map { units.toUnit(it.y) }
    val labels = points.map { dates.short(it.x) }
    val format: (Double) -> String = { "${formatNumber(it, 0)} ${units.label}" }
    StatCard(
        title,
        metric = Metric.VOLUME,
        subtitle = stringResource(Res.string.progress_chart_volume_subtitle, MAX_VOLUME_BARS)
    ) {
        if (points.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_chart_volume_session_empty))
            return@StatCard
        }
        BarChart(
            values = values,
            labels = labels,
            format = units::axis,
            unit = units.label,
            tooltip = { i -> "${labels[i]}\n${format(values[i])}" },
            description = barsDescription(title, labels, values, format),
            highlightLast = true,
        )
    }
}

private const val MAX_VOLUME_BARS = 20

@Composable
internal fun RepRangesCard(state: ExerciseProgressState) {
    val title = stringResource(Res.string.progress_chart_rep_ranges)
    val ranges = RepRange.entries
    val values = ranges.map { (state.repRanges[it] ?: 0).toDouble() }
    val labels = ranges.map { range ->
        range.max?.let { stringResource(Res.string.progress_rep_range, range.min, it) }
            ?: stringResource(Res.string.progress_rep_range_open, range.min)
    }
    val setsUnit = stringResource(Res.string.progress_axis_sets)
    val total = values.sum()
    val format: (Double) -> String = { "${formatNumber(it, 0)} $setsUnit" }
    StatCard(
        title,
        metric = Metric.REP_RANGES,
        subtitle = stringResource(Res.string.progress_chart_rep_ranges_subtitle)
    ) {
        if (total <= 0) {
            ChartEmpty(stringResource(Res.string.progress_chart_rep_ranges_empty))
            return@StatCard
        }
        BarChart(
            values = values,
            labels = labels,
            format = { formatNumber(it, 0) },
            unit = setsUnit,
            maxLabels = values.size,
            tooltip = { i ->
                val share = Numbers.percent(values[i] / total * 100)
                "${labels[i]}: ${format(values[i])} ($share)"
            },
            description = barsDescription(title, labels, values, format),
        )
        Column(Modifier.padding(top = 4.dp)) {
            Text(
                stringResource(Res.string.progress_rep_range_axis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
