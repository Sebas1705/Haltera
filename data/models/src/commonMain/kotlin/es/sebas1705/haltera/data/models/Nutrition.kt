package es.sebas1705.ironlog.data.models

import kotlinx.datetime.LocalDate

// Nutrition types (docs/designs/2026-10-15-nutrition-design.md). Amounts are grams (or ml for foods measured
// in ml, taking 1 ml as 1 g); energy is kcal. Food values are per 100 g.

/** Where a food comes from: the bundled database, an Open Food Facts product, or the user. */
enum class FoodSource {
    GENERIC, OFF, USER, RECIPE;

    companion object {
        fun fromDb(value: String?): FoodSource = lenientEnum(value, USER)
    }
}

enum class FoodUnit {
    G, ML;

    companion object {
        fun fromDb(value: String?): FoodUnit = lenientEnum(value, G)
    }
}

/** The four meals of a day, in display order. */
enum class Meal {
    BREAKFAST, LUNCH, DINNER, SNACK;

    companion object {
        fun fromDb(value: String?): Meal = lenientEnum(value, SNACK)
    }
}

/** Energy and nutrients of an amount of food (or per 100 g). Optional values are unknown, not zero. */
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
) {
    /** Sum; an optional value is known when it is known on either side (unknown counts as 0 then). */
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = sumOrNull(fiber, other.fiber),
        sugar = sumOrNull(sugar, other.sugar),
        salt = sumOrNull(salt, other.salt),
    )

    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber?.times(factor),
        sugar = sugar?.times(factor),
        salt = salt?.times(factor),
    )

    companion object {
        val ZERO = Nutrients()

        private fun sumOrNull(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

data class Food(
    val id: String,
    val source: FoodSource,
    val nameEn: String,
    val nameEs: String,
    val brand: String? = null,
    val barcode: String? = null,
    /** Per 100 g (or 100 ml). */
    val per100: Nutrients,
    val unit: FoodUnit = FoodUnit.G,
    val servingNameEn: String? = null,
    val servingNameEs: String? = null,
    val servingGrams: Double? = null,
    /** Recipes: cooked weight of the whole recipe; null when it is the sum of the items. */
    val yieldGrams: Double? = null,
    val favourite: Boolean = false,
    val deprecated: Boolean = false,
    val updatedAt: Long = 0,
) {
    fun name(language: String): String = if (language == "es") nameEs.ifBlank { nameEn } else nameEn.ifBlank { nameEs }

    fun servingName(language: String): String? =
        if (language == "es") servingNameEs ?: servingNameEn else servingNameEn ?: servingNameEs

    val hasServing: Boolean get() = servingGrams != null && servingGrams > 0

    /** Nutrients of [grams] of this food. */
    fun nutrientsFor(grams: Double): Nutrients = per100 * (grams / PER)

    val isEditable: Boolean get() = source == FoodSource.USER || source == FoodSource.RECIPE

    companion object {
        const val PER = 100.0
    }
}

data class RecipeItem(
    val id: String,
    val foodId: String,
    val grams: Double,
    val position: Int,
    /** Null when the food is not on this device (not synced yet). */
    val food: Food?,
)

/** A recipe with its ingredients. */
data class Recipe(val food: Food, val items: List<RecipeItem>)

data class MealEntry(
    val id: String,
    val date: LocalDate,
    val meal: Meal,
    val foodId: String,
    val grams: Double,
    /** The amount as typed in servings, when it was. */
    val servings: Double?,
    val position: Int,
    val createdAt: Long,
    /** Null when the food is not on this device (not synced yet). */
    val food: Food?,
) {
    val nutrients: Nutrients get() = food?.nutrientsFor(grams) ?: Nutrients.ZERO
}

/** Every entry of a day. */
data class FoodDay(val date: LocalDate, val entries: List<MealEntry>) {
    val totals: Nutrients get() = entries.fold(Nutrients.ZERO) { sum, e -> sum + e.nutrients }

    fun meal(meal: Meal): List<MealEntry> = entries.filter { it.meal == meal }.sortedBy { it.position }

    fun mealTotals(meal: Meal): Nutrients = meal(meal).fold(Nutrients.ZERO) { sum, e -> sum + e.nutrients }
}

/** A custom food or the header of a recipe, as typed by the user. */
data class FoodInput(
    val nameEn: String,
    val nameEs: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100: Nutrients = Nutrients.ZERO,
    val unit: FoodUnit = FoodUnit.G,
    val servingName: String? = null,
    val servingGrams: Double? = null,
)

/** One ingredient of a recipe being saved. */
data class RecipeItemInput(val foodId: String, val grams: Double)

/** Outcome of importing the bundled generic foods. */
sealed interface FoodImportResult {
    val version: Int

    data class Imported(override val version: Int, val foods: Int) : FoodImportResult

    data class UpToDate(override val version: Int) : FoodImportResult
}

/** Outcome of a barcode lookup on Open Food Facts. */
sealed interface BarcodeLookup {
    data class Found(val food: Food) : BarcodeLookup

    data object NotFound : BarcodeLookup

    /** No connection, timeout, rate limit or a server error. */
    data object Unavailable : BarcodeLookup
}

// Goals (design §1): optional guidance, never gates.

enum class Sex {
    MALE, FEMALE;

    companion object {
        fun fromDb(value: String?): Sex = lenientEnum(value, MALE)
    }
}

/** Physical activity level with its multiplier of the resting energy (FAO/WHO style factors). */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), ACTIVE(1.725), VERY_ACTIVE(1.9);

    companion object {
        fun fromDb(value: String?): ActivityLevel = lenientEnum(value, MODERATE)
    }
}

/** Weight objective with its adjustment of the maintenance energy. */
enum class Objective(val adjustment: Double) {
    LOSE(-0.20), MAINTAIN(0.0), GAIN(0.10);

    companion object {
        fun fromDb(value: String?): Objective = lenientEnum(value, MAINTAIN)
    }
}

/** How macro goals are entered: grams, or percentages of the kcal goal. */
enum class MacroMode {
    GRAMS, PERCENT;

    companion object {
        fun fromDb(value: String?): MacroMode = lenientEnum(value, GRAMS)
    }
}

/** Data for the goal calculator. Weight comes from the body-weight log. */
data class NutritionProfile(
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val objective: Objective = Objective.MAINTAIN,
)

/**
 * Daily goals; null means "no goal". Macros are always stored in grams; [macroMode] only says how the user
 * prefers to edit them.
 */
data class NutritionGoals(
    val kcal: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
    val macroMode: MacroMode = MacroMode.GRAMS,
    val profile: NutritionProfile = NutritionProfile(),
) {
    val isSet: Boolean get() = kcal != null || proteinG != null || carbsG != null || fatG != null
}
