package es.sebas1705.ironlog.data.repository.sql

import es.sebas1705.ironlog.core.datastore.NutritionSettings
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.data.local.SearchText
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.local.db.FoodEntity
import es.sebas1705.ironlog.data.local.db.MealEntryEntity
import es.sebas1705.ironlog.data.local.foods.FoodImporter
import es.sebas1705.ironlog.data.models.ActivityLevel
import es.sebas1705.ironlog.data.models.BarcodeLookup
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodDay
import es.sebas1705.ironlog.data.models.FoodImportResult
import es.sebas1705.ironlog.data.models.FoodInput
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.data.models.MacroMode
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.MealEntry
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.models.NutritionGoals
import es.sebas1705.ironlog.data.models.NutritionProfile
import es.sebas1705.ironlog.data.models.Objective
import es.sebas1705.ironlog.data.models.Recipe
import es.sebas1705.ironlog.data.models.RecipeItem
import es.sebas1705.ironlog.data.models.RecipeItemInput
import es.sebas1705.ironlog.data.models.RecipeMath
import es.sebas1705.ironlog.data.models.Sex
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.data.repository.off.OffProduct
import es.sebas1705.ironlog.data.repository.off.OffResult
import es.sebas1705.ironlog.data.repository.off.OpenFoodFactsClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

@Suppress("TooManyFunctions") // Implements the wide NutritionRepository contract.
internal class SqlNutritionRepository(
    private val db: AppDatabase,
    private val importer: FoodImporter,
    private val off: OpenFoodFactsClient,
    private val settings: SettingsRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : NutritionRepository {
    private val foods get() = db.foodEntityQueries
    private val entries get() = db.mealEntryEntityQueries
    private val items get() = db.recipeItemEntityQueries

    override suspend fun importBundledFoods(json: String): FoodImportResult =
        withContext(dispatcher) { importer.importIfNewer(json) }

    // Food log

    override fun observeDay(date: LocalDate): Flow<FoodDay> =
        tablesChanged(entries.selectByDate(date.toString()), foods.selectById(""))
            .conflate()
            .map { FoodDay(date, withFoods(entries.selectByDate(date.toString()).executeAsList())) }
            .flowOn(dispatcher)

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<FoodDay>> =
        tablesChanged(entries.selectBetween(from.toString(), to.toString()), foods.selectById(""))
            .conflate()
            .map {
                withFoods(entries.selectBetween(from.toString(), to.toString()).executeAsList())
                    .groupBy { it.date }
                    .map { (date, list) -> FoodDay(date, list) }
                    .sortedBy { it.date }
            }
            .flowOn(dispatcher)

    override suspend fun addEntry(date: LocalDate, meal: Meal, foodId: String, grams: Double, servings: Double?): String =
        withContext(dispatcher) {
            val id = newId()
            db.transaction {
                val position = entries.nextPosition(date.toString(), meal.name).executeAsOne()
                entries.insert(id, date.toString(), meal.name, foodId, grams, servings, position, now())
            }
            id
        }

    override suspend fun updateEntry(entryId: String, grams: Double, servings: Double?) {
        withContext(dispatcher) { entries.updateAmount(grams = grams, servings = servings, id = entryId) }
    }

    override suspend fun moveEntry(entryId: String, date: LocalDate, meal: Meal) = withContext(dispatcher) {
        db.transaction {
            val position = entries.nextPosition(date.toString(), meal.name).executeAsOne()
            entries.moveTo(date = date.toString(), meal = meal.name, position = position, id = entryId)
        }
    }

    override suspend fun deleteEntry(entryId: String) {
        withContext(dispatcher) { entries.delete(entryId) }
    }

    override suspend fun copyMeal(fromDate: LocalDate, fromMeal: Meal, toDate: LocalDate, toMeal: Meal): Int =
        withContext(dispatcher) {
            db.transactionWithResult {
                copy(entries.selectByDateAndMeal(fromDate.toString(), fromMeal.name).executeAsList(), toDate) { toMeal }
            }
        }

    override suspend fun copyDay(fromDate: LocalDate, toDate: LocalDate): Int = withContext(dispatcher) {
        db.transactionWithResult {
            val source = entries.selectByDate(fromDate.toString()).executeAsList()
                .sortedWith(compareBy({ Meal.fromDb(it.meal).ordinal }, { it.position }))
            copy(source, toDate) { Meal.fromDb(it.meal) }
        }
    }

    /** Appends copies of [source] (new ids, created now) to [toDate]; must run in a transaction. */
    private fun copy(source: List<MealEntryEntity>, toDate: LocalDate, meal: (MealEntryEntity) -> Meal): Int {
        val at = now()
        source.forEach { e ->
            val target = meal(e).name
            val position = entries.nextPosition(toDate.toString(), target).executeAsOne()
            entries.insert(newId(), toDate.toString(), target, e.foodId, e.grams, e.servings, position, at)
        }
        return source.size
    }

    // Foods

    override suspend fun search(query: String, language: String, limit: Int): List<Food> = withContext(dispatcher) {
        val words = SearchText.query(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        val key = words.maxBy { it.length }
        val es = language.lowercase().startsWith("es")
        foods.search(word = key, limit = CANDIDATES).executeAsList()
            .filter { row -> words.all { row.nameSearch.contains(it) } }
            .map { it.toModel() }
            .sortedWith(
                compareBy<Food>(
                    // Favourites first, then names in the user's language that start with the query, then own foods.
                    { if (it.favourite) 0 else 1 },
                    { if (SearchText.normalize(it.name(if (es) "es" else "en")).startsWith(words.first())) 0 else 1 },
                    { if (it.source == FoodSource.GENERIC) 1 else 0 },
                    { it.name(if (es) "es" else "en").length },
                ),
            )
            .take(limit)
    }

    override fun observeRecents(limit: Int): Flow<List<Food>> =
        tablesChanged(foods.selectRecents(limit.toLong()), entries.selectByDate(""))
            .conflate()
            .map { foods.selectRecents(limit.toLong()).executeAsList().map { it.toModel() } }
            .flowOn(dispatcher)

    override fun observeFavourites(): Flow<List<Food>> =
        tablesChanged(foods.selectFavourites()).conflate()
            .map { foods.selectFavourites().executeAsList().map { it.toModel() } }
            .flowOn(dispatcher)

    override fun observeOwnFoods(): Flow<List<Food>> =
        tablesChanged(foods.selectOwned()).conflate()
            .map { foods.selectOwned().executeAsList().map { it.toModel() } }
            .flowOn(dispatcher)

    override fun observeFood(id: String): Flow<Food?> =
        tablesChanged(foods.selectById(id)).conflate()
            .map { foods.selectById(id).executeAsOneOrNull()?.toModel() }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun getFood(id: String): Food? =
        withContext(dispatcher) { foods.selectById(id).executeAsOneOrNull()?.toModel() }

    override suspend fun setFavourite(foodId: String, favourite: Boolean) {
        withContext(dispatcher) { foods.setFavourite(favourite = favourite.toDb(), id = foodId) }
    }

    override suspend fun createFood(input: FoodInput): Food = withContext(dispatcher) {
        val id = "$USER_PREFIX${newId()}"
        insert(id, FoodSource.USER, input, input.per100, yieldGrams = null)
        foods.selectById(id).executeAsOne().toModel()
    }

    override suspend fun updateFood(foodId: String, input: FoodInput) = withContext(dispatcher) {
        db.transaction {
            update(foodId, input, input.per100, yieldGrams = null)
            foods.selectRecipeIdsUsing(foodId).executeAsList().forEach(::recompute)
        }
    }

    override suspend fun deleteFood(foodId: String): Boolean = withContext(dispatcher) {
        db.transactionWithResult {
            if (foods.countReferences(foodId).executeAsOne() > 0) {
                foods.deprecate(updatedAt = now(), id = foodId)
                false
            } else {
                foods.delete(foodId)
                true
            }
        }
    }

    override suspend fun getRecipe(id: String): Recipe? = withContext(dispatcher) { loadRecipe(id) }

    override suspend fun saveRecipe(
        recipeId: String?,
        header: FoodInput,
        yieldGrams: Double?,
        items: List<RecipeItemInput>,
    ): String = withContext(dispatcher) {
        val id = recipeId ?: "$RECIPE_PREFIX${newId()}"
        db.transaction {
            val per100 = per100Of(items.map { it.foodId to it.grams }, yieldGrams)
            if (recipeId == null) {
                insert(id, FoodSource.RECIPE, header, per100, yieldGrams)
            } else {
                update(id, header, per100, yieldGrams)
                this@SqlNutritionRepository.items.deleteByRecipe(id)
            }
            items.forEachIndexed { index, item ->
                this@SqlNutritionRepository.items.insert(newId(), id, item.foodId, item.grams, index.toLong())
            }
            // Recipes made with this one change too.
            foods.selectRecipeIdsUsing(id).executeAsList().forEach(::recompute)
        }
        id
    }

    override suspend fun lookupBarcode(barcode: String): BarcodeLookup {
        val code = barcode.trim()
        withContext(dispatcher) { foods.selectByBarcode(code).executeAsOneOrNull() }
            ?.let { return BarcodeLookup.Found(it.toModel()) }
        return when (val result = off.product(code)) {
            is OffResult.Found -> BarcodeLookup.Found(withContext(dispatcher) { cache(result.product) })
            OffResult.NotFound -> BarcodeLookup.NotFound
            OffResult.Unavailable -> BarcodeLookup.Unavailable
        }
    }

    /** Saves an Open Food Facts product as an OFF food (refreshing an earlier copy). */
    private fun cache(p: OffProduct): Food {
        val id = "$OFF_PREFIX${p.barcode}"
        val input = FoodInput(
            nameEn = p.nameEn,
            nameEs = p.nameEs,
            brand = p.brand,
            barcode = p.barcode,
            per100 = p.per100,
            unit = p.unit,
            servingName = p.servingName,
            servingGrams = p.servingGrams,
        )
        db.transaction {
            if (foods.selectById(id).executeAsOneOrNull() == null) {
                insert(id, FoodSource.OFF, input, p.per100, yieldGrams = null)
            } else {
                update(id, input, p.per100, yieldGrams = null)
            }
        }
        return foods.selectById(id).executeAsOne().toModel()
    }

    // Goals

    override fun observeGoals(): Flow<NutritionGoals> =
        settings.settings.map { it.nutrition.toModel() }.distinctUntilChanged()

    override fun setGoals(goals: NutritionGoals) = settings.setNutrition(goals.toSettings())

    // Helpers

    private fun insert(id: String, source: FoodSource, input: FoodInput, per100: Nutrients, yieldGrams: Double?) {
        val name = input.cleanNames()
        foods.insert(
            id = id, source = source.name, nameEn = name.first, nameEs = name.second,
            nameSearch = FoodImporter.searchText(
                name.first,
                name.second,
                input.brand
            ),
            brand = input.brand?.trim()?.ifBlank {
                null
            },
            barcode = input.barcode?.trim()?.ifBlank { null }, kcal = per100.kcal, protein = per100.protein,
            carbs = per100.carbs, fat = per100.fat, fiber = per100.fiber, sugar = per100.sugar, salt = per100.salt,
            unit = input.unit.name, servingNameEn = input.servingName?.trim()?.ifBlank { null },
            servingNameEs = input.servingName?.trim()?.ifBlank { null }, servingGrams = input.servingGrams?.takeIf { it > 0 },
            yieldGrams = yieldGrams, favourite = 0, updatedAt = now(),
        )
    }

    private fun update(id: String, input: FoodInput, per100: Nutrients, yieldGrams: Double?) {
        val name = input.cleanNames()
        foods.updateOwned(
            nameEn = name.first, nameEs = name.second,
            nameSearch = FoodImporter.searchText(
                name.first,
                name.second,
                input.brand
            ),
            brand = input.brand?.trim()?.ifBlank {
                null
            },
            barcode = input.barcode?.trim()?.ifBlank { null }, kcal = per100.kcal, protein = per100.protein,
            carbs = per100.carbs, fat = per100.fat, fiber = per100.fiber, sugar = per100.sugar, salt = per100.salt,
            unit = input.unit.name, servingNameEn = input.servingName?.trim()?.ifBlank { null },
            servingNameEs = input.servingName?.trim()?.ifBlank { null }, servingGrams = input.servingGrams?.takeIf { it > 0 },
            yieldGrams = yieldGrams, updatedAt = now(), id = id,
        )
    }

    /** A user food has one name: it fills both languages unless both were given. */
    private fun FoodInput.cleanNames(): Pair<String, String> {
        val en = nameEn.trim()
        val es = nameEs.trim()
        return en.ifBlank { es } to es.ifBlank { en }
    }

    private fun per100Of(items: List<Pair<String, Double>>, yieldGrams: Double?): Nutrients {
        val byId = if (items.isEmpty()) {
            emptyMap()
        } else {
            foods.selectByIds(
                items.map {
                    it.first
                }.distinct()
            ).executeAsList().associateBy { it.id }
        }
        return RecipeMath.per100(items.map { (foodId, grams) -> byId[foodId]?.toModel() to grams }, yieldGrams)
    }

    /** Recomputes a recipe from its current items (an ingredient changed). */
    private fun recompute(recipeId: String) {
        val recipe = foods.selectById(recipeId).executeAsOneOrNull() ?: return
        val per100 =
            per100Of(items.selectByRecipe(recipeId).executeAsList().map { it.foodId to it.grams }, recipe.yieldGrams)
        foods.updateNutrients(
            kcal = per100.kcal, protein = per100.protein, carbs = per100.carbs, fat = per100.fat, fiber = per100.fiber,
            sugar = per100.sugar, salt = per100.salt, updatedAt = now(), id = recipeId,
        )
    }

    private fun loadRecipe(id: String): Recipe? {
        val food = foods.selectById(id).executeAsOneOrNull()?.toModel() ?: return null
        val rows = items.selectByRecipe(id).executeAsList()
        val byId = if (rows.isEmpty()) {
            emptyMap()
        } else {
            foods.selectByIds(
                rows.map {
                    it.foodId
                }.distinct()
            ).executeAsList().associateBy { it.id }
        }
        return Recipe(
            food,
            rows.map {
                RecipeItem(it.id, it.foodId, it.grams, it.position.toInt(), byId[it.foodId]?.toModel())
            }
        )
    }

    private fun withFoods(rows: List<MealEntryEntity>): List<MealEntry> {
        if (rows.isEmpty()) return emptyList()
        val byId = foods.selectByIds(rows.map { it.foodId }.distinct()).executeAsList().associateBy { it.id }
        return rows.map { it.toModel(byId[it.foodId]?.toModel()) }
            .sortedWith(compareBy({ it.date }, { it.meal.ordinal }, { it.position }))
    }

    private companion object {
        const val USER_PREFIX = "food-"
        const val RECIPE_PREFIX = "recipe-"
        const val OFF_PREFIX = "off-"
        const val CANDIDATES = 300L
    }
}

internal fun FoodEntity.toModel() = Food(
    id = id,
    source = FoodSource.fromDb(source),
    nameEn = nameEn,
    nameEs = nameEs,
    brand = brand,
    barcode = barcode,
    per100 = Nutrients(
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        salt = salt
    ),
    unit = FoodUnit.fromDb(unit),
    servingNameEn = servingNameEn,
    servingNameEs = servingNameEs,
    servingGrams = servingGrams,
    yieldGrams = yieldGrams,
    favourite = favourite != 0L,
    deprecated = deprecated != 0L,
    updatedAt = updatedAt,
)

internal fun MealEntryEntity.toModel(food: Food?) = MealEntry(
    id = id,
    date = LocalDate.parse(date),
    meal = Meal.fromDb(meal),
    foodId = foodId,
    grams = grams,
    servings = servings,
    position = position.toInt(),
    createdAt = createdAt,
    food = food,
)

internal fun NutritionSettings.toModel() = NutritionGoals(
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    macroMode = MacroMode.fromDb(macroMode),
    profile = NutritionProfile(
        sex = sex?.let(Sex::fromDb),
        birthYear = birthYear,
        heightCm = heightCm,
        activity = ActivityLevel.fromDb(activity),
        objective = Objective.fromDb(objective),
    ),
)

internal fun NutritionGoals.toSettings() = NutritionSettings(
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    macroMode = macroMode.name,
    sex = profile.sex?.name,
    birthYear = profile.birthYear,
    heightCm = profile.heightCm,
    activity = profile.activity.name,
    objective = profile.objective.name,
)
