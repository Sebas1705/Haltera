package es.sebas1705.ironlog.feature.progress.ui.volume

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.feature.progress.model.ExerciseLookup
import es.sebas1705.ironlog.feature.progress.model.MuscleVolume
import es.sebas1705.ironlog.feature.progress.model.ProgressClock
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.startOfDayMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal data class MuscleVolumeState(
    val loading: Boolean = true,
    /** Mondays of the weeks shown, oldest first. */
    val weekStarts: List<LocalDate> = emptyList(),
    val muscles: List<MuscleVolume> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState {
    val maxWeekVolume: Long get() = muscles.maxOfOrNull { m -> m.weeks.maxOrNull() ?: 0 } ?: 0
}

/** The screen only reads: it has no intents. */
internal sealed interface MuscleVolumeIntent : MVIBaseIntent

/** Weekly volume per primary muscle over the last four weeks. */
internal class MuscleVolumeViewModel(
    private val sessions: SessionRepository,
    exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
) : MVIBaseViewModel<MuscleVolumeState, MuscleVolumeIntent>(MuscleVolumeState()) {

    private val lookup = ExerciseLookup(exercises)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: MuscleVolumeIntent): Job = execute { }

    private suspend fun observe() {
        val timeZone = clock.timeZone
        val weekStarts = ProgressMath.lastWeekStarts(clock.today())
        val from = weekStarts.first().startOfDayMillis(timeZone)
        val to = weekStarts.last().plus(1, DateTimeUnit.WEEK).startOfDayMillis(timeZone)
        combine(sessions.observeSessionsBetween(from, to), settings.settings) { list, userSettings ->
            val variants = lookup.resolve(list.flatMap { s -> s.exercises.map { it.variantId } }.distinct())
            MuscleVolumeState(
                loading = false,
                weekStarts = weekStarts,
                muscles = ProgressMath.weeklyMuscleVolume(
                    sessions = list,
                    musclesByVariant = variants.mapValues { it.value.primaryMuscles },
                    weekStarts = weekStarts,
                    timeZone = timeZone,
                ),
                unit = userSettings.unit,
            )
        }.collect { next -> updateState { next } }
    }
}
