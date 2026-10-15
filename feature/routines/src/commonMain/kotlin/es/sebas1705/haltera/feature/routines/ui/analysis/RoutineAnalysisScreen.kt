package es.sebas1705.haltera.feature.routines.ui.analysis

import es.sebas1705.haltera.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.HealthDisclaimer
import es.sebas1705.haltera.core.ui.components.HealthDisclaimerKind
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.ui.components.PlaceholderScreen
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.domain.usecases.routinescore.Criterion
import es.sebas1705.haltera.domain.usecases.routinescore.CriterionId
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineFocus
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineScore
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_disclaimer
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_empty
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_from_logs
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_how
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_missing
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_no_advice
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_out_of
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_schedule
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_sources
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_title
import es.sebas1705.haltera.feature.routines.resources.routines_analysis_weight
import es.sebas1705.haltera.feature.routines.resources.routines_close
import es.sebas1705.haltera.feature.routines.resources.routines_error
import es.sebas1705.haltera.feature.routines.ui.components.BackButton
import es.sebas1705.haltera.feature.routines.ui.components.SectionTitle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Routine analysis: total score, per-criterion cards with advice and the weekly sets chart. Read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineAnalysisScreen(
    routineId: String?,
    templateId: String?,
    onBack: () -> Unit,
    viewModel: RoutineAnalysisViewModel = koinViewModel { parametersOf(routineId, templateId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var info by remember { mutableStateOf<Criterion?>(null) }
    LaunchedEffect(state.error) {
        if (state.error) snackbar.showSnackbar(getString(Res.string.routines_error))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.routines_analysis_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val score = state.score
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            state.missing || score == null -> PlaceholderScreen(
                title = stringResource(Res.string.routines_analysis_title),
                message = stringResource(Res.string.routines_analysis_missing),
                modifier = Modifier.padding(padding),
            )
            else -> AnalysisContent(state, score, onInfo = { info = it }, modifier = Modifier.padding(padding))
        }
    }
    info?.let { criterion -> InfoDialog(criterion, onDismiss = { info = null }) }
}

@Composable
private fun AnalysisContent(
    state: RoutineAnalysisState,
    score: RoutineScore,
    onInfo: (Criterion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val planCriteria = score.criteria.filter { it.id !in SESSION_CRITERIA }
    val sessionCriteria = score.criteria.filter { it.id in SESSION_CRITERIA }
    LazyColumn(
        modifier = modifier.fillMaxSize().centeredContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") { SummaryCard(state, score) }
        item(key = "disclaimer") { HealthDisclaimer(HealthDisclaimerKind.ANALYSIS) }
        if (score.weeklySets.isNotEmpty() && score.scored) {
            item(key = "chart") { WeeklySetsCard(score.weeklySets, score.weeklySplit, state.language) }
        }
        items(planCriteria, key = { it.id.name }) { CriterionCard(it, state, onInfo) }
        if (sessionCriteria.isNotEmpty()) {
            item(key = "logs") { SectionTitle(stringResource(Res.string.routines_analysis_from_logs)) }
            items(sessionCriteria, key = { it.id.name }) { CriterionCard(it, state, onInfo) }
        }
    }
}

@Composable
private fun SummaryCard(state: RoutineAnalysisState, score: RoutineScore) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                state.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (score.scored) {
                ScoreHeadline(score)
                ScoreBar(score.total)
                Text(verdictText(score.verdict), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(
                        Res.string.routines_analysis_schedule,
                        formatOne(score.sessionsPerWeek, state.language)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (score.focus == RoutineFocus.EMPTY) {
                        stringResource(Res.string.routines_analysis_empty)
                    } else {
                        verdictText(score.verdict)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(Res.string.routines_analysis_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "87/100" in the score colour, then the verdict and the focus. */
@Composable
private fun ScoreHeadline(score: RoutineScore) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${score.total}",
                style = MaterialTheme.typography.displayMedium.tabularFigures(),
                fontWeight = FontWeight.Bold,
                color = scoreColor(score.total),
            )
            Text(
                stringResource(Res.string.routines_analysis_out_of),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(verdictLabel(score.verdict), style = MaterialTheme.typography.titleLarge)
            focusLabel(score.focus)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CriterionCard(criterion: Criterion, state: RoutineAnalysisState, onInfo: (Criterion) -> Unit) {
    val title = stringResource(criterionTitleRes(criterion.id))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${criterion.score}",
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(criterion.score),
                )
                IconButton(onClick = { onInfo(criterion) }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(Res.string.routines_analysis_how) + ": " + title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScoreBar(criterion.score)
                Text(
                    stringResource(Res.string.routines_analysis_weight, criterion.weight),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                findingLines(criterion.finding, state.language).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (criterion.advice.isEmpty()) {
                    Text(
                        stringResource(Res.string.routines_analysis_no_advice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                    criterion.advice.forEach { advice ->
                        AdviceLine(
                            adviceText(advice, state.language, state::exerciseName)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdviceLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoDialog(criterion: Criterion, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(criterionTitleRes(criterion.id))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(criterionInfoRes(criterion.id)), style = MaterialTheme.typography.bodyMedium)
                HealthDisclaimer(HealthDisclaimerKind.ANALYSIS)
                if (criterion.evidence.isNotEmpty()) {
                    Text(
                        stringResource(
                            Res.string.routines_analysis_sources,
                            criterion.evidence.joinToString("; ") { it.citation }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.routines_close)) } },
    )
}

private val SESSION_CRITERIA = setOf(CriterionId.ADHERENCE, CriterionId.LOAD_PROGRESS)
