package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.BarcodeLookup
import es.sebas1705.haltera.data.models.Food
import es.sebas1705.haltera.data.models.FoodDay
import es.sebas1705.haltera.data.models.FoodImportResult
import es.sebas1705.haltera.data.models.FoodInput
import es.sebas1705.haltera.data.models.Meal
import es.sebas1705.haltera.data.models.NutritionGoals
import es.sebas1705.haltera.data.models.Recipe
import es.sebas1705.haltera.data.models.RecipeItemInput
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Food log, foods and goals (docs/designs/2026-10-15-nutrition-design.md). A logbook: any date can be logged
 * and every entry edited. Amounts are grams (ml for foods measured in ml).
 */
@Suppress("TooManyFunctions") // Food log, foods, recipes and goals of one feature.
interface NutritionRepository {
    /** Imports the bundled generic foods when they are newer than the installed ones. */
    suspend fun importBundledFoods(json: String): FoodImportResult

    // Food log

    fun observeDay(date: LocalDate): Flow<FoodDay>

    /** Days with at least one entry between [from] and [to] (both included), oldest first. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<FoodDay>>

    /** Logs [grams] of a food in a meal (appended); [servings] remembers an amount typed in servings. */
    suspend fun addEntry(date: LocalDate, meal: Meal, foodId: String, grams: Double, servings: Double? = null): String

    suspend fun updateEntry(entryId: String, grams: Double, servings: Double?)

    /** Moves an entry to another meal or date (appended there). */
    suspend fun moveEntry(entryId: String, date: LocalDate, meal: Meal)

    suspend fun deleteEntry(entryId: String)

    /** Copies the entries of one meal into another (appended); returns how many were copied. */
    suspend fun copyMeal(fromDate: LocalDate, fromMeal: Meal, toDate: LocalDate, toMeal: Meal): Int

    /** Copies every entry of a day into another day, meal by meal (appended); returns how many were copied. */
    suspend fun copyDay(fromDate: LocalDate, toDate: LocalDate): Int

    // Foods

    /**
     * Fuzzy search over both names and the brand (docs/designs/2026-10-16-fuzzy-search-design.md): accent and
     * case insensitive, any word order, typos and plurals tolerated, best match first, favourites boosted.
     * A blank query returns nothing. Barcode lookups are [lookupBarcode].
     */
    suspend fun search(query: String, language: String, limit: Int = SEARCH_LIMIT): List<Food>

    /** Foods by the time they were last logged, newest first. */
    fun observeRecents(limit: Int = RECENTS_LIMIT): Flow<List<Food>>

    fun observeFavourites(): Flow<List<Food>>

    /** The user's own foods and recipes. */
    fun observeOwnFoods(): Flow<List<Food>>

    fun observeFood(id: String): Flow<Food?>

    suspend fun getFood(id: String): Food?

    suspend fun setFavourite(foodId: String, favourite: Boolean)

    suspend fun createFood(input: FoodInput): Food

    /** Edits a user food; recipes that use it are recomputed. Logged days use the new values too. */
    suspend fun updateFood(foodId: String, input: FoodInput)

    /**
     * Deletes a user food or recipe. One still used by logged days or recipes is hidden instead (deprecated), so
     * past days keep their values. Returns true when the row was really deleted.
     */
    suspend fun deleteFood(foodId: String): Boolean

    suspend fun getRecipe(id: String): Recipe?

    /**
     * Creates ([recipeId] null) or replaces a recipe: its name and serving from [header], its items, and its
     * per-100 g values computed from them and [yieldGrams] (the cooked weight, or null for the sum of the items).
     */
    suspend fun saveRecipe(
        recipeId: String?,
        header: FoodInput,
        yieldGrams: Double?,
        items: List<RecipeItemInput>
    ): String

    /** A food by barcode: a saved one first, else Open Food Facts (cached as an OFF food). */
    suspend fun lookupBarcode(barcode: String): BarcodeLookup

    // Goals (stored in the user settings, synced with them)

    fun observeGoals(): Flow<NutritionGoals>

    fun setGoals(goals: NutritionGoals)

    companion object {
        const val SEARCH_LIMIT = 50
        const val RECENTS_LIMIT = 20
    }
}
