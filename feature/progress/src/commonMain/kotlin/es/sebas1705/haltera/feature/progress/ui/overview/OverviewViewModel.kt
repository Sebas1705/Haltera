package es.sebas1705.haltera.feature.progress.ui.overview

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.domain.usecases.stats.Heatmap
import es.sebas1705.haltera.domain.usecases.stats.Heatmaps
import es.sebas1705.haltera.domain.usecases.stats.Overview
import es.sebas1705.haltera.domain.usecases.stats.OverviewStats
import es.sebas1705.haltera.domain.usecases.stats.StatsPeriod
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.model.StatsPeriodSelection
import es.sebas1705.haltera.feature.progress.model.dated
import es.sebas1705.haltera.feature.progress.model.toEpochDay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

internal data class OverviewState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
    val stats: OverviewStats? = null,
    val heatmap: Heatmap? = null,
    /** Whether any session was ever finished (the tab explains itself until then). */
    val hasHistory: Boolean = false,
    /** Whether a routine is active (to explain a missing adherence card). */
    val hasActiveRoutine: Boolean = false,
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState

internal sealed interface OverviewIntent : MVIBaseIntent {
    data class SelectPeriod(val period: StatsPeriod) : OverviewIntent
}

/** Overview tab: this period against the previous equal period, weekly charts and the training heatmap. */
internal class OverviewViewModel(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
    private val selection: StatsPeriodSelection,
) : MVIBaseViewModel<OverviewState, OverviewIntent>(OverviewState()) {

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: OverviewIntent): Job = execute {
        when (intent) {
            is OverviewIntent.SelectPeriod -> selection.select(intent.period)
        }
    }

    private suspend fun observe() {
        combine(
            sessions.observeSessionsBetween(0, Long.MAX_VALUE),
            routines.observeRoutines(),
            selection.period,
            settings.settings,
        ) { list, allRoutines, period, userSettings ->
            val timeZone = clock.timeZone
            val today = clock.today().toEpochDay()
            val dated = list.filter { !it.inProgress }.dated(timeZone)
            val active = allRoutines.firstOrNull { it.isActive }
            OverviewState(
                loading = false,
                period = period,
                stats = Overview.build(period, today, dated, active),
                heatmap = Heatmaps.build(dated, today, Heatmaps.weeksFor(period)),
                hasHistory = dated.isNotEmpty(),
                hasActiveRoutine = active != null,
                unit = userSettings.unit,
            )
        }.collect { next -> updateState { next } }
    }
}
