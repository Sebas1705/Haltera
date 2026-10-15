package es.sebas1705.haltera.feature.routines.ui.editor

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineFocus
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineScore
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_open
import es.sebas1705.haltera.feature.routines.ui.components.ScoreBadge
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.ui.components.PlaceholderScreen
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_active_routine
import es.sebas1705.haltera.feature.routines.resources.routines_add_rest_day
import es.sebas1705.haltera.feature.routines.resources.routines_add_training_day
import es.sebas1705.haltera.feature.routines.resources.routines_cycle_hint
import es.sebas1705.haltera.feature.routines.resources.routines_day_default_name
import es.sebas1705.haltera.feature.routines.resources.routines_delete
import es.sebas1705.haltera.feature.routines.resources.routines_delete_day_message
import es.sebas1705.haltera.feature.routines.resources.routines_delete_day_title
import es.sebas1705.haltera.feature.routines.resources.routines_delete_slot_message
import es.sebas1705.haltera.feature.routines.resources.routines_delete_slot_title
import es.sebas1705.haltera.feature.routines.resources.routines_description
import es.sebas1705.haltera.feature.routines.resources.routines_editor_title
import es.sebas1705.haltera.feature.routines.resources.routines_error
import es.sebas1705.haltera.feature.routines.resources.routines_name
import es.sebas1705.haltera.feature.routines.resources.routines_no_days
import es.sebas1705.haltera.feature.routines.resources.routines_rename_day
import es.sebas1705.haltera.feature.routines.resources.routines_rest_day_default_name
import es.sebas1705.haltera.feature.routines.resources.routines_routine_missing
import es.sebas1705.haltera.feature.routines.resources.routines_saved_automatically
import es.sebas1705.haltera.feature.routines.resources.routines_set_active
import es.sebas1705.haltera.feature.routines.ui.components.BackButton
import es.sebas1705.haltera.feature.routines.ui.components.ConfirmDialog
import es.sebas1705.haltera.feature.routines.ui.components.TextInputDialog
import es.sebas1705.haltera.feature.routines.ui.components.cycleLabel
import es.sebas1705.haltera.feature.routines.ui.components.targetLabel
import es.sebas1705.haltera.feature.routines.ui.components.trainingDays
import es.sebas1705.haltera.feature.routines.ui.list.ActiveBadge
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import es.sebas1705.haltera.feature.routines.resources.routines_share_more
import es.sebas1705.haltera.feature.routines.ui.share.ExportMenuItems
import es.sebas1705.haltera.feature.routines.ui.share.ExportOverlays
import es.sebas1705.haltera.feature.routines.ui.share.RoutineExportViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Routine editor (spec §7): name, description, the cycle of days and their slots. Saves as you edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditorScreen(
    routineId: String,
    onBack: () -> Unit,
    onOpenSlot: (slotId: String) -> Unit,
    onAddSlot: (dayId: String) -> Unit,
    onOpenAnalysis: () -> Unit,
    viewModel: RoutineEditorViewModel = koinViewModel { parametersOf(routineId) },
    export: RoutineExportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<RoutineDay?>(null) }
    val send: (RoutineEditorIntent) -> Unit = { viewModel.onEvent(it) }

    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            send(RoutineEditorIntent.ErrorShown)
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                hasRoutine = state.routine != null,
                isActive = state.routine?.isActive == true,
                onBack = onBack,
                onSetActive = { send(RoutineEditorIntent.SetActive(it)) },
                menu = { close -> ExportMenuItems(routineId, export, close) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            state.missing -> PlaceholderScreen(
                title = stringResource(Res.string.routines_editor_title),
                message = stringResource(Res.string.routines_routine_missing),
                modifier = Modifier.padding(padding),
            )
            else -> EditorContent(
                state = state,
                send = send,
                onRename = { renaming = it },
                onOpenSlot = onOpenSlot,
                onAddSlot = onAddSlot,
                onOpenAnalysis = onOpenAnalysis,
                modifier = Modifier.padding(padding),
            )
        }
    }

    renaming?.let { day ->
        TextInputDialog(
            title = stringResource(Res.string.routines_rename_day),
            label = stringResource(Res.string.routines_name),
            initial = day.name,
            onSave = {
                send(RoutineEditorIntent.RenameDay(day.id, it))
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    DeleteConfirmation(state, send)
    ExportOverlays(export)
}

@Composable
private fun EditorContent(
    state: RoutineEditorState,
    send: (RoutineEditorIntent) -> Unit,
    onRename: (RoutineDay) -> Unit,
    onOpenSlot: (String) -> Unit,
    onAddSlot: (String) -> Unit,
    onOpenAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = state.days
    val listState = rememberLazyListState()
    val flashDayId = highlightedDay(state, listState, send)
    val nextDayName = stringResource(Res.string.routines_day_default_name, days.size + 1)
    val restName = stringResource(Res.string.routines_rest_day_default_name)
    LazyColumn(
        modifier = modifier.fillMaxSize().centeredContent(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(state, send, onOpenAnalysis) }
        if (days.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(Res.string.routines_no_days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(days, key = { it.id }) { day ->
            DayCard(
                day = day,
                dayCount = days.size,
                index = state.index,
                language = state.language,
                send = send,
                onRename = { onRename(day) },
                onOpenSlot = onOpenSlot,
                onAddSlot = { onAddSlot(day.id) },
                highlighted = day.id == flashDayId,
            )
        }
        item(key = "add-day") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { send(RoutineEditorIntent.AddDay(nextDayName, DayKind.TRAINING)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.routines_add_training_day), Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { send(RoutineEditorIntent.AddDay(restName, DayKind.REST)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.routines_add_rest_day), Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(state: RoutineEditorState, send: (RoutineEditorIntent) -> Unit, onOpenAnalysis: () -> Unit) {
    // Local text keeps typing smooth; every change is also sent to be saved.
    var name by rememberSaveable { mutableStateOf(state.name) }
    var description by rememberSaveable { mutableStateOf(state.description) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                send(RoutineEditorIntent.SetName(it))
            },
            label = { Text(stringResource(Res.string.routines_name)) },
            maxLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                send(RoutineEditorIntent.SetDescription(it))
            },
            label = { Text(stringResource(Res.string.routines_description)) },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                cycleLabel(state.days.size, trainingDays(state.days.map { it.kind })),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.routine?.isActive == true) ActiveBadge()
        }
        Text(
            stringResource(Res.string.routines_cycle_hint) + " " + stringResource(Res.string.routines_saved_automatically),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.score?.focus?.let { it != RoutineFocus.EMPTY } == true) AnalysisRow(state.score, onOpenAnalysis)
    }
}

@Composable
private fun DeleteConfirmation(state: RoutineEditorState, send: (RoutineEditorIntent) -> Unit) {
    when (val pending = state.pendingDelete) {
        null -> Unit
        is PendingDelete.Day -> ConfirmDialog(
            title = stringResource(Res.string.routines_delete_day_title),
            message = stringResource(Res.string.routines_delete_day_message, pending.day.name),
            confirmLabel = stringResource(Res.string.routines_delete),
            onConfirm = { send(RoutineEditorIntent.ConfirmDelete) },
            onDismiss = { send(RoutineEditorIntent.DismissDelete) },
        )
        is PendingDelete.Slot -> ConfirmDialog(
            title = stringResource(Res.string.routines_delete_slot_title),
            message = stringResource(
                Res.string.routines_delete_slot_message,
                targetLabel(pending.slot.target, state.index, state.language),
            ),
            confirmLabel = stringResource(Res.string.routines_delete),
            onConfirm = { send(RoutineEditorIntent.ConfirmDelete) },
            onDismiss = { send(RoutineEditorIntent.DismissDelete) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    hasRoutine: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(Res.string.routines_editor_title)) },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (hasRoutine) {
                IconToggleButton(checked = isActive, onCheckedChange = onSetActive) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(
                            if (isActive) Res.string.routines_active_routine else Res.string.routines_set_active,
                        ),
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.routines_share_more))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
                }
            }
        },
    )
}

/**
 * Scrolls to a day just created by duplication once it is in the list and returns its id while it should be
 * highlighted (a moment), else null.
 */
@Composable
private fun highlightedDay(
    state: RoutineEditorState,
    listState: LazyListState,
    send: (RoutineEditorIntent) -> Unit,
): String? {
    var flashDayId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.highlightDayId, state.days) {
        val id = state.highlightDayId ?: return@LaunchedEffect
        if (state.days.none { it.id == id }) return@LaunchedEffect // Wait for the routine to include the copy.
        flashDayId = id
        send(RoutineEditorIntent.HighlightShown)
    }
    LaunchedEffect(flashDayId) {
        val id = flashDayId ?: return@LaunchedEffect
        val position = state.days.indexOfFirst { it.id == id }
        // Item 0 is the header.
        if (position >= 0) listState.animateScrollToItem(position + 1)
        delay(HIGHLIGHT_MS)
        flashDayId = null
    }
    return flashDayId
}

/** "Score 87 · See analysis" in the header. */
@Composable
private fun AnalysisRow(score: RoutineScore?, onOpenAnalysis: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreBadge(score)
        TextButton(onClick = onOpenAnalysis) { Text(stringResource(Res.string.routines_analysis_open)) }
    }
}

private const val HIGHLIGHT_MS = 1_800L
