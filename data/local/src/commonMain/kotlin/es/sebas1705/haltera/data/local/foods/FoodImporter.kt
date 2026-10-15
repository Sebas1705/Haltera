package es.sebas1705.haltera.data.local.foods

import es.sebas1705.haltera.data.local.SearchText
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.FoodImportResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `Meta` key holding the installed version of the bundled generic foods. */
const val FOODS_VERSION_KEY = "foodsVersion"

/**
 * Imports the bundled generic foods (nutrition design §2), like the exercise catalogue: only when the bundled
 * version is newer than the installed one, in one transaction, upserting `GENERIC` rows (the user's favourite
 * flag is kept) and deprecating removed ids. Other sources are never modified: a bundled id that collides with
 * a non-generic row is skipped. A malformed file, or any failure while writing, throws and changes nothing.
 */
class FoodImporter(
    private val db: AppDatabase,
    private val now: () -> Long,
) {
    fun installedVersion(): Int? = db.metaQueries.get(FOODS_VERSION_KEY).executeAsOneOrNull()?.toIntOrNull()

    fun importIfNewer(json: String): FoodImportResult {
        val bundledVersion = FoodsJson.decodeFromString(FoodsHeader.serializer(), json).foodsVersion
        return db.transactionWithResult {
            val installed = installedVersion()
            if (installed != null && installed >= bundledVersion) {
                return@transactionWithResult FoodImportResult.UpToDate(installed)
            }
            val file = FoodsJson.decodeFromString(FoodsDto.serializer(), json)
            val at = now()
            var written = 0
            file.foods.forEach { if (upsert(it, at)) written++ }
            file.deprecatedIds.forEach { db.foodEntityQueries.deprecateGeneric(updatedAt = at, id = it) }
            db.metaQueries.put(FOODS_VERSION_KEY, file.foodsVersion.toString())
            FoodImportResult.Imported(version = file.foodsVersion, foods = written)
        }
    }

    /** Writes a generic food; false when its id belongs to a row of another source. */
    private fun upsert(f: FoodDto, at: Long): Boolean {
        val existing = db.foodEntityQueries.selectById(f.id).executeAsOneOrNull()
        if (existing != null && existing.source != GENERIC) return false
        val search = searchText(f.name.en, f.name.es)
        with(db.foodEntityQueries) {
            val inserted = insertGenericIfMissing(
                id = f.id, nameEn = f.name.en, nameEs = f.name.es, nameSearch = search, kcal = f.kcal,
                protein = f.protein, carbs = f.carbs, fat = f.fat, fiber = f.fiber, sugar = f.sugar, salt = f.salt,
                unit = f.unit, servingNameEn = f.serving?.name?.en, servingNameEs = f.serving?.name?.es,
                servingGrams = f.serving?.grams, updatedAt = at,
            ).value
            if (inserted == 0L) {
                updateGeneric(
                    nameEn = f.name.en, nameEs = f.name.es, nameSearch = search, kcal = f.kcal, protein = f.protein,
                    carbs = f.carbs, fat = f.fat, fiber = f.fiber, sugar = f.sugar, salt = f.salt, unit = f.unit,
                    servingNameEn = f.serving?.name?.en, servingNameEs = f.serving?.name?.es,
                    servingGrams = f.serving?.grams, updatedAt = at, id = f.id,
                )
            }
        }
        return true
    }

    companion object {
        private const val GENERIC = "GENERIC"

        /** The `nameSearch` column: both names and the brand, normalised (SearchText). */
        fun searchText(vararg parts: String?): String =
            SearchText.normalize(parts.filterNot { it.isNullOrBlank() }.joinToString(" "))
    }
}

internal val FoodsJson = Json { ignoreUnknownKeys = true }

// Shape of composeApp/.../files/foods.json, produced by scripts/nutrition (nutrition/README.md).

@Serializable
internal data class FoodsHeader(val foodsVersion: Int)

@Serializable
internal data class FoodsDto(
    val foodsVersion: Int,
    val contentHash: String,
    val foods: List<FoodDto>,
    val deprecatedIds: List<String> = emptyList(),
)

@Serializable
internal data class FoodNameDto(val en: String, val es: String)

@Serializable
internal data class ServingDto(val name: FoodNameDto, val grams: Double)

@Serializable
internal data class FoodDto(
    val id: String,
    val name: FoodNameDto,
    val unit: String = "G",
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
    val serving: ServingDto? = null,
)
