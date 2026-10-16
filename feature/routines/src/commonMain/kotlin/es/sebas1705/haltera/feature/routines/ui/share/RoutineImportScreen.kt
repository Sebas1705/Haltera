package es.sebas1705.haltera.feature.routines.ui.share

import es.sebas1705.haltera.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.ui.components.PlaceholderScreen
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.share.ImportPreview
import es.sebas1705.haltera.data.repository.share.SharedDay
import es.sebas1705.haltera.data.repository.share.SharedRoutine
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_error
import es.sebas1705.haltera.feature.routines.resources.routines_import_add
import es.sebas1705.haltera.feature.routines.resources.routines_import_copy_note
import es.sebas1705.haltera.feature.routines.resources.routines_import_custom_count
import es.sebas1705.haltera.feature.routines.resources.routines_import_custom_by
import es.sebas1705.haltera.feature.routines.resources.routines_import_custom_existing
import es.sebas1705.haltera.feature.routines.resources.routines_import_invalid
import es.sebas1705.haltera.feature.routines.resources.routines_import_missing_drop
import es.sebas1705.haltera.feature.routines.resources.routines_import_missing_keep
import es.sebas1705.haltera.feature.routines.resources.routines_import_missing_title
import es.sebas1705.haltera.feature.routines.resources.routines_import_rest_day
import es.sebas1705.haltera.feature.routines.resources.routines_import_scan_unsupported
import es.sebas1705.haltera.feature.routines.resources.routines_import_set_active
import es.sebas1705.haltera.feature.routines.resources.routines_import_title
import es.sebas1705.haltera.feature.routines.resources.routines_import_unsupported
import es.sebas1705.haltera.feature.routines.ui.components.BackButton
import es.sebas1705.haltera.feature.routines.ui.components.routineSummary
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Preview of a shared routine (name, days, exercises, warnings) and "Add to my routines". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineImportScreen(
    source: ImportSource,
    onBack: () -> Unit,
    onImported: (routineId: String) -> Unit,
    viewModel: RoutineImportViewModel = koinViewModel { parametersOf(source) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val send: (RoutineImportIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(state.cancelled) { if (state.cancelled) onBack() }
    LaunchedEffect(state.importedId) {
        state.importedId?.let { id ->
            send(RoutineImportIntent.OpenHandled)
            onImported(id)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            send(RoutineImportIntent.ErrorShown)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.routines_import_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val preview = state.preview
        val modifier = Modifier.padding(padding)
        when {
            state.loading || state.cancelled -> LoadingScreen(modifier)
            state.problem != null -> PlaceholderScreen(
                title = stringResource(Res.string.routines_import_title),
                message = stringResource(
                    when (state.problem) {
                        ImportProblem.UNSUPPORTED_VERSION -> Res.string.routines_import_unsupported
                        ImportProblem.SCAN_UNSUPPORTED -> Res.string.routines_import_scan_unsupported
                        else -> Res.string.routines_import_invalid
                    },
                ),
                modifier = modifier,
            )
            preview != null -> PreviewContent(state, preview, send, modifier)
        }
    }
}

@Composable
private fun PreviewContent(
    state: RoutineImportState,
    preview: ImportPreview,
    send: (RoutineImportIntent) -> Unit,
    modifier: Modifier,
) {
    val routine = preview.file.routine
    LazyColumn(
        modifier = modifier.fillMaxSize().centeredContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(routine.name, style = MaterialTheme.typography.headlineSmall)
            if (routine.description.isNotBlank()) {
                Text(
                    routine.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SharedSummary(routine)
        }
        items(routine.days) { day -> DayPreview(day, preview, state.language) }
        if (preview.file.customExercises.isNotEmpty()) {
            item { CustomExercisesNote(preview) }
        }
        if (preview.missingIds.isNotEmpty()) item { MissingCard(preview.missingIds, state.dropMissing, send) }
        item {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = state.setActive,
                    role = Role.Checkbox,
                    onValueChange = { send(RoutineImportIntent.SetActive(it)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.setActive, onCheckedChange = null)
                Text(stringResource(Res.string.routines_import_set_active), Modifier.padding(start = 8.dp))
            }
        }
        item {
            Text(
                stringResource(Res.string.routines_import_copy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(
                onClick = { send(RoutineImportIntent.Confirm) },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.routines_import_add)) }
        }
    }
}

@Composable
private fun DayPreview(day: SharedDay, preview: ImportPreview, language: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(day.name, style = MaterialTheme.typography.titleMedium)
            if (DayKind.fromDb(day.kind) == DayKind.REST) {
                Text(
                    stringResource(Res.string.routines_import_rest_day),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.slots.forEach { slot ->
                val id = when (val t = slot.target) {
                    is SlotTarget.Fixed -> t.variantId
                    is SlotTarget.Flexible -> t.exerciseId
                }
                val missing = id in preview.missingIds
                Text(
                    "${slot.sets} × " + (preview.names[id]?.get(language) ?: id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MissingCard(missing: List<String>, drop: Boolean, send: (RoutineImportIntent) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(Res.string.routines_import_missing_title), style = MaterialTheme.typography.titleSmall)
            Text(missing.joinToString(), style = MaterialTheme.typography.bodySmall)
            listOf(false to Res.string.routines_import_missing_keep, true to Res.string.routines_import_missing_drop)
                .forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = drop == value,
                            role = Role.RadioButton,
                            onClick = { send(RoutineImportIntent.SetDropMissing(value)) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = drop == value, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp))
                    }
                }
        }
    }
}

/** Fixed: "4-day cycle · 3 training days"; flexible: "3 training days · 2 rest days a week". */
@Composable
private fun SharedSummary(routine: SharedRoutine) {
    val policy = routine.rest?.toPolicy() ?: RestPolicy.FIXED
    val kinds = routine.days.map { DayKind.fromDb(it.kind) }
    Text(
        routineSummary(if (policy.isFlexible) kinds.filter { it == DayKind.TRAINING } else kinds, policy),
        style = MaterialTheme.typography.titleSmall,
    )
}

/** How many custom exercises come with the routine, by whom, and how many are already here (reused). */
@Composable
private fun CustomExercisesNote(preview: ImportPreview) {
    val custom = preview.file.customExercises.size
    val existing = preview.existingCustomIds.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            pluralStringResource(Res.plurals.routines_import_custom_count, custom, custom),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (preview.authors.isNotEmpty()) {
            Text(
                stringResource(Res.string.routines_import_custom_by, preview.authors.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (existing > 0) {
            Text(
                pluralStringResource(Res.plurals.routines_import_custom_existing, existing, existing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
