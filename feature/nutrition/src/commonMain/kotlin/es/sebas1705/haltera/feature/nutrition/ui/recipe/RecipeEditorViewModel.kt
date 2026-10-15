package es.sebas1705.ironlog.feature.nutrition.ui.recipe

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodInput
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.models.RecipeItemInput
import es.sebas1705.ironlog.data.models.RecipeMath
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.model.attempt
import kotlinx.coroutines.Job

/** A recipe to edit, or null for a new one. */
internal data class RecipeArgs(val recipeId: String?)

internal data class RecipeRow(val foodId: String, val food: Food?, val gramsText: String) {
    val grams: Double? get() = Amounts.parse(gramsText)
}

internal data class RecipeEditorState(
    val loading: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val servingName: String = "",
    val servingGramsText: String = "",
    val yieldText: String = "",
    val rows: List<RecipeRow> = emptyList(),
    val showErrors: Boolean = false,
    /** Saved (the recipe id) or deleted (null id). */
    val finished: Finished? = null,
    val error: Boolean = false,
) : MVIBaseState {
    data class Finished(val recipeId: String?)

    val itemsWeight: Double get() = rows.sumOf { it.grams ?: 0.0 }

    val yieldGrams: Double? get() = Amounts.parse(yieldText)

    val totals: Nutrients get() = rows.fold(
        Nutrients.ZERO
    ) { sum, r -> sum + (r.food?.nutrientsFor(r.grams ?: 0.0) ?: Nutrients.ZERO) }

    val per100: Nutrients get() = RecipeMath.per100(rows.map { it.food to (it.grams ?: 0.0) }, yieldGrams)

    val nameError: Boolean get() = name.isBlank()
    val itemsError: Boolean get() = rows.isEmpty() || rows.any { it.grams == null }
    val yieldError: Boolean get() = yieldText.isNotBlank() && yieldGrams == null
    val servingError: Boolean
        get() = (servingName.isNotBlank() && Amounts.parse(servingGramsText) == null) ||
            (servingName.isBlank() && servingGramsText.isNotBlank())
    val valid: Boolean get() = !nameError && !itemsError && !yieldError && !servingError
}

internal sealed interface RecipeEditorIntent : MVIBaseIntent {
    data class NameChanged(val text: String) : RecipeEditorIntent

    data class ServingNameChanged(val text: String) : RecipeEditorIntent

    data class ServingGramsChanged(val text: String) : RecipeEditorIntent

    data class YieldChanged(val text: String) : RecipeEditorIntent

    data class AddItem(val foodId: String, val grams: Double) : RecipeEditorIntent

    data class ItemGramsChanged(val index: Int, val text: String) : RecipeEditorIntent

    data class RemoveItem(val index: Int) : RecipeEditorIntent

    data object Save : RecipeEditorIntent

    data object Delete : RecipeEditorIntent

    data object ErrorShown : RecipeEditorIntent
}

/** Recipe editor: ingredients with amounts and an optional cooked weight (design §1). */
internal class RecipeEditorViewModel(
    private val recipeId: String?,
    private val repository: NutritionRepository,
) : MVIBaseViewModel<RecipeEditorState, RecipeEditorIntent>(
    RecipeEditorState(loading = recipeId != null, isNew = recipeId == null)
) {
    init {
        if (recipeId != null) execute { load(recipeId) }
    }

    private suspend fun load(id: String) {
        val recipe = attempt { repository.getRecipe(id) }.getOrNull()
        updateState { state ->
            if (recipe == null) {
                state.copy(loading = false, error = true)
            } else {
                state.copy(
                    loading = false,
                    name = recipe.food.nameEn,
                    servingName = (recipe.food.servingNameEn ?: recipe.food.servingNameEs).orEmpty(),
                    servingGramsText = recipe.food.servingGrams?.let { Amounts.input(it, 1) }.orEmpty(),
                    yieldText = recipe.food.yieldGrams?.let { Amounts.input(it, 1) }.orEmpty(),
                    rows = recipe.items.map { RecipeRow(it.foodId, it.food, Amounts.input(it.grams, 1)) },
                )
            }
        }
    }

    override fun intentHandler(intent: RecipeEditorIntent): Job = execute {
        when (intent) {
            is RecipeEditorIntent.NameChanged -> updateState { it.copy(name = intent.text) }
            is RecipeEditorIntent.ServingNameChanged -> updateState { it.copy(servingName = intent.text) }
            is RecipeEditorIntent.ServingGramsChanged -> updateState { it.copy(servingGramsText = intent.text) }
            is RecipeEditorIntent.YieldChanged -> updateState { it.copy(yieldText = intent.text) }
            is RecipeEditorIntent.AddItem -> {
                val food = attempt { repository.getFood(intent.foodId) }.getOrNull()
                updateState {
                    it.copy(
                        rows = it.rows + RecipeRow(intent.foodId, food, Amounts.input(intent.grams, 1))
                    )
                }
            }
            is RecipeEditorIntent.ItemGramsChanged -> updateState { s ->
                s.copy(
                    rows = s.rows.mapIndexed { i, r -> if (i == intent.index) r.copy(gramsText = intent.text) else r }
                )
            }
            is RecipeEditorIntent.RemoveItem -> updateState { s ->
                s.copy(
                    rows = s.rows.filterIndexed { i, _ -> i != intent.index }
                )
            }
            RecipeEditorIntent.Save -> save()
            RecipeEditorIntent.Delete -> delete()
            RecipeEditorIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun save() {
        val state = currentState
        if (!state.valid) {
            updateState { it.copy(showErrors = true) }
            return
        }
        val header = FoodInput(
            nameEn = state.name.trim(),
            nameEs = state.name.trim(),
            servingName = state.servingName.trim().ifBlank { null },
            servingGrams = Amounts.parse(state.servingGramsText),
        )
        val items = state.rows.map { RecipeItemInput(it.foodId, it.grams ?: 0.0) }
        attempt { repository.saveRecipe(recipeId, header, state.yieldGrams, items) }
            .onSuccess { id -> updateState { it.copy(finished = RecipeEditorState.Finished(id)) } }
            .onFailure { updateState { it.copy(error = true) } }
    }

    private suspend fun delete() {
        val id = recipeId ?: return
        attempt { repository.deleteFood(id) }
            .onSuccess { updateState { it.copy(finished = RecipeEditorState.Finished(null)) } }
            .onFailure { updateState { it.copy(error = true) } }
    }
}
