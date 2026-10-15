package es.sebas1705.ironlog.feature.routines.ui.catalogue

import es.sebas1705.ironlog.core.ui.adaptive.LocalWindowSize
import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import es.sebas1705.ironlog.feature.routines.ui.list.CARD_MIN_WIDTH
import es.sebas1705.ironlog.feature.routines.ui.list.gridMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScore
import es.sebas1705.ironlog.feature.routines.resources.routines_analysis_open
import es.sebas1705.ironlog.feature.routines.ui.components.ScoreBadge
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.TemplateDay
import es.sebas1705.ironlog.feature.routines.model.ExerciseIndex
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_add_to_mine
import es.sebas1705.ironlog.feature.routines.resources.routines_catalogue_title
import es.sebas1705.ironlog.feature.routines.resources.routines_credit
import es.sebas1705.ironlog.feature.routines.resources.routines_error
import es.sebas1705.ironlog.feature.routines.resources.routines_make_active
import es.sebas1705.ironlog.feature.routines.resources.routines_rest_day
import es.sebas1705.ironlog.feature.routines.resources.routines_template_missing
import es.sebas1705.ironlog.feature.routines.ui.components.BackButton
import es.sebas1705.ironlog.feature.routines.ui.components.SectionTitle
import es.sebas1705.ironlog.feature.routines.ui.components.cycleLabel
import es.sebas1705.ironlog.feature.routines.ui.components.levelLabel
import es.sebas1705.ironlog.feature.routines.ui.components.slotSummary
import es.sebas1705.ironlog.feature.routines.ui.components.targetLabel
import es.sebas1705.ironlog.feature.routines.ui.components.trainingDays
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Famous routines (spec §6.3): name, description, days and credit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogueScreen(
    onBack: () -> Unit,
    onOpenTemplate: (templateId: String) -> Unit,
    viewModel: CatalogueViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.routines_catalogue_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_MIN_WIDTH),
            modifier = Modifier.fillMaxSize().padding(padding).centeredContent(gridMaxWidth()),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(state.templates, key = { it.id }) { template ->
                TemplateCard(
                    template,
                    state.language,
                    state.scores[template.id],
                    onClick = { onOpenTemplate(template.id) }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplate, language: String, score: RoutineScore?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    template.name.get(language),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                ScoreBadge(score, Modifier.padding(top = 2.dp))
            }
            Text(
                cycleLabel(template.days.size, trainingDays(template.days.map { it.kind })) + " · " +
                    levelLabel(template.level),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                template.description.get(language),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(Res.string.routines_credit, template.credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One famous routine's days and exercises, with "Add to my routines". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatePreviewScreen(
    templateId: String,
    onBack: () -> Unit,
    onAdded: (routineId: String) -> Unit,
    onOpenAnalysis: (templateId: String) -> Unit,
    viewModel: TemplatePreviewViewModel = koinViewModel { parametersOf(templateId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.createdRoutineId) {
        state.createdRoutineId?.let { id ->
            viewModel.onEvent(TemplatePreviewIntent.OpenHandled)
            onAdded(id)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            viewModel.onEvent(TemplatePreviewIntent.ErrorShown)
        }
    }
    val template = state.template
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        template?.name?.get(state.language).orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (template != null) {
                AddBar(
                    makeActive = state.makeActive,
                    copying = state.copying,
                    onMakeActive = { viewModel.onEvent(TemplatePreviewIntent.SetMakeActive(it)) },
                    onAdd = { viewModel.onEvent(TemplatePreviewIntent.AddToMyRoutines) },
                )
            }
        },
    ) { padding ->
        if (template == null) {
            PlaceholderScreen(
                title = stringResource(Res.string.routines_catalogue_title),
                message = stringResource(Res.string.routines_template_missing),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        TemplateContent(template, state, { onOpenAnalysis(template.id) }, Modifier.padding(padding))
    }
}

@Composable
private fun TemplateContent(
    template: RoutineTemplate,
    state: TemplatePreviewState,
    onOpenAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().centeredContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(template.description.get(state.language), style = MaterialTheme.typography.bodyLarge)
                Text(
                    cycleLabel(template.days.size, trainingDays(template.days.map { it.kind })) + " · " +
                        levelLabel(template.level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(Res.string.routines_credit, template.credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.score != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScoreBadge(state.score)
                        TextButton(onClick = onOpenAnalysis) { Text(stringResource(Res.string.routines_analysis_open)) }
                    }
                }
            }
        }
        template.days.forEachIndexed { index, day ->
            item(key = "day-$index") { TemplateDayItem(index, day, state.index, state.language) }
        }
    }
}

@Composable
private fun TemplateDayItem(index: Int, day: TemplateDay, exercises: ExerciseIndex, language: String) {
    Column {
        SectionTitle("${index + 1}. ${day.name.get(language)}")
        if (day.kind == DayKind.REST) {
            Text(
                stringResource(Res.string.routines_rest_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        day.slots.forEach { spec ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(targetLabel(spec.target, exercises, language), style = MaterialTheme.typography.bodyLarge)
                Text(
                    slotSummary(spec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AddBar(makeActive: Boolean, copying: Boolean, onMakeActive: (Boolean) -> Unit, onAdd: () -> Unit) {
    val toggle = @Composable { modifier: Modifier ->
        Row(
            modifier
                .toggleable(value = makeActive, role = Role.Checkbox, onValueChange = onMakeActive)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = makeActive, onCheckedChange = null)
            Text(stringResource(Res.string.routines_make_active), Modifier.padding(start = 8.dp))
        }
    }
    val button = @Composable { modifier: Modifier ->
        Button(onClick = onAdd, enabled = !copying, modifier = modifier) {
            if (copying) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.routines_add_to_mine))
            }
        }
    }
    Surface(tonalElevation = 3.dp) {
        val inner = Modifier.navigationBarsPadding().centeredContent().padding(horizontal = 16.dp, vertical = 8.dp)
        if (LocalWindowSize.current.useNavigationRail) {
            // Wide or short windows: one row, so the panel does not eat the height (F9).
            Row(inner, verticalAlignment = Alignment.CenterVertically) {
                toggle(Modifier.weight(1f))
                button(Modifier)
            }
        } else {
            Column(inner) {
                toggle(Modifier.fillMaxWidth())
                button(Modifier.fillMaxWidth())
            }
        }
    }
}
