package es.sebas1705.ironlog.feature.routines.ui.picker

import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.ExerciseFilterBar
import es.sebas1705.ironlog.core.ui.components.FilterChipSpec
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_clear_search
import es.sebas1705.ironlog.feature.routines.resources.routines_error
import es.sebas1705.ironlog.feature.routines.resources.routines_filter_any
import es.sebas1705.ironlog.feature.routines.resources.routines_filter_category
import es.sebas1705.ironlog.feature.routines.resources.routines_filter_clear
import es.sebas1705.ironlog.feature.routines.resources.routines_filter_equipment
import es.sebas1705.ironlog.feature.routines.resources.routines_filter_muscle
import es.sebas1705.ironlog.feature.routines.resources.routines_flexible_option
import es.sebas1705.ironlog.feature.routines.resources.routines_flexible_option_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_no_results
import es.sebas1705.ironlog.feature.routines.resources.routines_pick_exercise
import es.sebas1705.ironlog.feature.routines.resources.routines_search_exercises
import es.sebas1705.ironlog.feature.routines.resources.routines_variants_count
import es.sebas1705.ironlog.feature.routines.ui.components.BackButton
import es.sebas1705.ironlog.feature.routines.ui.components.categoryLabel
import es.sebas1705.ironlog.feature.routines.ui.components.equipmentLabel
import es.sebas1705.ironlog.feature.routines.ui.components.muscleLabel
import es.sebas1705.ironlog.feature.routines.ui.components.variantShortLabel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Picks the exercise of a new slot. An exercise with one variant is added at once; otherwise its row opens
 * to choose a fixed variant or "flexible".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerScreen(
    dayId: String,
    onBack: () -> Unit,
    onAdded: (slotId: String) -> Unit,
    viewModel: ExercisePickerViewModel = koinViewModel { parametersOf(dayId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val send: (ExercisePickerIntent) -> Unit = { viewModel.onEvent(it) }
    // Local text keeps typing smooth; the ViewModel debounces the search.
    var query by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.addedSlotId) {
        state.addedSlotId?.let { id ->
            send(ExercisePickerIntent.OpenHandled)
            onAdded(id)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            send(ExercisePickerIntent.ErrorShown)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.routines_pick_exercise)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            SearchField(query) {
                query = it
                send(ExercisePickerIntent.SetQuery(it))
            }
            PickerFilters(state, send)
            when {
                state.loading -> LoadingScreen()
                state.results.isEmpty() -> Text(
                    stringResource(Res.string.routines_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize().centeredContent()) {
                    items(state.results, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            language = state.language,
                            expanded = state.expandedId == exercise.id,
                            enabled = !state.adding,
                            send = send,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    language: String,
    expanded: Boolean,
    enabled: Boolean,
    send: (ExercisePickerIntent) -> Unit,
) {
    val variants = exercise.variants.filterNot { it.deprecated }
    val single = variants.size <= 1
    Column {
        ListItem(
            headlineContent = { Text(exercise.name.get(language)) },
            supportingContent = {
                Text(
                    if (single) {
                        variants.firstOrNull()?.let { variantShortLabel(it, language) }.orEmpty()
                    } else {
                        pluralStringResource(Res.plurals.routines_variants_count, variants.size, variants.size)
                    },
                )
            },
            trailingContent = if (single) {
                null
            } else {
                {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button) {
                val only = variants.firstOrNull()
                if (single && only != null) {
                    send(ExercisePickerIntent.Pick(exercise, SlotTarget.Fixed(only.id)))
                } else {
                    send(ExercisePickerIntent.Toggle(exercise.id))
                }
            },
        )
        if (expanded && !single) {
            val indent = Modifier.padding(start = 24.dp)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.routines_flexible_option)) },
                supportingContent = { Text(stringResource(Res.string.routines_flexible_option_hint)) },
                colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary),
                modifier = indent.clickable(enabled = enabled, role = Role.Button) {
                    send(ExercisePickerIntent.Pick(exercise, SlotTarget.Flexible(exercise.id)))
                },
            )
            variants.forEach { variant ->
                ListItem(
                    headlineContent = { Text(variantShortLabel(variant, language)) },
                    modifier = indent.clickable(enabled = enabled, role = Role.Button) {
                        send(ExercisePickerIntent.Pick(exercise, SlotTarget.Fixed(variant.id)))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(Res.string.routines_search_exercises)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.routines_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PickerFilters(state: ExercisePickerState, send: (ExercisePickerIntent) -> Unit) {
    ExerciseFilterBar(
        filters = listOf(
            FilterChipSpec(
                title = stringResource(Res.string.routines_filter_category),
                selected = state.category,
                options = ExerciseCategory.entries,
                optionLabel = { categoryLabel(it) },
                onSelect = { send(ExercisePickerIntent.CategorySelected(it)) },
            ),
            FilterChipSpec(
                title = stringResource(Res.string.routines_filter_muscle),
                selected = state.muscle,
                options = Muscle.entries,
                optionLabel = { muscleLabel(it) },
                onSelect = { send(ExercisePickerIntent.MuscleSelected(it)) },
            ),
            FilterChipSpec(
                title = stringResource(Res.string.routines_filter_equipment),
                selected = state.equipment,
                options = Equipment.entries,
                optionLabel = { equipmentLabel(it) },
                onSelect = { send(ExercisePickerIntent.EquipmentSelected(it)) },
            ),
        ),
        anyLabel = stringResource(Res.string.routines_filter_any),
        clearLabel = stringResource(Res.string.routines_filter_clear),
        onClear = { send(ExercisePickerIntent.ClearFilters) },
    )
}
