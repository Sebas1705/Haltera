package es.sebas1705.haltera.feature.progress.ui.session

import es.sebas1705.haltera.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.ErrorScreen
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.feature.progress.model.SessionSummary
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_back
import es.sebas1705.haltera.feature.progress.resources.progress_cancel
import es.sebas1705.haltera.feature.progress.resources.progress_change_failed
import es.sebas1705.haltera.feature.progress.resources.progress_delete
import es.sebas1705.haltera.feature.progress.resources.progress_delete_session
import es.sebas1705.haltera.feature.progress.resources.progress_delete_session_text
import es.sebas1705.haltera.feature.progress.resources.progress_delete_session_title
import es.sebas1705.haltera.feature.progress.resources.progress_edit_sets
import es.sebas1705.haltera.feature.progress.resources.progress_edit_times
import es.sebas1705.haltera.feature.progress.resources.progress_exercise_count
import es.sebas1705.haltera.feature.progress.resources.progress_free_session
import es.sebas1705.haltera.feature.progress.resources.progress_more
import es.sebas1705.haltera.feature.progress.resources.progress_no_sets
import es.sebas1705.haltera.feature.progress.resources.progress_notes
import es.sebas1705.haltera.feature.progress.resources.progress_reps
import es.sebas1705.haltera.feature.progress.resources.progress_session_detail_title
import es.sebas1705.haltera.feature.progress.resources.progress_session_not_found
import es.sebas1705.haltera.feature.progress.resources.progress_session_volume
import es.sebas1705.haltera.feature.progress.resources.progress_set_band_level
import es.sebas1705.haltera.feature.progress.resources.progress_set_kind_drop
import es.sebas1705.haltera.feature.progress.resources.progress_set_kind_failure
import es.sebas1705.haltera.feature.progress.resources.progress_set_kind_warmup
import es.sebas1705.haltera.feature.progress.resources.progress_set_meters
import es.sebas1705.haltera.feature.progress.resources.progress_set_not_completed
import es.sebas1705.haltera.feature.progress.resources.progress_set_number
import es.sebas1705.haltera.feature.progress.resources.progress_set_rpe
import es.sebas1705.haltera.feature.progress.resources.progress_set_seconds
import es.sebas1705.haltera.feature.progress.resources.progress_set_times
import es.sebas1705.haltera.feature.progress.ui.components.EditTimesDialog
import es.sebas1705.haltera.feature.progress.ui.components.formatDate
import es.sebas1705.haltera.feature.progress.ui.components.formatDuration
import es.sebas1705.haltera.feature.progress.ui.components.formatVolume
import es.sebas1705.haltera.feature.progress.ui.components.formatWeight
import es.sebas1705.haltera.feature.progress.ui.components.variantLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Detail of a saved session: its own Scaffold with a back arrow (nested route). Its sets are edited in
 * feature:session ([onEditSets], log mode); its date and time and its deletion are handled here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDetailScreen(viewModel: SessionDetailViewModel, onBack: () -> Unit, onEditSets: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(state.deleted) { if (state.deleted) back() }
    val onIntent: (SessionDetailIntent) -> Unit = { viewModel.onEvent(it) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.progress_session_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.progress_back)
                        )
                    }
                },
                actions = { if (state.summary != null && !state.deleted) DetailActions(onEditSets, onIntent) },
            )
        },
    ) { padding ->
        val summary = state.summary
        when {
            state.loading || state.deleted -> LoadingScreen(Modifier.padding(padding))
            summary == null -> ErrorScreen(
                stringResource(Res.string.progress_session_not_found),
                modifier = Modifier.padding(padding)
            )
            else -> SessionDetailContent(state, summary, Modifier.padding(padding))
        }
    }
    DetailDialogs(state.dialog, onIntent)
}

@Composable
private fun DetailActions(onEditSets: () -> Unit, onIntent: (SessionDetailIntent) -> Unit) {
    IconButton(onClick = onEditSets) {
        Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.progress_edit_sets))
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.progress_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.progress_edit_sets)) },
                onClick = {
                    open = false
                    onEditSets()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.progress_edit_times)) },
                onClick = {
                    open = false
                    onIntent(SessionDetailIntent.EditTimes)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.progress_delete_session)) },
                onClick = {
                    open = false
                    onIntent(SessionDetailIntent.RequestDelete)
                },
            )
        }
    }
}

@Composable
private fun DetailDialogs(dialog: DetailDialog?, onIntent: (SessionDetailIntent) -> Unit) {
    val dismiss = { onIntent(SessionDetailIntent.DismissDialog) }
    when (dialog) {
        is DetailDialog.EditTimes -> EditTimesDialog(
            initial = dialog.initial,
            maxDateUtcMillis = dialog.maxDateUtcMillis,
            onConfirm = { onIntent(SessionDetailIntent.SaveTimes(it)) },
            onDismiss = dismiss,
        )
        DetailDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(Res.string.progress_delete_session_title)) },
            text = { Text(stringResource(Res.string.progress_delete_session_text)) },
            confirmButton = {
                TextButton(onClick = { onIntent(SessionDetailIntent.ConfirmDelete) }) {
                    Text(stringResource(Res.string.progress_delete))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.progress_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun SessionDetailContent(state: SessionDetailState, summary: SessionSummary, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().centeredContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.failed) {
            item {
                Text(
                    stringResource(Res.string.progress_change_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { SummaryHeader(summary, state.unit) }
        if (state.notes.isNotBlank()) {
            item { NotesText(state.notes) }
        }
        items(state.exercises, key = { it.id }) { exercise -> ExerciseCard(exercise, state.unit) }
    }
}

@Composable
private fun SummaryHeader(summary: SessionSummary, unit: WeightUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val title = listOfNotNull(summary.routineName, summary.dayName).joinToString(" · ")
            .ifEmpty { stringResource(Res.string.progress_free_session) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        val details = buildList {
            add(formatDate(summary.startedAt, withYear = true))
            summary.durationMillis?.let { add(formatDuration(it)) }
            add(pluralStringResource(Res.plurals.progress_exercise_count, summary.exerciseCount, summary.exerciseCount))
            add(stringResource(Res.string.progress_session_volume, formatVolume(summary.volumeGrams, unit)))
        }
        Text(
            details.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotesText(notes: String) {
    Column {
        Text(stringResource(Res.string.progress_notes), style = MaterialTheme.typography.labelLarge)
        Text(notes, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseDetail, unit: WeightUnit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(variantLabel(exercise.variant), style = MaterialTheme.typography.titleMedium)
            if (exercise.volumeGrams > 0) {
                Text(
                    stringResource(Res.string.progress_session_volume, formatVolume(exercise.volumeGrams, unit)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exercise.notes.isNotBlank()) Text(exercise.notes, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            if (exercise.sets.isEmpty()) {
                Text(stringResource(Res.string.progress_no_sets), style = MaterialTheme.typography.bodyMedium)
            }
            exercise.sets.forEachIndexed { index, set ->
                SetRow(index + 1, set, exercise.variant?.loadType ?: LoadType.NONE, unit)
            }
        }
    }
}

@Composable
private fun SetRow(number: Int, set: SetLog, loadType: LoadType, unit: WeightUnit) {
    val color = if (set.completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.progress_set_number, number),
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            setKindLabel(set.kind)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(2f)) {
            Text(setValues(set, loadType, unit), style = MaterialTheme.typography.bodyMedium, color = color)
            val extra = listOfNotNull(
                set.rpe?.let { rpe ->
                    stringResource(
                        Res.string.progress_set_rpe,
                        if (rpe % 1.0 == 0.0) rpe.toInt().toString() else rpe.toString()
                    )
                },
                if (set.completed) null else stringResource(Res.string.progress_set_not_completed),
            )
            if (extra.isNotEmpty()) {
                Text(
                    extra.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun setKindLabel(kind: SetKind): String? = when (kind) {
    SetKind.WARMUP -> stringResource(Res.string.progress_set_kind_warmup)
    SetKind.DROP -> stringResource(Res.string.progress_set_kind_drop)
    SetKind.FAILURE -> stringResource(Res.string.progress_set_kind_failure)
    SetKind.WORKING -> null
}

/** "60 kg × 8 reps", "45 s", "2000 m · 600 s"… whatever the set recorded. */
@Composable
private fun setValues(set: SetLog, loadType: LoadType, unit: WeightUnit): String {
    val load = set.loadGrams?.let { grams ->
        when (loadType) {
            LoadType.BAND -> stringResource(Res.string.progress_set_band_level, grams.toString())
            else -> formatWeight(grams, unit)
        }
    }
    val reps = set.reps?.let { pluralStringResource(Res.plurals.progress_reps, it, it) }
    val main = when {
        load != null && reps != null -> stringResource(Res.string.progress_set_times, load, reps)
        else -> load ?: reps
    }
    return listOfNotNull(
        main,
        set.distanceM?.let { stringResource(Res.string.progress_set_meters, it) },
        set.durationS?.let { stringResource(Res.string.progress_set_seconds, it) },
    ).joinToString(" · ").ifEmpty { "—" }
}
