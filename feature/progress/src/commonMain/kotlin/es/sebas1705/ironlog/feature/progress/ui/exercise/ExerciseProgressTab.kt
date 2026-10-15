package es.sebas1705.ironlog.feature.progress.ui.exercise

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.components.PrBadge
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.domain.usecases.stats.PersonalRecords
import es.sebas1705.ironlog.domain.usecases.stats.Record
import es.sebas1705.ironlog.feature.progress.model.VariantInfo
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_one_rm
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_top_set
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_volume
import es.sebas1705.ironlog.feature.progress.resources.progress_exercise_empty
import es.sebas1705.ironlog.feature.progress.resources.progress_no_records
import es.sebas1705.ironlog.feature.progress.resources.progress_pick_exercise
import es.sebas1705.ironlog.feature.progress.resources.progress_record_heaviest
import es.sebas1705.ironlog.feature.progress.resources.progress_record_one_rm
import es.sebas1705.ironlog.feature.progress.resources.progress_record_reps
import es.sebas1705.ironlog.feature.progress.resources.progress_record_volume
import es.sebas1705.ironlog.feature.progress.resources.progress_records_title
import es.sebas1705.ironlog.feature.progress.resources.progress_reps
import es.sebas1705.ironlog.feature.progress.ui.components.ChartSeries
import es.sebas1705.ironlog.feature.progress.ui.components.LineChartCard
import es.sebas1705.ironlog.feature.progress.ui.components.currentLanguage
import es.sebas1705.ironlog.feature.progress.ui.components.equipmentLabel
import es.sebas1705.ironlog.feature.progress.ui.components.formatDate
import es.sebas1705.ironlog.feature.progress.ui.components.formatVolume
import es.sebas1705.ironlog.feature.progress.ui.components.formatWeight
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToLong

/** Exercises tab: pick an exercise with history, see its charts and personal records. */
@Composable
internal fun ExerciseProgressTab(viewModel: ExerciseProgressViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> LoadingScreen()
        state.exercises.isEmpty() -> EmptyMessage(stringResource(Res.string.progress_exercise_empty))
        else -> ExerciseProgressContent(state) { viewModel.onEvent(it) }
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ExerciseProgressContent(state: ExerciseProgressState, onEvent: (ExerciseProgressIntent) -> Unit) {
    val unit = state.unit
    val primary = MaterialTheme.colorScheme.primary
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ExercisePicker(
                state.exercises,
                state.selectedExercise
            ) { onEvent(ExerciseProgressIntent.SelectExercise(it)) }
        }
        if (state.variants.size > 1) {
            item {
                VariantChips(
                    state.variants,
                    state.selectedVariantId
                ) { onEvent(ExerciseProgressIntent.SelectVariant(it)) }
            }
        }
        item {
            LineChartCard(
                title = stringResource(Res.string.progress_chart_top_set),
                series = listOf(ChartSeries(state.charts.topSet, primary)),
                formatValue = { formatWeight(it.roundToLong(), unit) },
            )
        }
        item {
            LineChartCard(
                title = stringResource(Res.string.progress_chart_one_rm),
                series = listOf(ChartSeries(state.charts.oneRm, primary)),
                formatValue = { formatWeight(it.roundToLong(), unit, decimals = 1) },
            )
        }
        item {
            LineChartCard(
                title = stringResource(Res.string.progress_chart_volume),
                series = listOf(ChartSeries(state.charts.volume, primary)),
                formatValue = { formatVolume(it.roundToLong(), unit) },
            )
        }
        item { RecordsCard(state.records, unit) }
    }
}

@Composable
private fun ExercisePicker(options: List<VariantInfo>, selected: VariantInfo?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val language = currentLanguage()
    Column {
        Text(stringResource(Res.string.progress_pick_exercise), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.exerciseName?.get(language).orEmpty(), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.exerciseName.get(language)) },
                        onClick = {
                            expanded = false
                            onSelect(option.exerciseId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantChips(variants: List<VariantInfo>, selectedId: String?, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        variants.forEach { variant ->
            FilterChip(
                selected = variant.variantId == selectedId,
                onClick = { onSelect(variant.variantId) },
                label = {
                    Text(
                        variant.variantName?.get(currentLanguage()) ?: equipmentLabel(variant.equipment)
                    )
                },
            )
        }
    }
}

@Composable
private fun RecordsCard(records: PersonalRecords?, unit: WeightUnit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.progress_records_title), style = MaterialTheme.typography.titleMedium)
                if (records != null) PrBadge()
            }
            val rows = listOfNotNull(
                records?.heaviestLoad?.let {
                    stringResource(Res.string.progress_record_heaviest) to it to formatWeight(it.value, unit)
                },
                records?.bestEstimatedOneRm?.let {
                    stringResource(Res.string.progress_record_one_rm) to it to formatWeight(it.value, unit, decimals = 1)
                },
                records?.mostReps?.let {
                    stringResource(Res.string.progress_record_reps) to it to
                        pluralStringResource(Res.plurals.progress_reps, it.value.toInt(), it.value.toInt())
                },
                records?.bestSessionVolume?.let {
                    stringResource(Res.string.progress_record_volume) to it to formatVolume(it.value, unit)
                },
            )
            if (rows.isEmpty()) {
                Text(stringResource(Res.string.progress_no_records), style = MaterialTheme.typography.bodyMedium)
            }
            rows.forEach { (labelAndRecord, value) -> RecordRow(labelAndRecord.first, labelAndRecord.second, value) }
        }
    }
}

@Composable
private fun RecordRow(label: String, record: Record, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatDate(record.at, withYear = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.titleMedium.tabularFigures(), color = IronlogTheme.colors.prGold)
    }
}
