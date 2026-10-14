package es.sebas1705.ironlog.feature.exercises.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.feature.exercises.resources.Res
import es.sebas1705.ironlog.feature.exercises.resources.exercises_back
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_category
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_equipment
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_equipment_error
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_failed
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_muscles
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_name
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_name_error
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_save
import es.sebas1705.ironlog.feature.exercises.resources.exercises_create_tracking
import es.sebas1705.ironlog.feature.exercises.resources.exercises_new
import es.sebas1705.ironlog.feature.exercises.ui.common.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Nested destination: form for a custom exercise. [onSaved] receives the new exercise id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewExerciseScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewExerciseViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedId = state.savedExerciseId
    LaunchedEffect(savedId) { if (savedId != null) onSaved(savedId) }

    Scaffold(
        modifier = modifier,
        // The shell's Scaffold already applies the system insets.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.exercises_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.exercises_back)
                        )
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp))
                    } else {
                        TextButton(onClick = { viewModel.onEvent(NewExerciseIntent.Save) }) {
                            Text(stringResource(Res.string.exercises_create_save))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        NewExerciseForm(
            state = state,
            onIntent = { viewModel.onEvent(it) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun NewExerciseForm(
    state: NewExerciseState,
    onIntent: (NewExerciseIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        NameField(
            name = state.name,
            showError = state.showErrors && state.nameError,
            onNameChange = { onIntent(NewExerciseIntent.NameChanged(it)) },
        )
        ChipGroup(
            title = Res.string.exercises_create_category,
            options = ExerciseCategory.entries,
            isSelected = { it == state.category },
            label = { it.label },
            onClick = { onIntent(NewExerciseIntent.CategorySelected(it)) },
        )
        ChipGroup(
            title = Res.string.exercises_create_tracking,
            options = TrackingType.entries,
            isSelected = { it == state.trackingType },
            label = { it.label },
            onClick = { onIntent(NewExerciseIntent.TrackingSelected(it)) },
        )
        ChipGroup(
            title = Res.string.exercises_create_muscles,
            options = Muscle.entries,
            isSelected = { it in state.primaryMuscles },
            label = { it.label },
            onClick = { onIntent(NewExerciseIntent.MuscleToggled(it)) },
        )
        ChipGroup(
            title = Res.string.exercises_create_equipment,
            options = Equipment.entries,
            isSelected = { it in state.equipment },
            label = { it.label },
            onClick = { onIntent(NewExerciseIntent.EquipmentToggled(it)) },
            error = if (state.showErrors && state.equipmentError) Res.string.exercises_create_equipment_error else null,
        )
        if (state.saveFailed) {
            Text(
                stringResource(Res.string.exercises_create_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The name is stored for both languages. */
@Composable
private fun NameField(name: String, showError: Boolean, onNameChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val errorText = stringResource(Res.string.exercises_create_name_error)
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(Res.string.exercises_create_name)) },
        isError = showError,
        supportingText = if (showError) {
            { Text(errorText) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (showError) error(errorText) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: StringResource,
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> StringResource,
    onClick: (T) -> Unit,
    error: StringResource? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(stringResource(label(option))) },
                )
            }
        }
        if (error != null) {
            Text(
                stringResource(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
