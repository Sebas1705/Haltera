package es.sebas1705.ironlog.feature.nutrition.ui.detail

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.nutrition.model.AmountMode
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.model.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** Which food, and where it is logged: a new entry, or [entryId] being edited. */
internal data class FoodDetailArgs(val foodId: String, val date: LocalDate, val meal: Meal, val entryId: String? = null)

internal data class FoodDetailState(
    val loading: Boolean = true,
    val food: Food? = null,
    val notFound: Boolean = false,
    val isEdit: Boolean = false,
    val meal: Meal,
    val date: LocalDate,
    val mode: AmountMode = AmountMode.GRAMS,
    val amountText: String = "",
    /** Done: the screen goes back to the day. */
    val finished: Boolean = false,
    val error: Boolean = false,
) : MVIBaseState {
    val grams: Double? get() = Amounts.parse(amountText)?.let { Amounts.grams(it, mode, food?.servingGrams) }

    val nutrients: Nutrients? get() = grams?.let { food?.nutrientsFor(it) }
}

internal sealed interface FoodDetailIntent : MVIBaseIntent {
    data class AmountChanged(val text: String) : FoodDetailIntent

    data class ModeChanged(val mode: AmountMode) : FoodDetailIntent

    data class MealChanged(val meal: Meal) : FoodDetailIntent

    data object ToggleFavourite : FoodDetailIntent

    data object Save : FoodDetailIntent

    data object Delete : FoodDetailIntent

    data object ErrorShown : FoodDetailIntent
}

/** A food with the amount editor (grams or servings) that logs or edits an entry (design §4). */
internal class FoodDetailViewModel(
    private val args: FoodDetailArgs,
    private val repository: NutritionRepository,
) : MVIBaseViewModel<FoodDetailState, FoodDetailIntent>(
    FoodDetailState(meal = args.meal, date = args.date, isEdit = args.entryId != null),
) {
    init {
        execute { load() }
        execute {
            // Favourite flag and edits of the food while this screen is open.
            repository.observeFood(
                args.foodId
            ).collect { food -> if (food != null) updateState { it.copy(food = food) } }
        }
    }

    private suspend fun load() {
        val food = attempt { repository.getFood(args.foodId) }.getOrNull()
        if (food == null) {
            updateState { it.copy(loading = false, notFound = true) }
            return
        }
        val entry = args.entryId?.let { id ->
            attempt { repository.observeDay(args.date).first().entries.firstOrNull { it.id == id } }.getOrNull()
        }
        val (mode, amount) = when {
            entry?.servings != null && food.hasServing -> AmountMode.SERVINGS to entry.servings
            entry != null -> AmountMode.GRAMS to entry.grams
            food.hasServing -> AmountMode.SERVINGS to 1.0
            else -> AmountMode.GRAMS to Food.PER
        }
        updateState {
            it.copy(
                loading = false,
                food = food,
                meal = entry?.meal ?: it.meal,
                mode = mode,
                amountText = Amounts.format(amount ?: Food.PER, 2),
            )
        }
    }

    override fun intentHandler(intent: FoodDetailIntent): Job = execute {
        when (intent) {
            is FoodDetailIntent.AmountChanged -> updateState { it.copy(amountText = intent.text) }
            is FoodDetailIntent.ModeChanged -> changeMode(intent.mode)
            is FoodDetailIntent.MealChanged -> updateState { it.copy(meal = intent.meal) }
            FoodDetailIntent.ToggleFavourite -> currentState.food?.let { food ->
                attempt {
                    repository.setFavourite(
                        food.id,
                        !food.favourite
                    )
                }.onFailure { updateState { it.copy(error = true) } }
            }
            FoodDetailIntent.Save -> save()
            FoodDetailIntent.Delete -> args.entryId?.let { id ->
                attempt { repository.deleteEntry(id) }
                    .onSuccess { updateState { it.copy(finished = true) } }
                    .onFailure { updateState { it.copy(error = true) } }
            }
            FoodDetailIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    /** Switching mode keeps the same quantity of food, converted. */
    private fun changeMode(mode: AmountMode) {
        updateState { state ->
            val serving = state.food?.servingGrams
            val grams = state.grams
            val text = when {
                grams == null || serving == null || serving <= 0 -> state.amountText
                mode == AmountMode.SERVINGS -> Amounts.format(grams / serving, 2)
                else -> Amounts.format(grams, 1)
            }
            state.copy(mode = mode, amountText = text)
        }
    }

    private suspend fun save() {
        val state = currentState
        val grams = state.grams ?: return
        val servings = if (state.mode == AmountMode.SERVINGS) Amounts.parse(state.amountText) else null
        val entryId = args.entryId
        attempt {
            if (entryId == null) {
                repository.addEntry(state.date, state.meal, args.foodId, grams, servings)
            } else {
                repository.updateEntry(entryId, grams, servings)
                if (state.meal != args.meal) repository.moveEntry(entryId, state.date, state.meal)
            }
        }.onSuccess { updateState { it.copy(finished = true) } }
            .onFailure { updateState { it.copy(error = true) } }
    }
}
