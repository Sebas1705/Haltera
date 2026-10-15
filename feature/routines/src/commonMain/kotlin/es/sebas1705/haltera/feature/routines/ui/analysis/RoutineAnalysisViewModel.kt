package es.sebas1705.ironlog.feature.routines.ui.analysis

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScore
import es.sebas1705.ironlog.domain.usecases.routinescore.toScoringRoutine
import es.sebas1705.ironlog.feature.routines.model.AppLanguage
import es.sebas1705.ironlog.feature.routines.model.ExerciseIndex
import es.sebas1705.ironlog.feature.routines.model.RoutinesClock
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.model.indexFor
import es.sebas1705.ironlog.feature.routines.model.indexForExercises
import es.sebas1705.ironlog.feature.routines.model.scoreRoutine
import es.sebas1705.ironlog.feature.routines.model.suggestedExerciseIds
import es.sebas1705.ironlog.feature.routines.model.targetsOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

internal data class RoutineAnalysisState(
    val loading: Boolean = true,
    /** The routine or template does not exist (deleted elsewhere). */
    val missing: Boolean = false,
    val language: String = "en",
    val name: String = "",
    val score: RoutineScore? = null,
    /** Exercises of the routine and of the advice, to name them. */
    val index: ExerciseIndex = ExerciseIndex(),
    val error: Boolean = false,
) : MVIBaseState {
    fun exerciseName(id: String): String = index.exercise(id)?.name?.get(language) ?: id
}

internal sealed interface RoutineAnalysisIntent : MVIBaseIntent

/**
 * Routine analysis: the [RoutineScore] of a saved routine ([routineId], with its recent sessions for adherence and
 * load progress) or of a famous-routine template ([templateId], plan only). Read-only: it never changes anything.
 */
internal class RoutineAnalysisViewModel(
    private val routineId: String?,
    private val templateId: String?,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val sessions: SessionRepository,
    language: AppLanguage,
    private val clock: RoutinesClock,
) : MVIBaseViewModel<RoutineAnalysisState, RoutineAnalysisIntent>(RoutineAnalysisState(language = language.code())) {

    init {
        execute {
            val routine = when {
                routineId != null -> routines.observeRoutine(routineId)
                else -> flowOf(
                    routines.templates().firstOrNull { it.id == templateId }?.toScoringRoutine(currentState.language)
                )
            }
            val history = if (routineId != null) sessions.observeHistory() else flowOf(emptyList())
            routine.combine(history) { r, h -> r to h }
                .catch { updateState { it.copy(loading = false, error = true) } }
                .collect { (r, h) -> onData(r, h) }
        }
    }

    private suspend fun onData(routine: Routine?, history: List<WorkoutSession>) {
        if (routine == null) {
            updateState { it.copy(loading = false, missing = true, score = null) }
            return
        }
        attempt {
            val index = exercises.indexFor(targetsOf(listOf(routine)), currentState.index)
            val now = clock.now()
            val score = scoreRoutine(routine, index, history.filter { it.startedAt >= now - SESSION_WINDOW_MS }, now)
            score to exercises.indexForExercises(suggestedExerciseIds(score), index)
        }.onSuccess { (score, index) ->
            updateState { it.copy(loading = false, missing = false, name = routine.name, score = score, index = index) }
        }.onFailure {
            updateState { it.copy(loading = false, error = true) }
        }
    }

    override fun intentHandler(intent: RoutineAnalysisIntent): Job = execute { }

    private companion object {
        /** Load progress looks back 8 weeks. */
        const val SESSION_WINDOW_MS = 56L * 86_400_000L
    }
}
