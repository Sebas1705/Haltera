package es.sebas1705.ironlog.feature.routines.ui.catalogue

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
import androidx.compose.foundation.lazy.items
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.templates, key = { it.id }) { template ->
                TemplateCard(template, state.language, onClick = { onOpenTemplate(template.id) })
            }
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplate, language: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(template.name.get(language), style = MaterialTheme.typography.titleMedium)
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
                        maxLines = 1,
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
        TemplateContent(template, state, Modifier.padding(padding))
    }
}

@Composable
private fun TemplateContent(template: RoutineTemplate, state: TemplatePreviewState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = makeActive, role = Role.Checkbox, onValueChange = onMakeActive)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = makeActive, onCheckedChange = null)
                Text(stringResource(Res.string.routines_make_active), Modifier.padding(start = 8.dp))
            }
            Button(onClick = onAdd, enabled = !copying, modifier = Modifier.fillMaxWidth()) {
                if (copying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.routines_add_to_mine))
                }
            }
        }
    }
}
