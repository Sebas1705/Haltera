package es.sebas1705.ironlog.feature.nutrition

import es.sebas1705.ironlog.data.models.BarcodeLookup
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodDay
import es.sebas1705.ironlog.data.models.FoodImportResult
import es.sebas1705.ironlog.data.models.FoodInput
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.MealEntry
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.models.NutritionGoals
import es.sebas1705.ironlog.data.models.Recipe
import es.sebas1705.ironlog.data.models.RecipeItemInput
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.nutrition.model.Today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

val DAY = LocalDate(2026, 10, 15)

internal val FixedToday = Today { DAY }

val OATS = Food(
    id = "oats",
    source = FoodSource.GENERIC,
    nameEn = "Rolled oats",
    nameEs = "Copos de avena",
    per100 = Nutrients(kcal = 380.0, protein = 13.0, carbs = 68.0, fat = 6.0),
)

val MILK = Food(
    id = "milk", source = FoodSource.GENERIC, nameEn = "Whole milk", nameEs = "Leche entera", unit = FoodUnit.ML,
    per100 = Nutrients(kcal = 60.0, protein = 3.0, carbs = 5.0, fat = 3.0), servingNameEn = "glass",
    servingNameEs = "vaso", servingGrams = 250.0,
)

/** In-memory [NutritionRepository] with just enough behaviour for the ViewModels. */
class FakeNutritionRepository(foods: List<Food> = listOf(OATS, MILK)) : NutritionRepository {
    val foods = MutableStateFlow(foods.associateBy { it.id })
    val entries = MutableStateFlow<List<MealEntry>>(emptyList())
    val goals = MutableStateFlow(NutritionGoals())
    val copies = mutableListOf<String>()
    var lookup: BarcodeLookup = BarcodeLookup.NotFound
    private var next = 0

    override suspend fun importBundledFoods(json: String) = FoodImportResult.UpToDate(1)

    override fun observeDay(date: LocalDate): Flow<FoodDay> =
        entries.map { all ->
            FoodDay(
                date,
                all.filter { it.date == date }.map { it.copy(food = foods.value[it.foodId]) }
            )
        }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<FoodDay>> = entries.map { all ->
        all.filter { it.date in from..to }.map { it.copy(food = foods.value[it.foodId]) }.groupBy { it.date }
            .map { (d, list) -> FoodDay(d, list) }.sortedBy { it.date }
    }

    override suspend fun addEntry(date: LocalDate, meal: Meal, foodId: String, grams: Double, servings: Double?): String {
        val id = "e${next++}"
        entries.update { it + MealEntry(id, date, meal, foodId, grams, servings, it.size, 0, null) }
        return id
    }

    override suspend fun updateEntry(entryId: String, grams: Double, servings: Double?) =
        entries.update { list ->
            list.map {
                if (it.id == entryId) {
                    it.copy(
                        grams = grams,
                        servings = servings
                    )
                } else {
                    it
                }
            }
        }

    override suspend fun moveEntry(entryId: String, date: LocalDate, meal: Meal) =
        entries.update { list -> list.map { if (it.id == entryId) it.copy(date = date, meal = meal) else it } }

    override suspend fun deleteEntry(entryId: String) = entries.update { list -> list.filterNot { it.id == entryId } }

    override suspend fun copyMeal(fromDate: LocalDate, fromMeal: Meal, toDate: LocalDate, toMeal: Meal): Int {
        copies += "meal $fromDate $fromMeal -> $toDate $toMeal"
        return entries.value.count { it.date == fromDate && it.meal == fromMeal }
    }

    override suspend fun copyDay(fromDate: LocalDate, toDate: LocalDate): Int {
        copies += "day $fromDate -> $toDate"
        return entries.value.count { it.date == fromDate }
    }

    override suspend fun search(query: String, language: String, limit: Int): List<Food> =
        foods.value.values.filter { it.name(language).contains(query, ignoreCase = true) }

    override fun observeRecents(limit: Int): Flow<List<Food>> = entries.map { all ->
        all.mapNotNull {
            foods.value[it.foodId]
        }.distinct()
    }

    override fun observeFavourites(): Flow<List<Food>> = foods.map { all -> all.values.filter { it.favourite } }

    override fun observeOwnFoods(): Flow<List<Food>> = foods.map { all -> all.values.filter { it.isEditable } }

    override fun observeFood(id: String): Flow<Food?> = foods.map { it[id] }

    override suspend fun getFood(id: String): Food? = foods.value[id]

    override suspend fun setFavourite(foodId: String, favourite: Boolean) =
        foods.update { all -> all.mapValues { (id, f) -> if (id == foodId) f.copy(favourite = favourite) else f } }

    override suspend fun createFood(input: FoodInput): Food {
        val food = Food(
            id = "food-${next++}", source = FoodSource.USER, nameEn = input.nameEn, nameEs = input.nameEs,
            per100 = input.per100, unit = input.unit, servingNameEn = input.servingName, servingGrams = input.servingGrams,
            barcode = input.barcode,
        )
        foods.update { it + (food.id to food) }
        return food
    }

    override suspend fun updateFood(foodId: String, input: FoodInput) =
        foods.update { all ->
            all.mapValues { (id, f) ->
                if (id == foodId) {
                    f.copy(
                        nameEn = input.nameEn,
                        per100 = input.per100
                    )
                } else {
                    f
                }
            }
        }

    override suspend fun deleteFood(foodId: String): Boolean {
        foods.update { it - foodId }
        return true
    }

    override suspend fun getRecipe(id: String): Recipe? = null

    override suspend fun saveRecipe(recipeId: String?, header: FoodInput, yieldGrams: Double?, items: List<RecipeItemInput>) =
        recipeId ?: "recipe-${next++}"

    override suspend fun lookupBarcode(barcode: String): BarcodeLookup = lookup

    override fun observeGoals(): Flow<NutritionGoals> = goals

    override fun setGoals(goals: NutritionGoals) {
        this.goals.value = goals
    }
}

class FakeBodyWeightRepository(entries: List<BodyWeightEntry> = emptyList()) : BodyWeightRepository {
    val entries = MutableStateFlow(entries)

    override fun observeAll(): Flow<List<BodyWeightEntry>> = entries

    override fun observeLatest(): Flow<BodyWeightEntry?> = entries.map { list -> list.maxByOrNull { it.measuredAt } }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<BodyWeightEntry>> =
        entries.map { list -> list.filter { it.measuredAt in fromMillis until toMillis } }

    override suspend fun add(weightGrams: Long, measuredAt: Long?, notes: String): String = "bw"

    override suspend fun update(entry: BodyWeightEntry) = Unit

    override suspend fun delete(id: String) = Unit
}
