package es.sebas1705.ironlog.feature.routines.ui.slot

import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.feature.routines.model.SlotLimits
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_delete
import es.sebas1705.ironlog.feature.routines.resources.routines_delete_slot_message
import es.sebas1705.ironlog.feature.routines.resources.routines_delete_slot_action
import es.sebas1705.ironlog.feature.routines.resources.routines_delete_slot_title
import es.sebas1705.ironlog.feature.routines.resources.routines_error
import es.sebas1705.ironlog.feature.routines.resources.routines_notes
import es.sebas1705.ironlog.feature.routines.resources.routines_saved_automatically
import es.sebas1705.ironlog.feature.routines.resources.routines_section_sets
import es.sebas1705.ironlog.feature.routines.resources.routines_sets
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_editor_title
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_missing
import es.sebas1705.ironlog.feature.routines.resources.routines_warmup_sets
import es.sebas1705.ironlog.feature.routines.ui.components.BackButton
import es.sebas1705.ironlog.feature.routines.ui.components.ConfirmDialog
import es.sebas1705.ironlog.feature.routines.ui.components.SectionTitle
import es.sebas1705.ironlog.feature.routines.ui.components.Stepper
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Edits one slot of a routine day. Every change is saved straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotEditorScreen(
    routineId: String,
    slotId: String,
    onBack: () -> Unit,
    viewModel: SlotEditorViewModel = koinViewModel { parametersOf(routineId, slotId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val send: (SlotEditorIntent) -> Unit = { viewModel.onEvent(it) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            send(SlotEditorIntent.ErrorShown)
        }
    }

    val exercise = state.exercise
    val spec = state.spec
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        exercise?.name?.get(state.language) ?: stringResource(Res.string.routines_slot_editor_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (spec != null) {
                        IconButton(onClick = { send(SlotEditorIntent.RequestDelete) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.routines_delete_slot_action)
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            exercise == null || spec == null -> PlaceholderScreen(
                title = stringResource(Res.string.routines_slot_editor_title),
                message = stringResource(Res.string.routines_slot_missing),
                modifier = Modifier.padding(padding),
            )
            else -> SlotForm(state, exercise, spec, send, Modifier.padding(padding))
        }
    }

    if (state.confirmDelete && exercise != null) {
        ConfirmDialog(
            title = stringResource(Res.string.routines_delete_slot_title),
            message = stringResource(Res.string.routines_delete_slot_message, exercise.name.get(state.language)),
            confirmLabel = stringResource(Res.string.routines_delete),
            onConfirm = { send(SlotEditorIntent.ConfirmDelete) },
            onDismiss = { send(SlotEditorIntent.DismissDelete) },
        )
    }
}

@Composable
private fun SlotForm(
    state: SlotEditorState,
    exercise: Exercise,
    spec: SlotSpec,
    send: (SlotEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().centeredContent().imePadding().verticalScroll(
            rememberScrollState()
        ).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (state.dayName.isNotBlank()) {
            Text(
                state.dayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            stringResource(Res.string.routines_saved_automatically),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TargetSection(state, exercise, spec, send)

        SectionTitle(stringResource(Res.string.routines_section_sets))
        Stepper(
            label = stringResource(Res.string.routines_sets),
            value = spec.sets,
            onValueChange = { send(SlotEditorIntent.SetSets(it)) },
            range = SlotLimits.SETS,
        )
        Stepper(
            label = stringResource(Res.string.routines_warmup_sets),
            value = spec.warmupSets,
            onValueChange = { send(SlotEditorIntent.SetWarmupSets(it)) },
            range = SlotLimits.WARMUP_SETS,
        )
        PrescriptionSection(state, spec, send)
        RestSection(spec, send)
        ExtrasSection(state, spec, send)
        ProgressionSection(state, spec, send)
        NotesField(spec.notes) { send(SlotEditorIntent.SetNotes(it)) }
    }
}

@Composable
private fun NotesField(initial: String, onChange: (String) -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initial) }
    OutlinedTextField(
        value = notes,
        onValueChange = {
            notes = it
            onChange(it)
        },
        label = { Text(stringResource(Res.string.routines_notes)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
    )
}
