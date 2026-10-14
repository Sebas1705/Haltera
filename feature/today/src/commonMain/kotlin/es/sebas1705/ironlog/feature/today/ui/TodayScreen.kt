package es.sebas1705.ironlog.feature.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import es.sebas1705.ironlog.feature.today.resources.Res
import es.sebas1705.ironlog.feature.today.resources.today_error_save
import es.sebas1705.ironlog.feature.today.resources.today_error_start
import es.sebas1705.ironlog.feature.today.resources.today_log_past
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Today tab root: wires the ViewModel and forwards session openings to the shell. */
@Composable
internal fun TodayTab(onOpenSession: (sessionId: String) -> Unit, modifier: Modifier = Modifier) {
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
    TodayScreen(state = state, onIntent = { viewModel.onEvent(it) }, modifier = modifier)
}

@Composable
internal fun TodayScreen(state: TodayState, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.inProgress?.let { session ->
                item(key = "resume") { ResumeCard(session, onResume = { onIntent(TodayIntent.ResumeSession) }) }
            }
            item(key = "plan") {
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
            }
            item(key = "log-past") {
                LogPastButton(enabled = !state.starting, onClick = { onIntent(TodayIntent.OpenLogPast) })
            }
            item(key = "week") { WeekCard(state.week, state.unit) }
            item(key = "weight") {
                BodyWeightCard(
                    latest = state.latestWeight,
                    unit = state.unit,
                    onLog = { onIntent(TodayIntent.OpenWeightDialog) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    TodayDialogs(state, onIntent)
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
