package es.sebas1705.haltera.dataimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.ImportError
import es.sebas1705.haltera.data.repository.importer.ImportPreview
import es.sebas1705.haltera.data.repository.importer.ImportSource
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.UnmatchedExercise
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.import_suggestion_medium
import es.sebas1705.haltera.resources.import_suggestion_high
import es.sebas1705.haltera.resources.import_suggestion_medium_selected
import es.sebas1705.haltera.resources.import_suggestion_high_unselected
import es.sebas1705.haltera.resources.import_suggested
import es.sebas1705.haltera.data.repository.importer.SuggestionConfidence
import es.sebas1705.haltera.core.units.appLanguage
import es.sebas1705.haltera.resources.import_action
import es.sebas1705.haltera.resources.import_body_weights
import es.sebas1705.haltera.resources.import_cancel
import es.sebas1705.haltera.resources.import_confirm
import es.sebas1705.haltera.resources.import_create_custom
import es.sebas1705.haltera.resources.import_detected
import es.sebas1705.haltera.resources.import_dismiss
import es.sebas1705.haltera.resources.import_done
import es.sebas1705.haltera.resources.import_error_compressed_zip
import es.sebas1705.haltera.resources.import_error_corrupt
import es.sebas1705.haltera.resources.import_error_newer_format
import es.sebas1705.haltera.resources.import_error_no_data
import es.sebas1705.haltera.resources.import_error_not_an_export
import es.sebas1705.haltera.resources.import_error_read
import es.sebas1705.haltera.resources.import_error_unrecognised
import es.sebas1705.haltera.resources.import_existing_replace
import es.sebas1705.haltera.resources.import_existing_skip
import es.sebas1705.haltera.resources.import_existing_title
import es.sebas1705.haltera.resources.import_map
import es.sebas1705.haltera.resources.import_map_title
import es.sebas1705.haltera.resources.import_mapped_to
import es.sebas1705.haltera.resources.import_matched
import es.sebas1705.haltera.resources.import_no_results
import es.sebas1705.haltera.resources.import_nothing_new
import es.sebas1705.haltera.resources.import_range
import es.sebas1705.haltera.resources.import_search
import es.sebas1705.haltera.resources.import_section
import es.sebas1705.haltera.resources.import_skipped_rows
import es.sebas1705.haltera.resources.import_source_fitnotes
import es.sebas1705.haltera.resources.import_source_haltera
import es.sebas1705.haltera.resources.import_source_hevy
import es.sebas1705.haltera.resources.import_source_strong
import es.sebas1705.haltera.resources.import_summary
import es.sebas1705.haltera.resources.import_text
import es.sebas1705.haltera.resources.import_title
import es.sebas1705.haltera.resources.import_unit_title
import es.sebas1705.haltera.resources.import_unmatched_row
import es.sebas1705.haltera.resources.import_unmatched_text
import es.sebas1705.haltera.resources.import_unmatched_title
import es.sebas1705.haltera.resources.import_working
import es.sebas1705.haltera.resources.unit_kg
import es.sebas1705.haltera.resources.unit_lb
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Settings → Data → Import: bring in a history from Hevy, Strong, FitNotes or a Haltera export. */
@Composable
fun DataImportSection(viewModel: DataImportViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.import_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(Res.string.import_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { viewModel.onEvent(DataImportIntent.Pick) },
                enabled = !state.busy && state.review == null,
            ) { Text(stringResource(Res.string.import_action)) }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        when (val outcome = state.outcome) {
            is ImportOutcome.Done -> Outcome(
                stringResource(
                    Res.string.import_done,
                    outcome.summary.sessionsWritten,
                    outcome.summary.sessionsReplaced,
                    outcome.summary.sessionsSkipped,
                    outcome.summary.sets,
                    outcome.summary.bodyWeights,
                    outcome.summary.exercisesCreated,
                ),
                MaterialTheme.colorScheme.primary,
            ) { viewModel.onEvent(DataImportIntent.DismissOutcome) }
            is ImportOutcome.Failed -> Outcome(
                stringResource(errorText(outcome.error)),
                MaterialTheme.colorScheme.error,
            ) { viewModel.onEvent(DataImportIntent.DismissOutcome) }
            null -> Unit
        }
    }
    state.review?.let { review ->
        ReviewDialog(review, viewModel::onEvent)
    }
    state.search?.let { search ->
        MappingDialog(search, viewModel::onEvent)
    }
}

private fun errorText(error: ImportError?): StringResource = when (error) {
    ImportError.UNRECOGNISED -> Res.string.import_error_unrecognised
    ImportError.NO_DATA -> Res.string.import_error_no_data
    ImportError.COMPRESSED_ZIP -> Res.string.import_error_compressed_zip
    ImportError.NOT_AN_EXPORT -> Res.string.import_error_not_an_export
    ImportError.NEWER_FORMAT -> Res.string.import_error_newer_format
    ImportError.CORRUPT -> Res.string.import_error_corrupt
    null -> Res.string.import_error_read
}

private fun sourceName(source: ImportSource): StringResource = when (source) {
    ImportSource.HEVY -> Res.string.import_source_hevy
    ImportSource.STRONG -> Res.string.import_source_strong
    ImportSource.FITNOTES -> Res.string.import_source_fitnotes
    ImportSource.HALTERA -> Res.string.import_source_haltera
}

@OptIn(ExperimentalTime::class)
private fun dateOf(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

@Composable
private fun Outcome(text: String, color: Color, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Line(text, color)
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.import_dismiss)) }
    }
}

@Composable
private fun RowScope.Line(text: String, color: Color) {
    Text(text, color = color, modifier = Modifier.weight(1f))
}

@Composable
private fun ReviewDialog(review: ImportReview, onEvent: (DataImportIntent) -> Unit) {
    val preview = review.preview
    val nothingNew = review.nothingNew
    AlertDialog(
        onDismissRequest = { if (!review.working) onEvent(DataImportIntent.Cancel) },
        title = { Text(stringResource(Res.string.import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(review.fileName, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(Res.string.import_detected, stringResource(sourceName(preview.source))))
                Summary(preview, review.matchedLine)
                if (preview.needsUnitChoice) UnitChoice(review, onEvent)
                if (preview.existingSessions > 0) ExistingChoice(review, onEvent)
                if (nothingNew) {
                    Text(
                        stringResource(Res.string.import_nothing_new),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (review.unmatchedShown) Unmatched(review, onEvent)
                if (review.working) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.import_working))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(DataImportIntent.Confirm) },
                enabled = !review.working && !nothingNew,
            ) { Text(stringResource(Res.string.import_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(DataImportIntent.Cancel) }, enabled = !review.working) {
                Text(stringResource(Res.string.import_cancel))
            }
        },
    )
}

@Composable
private fun Summary(preview: ImportPreview, matched: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.import_summary, preview.sessions, preview.sets))
        val first = preview.firstStartedAt
        val last = preview.lastStartedAt
        if (first != null && last != null) Text(stringResource(Res.string.import_range, dateOf(first), dateOf(last)))
        if (preview.bodyWeights > 0) Text(stringResource(Res.string.import_body_weights, preview.bodyWeights))
        if (matched != null) Text(pluralStringResource(Res.plurals.import_matched, matched, matched))
        if (preview.skippedRows > 0) {
            Text(
                stringResource(Res.string.import_skipped_rows, preview.skippedRows),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitChoice(review: ImportReview, onEvent: (DataImportIntent) -> Unit) {
    Text(stringResource(Res.string.import_unit_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportWeightUnit.entries.forEach { unit ->
            FilterChip(
                selected = review.unit == unit,
                onClick = { onEvent(DataImportIntent.SetUnit(unit)) },
                label = {
                    Text(stringResource(if (unit == ImportWeightUnit.KG) Res.string.unit_kg else Res.string.unit_lb))
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExistingChoice(review: ImportReview, onEvent: (DataImportIntent) -> Unit) {
    Text(
        stringResource(Res.string.import_existing_title, review.preview.existingSessions),
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = review.existing == ExistingPolicy.SKIP,
            onClick = { onEvent(DataImportIntent.SetExisting(ExistingPolicy.SKIP)) },
            label = { Text(stringResource(Res.string.import_existing_skip)) },
        )
        FilterChip(
            selected = review.existing == ExistingPolicy.REPLACE,
            onClick = { onEvent(DataImportIntent.SetExisting(ExistingPolicy.REPLACE)) },
            label = { Text(stringResource(Res.string.import_existing_replace)) },
        )
    }
}

@Composable
private fun Unmatched(review: ImportReview, onEvent: (DataImportIntent) -> Unit) {
    HorizontalDivider()
    Text(
        stringResource(Res.string.import_unmatched_title, review.preview.unmatched.size),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(Res.string.import_unmatched_text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    review.preview.unmatched.forEach { item -> UnmatchedRow(item, review.mapped[item.key], onEvent) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnmatchedRow(item: UnmatchedExercise, mapped: MappedExercise?, onEvent: (DataImportIntent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(item.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(Res.string.import_unmatched_row, item.sets, item.sessions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val suggestion = item.suggestion
        val suggestedChosen = suggestion != null && mapped?.exerciseId == suggestion.exerciseId
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mapped == null,
                onClick = { onEvent(DataImportIntent.CreateCustom(item.key)) },
                label = { Text(stringResource(Res.string.import_create_custom)) },
            )
            if (suggestion != null) {
                FilterChip(
                    selected = suggestedChosen,
                    onClick = { onEvent(DataImportIntent.AcceptSuggestion(item.key)) },
                    label = { Text(stringResource(Res.string.import_suggested, suggestion.name.get(appLanguage()))) },
                )
            }
            FilterChip(
                selected = mapped != null && !suggestedChosen,
                onClick = { onEvent(DataImportIntent.OpenMapping(item.key)) },
                label = {
                    Text(
                        if (mapped != null && !suggestedChosen) {
                            stringResource(Res.string.import_mapped_to, mapped.name)
                        } else {
                            stringResource(Res.string.import_map)
                        },
                    )
                },
            )
        }
        if (suggestion != null) {
            Text(
                stringResource(suggestionWording(suggestion.confidence, suggestedChosen)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The confidence wording follows what is selected now, so it never says "selected" after the user changed it. */
internal fun suggestionWording(confidence: SuggestionConfidence, chosen: Boolean): StringResource = when {
    confidence == SuggestionConfidence.HIGH && chosen -> Res.string.import_suggestion_high
    confidence == SuggestionConfidence.HIGH -> Res.string.import_suggestion_high_unselected
    chosen -> Res.string.import_suggestion_medium_selected
    else -> Res.string.import_suggestion_medium
}

@Composable
private fun MappingDialog(search: MappingSearch, onEvent: (DataImportIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(DataImportIntent.CloseMapping) },
        title = { Text(stringResource(Res.string.import_map_title, search.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = { onEvent(DataImportIntent.Search(it)) },
                    label = { Text(stringResource(Res.string.import_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.results.isEmpty()) {
                    Text(
                        stringResource(Res.string.import_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(search.results, key = { it.exerciseId }) { exercise ->
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEvent(DataImportIntent.MapTo(exercise)) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(DataImportIntent.CloseMapping) }) {
                Text(stringResource(Res.string.import_cancel))
            }
        },
    )
}
