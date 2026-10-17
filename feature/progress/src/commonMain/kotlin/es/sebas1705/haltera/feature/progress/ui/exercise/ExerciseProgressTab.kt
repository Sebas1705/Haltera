package es.sebas1705.haltera.feature.progress.ui.exercise

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.domain.usecases.stats.Metric
import es.sebas1705.haltera.feature.progress.model.VariantInfo
import es.sebas1705.haltera.feature.progress.resources.*
import es.sebas1705.haltera.feature.progress.resources.progress_exercise_details
import es.sebas1705.haltera.feature.progress.ui.components.ComparisonTile
import es.sebas1705.haltera.feature.progress.ui.components.DateFormat
import es.sebas1705.haltera.feature.progress.ui.components.StatsGrid
import es.sebas1705.haltera.feature.progress.ui.components.TileGrid
import es.sebas1705.haltera.feature.progress.ui.components.UnitFormat
import es.sebas1705.haltera.feature.progress.ui.components.currentLanguage
import es.sebas1705.haltera.feature.progress.ui.components.equipmentLabel
import es.sebas1705.haltera.feature.progress.ui.components.formatNumber
import es.sebas1705.haltera.feature.progress.ui.components.fullWidth
import es.sebas1705.haltera.feature.progress.ui.components.rememberDateFormat
import es.sebas1705.haltera.feature.progress.ui.components.rememberUnitFormat
import es.sebas1705.haltera.feature.progress.ui.components.signed
import es.sebas1705.haltera.feature.progress.ui.components.variantLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Exercises tab: pick an exercise with history, see its charts, progression table and records. */
@Composable
internal fun ExerciseProgressTab(
    onOpenExerciseDetail: (exerciseId: String) -> Unit = {},
    viewModel: ExerciseProgressViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> LoadingScreen()
        state.exercises.isEmpty() -> EmptyMessage(stringResource(Res.string.progress_exercise_empty))
        else -> ExerciseProgressContent(state, onOpenExerciseDetail) { viewModel.onEvent(it) }
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ExerciseProgressContent(
    state: ExerciseProgressState,
    onOpenExerciseDetail: (exerciseId: String) -> Unit,
    onEvent: (ExerciseProgressIntent) -> Unit,
) {
    val units = rememberUnitFormat(state.unit)
    val dates = rememberDateFormat()
    StatsGrid {
        fullWidth("picker") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Opened for one exercise: no picker, and the equipment is always shown.
                if (!state.focused) {
                    ExercisePicker(
                        state.exercises,
                        state.selectedExercise
                    ) { onEvent(ExerciseProgressIntent.SelectExercise(it)) }
                }
                if (state.variants.size > 1 || (state.focused && state.variants.isNotEmpty())) {
                    VariantChips(
                        state.variants,
                        state.selectedVariantId
                    ) { onEvent(ExerciseProgressIntent.SelectVariant(it)) }
                }
                // The exercise's own page: description, records and, for a deleted custom exercise, Restore.
                state.selectedExerciseId?.let { id ->
                    TextButton(onClick = { onOpenExerciseDetail(id) }) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.progress_exercise_details))
                    }
                }
            }
        }
        fullWidth("tiles") { ExerciseTiles(state, units, dates) }
        item("one-rm") { OneRmCard(state, units, dates, onCompare = { onEvent(ExerciseProgressIntent.Compare(it)) }) }
        item("top-set") { TopSetCard(state, units, dates) }
        item("volume") { SessionVolumeCard(state, units, dates) }
        item("reps") { RepRangesCard(state) }
        fullWidth("table") { ProgressionTableCard(state.rows, units, dates) }
        item("records") { RecordsCard(state.records, units, dates) }
    }
}

@Composable
private fun ExerciseTiles(state: ExerciseProgressState, units: UnitFormat, dates: DateFormat) {
    val trend = state.trend
    val best = state.records?.bestOneRm
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { m ->
            ComparisonTile(
                stringResource(Res.string.progress_tile_best_e1rm),
                best?.let { units.weight(it.value.toDouble()) } ?: "—",
                null,
                units::volume,
                Metric.ESTIMATED_ONE_RM,
                m,
                footnote = best?.let { dates.full(it.at) },
                noComparison = stringResource(Res.string.progress_change_no_sets),
            )
        },
        { m ->
            ComparisonTile(
                stringResource(Res.string.progress_tile_trend),
                trend?.let {
                    stringResource(Res.string.progress_trend_value, signed(it.gramsPerWeek) { g -> units.weight(g) })
                } ?: "—",
                null,
                units::volume,
                Metric.ONE_RM_TREND,
                m,
                footnote = trend?.let {
                    val n = it.trend.n
                    pluralStringResource(Res.plurals.progress_trend_detail, n, formatNumber(it.trend.r2, 2), n)
                } ?: stringResource(Res.string.progress_trend_none),
            )
        },
        { m ->
            ComparisonTile(
                stringResource(Res.string.progress_tile_exercise_sessions),
                state.rows.size.toString(),
                null,
                units::volume,
                Metric.PROGRESSION_TABLE,
                m,
                footnote = state.rows.sumOf { it.effectiveSets }.let {
                    pluralStringResource(Res.plurals.progress_tile_exercise_sets, it, it)
                },
            )
        },
    )
    TileGrid(tiles)
}

@Composable
private fun ExercisePicker(options: List<VariantInfo>, selected: VariantInfo?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val language = currentLanguage()
    Column {
        Text(stringResource(Res.string.progress_pick_exercise), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selected?.exerciseName?.get(language).orEmpty(),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        if (variant.variantName != null) variantLabel(variant) else equipmentLabel(variant.equipment)
                    )
                },
            )
        }
    }
}
