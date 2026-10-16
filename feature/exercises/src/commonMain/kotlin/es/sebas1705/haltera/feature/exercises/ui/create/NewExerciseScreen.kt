package es.sebas1705.haltera.feature.exercises.ui.create

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
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.adaptive.centeredContent
import es.sebas1705.haltera.core.ui.components.ErrorScreen
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.feature.exercises.resources.Res
import es.sebas1705.haltera.feature.exercises.resources.exercises_back
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_category
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_equipment
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_equipment_error
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_failed
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_instructions
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_level
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_load_types
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_muscles
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_name
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_name_en_optional
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_name_error
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_name_es_optional
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_other_name_hint
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_removed_logged
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_save
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_secondary
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_tracking
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_tracking_locked
import es.sebas1705.haltera.feature.exercises.resources.exercises_create_variant_locked
import es.sebas1705.haltera.feature.exercises.resources.exercises_detail_not_found
import es.sebas1705.haltera.feature.exercises.resources.exercises_edit
import es.sebas1705.haltera.feature.exercises.resources.exercises_new
import es.sebas1705.haltera.feature.exercises.ui.common.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Nested destination: form for a custom exercise, new ([exerciseId] null) or edited. [onSaved] receives its id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewExerciseScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    exerciseId: String? = null,
    viewModel: NewExerciseViewModel = koinViewModel(key = "exercise-editor-$exerciseId") {
        parametersOf(exerciseId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = Locale.current.language
    LaunchedEffect(language) { viewModel.onEvent(NewExerciseIntent.SetLanguage(language)) }
    val savedId = state.savedExerciseId
    LaunchedEffect(savedId) { if (savedId != null) onSaved(savedId) }

    Scaffold(
        modifier = modifier,
        // The shell's Scaffold already applies the system insets.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.editing) Res.string.exercises_edit else Res.string.exercises_new)
                    )
                },
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
                    } else if (!state.loading && !state.notFound) {
                        TextButton(onClick = { viewModel.onEvent(NewExerciseIntent.Save) }) {
                            Text(stringResource(Res.string.exercises_create_save))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when {
            state.notFound -> ErrorScreen(stringResource(Res.string.exercises_detail_not_found), modifier = content)
            state.loading -> LoadingScreen(content)
            else -> NewExerciseForm(state = state, onIntent = { viewModel.onEvent(it) }, modifier = content)
        }
    }
}

@Composable
private fun NewExerciseForm(
    state: NewExerciseState,
    onIntent: (NewExerciseIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.centeredContent()
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
        OtherNameField(state.otherName, state.language) { onIntent(NewExerciseIntent.OtherNameChanged(it)) }
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
            enabled = !state.trackingLocked,
            note = if (state.trackingLocked) Res.string.exercises_create_tracking_locked else null,
        )
        MusclesAndLevel(state, onIntent)
        ChipGroup(
            title = Res.string.exercises_create_equipment,
            options = Equipment.entries,
            isSelected = { it in state.equipment },
            label = { it.label },
            onClick = { onIntent(NewExerciseIntent.EquipmentToggled(it)) },
            error = if (state.showErrors && state.equipmentError) Res.string.exercises_create_equipment_error else null,
            note = if (state.removesLoggedVariant) Res.string.exercises_create_removed_logged else null,
        )
        if (state.variants.isNotEmpty()) LoadTypes(state.variants, onIntent)
        InstructionsField(state.instructions) { onIntent(NewExerciseIntent.InstructionsChanged(it)) }
        if (state.saveFailed) {
            Text(
                stringResource(Res.string.exercises_create_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MusclesAndLevel(state: NewExerciseState, onIntent: (NewExerciseIntent) -> Unit) {
    ChipGroup(
        title = Res.string.exercises_create_muscles,
        options = Muscle.entries,
        isSelected = { it in state.primaryMuscles },
        label = { it.label },
        onClick = { onIntent(NewExerciseIntent.MuscleToggled(it)) },
    )
    ChipGroup(
        title = Res.string.exercises_create_secondary,
        options = Muscle.entries,
        isSelected = { it in state.secondaryMuscles },
        label = { it.label },
        onClick = { onIntent(NewExerciseIntent.SecondaryMuscleToggled(it)) },
    )
    ChipGroup(
        title = Res.string.exercises_create_level,
        options = Level.entries,
        isSelected = { it == state.level },
        label = { it.label },
        onClick = { onIntent(NewExerciseIntent.LevelSelected(it)) },
    )
}

/** The name in the app language. */
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

/** The optional name in the other UI language; empty means the same name in both. */
@Composable
private fun OtherNameField(name: String, language: String, onChange: (String) -> Unit) {
    val spanish = language.lowercase().startsWith("es")
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = {
            Text(
                stringResource(
                    if (spanish) Res.string.exercises_create_name_en_optional else Res.string.exercises_create_name_es_optional,
                ),
            )
        },
        supportingText = { Text(stringResource(Res.string.exercises_create_other_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The load type of each variant; a variant with history keeps its own (shown, not selectable). */
@Composable
private fun LoadTypes(variants: List<VariantDraft>, onIntent: (NewExerciseIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(Res.string.exercises_create_load_types)
        variants.forEachIndexed { index, variant ->
            ChipGroup(
                title = variant.equipment.label,
                options = LoadType.entries,
                isSelected = { it == variant.loadType },
                label = { it.label },
                onClick = { onIntent(NewExerciseIntent.LoadTypeSelected(index, it)) },
                enabled = !variant.locked,
                note = if (variant.locked) Res.string.exercises_create_variant_locked else null,
                subtitle = true,
            )
        }
    }
}

@Composable
private fun InstructionsField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.exercises_create_instructions)) },
        minLines = INSTRUCTION_LINES,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Title(title: StringResource, subtitle: Boolean = false) {
    Text(
        text = stringResource(title),
        style = if (subtitle) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
        color = if (subtitle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
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
    enabled: Boolean = true,
    note: StringResource? = null,
    subtitle: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title(title, subtitle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(stringResource(label(option))) },
                    enabled = enabled,
                )
            }
        }
        if (note != null) {
            Text(
                stringResource(note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
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

private const val INSTRUCTION_LINES = 3
