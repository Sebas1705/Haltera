package es.sebas1705.ironlog.feature.progress.ui.bodyweight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_add_title
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_average
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_change
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_change_none
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_chart
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_edit_title
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_empty
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_entries
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_field
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_invalid
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_latest
import es.sebas1705.ironlog.feature.progress.resources.progress_bw_log
import es.sebas1705.ironlog.feature.progress.resources.progress_cancel
import es.sebas1705.ironlog.feature.progress.resources.progress_delete
import es.sebas1705.ironlog.feature.progress.resources.progress_save
import es.sebas1705.ironlog.feature.progress.ui.components.ChartSeries
import es.sebas1705.ironlog.feature.progress.ui.components.LineChartCard
import es.sebas1705.ironlog.feature.progress.ui.components.formatDate
import es.sebas1705.ironlog.feature.progress.ui.components.formatWeight
import es.sebas1705.ironlog.feature.progress.ui.components.unitLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToLong

/** Body weight tab: summary with trend, chart with moving average and the editable entry list. */
@Composable
internal fun BodyWeightTab(viewModel: BodyWeightViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (BodyWeightIntent) -> Unit = { viewModel.onEvent(it) }
    if (state.loading) {
        LoadingScreen()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(state, onAdd = { onEvent(BodyWeightIntent.OpenAdd) }) }
        if (state.entries.isNotEmpty()) {
            item { WeightChart(state) }
            item { Text(stringResource(Res.string.progress_bw_entries), style = MaterialTheme.typography.titleMedium) }
            items(state.entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    unit = state.unit,
                    onEdit = { onEvent(BodyWeightIntent.OpenEdit(entry)) },
                    onDelete = { onEvent(BodyWeightIntent.Delete(entry.id)) },
                )
            }
        }
    }
    if (state.dialog != null) {
        WeightDialogContent(state, onEvent)
    }
}

@Composable
private fun SummaryCard(state: BodyWeightState, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val latest = state.entries.firstOrNull()
            if (latest == null) {
                Text(stringResource(Res.string.progress_bw_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    stringResource(Res.string.progress_bw_latest, formatWeight(latest.weightGrams, state.unit)),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(trendText(state.change30Days, state.unit), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.progress_bw_log), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun trendText(change: Long?, unit: WeightUnit): String {
    if (change == null) return stringResource(Res.string.progress_bw_change_none)
    val sign = when {
        change > 0 -> "+"
        else -> ""
    }
    return stringResource(Res.string.progress_bw_change, sign + formatWeight(change, unit, decimals = 1))
}

@Composable
private fun WeightChart(state: BodyWeightState) {
    val colors = MaterialTheme.colorScheme
    LineChartCard(
        title = stringResource(Res.string.progress_bw_chart),
        series = listOf(
            ChartSeries(
                points = state.chart.raw,
                color = colors.outline,
                label = stringResource(Res.string.progress_bw_chart),
                strokeWidth = 1.5.dp,
            ),
            ChartSeries(
                points = state.chart.average,
                color = colors.primary,
                label = stringResource(Res.string.progress_bw_average),
                showDots = false,
                strokeWidth = 3.dp,
            ),
        ),
        formatValue = { formatWeight(it.roundToLong(), state.unit, decimals = 1) },
    )
}

@Composable
private fun EntryRow(entry: BodyWeightEntry, unit: WeightUnit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(formatWeight(entry.weightGrams, unit)) },
        supportingContent = { Text(formatDate(entry.measuredAt, withYear = true)) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.progress_delete))
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun WeightDialogContent(state: BodyWeightState, onEvent: (BodyWeightIntent) -> Unit) {
    val dialog = state.dialog ?: return
    AlertDialog(
        onDismissRequest = { onEvent(BodyWeightIntent.Dismiss) },
        title = {
            Text(
                stringResource(
                    if (dialog is WeightDialog.Edit) Res.string.progress_bw_edit_title else Res.string.progress_bw_add_title,
                ),
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
