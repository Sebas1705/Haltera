package es.sebas1705.haltera.feature.nutrition.ui.editor

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.Food
import es.sebas1705.haltera.data.models.FoodInput
import es.sebas1705.haltera.data.models.FoodUnit
import es.sebas1705.haltera.data.models.Nutrients
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.feature.nutrition.model.Amounts
import es.sebas1705.haltera.feature.nutrition.model.ParsedValue
import es.sebas1705.haltera.feature.nutrition.model.attempt
import kotlinx.coroutines.Job

/** A food to edit, or a new one (optionally with a scanned barcode). */
internal data class FoodEditorArgs(val foodId: String?, val barcode: String?)

internal enum class FoodField { NAME, KCAL, PROTEIN, CARBS, FAT, FIBER, SUGAR, SALT, SERVING_NAME, SERVING_GRAMS }

internal data class FoodEditorState(
    val loading: Boolean = false,
    val isNew: Boolean = true,
    val values: Map<FoodField, String> = emptyMap(),
    val brand: String = "",
    val barcode: String = "",
    val unit: FoodUnit = FoodUnit.G,
    val showErrors: Boolean = false,
    /** Set when saved or deleted: the id of the saved food (null after a delete). */
    val finished: Finished? = null,
    val error: Boolean = false,
) : MVIBaseState {
    data class Finished(val foodId: String?, val wasHidden: Boolean = false)

    fun value(field: FoodField): String = values[field].orEmpty()

    val errors: Set<FoodField>
        get() = buildSet {
            if (value(FoodField.NAME).isBlank()) add(FoodField.NAME)
            listOf(FoodField.KCAL, FoodField.PROTEIN, FoodField.CARBS, FoodField.FAT).forEach {
                if (Amounts.parseOptional(value(it)) !is ParsedValue.Value) add(it)
            }
            listOf(FoodField.FIBER, FoodField.SUGAR, FoodField.SALT).forEach {
                if (Amounts.parseOptional(value(it)) == ParsedValue.Invalid) add(it)
            }
            // A serving needs both its name and its size, or neither.
            val servingName = value(FoodField.SERVING_NAME).isNotBlank()
            val servingGrams = value(FoodField.SERVING_GRAMS)
            if (servingGrams.isNotBlank() && Amounts.parse(servingGrams) == null) add(FoodField.SERVING_GRAMS)
            if (servingName && servingGrams.isBlank()) add(FoodField.SERVING_GRAMS)
            if (!servingName && servingGrams.isNotBlank()) add(FoodField.SERVING_NAME)
        }
}

internal sealed interface FoodEditorIntent : MVIBaseIntent {
    data class Changed(val field: FoodField, val text: String) : FoodEditorIntent

    data class BrandChanged(val text: String) : FoodEditorIntent

    data class BarcodeChanged(val text: String) : FoodEditorIntent

    data class UnitChanged(val unit: FoodUnit) : FoodEditorIntent

    data object Save : FoodEditorIntent

    data object Delete : FoodEditorIntent

    data object ErrorShown : FoodEditorIntent
}

/** Custom food editor: values per 100 g (or 100 ml) as on a label (design §4). */
internal class FoodEditorViewModel(
    private val foodId: String?,
    barcode: String?,
    private val repository: NutritionRepository,
) : MVIBaseViewModel<FoodEditorState, FoodEditorIntent>(
    FoodEditorState(loading = foodId != null, isNew = foodId == null, barcode = barcode.orEmpty()),
) {
    init {
        if (foodId != null) execute { load(foodId) }
    }

    private suspend fun load(id: String) {
        val food = attempt { repository.getFood(id) }.getOrNull()
        updateState { if (food == null) it.copy(loading = false, error = true) else fill(it, food) }
    }

    private fun fill(state: FoodEditorState, food: Food): FoodEditorState {
        fun f(v: Double?) = v?.let { Amounts.input(it) }.orEmpty()
        val n = food.per100
        return state.copy(
            loading = false,
            values = mapOf(
                FoodField.NAME to food.nameEn,
                FoodField.KCAL to f(n.kcal),
                FoodField.PROTEIN to f(n.protein),
                FoodField.CARBS to f(n.carbs),
                FoodField.FAT to f(n.fat),
                FoodField.FIBER to f(n.fiber),
                FoodField.SUGAR to f(n.sugar),
                FoodField.SALT to f(n.salt),
                FoodField.SERVING_NAME to (food.servingNameEn ?: food.servingNameEs).orEmpty(),
                FoodField.SERVING_GRAMS to f(food.servingGrams),
            ),
            brand = food.brand.orEmpty(),
            barcode = food.barcode.orEmpty(),
            unit = food.unit,
        )
    }

    override fun intentHandler(intent: FoodEditorIntent): Job = execute {
        when (intent) {
            is FoodEditorIntent.Changed -> updateState { it.copy(values = it.values + (intent.field to intent.text)) }
            is FoodEditorIntent.BrandChanged -> updateState { it.copy(brand = intent.text) }
            is FoodEditorIntent.BarcodeChanged -> updateState { it.copy(barcode = intent.text.filter(Char::isDigit)) }
            is FoodEditorIntent.UnitChanged -> updateState { it.copy(unit = intent.unit) }
            FoodEditorIntent.Save -> save()
            FoodEditorIntent.Delete -> delete()
            FoodEditorIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun save() {
        val state = currentState
        if (state.errors.isNotEmpty()) {
            updateState { it.copy(showErrors = true) }
            return
        }
        val input = state.toInput()
        attempt {
            if (foodId == null) repository.createFood(input).id else foodId.also { repository.updateFood(it, input) }
        }.onSuccess { id -> updateState { it.copy(finished = FoodEditorState.Finished(id)) } }
            .onFailure { updateState { it.copy(error = true) } }
    }

    private suspend fun delete() {
        val id = foodId ?: return
        attempt { repository.deleteFood(id) }
            .onSuccess { deleted ->
                updateState {
                    it.copy(
                        finished = FoodEditorState.Finished(null, wasHidden = !deleted)
                    )
                }
            }
            .onFailure { updateState { it.copy(error = true) } }
    }

    private fun FoodEditorState.toInput(): FoodInput {
        fun required(field: FoodField) = (Amounts.parseOptional(value(field)) as ParsedValue.Value).value
        fun optional(field: FoodField) = (Amounts.parseOptional(value(field)) as? ParsedValue.Value)?.value
        val name = value(FoodField.NAME).trim()
        return FoodInput(
            nameEn = name,
            nameEs = name,
            brand = brand.trim().ifBlank { null },
            barcode = barcode.trim().ifBlank { null },
            per100 = Nutrients(
                kcal = required(FoodField.KCAL),
                protein = required(FoodField.PROTEIN),
                carbs = required(FoodField.CARBS),
                fat = required(FoodField.FAT),
                fiber = optional(FoodField.FIBER),
                sugar = optional(FoodField.SUGAR),
                salt = optional(FoodField.SALT),
            ),
            unit = unit,
            servingName = value(FoodField.SERVING_NAME).trim().ifBlank { null },
            servingGrams = Amounts.parse(value(FoodField.SERVING_GRAMS)),
        )
    }
}
