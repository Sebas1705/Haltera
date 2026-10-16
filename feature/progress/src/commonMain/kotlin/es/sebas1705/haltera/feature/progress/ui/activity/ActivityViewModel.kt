package es.sebas1705.haltera.feature.progress.ui.activity

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.domain.usecases.stats.ActivityStats
import es.sebas1705.haltera.domain.usecases.stats.DatedValue
import es.sebas1705.haltera.domain.usecases.stats.WeeklySteps
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal enum class ActivityRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

/** Everything the Activity tab shows; computed by [ActivityViewModel.summarize] from the cached days. */
internal data class ActivitySummary(
    val today: LocalDate,
    val from: LocalDate,
    /** Every date of the range with its steps (null = no data), oldest first. */
    val daily: List<Pair<LocalDate, Long?>> = emptyList(),
    val todaySteps: Long? = null,
    val sevenDayAverage: Double? = null,
    val best: DatedValue? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val daysAtGoal: Int = 0,
    /** Latest week first. */
    val weekly: List<WeeklySteps> = emptyList(),
    val activeKcal: List<Pair<LocalDate, Double>> = emptyList(),
    val totalKcal: List<Pair<LocalDate, Double>> = emptyList(),
    val restingHeartRate: List<Pair<LocalDate, Double>> = emptyList(),
    val restingHeartRateAverage: Double? = null,
    val sleepHours: List<Pair<LocalDate, Double>> = emptyList(),
    val sleepHoursAverage: Double? = null,
) {
    val hasSteps: Boolean get() = daily.any { it.second != null }
}

internal data class ActivityState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    /** Steps are switched on and allowed in Health Connect (otherwise the chart says why it is empty). */
    val stepsAllowed: Boolean = true,
    val range: ActivityRange = ActivityRange.MONTH,
    val stepGoal: Int = HealthSettings.DEFAULT_STEP_GOAL,
    val summary: ActivitySummary? = null,
    val goalInput: String? = null,
    val goalInvalid: Boolean = false,
) : MVIBaseState

internal sealed interface ActivityIntent : MVIBaseIntent {
    data class SetRange(val range: ActivityRange) : ActivityIntent

    /** Reads Health Connect now (the refresh button). */
    data object Refresh : ActivityIntent

    data object OpenGoal : ActivityIntent

    data class GoalChanged(val text: String) : ActivityIntent

    data object SaveGoal : ActivityIntent

    data object DismissGoal : ActivityIntent
}

/**
 * Progress → Activity: steps, calories, resting heart rate and sleep read from Health Connect (cached by
 * [HealthRepository]). Opening the tab asks for a (throttled) foreground read.
 */
internal class ActivityViewModel(
    private val health: HealthRepository,
    private val store: HealthSettingsStore,
    private val clock: ProgressClock = ProgressClock(),
) : MVIBaseViewModel<ActivityState, ActivityIntent>(ActivityState()) {
    private val range = MutableStateFlow(ActivityRange.MONTH)

    init {
        execute {
            val today = clock.today()
            // A year of days for the longest streak; the charts use the selected range.
            combine(
                health.observeDays(today.minus(DatePeriod(days = HISTORY_DAYS - 1)), today),
                store.settings,
                range,
            ) { days, settings, r -> Triple(days, settings, r) }
                .collect { (days, settings, r) ->
                    val summary = summarize(days, clock.today(), r, settings.stepGoal)
                    updateState {
                        it.copy(
                            loading = false,
                            connected = settings.connected,
                            range = r,
                            stepGoal = settings.stepGoal,
                            summary = summary,
                        )
                    }
                }
        }
        execute {
            combine(store.settings, health.status) { s, status ->
                // Unknown until the status is first checked: assume allowed rather than flash a warning.
                HealthDataType.STEPS in s.enabled && (status.availability == null || HealthDataType.STEPS in status.granted)
            }.collect { allowed -> updateState { it.copy(stepsAllowed = allowed) } }
        }
        execute { health.refresh() }
    }

    override fun intentHandler(intent: ActivityIntent): Job = execute {
        when (intent) {
            is ActivityIntent.SetRange -> range.value = intent.range
            ActivityIntent.Refresh -> health.refresh(force = true)
            ActivityIntent.OpenGoal -> updateState { it.copy(goalInput = it.stepGoal.toString(), goalInvalid = false) }
            is ActivityIntent.GoalChanged -> updateState {
                it.copy(goalInput = intent.text.filter(Char::isDigit).take(GOAL_DIGITS), goalInvalid = false)
            }
            ActivityIntent.SaveGoal -> {
                val goal = currentState.goalInput?.toIntOrNull()
                if (goal == null || goal !in HealthSettings.MIN_STEP_GOAL..HealthSettings.MAX_STEP_GOAL) {
                    updateState { it.copy(goalInvalid = true) }
                } else {
                    store.setStepGoal(goal)
                    updateState { it.copy(goalInput = null, goalInvalid = false) }
                }
            }
            ActivityIntent.DismissGoal -> updateState { it.copy(goalInput = null, goalInvalid = false) }
        }
    }

    companion object {
        private const val HISTORY_DAYS = 365
        private const val WEEKS_SHOWN = 8
        private const val GOAL_DIGITS = 6
        private const val MINUTES_PER_HOUR = 60.0

        /** The tab's statistics for [range] ending [today], from every cached day in [days]. */
        fun summarize(days: List<HealthDay>, today: LocalDate, range: ActivityRange, goal: Int): ActivitySummary {
            val from = today.minus(DatePeriod(days = range.days - 1))
            val inRange = days.filter { it.date in from..today }
            return ActivitySummary(
                today = today,
                from = from,
                daily = ActivityStats.dailySteps(days, from, today),
                todaySteps = days.firstOrNull { it.date == today }?.steps,
                sevenDayAverage = ActivityStats.sevenDayAverage(days, today),
                best = ActivityStats.best(days, from, today),
                currentStreak = ActivityStats.currentStreak(days, today, goal),
                longestStreak = ActivityStats.longestStreak(days, goal),
                daysAtGoal = ActivityStats.daysAtGoal(days, from, today, goal),
                // Weeks before the first day read are left out rather than shown empty.
                weekly = ActivityStats.weeklyTotals(days, today, WEEKS_SHOWN).dropLastWhile { it.daysWithData == 0 },
                activeKcal = ActivityStats.series(inRange) { it.activeKcal },
                totalKcal = ActivityStats.series(inRange) { it.totalKcal },
                restingHeartRate = ActivityStats.series(inRange) { it.restingHeartRate },
                restingHeartRateAverage = ActivityStats.averageBefore(days, today) { it.restingHeartRate },
                sleepHours = ActivityStats.series(inRange) { d -> d.sleepMinutes?.let { it / MINUTES_PER_HOUR } },
                sleepHoursAverage = ActivityStats.averageBefore(days, today) { it.sleepMinutes }?.div(MINUTES_PER_HOUR),
            )
        }
    }
}
