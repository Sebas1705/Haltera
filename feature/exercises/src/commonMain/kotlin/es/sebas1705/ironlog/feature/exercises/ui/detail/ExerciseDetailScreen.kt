package es.sebas1705.ironlog.feature.exercises.ui.detail

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.ErrorScreen
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.exercises.model.formatDate
import es.sebas1705.ironlog.feature.exercises.model.formatDuration
import es.sebas1705.ironlog.feature.exercises.resources.Res
import es.sebas1705.ironlog.feature.exercises.resources.exercises_back
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_category
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_history
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_instructions
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_level
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_no_history
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_no_instructions
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_no_records
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_not_found
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_primary
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_records
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_secondary
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_tracking
import es.sebas1705.ironlog.feature.exercises.resources.exercises_detail_variant
import es.sebas1705.ironlog.feature.exercises.resources.exercises_distance
import es.sebas1705.ironlog.feature.exercises.resources.exercises_error
import es.sebas1705.ironlog.feature.exercises.resources.exercises_load
import es.sebas1705.ironlog.feature.exercises.resources.exercises_load_level
import es.sebas1705.ironlog.feature.exercises.resources.exercises_record_heaviest
import es.sebas1705.ironlog.feature.exercises.resources.exercises_record_one_rm
import es.sebas1705.ironlog.feature.exercises.resources.exercises_record_reps
import es.sebas1705.ironlog.feature.exercises.resources.exercises_record_volume
import es.sebas1705.ironlog.feature.exercises.resources.exercises_reps
import es.sebas1705.ironlog.feature.exercises.resources.exercises_set_times
import es.sebas1705.ironlog.feature.exercises.resources.exercises_sets
import es.sebas1705.ironlog.feature.exercises.ui.CustomBadge
import es.sebas1705.ironlog.feature.exercises.ui.SmallChip
import es.sebas1705.ironlog.feature.exercises.ui.common.label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Nested destination: one exercise with its own top bar and back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = koinViewModel { parametersOf(exerciseId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = Locale.current.language
    LaunchedEffect(language) { viewModel.onEvent(ExerciseDetailIntent.SetLanguage(language)) }

    Scaffold(
        modifier = modifier,
        // The shell's Scaffold already applies the system insets.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.exercises_back)
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val detail = state.detail
        val content = Modifier.fillMaxSize().padding(padding)
        when {
            state.error -> ErrorScreen(stringResource(Res.string.exercises_error), modifier = content)
            state.notFound -> ErrorScreen(stringResource(Res.string.exercises_detail_not_found), modifier = content)
            detail == null -> LoadingScreen(content)
            else -> DetailContent(
                detail = detail,
                state = state,
                onSelectVariant = { viewModel.onEvent(ExerciseDetailIntent.SelectVariant(it)) },
                modifier = content,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: ExerciseDetail,
    state: ExerciseDetailState,
    onSelectVariant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "media") {
            ExerciseMedia(
                exerciseName = detail.name,
                frames = detail.frames,
                primaryMuscles = detail.primaryMuscles,
            )
        }
        if (detail.variants.size > 1) {
            item(key = "variants") {
                Section(stringResource(Res.string.exercises_detail_variant)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.variants.forEach { variant ->
                            FilterChip(
                                selected = variant.id == detail.selectedVariantId,
                                onClick = { onSelectVariant(variant.id) },
                                label = { Text(variant.name ?: stringResource(variant.equipment.label)) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "facts") { Facts(detail) }
        item(key = "muscles") { Muscles(detail) }
        item(key = "instructions") { Instructions(detail.steps) }
        item(key = "records") { Records(state.records, state.unit) }
        item(key = "history-title") { SectionTitle(stringResource(Res.string.exercises_detail_history)) }
        if (state.history.isEmpty()) {
            item(key = "history-empty") { MutedText(stringResource(Res.string.exercises_detail_no_history)) }
        } else {
            items(state.history, key = { "history-${it.sessionId}" }) { row -> HistoryItem(row, state.unit) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(detail: ExerciseDetail) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (detail.custom) CustomBadge()
        Fact(stringResource(Res.string.exercises_detail_category), stringResource(detail.category.label))
        Fact(stringResource(Res.string.exercises_detail_level), stringResource(detail.level.label))
        Fact(stringResource(Res.string.exercises_detail_tracking), stringResource(detail.trackingType.label))
    }
}

@Composable
private fun Fact(title: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Muscles(detail: ExerciseDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail.primaryMuscles.isNotEmpty()) {
            MuscleGroup(stringResource(Res.string.exercises_detail_primary), detail.primaryMuscles)
        }
        if (detail.secondaryMuscles.isNotEmpty()) {
            MuscleGroup(stringResource(Res.string.exercises_detail_secondary), detail.secondaryMuscles)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleGroup(title: String, muscles: List<Muscle>) {
    Section(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            muscles.forEach { SmallChip(stringResource(it.label)) }
        }
    }
}

@Composable
private fun Instructions(steps: List<String>) {
    Section(stringResource(Res.string.exercises_detail_instructions)) {
        if (steps.isEmpty()) {
            MutedText(stringResource(Res.string.exercises_detail_no_instructions))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 24.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Records(records: List<RecordRow>, unit: WeightUnit) {
    Section(stringResource(Res.string.exercises_detail_records)) {
        if (records.isEmpty()) {
            MutedText(stringResource(Res.string.exercises_detail_no_records))
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    records.forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        RecordItem(record, unit)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItem(record: RecordRow, unit: WeightUnit) {
    val title = when (record.kind) {
        RecordRowKind.HEAVIEST_LOAD -> Res.string.exercises_record_heaviest
        RecordRowKind.ESTIMATED_ONE_RM -> Res.string.exercises_record_one_rm
        RecordRowKind.MOST_REPS -> Res.string.exercises_record_reps
        RecordRowKind.SESSION_VOLUME -> Res.string.exercises_record_volume
    }
    val value = record.load?.let { loadString(it, unit) }
        ?: record.count?.let { stringResource(Res.string.exercises_reps, it.toString()) }
            .orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatDate(record.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, unit: WeightUnit) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDate(row.at), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.exercises_sets, row.sets.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.topSet?.let { Text(topSetString(it, unit), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun loadString(load: LoadText, unit: WeightUnit): String =
    if (load.isLevel) {
        stringResource(Res.string.exercises_load_level, load.value)
    } else {
        stringResource(Res.string.exercises_load, load.value, stringResource(unit.label))
    }

/** "100 kg × 5", "12 reps", "20 kg × 1:30", "5000 m × 25:00"… depending on what the set recorded. */
@Composable
private fun topSetString(top: TopSet, unit: WeightUnit): String {
    val first = top.load?.let { loadString(it, unit) }
        ?: top.distanceM?.let { stringResource(Res.string.exercises_distance, it.toString()) }
    val second = top.reps?.let {
        if (first == null) stringResource(Res.string.exercises_reps, it.toString()) else it.toString()
    }
        ?: top.durationS?.let { formatDuration(it) }
    return when {
        first != null && second != null -> stringResource(Res.string.exercises_set_times, first, second)
        else -> first ?: second.orEmpty()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
