package es.sebas1705.ironlog.feature.exercises.ui

import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.ErrorScreen
import es.sebas1705.ironlog.core.ui.components.ExerciseFilterBar
import es.sebas1705.ironlog.core.ui.components.FilterChipSpec
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.exercises.resources.Res
import es.sebas1705.ironlog.feature.exercises.resources.exercises_custom_badge
import es.sebas1705.ironlog.feature.exercises.resources.exercises_empty_message
import es.sebas1705.ironlog.feature.exercises.resources.exercises_empty_title
import es.sebas1705.ironlog.feature.exercises.resources.exercises_error
import es.sebas1705.ironlog.feature.exercises.resources.exercises_filter_any
import es.sebas1705.ironlog.feature.exercises.resources.exercises_filter_category
import es.sebas1705.ironlog.feature.exercises.resources.exercises_filter_clear
import es.sebas1705.ironlog.feature.exercises.resources.exercises_filter_equipment
import es.sebas1705.ironlog.feature.exercises.resources.exercises_filter_muscle
import es.sebas1705.ironlog.feature.exercises.resources.exercises_new
import es.sebas1705.ironlog.feature.exercises.resources.exercises_search_clear
import es.sebas1705.ironlog.feature.exercises.resources.exercises_search_hint
import es.sebas1705.ironlog.feature.exercises.ui.common.label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Exercises tab root: search, filter chips and the exercise list. The shell draws the top bar. */
@Composable
internal fun ExercisesScreen(
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = Locale.current.language
    LaunchedEffect(language) { viewModel.onEvent(ExercisesIntent.SetLanguage(language)) }

    Scaffold(
        modifier = modifier,
        // The shell's Scaffold already applies the system insets.
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateExercise) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.exercises_new))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query,
                onQueryChange = { viewModel.onEvent(ExercisesIntent.QueryChanged(it)) },
            )
            FilterRow(state = state, onIntent = { viewModel.onEvent(it) })
            HorizontalDivider()
            when {
                state.error -> ErrorScreen(
                    message = stringResource(Res.string.exercises_error),
                    onRetry = { viewModel.onEvent(ExercisesIntent.Retry) },
                )
                state.loading && state.items.isEmpty() -> LoadingScreen()
                state.items.isEmpty() -> EmptyState(
                    showClear = state.hasFilters,
                    onClear = { viewModel.onEvent(ExercisesIntent.ClearFilters) },
                )
                else -> ExerciseList(items = state.items, onOpenExercise = onOpenExercise)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(Res.string.exercises_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.exercises_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

@Composable
private fun FilterRow(state: ExercisesState, onIntent: (ExercisesIntent) -> Unit) {
    ExerciseFilterBar(
        filters = listOf(
            FilterChipSpec(
                title = stringResource(Res.string.exercises_filter_category),
                selected = state.category,
                options = ExerciseCategory.entries,
                optionLabel = { stringResource(it.label) },
                onSelect = { onIntent(ExercisesIntent.CategorySelected(it)) },
            ),
            FilterChipSpec(
                title = stringResource(Res.string.exercises_filter_muscle),
                selected = state.muscle,
                options = Muscle.entries,
                optionLabel = { stringResource(it.label) },
                onSelect = { onIntent(ExercisesIntent.MuscleSelected(it)) },
            ),
            FilterChipSpec(
                title = stringResource(Res.string.exercises_filter_equipment),
                selected = state.equipment,
                options = Equipment.entries,
                optionLabel = { stringResource(it.label) },
                onSelect = { onIntent(ExercisesIntent.EquipmentSelected(it)) },
            ),
        ),
        anyLabel = stringResource(Res.string.exercises_filter_any),
        clearLabel = stringResource(Res.string.exercises_filter_clear),
        onClear = { onIntent(ExercisesIntent.ClearFilters) },
    )
}

@Composable
private fun ExerciseList(items: List<ExerciseRow>, onOpenExercise: (String) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().centeredContent(),
        // Room for the floating action button over the last row.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(items, key = { it.id }, contentType = { "exercise" }) { row ->
            ExerciseRowItem(row = row, onClick = { onOpenExercise(row.id) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRowItem(row: ExerciseRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.custom) CustomBadge()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            row.mainMuscle?.let { muscle ->
                Text(
                    text = stringResource(muscle.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.equipment.forEach { equipment -> SmallChip(stringResource(equipment.label)) }
        }
    }
}

@Composable
internal fun SmallChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun CustomBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(Res.string.exercises_custom_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(showClear: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.exercises_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.exercises_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showClear) {
            TextButton(onClick = onClear) { Text(stringResource(Res.string.exercises_filter_clear)) }
        }
    }
}
