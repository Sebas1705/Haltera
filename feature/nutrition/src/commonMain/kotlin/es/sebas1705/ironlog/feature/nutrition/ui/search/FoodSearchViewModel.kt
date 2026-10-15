package es.sebas1705.ironlog.feature.nutrition.ui.search

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.BarcodeLookup
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.nutrition.model.AppLanguage
import es.sebas1705.ironlog.feature.nutrition.model.attempt
import es.sebas1705.ironlog.feature.nutrition.scan.BarcodeScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

internal enum class FoodTab { RECENT, FAVOURITES, MINE }

/** Outcome of a scan that did not open a food. */
internal sealed interface ScanProblem {
    data class NotFound(val barcode: String) : ScanProblem

    data object Unavailable : ScanProblem
}

internal data class FoodSearchState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val searching: Boolean = false,
    val tab: FoodTab = FoodTab.RECENT,
    val recents: List<Food> = emptyList(),
    val favourites: List<Food> = emptyList(),
    val own: List<Food> = emptyList(),
    val canScan: Boolean = false,
    val scanning: Boolean = false,
    val scanProblem: ScanProblem? = null,
    /** A scanned food to open (consumed by the screen). */
    val openFoodId: String? = null,
    val error: Boolean = false,
) : MVIBaseState {
    val listed: List<Food>
        get() = if (query.isNotBlank()) {
            results
        } else {
            when (tab) {
                FoodTab.RECENT -> recents
                FoodTab.FAVOURITES -> favourites
                FoodTab.MINE -> own
            }
        }
}

internal sealed interface FoodSearchIntent : MVIBaseIntent {
    data class QueryChanged(val text: String) : FoodSearchIntent

    data class SelectTab(val tab: FoodTab) : FoodSearchIntent

    data class ToggleFavourite(val food: Food) : FoodSearchIntent

    data object Scan : FoodSearchIntent

    data object ScanProblemShown : FoodSearchIntent

    data object FoodOpened : FoodSearchIntent

    data object ErrorShown : FoodSearchIntent
}

/** Food search, recents, favourites, own foods and barcode scan (design §1). */
internal class FoodSearchViewModel(
    private val repository: NutritionRepository,
    private val language: AppLanguage,
    private val scanner: BarcodeScanner,
) : MVIBaseViewModel<FoodSearchState, FoodSearchIntent>(FoodSearchState(canScan = scanner.isAvailable)) {
    private var searchJob: Job? = null

    init {
        execute {
            combine(
                repository.observeRecents(),
                repository.observeFavourites(),
                repository.observeOwnFoods()
            ) { r, f, o ->
                Triple(r, f, o)
            }.collect { (recents, favourites, own) ->
                updateState { it.copy(recents = recents, favourites = favourites, own = own) }
            }
        }
    }

    override fun intentHandler(intent: FoodSearchIntent): Job = when (intent) {
        is FoodSearchIntent.QueryChanged -> search(intent.text)
        else -> execute { handle(intent) }
    }

    private suspend fun handle(intent: FoodSearchIntent) {
        when (intent) {
            is FoodSearchIntent.SelectTab -> updateState { it.copy(tab = intent.tab) }
            is FoodSearchIntent.ToggleFavourite -> {
                attempt { repository.setFavourite(intent.food.id, !intent.food.favourite) }
                    .onFailure { updateState { s -> s.copy(error = true) } }
                // Results are a one-off search: refresh the flag in place.
                updateState { s ->
                    s.copy(
                        results = s.results.map {
                            if (it.id == intent.food.id) {
                                it.copy(
                                    favourite = !it.favourite
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }
            FoodSearchIntent.Scan -> scan()
            FoodSearchIntent.ScanProblemShown -> updateState { it.copy(scanProblem = null) }
            FoodSearchIntent.FoodOpened -> updateState { it.copy(openFoodId = null) }
            FoodSearchIntent.ErrorShown -> updateState { it.copy(error = false) }
            is FoodSearchIntent.QueryChanged -> Unit
        }
    }

    private fun search(text: String): Job {
        updateState { it.copy(query = text, searching = text.isNotBlank()) }
        searchJob?.cancel()
        return execute {
            if (text.isBlank()) {
                updateState { it.copy(results = emptyList(), searching = false) }
                return@execute
            }
            delay(DEBOUNCE_MILLIS)
            val found = attempt { repository.search(text, language.code()) }
            updateState {
                if (it.query != text) {
                    it
                } else {
                    it.copy(
                        results = found.getOrDefault(emptyList()),
                        searching = false,
                        error = found.isFailure
                    )
                }
            }
        }.also { searchJob = it }
    }

    private suspend fun scan() {
        if (!scanner.isAvailable || currentState.scanning) return
        val code = scanner.scan()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        updateState { it.copy(scanning = true) }
        val result = attempt { repository.lookupBarcode(code) }.getOrDefault(BarcodeLookup.Unavailable)
        updateState {
            when (result) {
                is BarcodeLookup.Found -> it.copy(scanning = false, openFoodId = result.food.id)
                BarcodeLookup.NotFound -> it.copy(scanning = false, scanProblem = ScanProblem.NotFound(code))
                BarcodeLookup.Unavailable -> it.copy(scanning = false, scanProblem = ScanProblem.Unavailable)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
