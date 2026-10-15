package es.sebas1705.ironlog.feature.progress.ui.bodyweight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.domain.usecases.stats.WeeklyWeight
import es.sebas1705.ironlog.feature.progress.resources.*
import es.sebas1705.ironlog.feature.progress.ui.components.ChartEmpty
import es.sebas1705.ironlog.feature.progress.ui.components.ChartLegend
import es.sebas1705.ironlog.feature.progress.ui.components.ComparisonTile
import es.sebas1705.ironlog.feature.progress.ui.components.DateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.LineChart
import es.sebas1705.ironlog.feature.progress.ui.components.LineSeries
import es.sebas1705.ironlog.feature.progress.ui.components.StatCard
import es.sebas1705.ironlog.feature.progress.ui.components.StatsGrid
import es.sebas1705.ironlog.feature.progress.ui.components.TileGrid
import es.sebas1705.ironlog.feature.progress.ui.components.UnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.formatNumber
import es.sebas1705.ironlog.feature.progress.ui.components.formatWeight
import es.sebas1705.ironlog.feature.progress.ui.components.fullWidth
import es.sebas1705.ironlog.feature.progress.ui.components.lineDescription
import es.sebas1705.ironlog.feature.progress.ui.components.rememberDateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.rememberUnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.signed
import es.sebas1705.ironlog.feature.progress.ui.components.unitLabel
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val WEEKS_SHOWN = 8

/** Body weight tab: summary tiles, chart with moving average, weekly averages and the editable entries. */
@Composable
internal fun BodyWeightTab(viewModel: BodyWeightViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (BodyWeightIntent) -> Unit = { viewModel.onEvent(it) }
    if (state.loading) {
        LoadingScreen()
        return
    }
    val units = rememberUnitFormat(state.unit)
    val dates = rememberDateFormat()
    StatsGrid {
        fullWidth("summary") { SummaryTiles(state, units, onAdd = { onEvent(BodyWeightIntent.OpenAdd) }) }
        item("chart") { WeightChart(state, units, dates) }
        item("weekly") { WeeklyTable(state, units, dates) }
        if (state.entries.isNotEmpty()) {
            fullWidth("entries") { EntriesCard(state, dates, onEvent) }
        }
    }
    if (state.dialog != null) {
        WeightDialogContent(state, onEvent)
    }
}

@Composable
private fun SummaryTiles(state: BodyWeightState, units: UnitFormat, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val latest = state.entries.firstOrNull()
        if (latest == null) ChartEmpty(stringResource(Res.string.progress_bw_empty))
        if (latest != null) {
            val rate = state.rate
            TileGrid(
                listOf(
                    { m ->
                        ComparisonTile(
                            stringResource(Res.string.progress_bw_latest_title),
                            units.weight(latest.weightGrams.toDouble(), 2),
                            null,
                            units::volume,
                            Metric.BODY_WEIGHT_CHANGE,
                            m,
                            footnote = trendText(state.change30Days, units),
                        )
                    },
                    { m ->
                        ComparisonTile(
                            stringResource(Res.string.progress_bw_rate_title),
                            rate?.let { r -> rateText(r.gramsPerWeek, units) } ?: "—",
                            null,
                            units::volume,
                            Metric.BODY_WEIGHT_RATE,
                            m,
                            footnote = rate?.let { stringResource(Res.string.progress_bw_rate_detail, it.entries, it.days) }
                                ?: stringResource(Res.string.progress_bw_rate_none),
                        )
                    },
                ),
            )
        }
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(Res.string.progress_bw_log), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun rateText(gramsPerWeek: Double, units: UnitFormat): String =
    stringResource(Res.string.progress_trend_value, signed(gramsPerWeek) { units.weight(it, 2) })

@Composable
private fun trendText(change: Long?, units: UnitFormat): String {
    if (change == null) return stringResource(Res.string.progress_bw_change_none)
    return stringResource(Res.string.progress_bw_change, signed(change.toDouble()) { units.weight(it, 1) })
}

@Composable
private fun WeightChart(state: BodyWeightState, units: UnitFormat, dates: DateFormat) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.progress_bw_chart)
    val raw = state.chart.raw.map { ChartPoint(it.x, units.toUnit(it.y)) }
    val average = state.chart.average.map { ChartPoint(it.x, units.toUnit(it.y)) }
    val rawLabel = stringResource(Res.string.progress_bw_entries_series)
    val averageLabel = stringResource(Res.string.progress_bw_average)
    val format: (Double) -> String = { "${formatNumber(it, 1)} ${units.label}" }
    StatCard(title, metric = Metric.MOVING_AVERAGE, subtitle = stringResource(Res.string.progress_bw_chart_subtitle)) {
        if (raw.size < 2) {
            ChartEmpty(stringResource(Res.string.progress_bw_chart_empty))
            return@StatCard
        }
        val series = listOf(
            LineSeries(raw, colors.outline, rawLabel, width = 1.5.dp),
            LineSeries(average, colors.primary, averageLabel, dots = false, width = 3.dp),
        )
        LineChart(
            series = series,
            format = units::axis,
            unit = units.label,
            dateLabel = { dates.short(it) },
            tooltip = { s, p ->
                val point = series[s].points[p]
                "${dates.short(point.x)}\n${series[s].label}: ${format(point.y)}"
            },
            description = lineDescription(
                title,
                dates.short(raw.first().x),
                dates.short(raw.last().x),
                raw.map { it.y },
                format
            ),
        )
        ChartLegend(series.map { it.label.orEmpty() to it.color })
    }
}

@Composable
private fun WeeklyTable(state: BodyWeightState, units: UnitFormat, dates: DateFormat) {
    StatCard(
        stringResource(Res.string.progress_bw_weekly_title),
        metric = Metric.BODY_WEIGHT_WEEKLY,
        subtitle = stringResource(Res.string.progress_bw_weekly_subtitle, units.label),
    ) {
        if (state.weekly.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_bw_weekly_empty))
            return@StatCard
        }
        val header = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        WeekCells(
            listOf(
                stringResource(Res.string.progress_bw_week),
                stringResource(Res.string.progress_bw_avg),
                stringResource(Res.string.progress_bw_range),
                stringResource(Res.string.progress_table_delta),
            ),
            header,
            Modifier.padding(vertical = 6.dp),
        )
        HorizontalDivider()
        state.weekly.take(WEEKS_SHOWN).forEach { week -> WeekRow(week, units, dates) }
    }
}

@Composable
private fun WeekRow(week: WeeklyWeight, units: UnitFormat, dates: DateFormat) {
    fun kg(grams: Double) = formatNumber(units.toUnit(grams), 1)
    val avg = kg(week.averageGrams)
    val range = "${kg(week.minGrams.toDouble())}–${kg(week.maxGrams.toDouble())}"
    val change = week.change?.let { c ->
        val arrow = when {
            c > 0 -> "▲ "
            c < 0 -> "▼ "
            else -> "= "
        }
        arrow + signed(units.toUnit(c)) { formatNumber(it, 1) }
    } ?: "—"
    val label = dates.epochDay(week.weekStart)
    val description = stringResource(Res.string.progress_bw_week_description, label, avg, week.entries, range, change)
    WeekCells(
        listOf(label, avg, range, change),
        MaterialTheme.typography.bodySmall.tabularFigures(),
        Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = description },
    )
}

private val WEEK_WEIGHTS = floatArrayOf(1.4f, 1f, 1.4f, 1f)

@Composable
private fun WeekCells(texts: List<String>, style: TextStyle, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        texts.forEachIndexed { i, text ->
            Text(
                text,
                style = style,
                modifier = Modifier.weight(WEEK_WEIGHTS[i]),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
            )
        }
    }
}

@Composable
private fun EntriesCard(state: BodyWeightState, dates: DateFormat, onEvent: (BodyWeightIntent) -> Unit) {
    StatCard(stringResource(Res.string.progress_bw_entries)) {
        state.entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EntryRow(entry, state, dates, onEdit = {
                onEvent(BodyWeightIntent.OpenEdit(entry))
            }, onDelete = { onEvent(BodyWeightIntent.Delete(entry.id)) })
        }
    }
}

@Composable
private fun EntryRow(
    entry: BodyWeightEntry,
    state: BodyWeightState,
    dates: DateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatWeight(entry.weightGrams, state.unit), style = MaterialTheme.typography.bodyLarge)
            Text(
                dates.full(entry.measuredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.progress_delete))
        }
    }
}

@Composable
private fun WeightDialogContent(state: BodyWeightState, onEvent: (BodyWeightIntent) -> Unit) {
    val dialog = state.dialog ?: return
    AlertDialog(
        onDismissRequest = { onEvent(BodyWeightIntent.Dismiss) },
        title = {
            Text(
                stringResource(
                    if (dialog is WeightDialog.Edit) Res.string.progress_bw_edit_title else Res.string.progress_bw_add_title
                )
            )
        },
        text = {
            OutlinedTextField(
                value = state.input,
                onValueChange = { onEvent(BodyWeightIntent.InputChanged(it)) },
                label = { Text(stringResource(Res.string.progress_bw_field, unitLabel(state.unit))) },
                isError = state.inputError,
                supportingText = if (state.inputError) {
                    { Text(stringResource(Res.string.progress_bw_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(BodyWeightIntent.Save) }) { Text(stringResource(Res.string.progress_save)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dialog is WeightDialog.Edit) {
                    TextButton(onClick = { onEvent(BodyWeightIntent.Delete(dialog.entry.id)) }) {
                        Text(stringResource(Res.string.progress_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = { onEvent(BodyWeightIntent.Dismiss) }
                ) { Text(stringResource(Res.string.progress_cancel)) }
            }
        },
    )
}
