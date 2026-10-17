package es.sebas1705.haltera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.core.units.Numbers
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.feature.today.resources.Res
import es.sebas1705.haltera.feature.today.resources.today_steps_none
import es.sebas1705.haltera.feature.today.resources.today_steps_reached
import es.sebas1705.haltera.feature.today.resources.today_steps_source
import es.sebas1705.haltera.feature.today.resources.today_steps_title
import es.sebas1705.haltera.feature.today.resources.today_steps_value
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Today's steps from Health Connect.
 *
 * @property visible Only once the user connected Health Connect with steps switched on; otherwise the card is absent.
 * @property steps Null until Health Connect has a count for today.
 */
internal data class TodayStepsState(
    val visible: Boolean = false,
    val steps: Long? = null,
    val goal: Int = HealthSettings.DEFAULT_STEP_GOAL,
) : MVIBaseState {
    val reached: Boolean get() = (steps ?: 0) >= goal
}

internal sealed interface TodayStepsIntent : MVIBaseIntent

/** The optional steps card of the Today tab; asks for a (throttled) foreground read when shown. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TodayStepsViewModel(
    private val health: HealthRepository,
    store: HealthSettingsStore,
    clock: TodayClock,
) : MVIBaseViewModel<TodayStepsState, TodayStepsIntent>(TodayStepsState()) {
    init {
        val today: LocalDate = PastSessionTime.localDate(clock.now(), clock.timeZone)
        execute {
            store.settings.flatMapLatest { s ->
                val shown = s.connected && HealthDataType.STEPS in s.enabled
                val days = if (shown) health.observeDays(today, today) else flowOf(emptyList())
                days.map { d -> TodayStepsState(visible = shown, steps = d.firstOrNull()?.steps, goal = s.stepGoal) }
            }.collect { s -> updateState { s } }
        }
        execute { health.refresh() }
    }

    override fun intentHandler(intent: TodayStepsIntent): Job = execute { }
}

/** "Steps: 6 234 / 8 000" with a progress bar; absent unless Health Connect steps are connected. */
@Composable
internal fun TodayStepsCard(viewModel: TodayStepsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.visible) return
    val goal = Numbers.format(state.goal.toLong())
    val value = state.steps?.let { stringResource(Res.string.today_steps_value, Numbers.format(it), goal) }
        ?: stringResource(Res.string.today_steps_none)
    val note = if (state.reached) {
        stringResource(
            Res.string.today_steps_reached
        )
    } else {
        stringResource(Res.string.today_steps_source)
    }
    val title = stringResource(Res.string.today_steps_title)
    OutlinedCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title, $value, $note" }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall.tabularFigures())
            val fraction = ((state.steps ?: 0).toFloat() / state.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
