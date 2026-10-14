package es.sebas1705.ironlog.feature.routines.ui.picker

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.feature.routines.model.AppLanguage
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.model.defaultSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

internal data class ExercisePickerState(
    val language: String = "en",
    val query: String = "",
    val loading: Boolean = true,
    val results: List<Exercise> = emptyList(),
    /** Exercise whose variants are shown to pick fixed or flexible. */
    val expandedId: String? = null,
    val adding: Boolean = false,
    /** One-shot: the slot just added, to open in the slot editor. */
    val addedSlotId: String? = null,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface ExercisePickerIntent : MVIBaseIntent {
    data class SetQuery(val query: String) : ExercisePickerIntent

    /** Shows or hides the variant choice of an exercise. */
    data class Toggle(val exerciseId: String) : ExercisePickerIntent

    /** Adds a slot for [exercise] aimed at [target] with sensible defaults. */
    data class Pick(val exercise: Exercise, val target: SlotTarget) : ExercisePickerIntent
    data object OpenHandled : ExercisePickerIntent
    data object ErrorShown : ExercisePickerIntent
}

/** Picks the exercise of a new slot with `ExerciseRepository.search`, then adds the slot to the day. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class ExercisePickerViewModel(
    private val dayId: String,
    private val routines: RoutineRepository,
    exercises: ExerciseRepository,
    language: AppLanguage,
) : MVIBaseViewModel<ExercisePickerState, ExercisePickerIntent>(ExercisePickerState(language = language.code())) {

    private val query = MutableStateFlow("")

    init {
        execute {
            query.debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { text ->
                    exercises.search(ExerciseFilter(query = text.trim()), currentState.language, RESULT_LIMIT)
                }
                .catch { updateState { it.copy(loading = false, error = true) } }
                .collect { list -> updateState { it.copy(loading = false, results = list) } }
        }
    }

    override fun intentHandler(intent: ExercisePickerIntent): Job = execute {
        when (intent) {
            is ExercisePickerIntent.SetQuery -> {
                updateState { it.copy(query = intent.query) }
                query.value = intent.query
            }
            is ExercisePickerIntent.Toggle -> updateState {
                it.copy(expandedId = intent.exerciseId.takeIf { id -> id != it.expandedId })
            }
            is ExercisePickerIntent.Pick -> add(intent.exercise, intent.target)
            ExercisePickerIntent.OpenHandled -> updateState { it.copy(addedSlotId = null) }
            ExercisePickerIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun add(exercise: Exercise, target: SlotTarget) {
        if (currentState.adding) return
        updateState { it.copy(adding = true) }
        attempt { routines.addSlot(dayId, defaultSpec(exercise, target)) }
            .onSuccess { id -> updateState { it.copy(adding = false, addedSlotId = id) } }
            .onFailure { updateState { it.copy(adding = false, error = true) } }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val RESULT_LIMIT = 60
    }
}
