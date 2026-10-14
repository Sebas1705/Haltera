package es.sebas1705.ironlog.feature.progress.ui.calendar

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.feature.progress.model.MonthKey
import es.sebas1705.ironlog.feature.progress.model.ProgressClock
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.SessionSummary
import es.sebas1705.ironlog.feature.progress.model.startOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal data class CalendarState(
    val month: MonthKey,
    val today: LocalDate,
    val selectedDay: LocalDate?,
    val sessionsByDay: Map<LocalDate, List<SessionSummary>> = emptyMap(),
    val unit: WeightUnit = WeightUnit.KG,
    val loading: Boolean = true,
) : MVIBaseState {
    val selectedSessions: List<SessionSummary> get() = selectedDay?.let { sessionsByDay[it] }.orEmpty()
}

internal sealed interface CalendarIntent : MVIBaseIntent {
    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data class SelectDay(val date: LocalDate) : CalendarIntent
}

/** Month calendar of sessions (spec §7 Progress): days with sessions are marked; a tapped day lists them. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarViewModel(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
) : MVIBaseViewModel<CalendarState, CalendarIntent>(initialState(clock)) {

    private val month = MutableStateFlow(currentState.month)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: CalendarIntent): Job = execute {
        when (intent) {
            CalendarIntent.PreviousMonth -> changeMonth(-1)
            CalendarIntent.NextMonth -> changeMonth(1)
            is CalendarIntent.SelectDay -> updateState { it.copy(selectedDay = intent.date) }
        }
    }

    private fun changeMonth(delta: Int) {
        val target = currentState.month.plus(delta)
        updateState {
            it.copy(
                month = target,
                selectedDay = it.today.takeIf { today -> today in target },
                sessionsByDay = emptyMap(),
                loading = true,
            )
        }
        month.value = target
    }

    private suspend fun observe() {
        val timeZone = clock.timeZone
        val monthSessions = month.flatMapLatest { m ->
            sessions.observeSessionsBetween(
                m.firstDay.startOfDayMillis(timeZone),
                m.plus(1).firstDay.startOfDayMillis(timeZone),
            ).map { m to it }
        }
        combine(monthSessions, routines.observeRoutines(), settings.settings) { (m, list), routineList, userSettings ->
            Triple(
                m,
                ProgressMath.groupByDay(list, routineList.associate { it.id to it.name }, timeZone),
                userSettings.unit
            )
        }.collect { (m, grouped, unit) ->
            updateState { state ->
                if (state.month == m) state.copy(sessionsByDay = grouped, unit = unit, loading = false) else state
            }
        }
    }

    private companion object {
        fun initialState(clock: ProgressClock): CalendarState {
            val today = clock.today()
            return CalendarState(month = MonthKey.of(today), today = today, selectedDay = today)
        }
    }
}
