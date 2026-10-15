package es.sebas1705.haltera.feature.progress.ui.muscles

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.domain.usecases.stats.Balance
import es.sebas1705.haltera.domain.usecases.stats.DayWindow
import es.sebas1705.haltera.domain.usecases.stats.MuscleComparison
import es.sebas1705.haltera.domain.usecases.stats.MuscleStats
import es.sebas1705.haltera.domain.usecases.stats.Periods
import es.sebas1705.haltera.domain.usecases.stats.StatsPeriod
import es.sebas1705.haltera.feature.progress.model.ExerciseLookup
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.model.StatsPeriodSelection
import es.sebas1705.haltera.feature.progress.model.bilateralSlots
import es.sebas1705.haltera.feature.progress.model.dated
import es.sebas1705.haltera.feature.progress.model.toEpochDay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

internal data class MusclesState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
    val current: DayWindow? = null,
    val previous: DayWindow? = null,
    /** Muscles trained in either period, by this period's weekly sets. */
    val muscles: List<MuscleComparison> = emptyList(),
    val balance: List<Balance> = emptyList(),
    /** The same ratios in the previous period (empty for "all"). */
    val previousBalance: List<Balance> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState {
    val maxSets: Double get() = muscles.maxOfOrNull { maxOf(it.setsPerWeek.current, it.setsPerWeek.previous ?: 0.0) } ?: 0.0
    val maxVolume: Double get() = muscles.maxOfOrNull {
        maxOf(it.volumePerWeek.current, it.volumePerWeek.previous ?: 0.0)
    } ?: 0.0
}

internal sealed interface MusclesIntent : MVIBaseIntent {
    data class SelectPeriod(val period: StatsPeriod) : MusclesIntent
}

/** Muscles tab: weekly effective sets and volume per primary muscle against the previous period, and balance ratios. */
internal class MusclesViewModel(
    private val sessions: SessionRepository,
    exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
    private val selection: StatsPeriodSelection,
) : MVIBaseViewModel<MusclesState, MusclesIntent>(MusclesState()) {

    private val lookup = ExerciseLookup(exercises)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: MusclesIntent): Job = execute {
        when (intent) {
            is MusclesIntent.SelectPeriod -> selection.select(intent.period)
        }
    }

    private suspend fun observe() {
        combine(
            sessions.observeSessionsBetween(0, Long.MAX_VALUE),
            routines.observeRoutines(),
            selection.period,
            settings.settings,
        ) { list, allRoutines, period, userSettings ->
            val dated = list.filter { !it.inProgress }.dated(clock.timeZone)
            val infos = lookup.resolve(dated.flatMap { d -> d.session.exercises.map { it.variantId } }.distinct())
            val muscles = infos.mapValues { it.value.primaryMuscles }
            val today = clock.today().toEpochDay()
            val current = Periods.current(period, today, dated.minOfOrNull { it.day })
            val previous = Periods.previous(period, current)
            MusclesState(
                loading = false,
                period = period,
                current = current,
                previous = previous,
                muscles = MuscleStats.compare(dated, muscles, current, previous, allRoutines.bilateralSlots()),
                balance = MuscleStats.balance(dated, muscles, current),
                previousBalance = previous?.let { MuscleStats.balance(dated, muscles, it) }.orEmpty(),
                unit = userSettings.unit,
            )
        }.collect { next -> updateState { next } }
    }
}
