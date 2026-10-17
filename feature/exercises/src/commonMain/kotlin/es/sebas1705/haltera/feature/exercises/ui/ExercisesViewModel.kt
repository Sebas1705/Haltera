package es.sebas1705.haltera.feature.exercises.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.repository.ExerciseRepository
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
import kotlinx.coroutines.flow.filterNotNull
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
    /** The user's soft-deleted custom exercises (core design §3.1.1), sorted by name; empty hides the chip. */
    val hidden: List<ExerciseRow> = emptyList(),
    /** The "Hidden" chip is selected: the list shows [hidden] with Restore instead of the search results. */
    val hiddenSelected: Boolean = false,
    /** A restore failed (shown once). */
    val restoreFailed: Boolean = false,
) : MVIBaseState {
    val hasFilters: Boolean get() = query.isNotBlank() || category != null || muscle != null || equipment != null

    /** The hidden list is on screen: the chip is selected and something is hidden. */
    val showingHidden: Boolean get() = hiddenSelected && hidden.isNotEmpty()
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

    /** Shows or leaves the list of hidden (soft-deleted) custom exercises. */
    data object ToggleHidden : ExercisesIntent

    /** Restores the hidden custom exercise [id]: back in the list, search and pickers at once. */
    data class Restore(val id: String) : ExercisesIntent
    data object RestoreFailedShown : ExercisesIntent
}

/**
 * Exercise list with search and filter chips (spec §7 Exercises). The query, the chips and the "Hidden" chip are
 * kept in [savedState], so they survive process death (Koin passes the navigation entry's handle).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExercisesViewModel(
    private val exercises: ExerciseRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : MVIBaseViewModel<ExercisesState, ExercisesIntent>(ExercisesFilters.restore(savedState)) {

    private data class Params(val filter: ExerciseFilter, val language: String?)

    private val params = MutableStateFlow(Params(currentState.toFilter(), language = null))
    private var searchJob: Job? = null

    init {
        startSearch()
        observeHidden()
        saveFilters()
    }

    override fun intentHandler(intent: ExercisesIntent): Job = execute {
        when (intent) {
            is ExercisesIntent.SetLanguage -> params.update { it.copy(language = intent.language) }
            is ExercisesIntent.QueryChanged -> {
                updateState { it.copy(query = intent.query, hiddenSelected = false) }
                updateFilter { it.copy(query = intent.query) }
            }
            is ExercisesIntent.CategorySelected -> {
                updateState { it.copy(category = intent.category, hiddenSelected = false) }
                updateFilter { it.copy(category = intent.category) }
            }
            is ExercisesIntent.MuscleSelected -> {
                updateState { it.copy(muscle = intent.muscle, hiddenSelected = false) }
                updateFilter { it.copy(muscle = intent.muscle) }
            }
            is ExercisesIntent.EquipmentSelected -> {
                updateState { it.copy(equipment = intent.equipment, hiddenSelected = false) }
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
            ExercisesIntent.ToggleHidden -> {
                val show = !currentState.showingHidden
                // The hidden list ignores the search and the chips, so they are cleared rather than left misleading.
                updateState { it.copy(hiddenSelected = show) }
                if (show && currentState.hasFilters) {
                    updateState { it.copy(query = "", category = null, muscle = null, equipment = null) }
                    updateFilter { ExerciseFilter() }
                }
            }
            is ExercisesIntent.Restore -> restore(intent.id)
            ExercisesIntent.RestoreFailedShown -> updateState { it.copy(restoreFailed = false) }
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

    private suspend fun restore(id: String) {
        val ok = runCatching { exercises.restoreCustom(id) }
            .onFailure { if (it is CancellationException) throw it }
            .isSuccess
        if (!ok) updateState { it.copy(restoreFailed = true) }
    }

    /** Hidden custom exercises in the app language; the chip disappears with the last one restored. */
    private fun observeHidden() = execute {
        params.map { it.language }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { language ->
                exercises.observeHiddenCustom()
                    .map { list -> list.map { it.toRow(language) }.sortedBy { it.name.lowercase() } }
                    .flowOn(dispatcher)
            }
            .catch { e -> if (e is CancellationException) throw e }
            .collect { rows ->
                updateState { it.copy(hidden = rows, hiddenSelected = it.hiddenSelected && rows.isNotEmpty()) }
            }
    }

    /** Writes the filters to [savedState] whenever they change (the reducers stay free of side effects). */
    private fun saveFilters() = execute {
        uiState.map { ExercisesFilters.of(it) }
            .distinctUntilChanged()
            .collect { it.writeTo(savedState) }
    }

    companion object {
        const val QUERY_DEBOUNCE_MS = 250L

        /** Above the catalogue size (~800) so the whole catalogue is listed. */
        const val LIST_LIMIT = 5_000
    }
}

private fun ExerciseFilter.normalized() = copy(query = query.trim())

private fun ExercisesState.toFilter() =
    ExerciseFilter(query = query, category = category, muscle = muscle, equipment = equipment)

/** The part of [ExercisesState] that survives process death, stored as plain strings in a [SavedStateHandle]. */
internal data class ExercisesFilters(
    val query: String = "",
    val category: ExerciseCategory? = null,
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
    val hiddenSelected: Boolean = false,
) {
    fun writeTo(handle: SavedStateHandle) {
        handle[KEY_QUERY] = query
        handle[KEY_CATEGORY] = category?.name
        handle[KEY_MUSCLE] = muscle?.name
        handle[KEY_EQUIPMENT] = equipment?.name
        handle[KEY_HIDDEN] = hiddenSelected
    }

    companion object {
        private const val KEY_QUERY = "exercises.query"
        private const val KEY_CATEGORY = "exercises.category"
        private const val KEY_MUSCLE = "exercises.muscle"
        private const val KEY_EQUIPMENT = "exercises.equipment"
        private const val KEY_HIDDEN = "exercises.hidden"

        fun of(state: ExercisesState) =
            ExercisesFilters(state.query, state.category, state.muscle, state.equipment, state.hiddenSelected)

        /**
         * The initial state with the saved filters; values of an unexpected type or unknown enum names (another
         * build) are dropped. "Hidden" stays selected only while something is hidden: the hidden list clears it when
         * it loads empty.
         */
        fun restore(handle: SavedStateHandle) = ExercisesState(
            query = handle.string(KEY_QUERY).orEmpty(),
            category = handle.string(KEY_CATEGORY)?.let { n -> ExerciseCategory.entries.firstOrNull { it.name == n } },
            muscle = handle.string(KEY_MUSCLE)?.let { n -> Muscle.entries.firstOrNull { it.name == n } },
            equipment = handle.string(KEY_EQUIPMENT)?.let { n -> Equipment.entries.firstOrNull { it.name == n } },
            hiddenSelected = handle.get<Any?>(KEY_HIDDEN) as? Boolean ?: false,
        )

        private fun SavedStateHandle.string(key: String): String? = get<Any?>(key) as? String
    }
}

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
