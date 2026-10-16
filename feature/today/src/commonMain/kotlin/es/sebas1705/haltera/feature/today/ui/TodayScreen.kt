package es.sebas1705.haltera.feature.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.adaptive.LocalWindowSize
import es.sebas1705.haltera.core.ui.adaptive.centeredContent
import es.sebas1705.haltera.feature.today.resources.Res
import es.sebas1705.haltera.feature.today.resources.today_error_save
import es.sebas1705.haltera.feature.today.resources.today_error_start
import es.sebas1705.haltera.feature.today.resources.today_log_past
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Today tab root: wires the ViewModel and forwards session openings to the shell. */
@Composable
internal fun TodayTab(
    onOpenSession: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNutrition: () -> Unit = {},
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openSession by rememberUpdatedState(onOpenSession)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodayEffect.OpenSession -> openSession(effect.sessionId)
            }
        }
    }
    TodayScreen(
        state = state,
        onIntent = { viewModel.onEvent(it) },
        modifier = modifier,
        nutrition = { TodayNutritionCard(onOpen = onOpenNutrition) },
        steps = { TodayStepsCard() },
    )
}

@Composable
internal fun TodayScreen(
    state: TodayState,
    onIntent: (TodayIntent) -> Unit,
    modifier: Modifier = Modifier,
    nutrition: @Composable () -> Unit = {},
    steps: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        val message = when (error) {
            TodayError.START_FAILED -> getString(Res.string.today_error_start)
            TodayError.SAVE_FAILED -> getString(Res.string.today_error_save)
        }
        onIntent(TodayIntent.ErrorShown)
        snackbar.showSnackbar(message)
    }

    Box(modifier.fillMaxSize()) {
        if (LocalWindowSize.current.isExpanded) {
            // Expanded width: today's plan on the left, the week and body weight on the right (F3).
            Row(
                Modifier.fillMaxSize().centeredContent(TWO_COLUMN_MAX_WIDTH).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TodayColumn(Modifier.weight(1f)) { PlanItems(state, onIntent) }
                TodayColumn(Modifier.weight(1f)) { SummaryItems(state, onIntent, nutrition, steps) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().centeredContent(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(
                    key = "plan"
                ) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { PlanItems(state, onIntent) } }
                item(key = "summary") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) { SummaryItems(state, onIntent, nutrition, steps) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    TodayDialogs(state, onIntent)
}

private val TWO_COLUMN_MAX_WIDTH = 1200.dp

@Composable
private fun TodayColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** Resume card, today's plan and the log-past button. */
@Composable
private fun PlanItems(state: TodayState, onIntent: (TodayIntent) -> Unit) {
    state.inProgress?.let { session ->
        ResumeCard(session, onResume = { onIntent(TodayIntent.ResumeSession) })
    }
    val plan = state.plan
    when {
        !state.planLoaded -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
            CircularProgressIndicator()
        }
        plan == null -> NoRoutineCard(
            startEnabled = !state.starting && state.inProgress == null,
            onFreeSession = { onIntent(TodayIntent.StartFree) },
        )
        else -> PlanCard(
            plan = plan,
            startEnabled = !state.starting && state.inProgress == null,
            onIntent = onIntent,
        )
    }
    LogPastButton(enabled = !state.starting, onClick = { onIntent(TodayIntent.OpenLogPast) })
}

/** This week's summary, today's steps and nutrition (when used) and body weight. */
@Composable
private fun SummaryItems(
    state: TodayState,
    onIntent: (TodayIntent) -> Unit,
    nutrition: @Composable () -> Unit,
    steps: @Composable () -> Unit,
) {
    WeekCard(state.week)
    steps()
    nutrition()
    BodyWeightCard(
        latest = state.latestWeight,
        unit = state.unit,
        onLog = { onIntent(TodayIntent.OpenWeightDialog) },
    )
}

@Composable
private fun TodayDialogs(state: TodayState, onIntent: (TodayIntent) -> Unit) {
    state.plan?.takeIf { state.dayPickerOpen }?.let { plan ->
        DayPickerDialog(
            plan = plan,
            startEnabled = !state.starting && state.inProgress == null,
            onIntent = onIntent,
        )
    }
    state.weightDialog?.let { dialog -> WeightDialog(dialog, state.unit, onIntent) }
    state.logPast?.let { dialog -> LogPastDialog(dialog, onIntent) }
}

/** Secondary action: the logbook accepts any past day, routine day or free session. */
@Composable
private fun LogPastButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.today_log_past))
    }
}
