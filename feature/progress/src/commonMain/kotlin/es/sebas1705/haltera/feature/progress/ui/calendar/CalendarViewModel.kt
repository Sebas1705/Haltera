package es.sebas1705.haltera.feature.progress.ui.calendar

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.ui.components.PastSessionForm
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.domain.usecases.session.LogPastSession
import es.sebas1705.haltera.domain.usecases.session.ObserveTodayPlan
import es.sebas1705.haltera.domain.usecases.session.PastSessionOption
import es.sebas1705.haltera.domain.usecases.session.PastSessionTarget
import es.sebas1705.haltera.feature.progress.model.MonthKey
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.model.ProgressMath
import es.sebas1705.haltera.feature.progress.model.bilateralSlots
import es.sebas1705.haltera.feature.progress.model.SessionSummary
import es.sebas1705.haltera.feature.progress.model.startOfDayMillis
import es.sebas1705.haltera.feature.progress.ui.components.FREE_CHOICE_KEY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate

/**
 * "Add session on this day": the dialog's starting values and the training days that can be chosen
 * ([options]; the form's choice key is the option index, or [FREE_CHOICE_KEY]). [maxDateUtcMillis] is today.
 */
internal data class LogPastUi(
    val initial: PastSessionForm,
    val options: List<PastSessionOption>,
    val maxDateUtcMillis: Long,
)

internal data class CalendarState(
    val month: MonthKey,
    val today: LocalDate,
    val selectedDay: LocalDate?,
    val sessionsByDay: Map<LocalDate, List<SessionSummary>> = emptyMap(),
    val unit: WeightUnit = WeightUnit.KG,
    val loading: Boolean = true,
    val logPast: LogPastUi? = null,
    /** Logging the last session failed (cleared when the dialog opens again). */
    val logFailed: Boolean = false,
) : MVIBaseState {
    val selectedSessions: List<SessionSummary> get() = selectedDay?.let { sessionsByDay[it] }.orEmpty()

    /** Sessions can be added on today or any past day (never in the future). */
    val canAddOnSelectedDay: Boolean get() = selectedDay != null && selectedDay <= today
}

internal sealed interface CalendarIntent : MVIBaseIntent {
    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data class SelectDay(val date: LocalDate) : CalendarIntent

    /** Opens "Add session on this day" for the selected day. */
    data object OpenLogPast : CalendarIntent

    data object DismissLogPast : CalendarIntent

    data class ConfirmLogPast(val form: PastSessionForm) : CalendarIntent
}

/** One-off events: the session just logged opens in feature:session (log mode). */
internal sealed interface CalendarEffect {
    data class OpenSession(val sessionId: String) : CalendarEffect
}

/**
 * Month calendar of sessions (spec §7 Progress): days with sessions are marked; a tapped day lists them
 * and can get a session logged after the fact.
 */
@Suppress("LongParameterList") // One dependency per data source.
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarViewModel(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
    private val observeTodayPlan: ObserveTodayPlan,
    private val logPastSession: LogPastSession,
) : MVIBaseViewModel<CalendarState, CalendarIntent>(initialState(clock)) {

    private val month = MutableStateFlow(currentState.month)
    private val effectChannel = Channel<CalendarEffect>(Channel.BUFFERED)
    val effects: Flow<CalendarEffect> = effectChannel.receiveAsFlow()

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: CalendarIntent): Job = execute {
        when (intent) {
            CalendarIntent.PreviousMonth -> changeMonth(-1)
            CalendarIntent.NextMonth -> changeMonth(1)
            is CalendarIntent.SelectDay -> updateState { it.copy(selectedDay = intent.date) }
            CalendarIntent.OpenLogPast -> openLogPast()
            CalendarIntent.DismissLogPast -> updateState { it.copy(logPast = null) }
            is CalendarIntent.ConfirmLogPast -> confirmLogPast(intent.form)
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

    /** Preselects the active routine's next day; any other training day or a free session can be picked. */
    private suspend fun openLogPast() {
        val state = currentState
        val day = state.selectedDay?.takeIf { state.canAddOnSelectedDay } ?: return
        val options = attempt { LogPastSession.options(routines.observeRoutines().first()) }.orEmpty()
        val plan = attempt { observeTodayPlan().first() }
        val next = options.indexOfFirst {
            it.target.routineId == plan?.routine?.id && it.target.dayIndex == plan.next?.dayIndex
        }
        val choice = if (next >= 0) next.toString() else FREE_CHOICE_KEY
        val form = PastSessionTime.newForm(
            day,
            clock.now(),
            clock.timeZone,
            LogPastSession.DEFAULT_DURATION_MINUTES,
            choice,
        )
        val maxDate = PastSessionTime.utcMillis(state.today)
        updateState { it.copy(logPast = LogPastUi(form, options, maxDate), logFailed = false) }
    }

    private suspend fun confirmLogPast(form: PastSessionForm) {
        val dialog = currentState.logPast ?: return
        updateState { it.copy(logPast = null) }
        val target = form.choiceKey?.toIntOrNull()?.let { dialog.options.getOrNull(it)?.target }
            ?: PastSessionTarget.Free
        val id = attempt {
            logPastSession(
                target,
                PastSessionTime.startMillis(form, clock.timeZone),
                PastSessionTime.durationMillis(form),
            )
        }
        if (id == null) {
            updateState { it.copy(logFailed = true) }
        } else {
            effectChannel.send(CalendarEffect.OpenSession(id))
        }
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
                ProgressMath.groupByDay(
                    list,
                    routineList.associate { it.id to it.name },
                    timeZone,
                    routineList.bilateralSlots(),
                ),
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

        /** Runs [block]; a failure (other than cancellation) becomes null. */
        suspend fun <T> attempt(block: suspend () -> T): T? =
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
}
