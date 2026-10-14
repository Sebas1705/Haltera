package es.sebas1705.ironlog.feature.progress.ui.session

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.stats.Stats
import es.sebas1705.ironlog.feature.progress.model.ExerciseLookup
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.SessionSummary
import es.sebas1705.ironlog.feature.progress.model.VariantInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

/** One performed exercise of the detail. [variant] is null if the variant is no longer known. */
internal data class ExerciseDetail(
    val id: String,
    val variant: VariantInfo?,
    val notes: String,
    val sets: List<SetLog>,
    val volumeGrams: Long,
)

internal data class SessionDetailState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val summary: SessionSummary? = null,
    val notes: String = "",
    val exercises: List<ExerciseDetail> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState

/** The detail is read-only: it has no intents. */
internal sealed interface SessionDetailIntent : MVIBaseIntent

/** Read-only detail of one session with its exercises and sets. */
internal class SessionDetailViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    exercises: ExerciseRepository,
    private val settings: SettingsRepository,
) : MVIBaseViewModel<SessionDetailState, SessionDetailIntent>(SessionDetailState()) {

    private val lookup = ExerciseLookup(exercises)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: SessionDetailIntent): Job = execute { }

    private suspend fun observe() {
        combine(sessions.observeSession(sessionId), settings.settings) { session, userSettings ->
            if (session == null) {
                SessionDetailState(loading = false, notFound = true, unit = userSettings.unit)
            } else {
                val routineNames = session.routineId
                    ?.let { routines.getRoutine(it) }
                    ?.let { mapOf(it.id to it.name) }
                    .orEmpty()
                val variants = lookup.resolve(session.exercises.map { it.variantId })
                SessionDetailState(
                    loading = false,
                    summary = ProgressMath.summarize(session, routineNames),
                    notes = session.notes,
                    exercises = session.exercises.sortedBy { it.position }.map { exercise ->
                        ExerciseDetail(
                            id = exercise.id,
                            variant = variants[exercise.variantId],
                            notes = exercise.notes,
                            sets = exercise.sets.sortedBy { it.position },
                            volumeGrams = Stats.volume(exercise),
                        )
                    },
                    unit = userSettings.unit,
                )
            }
        }.collect { next -> updateState { next } }
    }
}
