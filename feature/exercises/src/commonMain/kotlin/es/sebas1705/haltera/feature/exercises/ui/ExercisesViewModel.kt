package es.sebas1705.ironlog.feature.exercises.ui

import androidx.compose.runtime.Immutable
import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** One row of the exercise list, already localised so the list does no work while scrolling. */
@Immutable
data class ExerciseRow(
    val id: String,
    val name: String,
    val mainMuscle: Muscle?,
    /** Distinct equipment of the active variants, in variant order. */
    val equipment: List<Equipment>,
    val custom: Boolean,
)

@Immutable
data class ExercisesState(
    val query: String = "",
    val category: ExerciseCategory? = null,
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val items: List<ExerciseRow> = emptyList(),
) : MVIBaseState {
    val hasFilters: Boolean get() = query.isNotBlank() || category != null || muscle != null || equipment != null
}

sealed interface ExercisesIntent : MVIBaseIntent {
    /** The app language ("en"/"es"); names are shown and sorted in it. The list loads once it is set. */
    data class SetLanguage(val language: String) : ExercisesIntent
    data class QueryChanged(val query: String) : ExercisesIntent
    data class CategorySelected(val category: ExerciseCategory?) : ExercisesIntent
    data class MuscleSelected(val muscle: Muscle?) : ExercisesIntent
    data class EquipmentSelected(val equipment: Equipment?) : ExercisesIntent
    data object ClearFilters : ExercisesIntent
    data object Retry : ExercisesIntent
}

/** Exercise list with search and filter chips (spec §7 Exercises). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExercisesViewModel(
    private val exercises: ExerciseRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MVIBaseViewModel<ExercisesState, ExercisesIntent>(ExercisesState()) {

    private data class Params(val filter: ExerciseFilter, val language: String?)

    private val params = MutableStateFlow(Params(ExerciseFilter(), language = null))
    private var searchJob: Job? = null

    init {
        startSearch()
    }

    override fun intentHandler(intent: ExercisesIntent): Job = execute {
        when (intent) {
            is ExercisesIntent.SetLanguage -> params.update { it.copy(language = intent.language) }
            is ExercisesIntent.QueryChanged -> {
                updateState { it.copy(query = intent.query) }
                updateFilter { it.copy(query = intent.query) }
            }
            is ExercisesIntent.CategorySelected -> {
                updateState { it.copy(category = intent.category) }
                updateFilter { it.copy(category = intent.category) }
            }
            is ExercisesIntent.MuscleSelected -> {
                updateState { it.copy(muscle = intent.muscle) }
                updateFilter { it.copy(muscle = intent.muscle) }
            }
            is ExercisesIntent.EquipmentSelected -> {
                updateState { it.copy(equipment = intent.equipment) }
                updateFilter { it.copy(equipment = intent.equipment) }
            }
            ExercisesIntent.ClearFilters -> {
                updateState { it.copy(query = "", category = null, muscle = null, equipment = null) }
                updateFilter { ExerciseFilter() }
            }
            ExercisesIntent.Retry -> {
                updateState { it.copy(loading = true, error = false) }
                startSearch()
            }
        }
    }

    private fun updateFilter(change: (ExerciseFilter) -> ExerciseFilter) =
        params.update { it.copy(filter = change(it.filter)) }

    private fun startSearch() {
        searchJob?.cancel()
        searchJob = execute {
            params
                // Typing is debounced; clearing the query, chips and language apply at once.
                .debounce { if (it.filter.query.isBlank()) 0L else QUERY_DEBOUNCE_MS }
                .distinctUntilChanged()
                .flatMapLatest { p ->
                    val language = p.language ?: return@flatMapLatest emptyFlow()
                    exercises.search(p.filter.normalized(), language, LIST_LIMIT)
                        .map { list -> list.map { it.toRow(language) } }
                        .flowOn(dispatcher)
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    updateState { it.copy(loading = false, error = true) }
                }
                .collect { rows -> updateState { it.copy(loading = false, error = false, items = rows) } }
        }
    }

    companion object {
        const val QUERY_DEBOUNCE_MS = 250L

        /** Above the catalogue size (~800) so the whole catalogue is listed. */
        const val LIST_LIMIT = 5_000
    }
}

private fun ExerciseFilter.normalized() = copy(query = query.trim())

internal fun Exercise.toRow(language: String): ExerciseRow {
    val active = variants.filterNot { it.deprecated }.ifEmpty { variants }
    return ExerciseRow(
        id = id,
        name = name.get(language),
        mainMuscle = primaryMuscles.firstOrNull(),
        equipment = active.map { it.equipment }.distinct().filterNot { it == Equipment.NONE },
        custom = source == ExerciseSource.USER,
    )
}
