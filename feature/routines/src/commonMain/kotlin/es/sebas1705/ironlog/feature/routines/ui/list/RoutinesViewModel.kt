package es.sebas1705.ironlog.feature.routines.ui.list

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScore
import es.sebas1705.ironlog.feature.routines.model.ExerciseIndex
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.model.indexFor
import es.sebas1705.ironlog.feature.routines.model.scoreRoutine
import es.sebas1705.ironlog.feature.routines.model.targetsOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch

internal data class RoutinesState(
    val loading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    /** Advice-only score per routine id, filled once their exercises are loaded. */
    val scores: Map<String, RoutineScore> = emptyMap(),
    /** Routine waiting for the delete confirmation. */
    val pendingDelete: Routine? = null,
    /** One-shot: a routine just created, to open in the editor. */
    val openRoutineId: String? = null,
    val creating: Boolean = false,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface RoutinesIntent : MVIBaseIntent {
    data class SetActive(val routineId: String, val active: Boolean) : RoutinesIntent
    data class RequestDelete(val routine: Routine) : RoutinesIntent
    data object ConfirmDelete : RoutinesIntent
    data object DismissDelete : RoutinesIntent

    /** Creates a routine called [name] with one training day called [firstDayName], then opens it. */
    data class Create(val name: String, val firstDayName: String) : RoutinesIntent
    data object OpenHandled : RoutinesIntent
    data object ErrorShown : RoutinesIntent
}

/** My routines: list, active routine, delete with confirmation and creation. */
internal class RoutinesViewModel(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
) : MVIBaseViewModel<RoutinesState, RoutinesIntent>(RoutinesState()) {

    /** Exercises of every routine, kept across emissions so only new ones are loaded. */
    private var index = ExerciseIndex()

    init {
        execute {
            routines.observeRoutines()
                .catch { updateState { it.copy(loading = false, error = true) } }
                .collect { list ->
                    updateState { it.copy(loading = false, routines = list) }
                    score(list)
                }
        }
    }

    override fun intentHandler(intent: RoutinesIntent): Job = execute {
        when (intent) {
            is RoutinesIntent.SetActive -> safely { routines.setActive(intent.routineId.takeIf { intent.active }) }
            is RoutinesIntent.RequestDelete -> updateState { it.copy(pendingDelete = intent.routine) }
            RoutinesIntent.DismissDelete -> updateState { it.copy(pendingDelete = null) }
            RoutinesIntent.ConfirmDelete -> {
                val routine = currentState.pendingDelete ?: return@execute
                updateState { it.copy(pendingDelete = null) }
                safely { routines.deleteRoutine(routine.id) }
            }
            is RoutinesIntent.Create -> create(intent.name, intent.firstDayName)
            RoutinesIntent.OpenHandled -> updateState { it.copy(openRoutineId = null) }
            RoutinesIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun score(list: List<Routine>) {
        attempt { exercises.indexFor(targetsOf(list), index) }.onSuccess { loaded ->
            index = loaded
            val scores = list.associate { it.id to scoreRoutine(it, loaded) }
            updateState { it.copy(scores = scores) }
        }
    }

    private suspend fun create(name: String, firstDayName: String) {
        if (currentState.creating) return
        updateState { it.copy(creating = true) }
        val id = safely { routines.createRoutine(name).also { routines.addDay(it, firstDayName) } }
        updateState { it.copy(creating = false, openRoutineId = id) }
    }

    private suspend inline fun <T> safely(block: () -> T): T? =
        attempt(block).onFailure { updateState { it.copy(error = true) } }.getOrNull()
}
