package es.sebas1705.haltera.data.repository.search

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.models.FoodInput
import es.sebas1705.haltera.data.models.Nutrients
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import es.sebas1705.haltera.data.repository.sql.FoodSearch
import es.sebas1705.haltera.data.repository.sql.SqlNutritionRepository
import es.sebas1705.haltera.data.repository.sql.toModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Food search against the 705 bundled generic foods, through `NutritionRepository.search`. */
class FoodSearchBundledTest {

    private data class Case(val lang: String, val query: String, val expected: String?, val within: Int = 1)

    private val cases = listOf(
        Case("es", "pechuga pollo", "usda-171477", 4), // every "Pechuga de pollo …" first
        Case("es", "pollo pechuga plancha", "usda-171534"),
        Case("es", "platano", "usda-173944"),
        Case("es", "plátanos", "usda-173944"),
        Case("en", "bananas", "usda-173944"),
        Case("es", "copos avena", "usda-173904"),
        Case("es", "avena copos", "usda-173904"),
        Case("en", "oats rolled", "usda-173904"),
        Case("es", "leche entera", "usda-171265"),
        Case("es", "entera leche", "usda-171265"),
        Case("es", "lech", "usda-171265", 3),
        Case("es", "aceite oliva", "usda-171413"),
        Case("es", "aceite de olvia", "usda-171413"),
        Case("en", "olive oil", "usda-171413"),
        Case("en", "chiken brest", "usda-171077", 4),
        Case("es", "huevos cocidos", "usda-173424"),
        Case("es", "yogur griego", "usda-171304"),
        Case("en", "greek yoghurt", "usda-171304"),
        Case("es", "manzana", "usda-171688"),
        Case("en", "aple", "usda-171688"),
        Case("es", "salmon ahumado", "usda-173687"),
        Case("es", "atun aceite", "usda-173708"),
        Case("en", "tuna", "usda-173708", 5),
        Case("es", "patata cruda", "usda-170026"),
        Case("en", "potato raw", "usda-170026"),
        Case("es", "chicken breast", "usda-171077", 4), // the other language
        Case("en", "leche entera", "usda-171265"),
        Case("es", "xyzzy", null),
        Case("en", "qwerty asdf", null),
        Case("es", "pollo xyzzy", null),
    )

    private fun TestScope.repository(): NutritionRepository {
        val db = BundledData.database(foods = true)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val off = OpenFoodFactsClient(
            HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }),
            "test",
            now = { 0L }
        )
        return SqlNutritionRepository(
            db,
            FoodImporter(db, now = {
                0L
            }),
            off,
            KeyValueSettingsRepository(MapSettings()),
            dispatcher,
            now = { 0L }
        )
    }

    @Test
    fun `table of real food queries finds the expected food`() = runTest {
        val repository = repository()
        val failures = cases.mapNotNull { case ->
            val ids = repository.search(case.query, case.lang).map { it.id }
            val ok = if (case.expected == null) ids.isEmpty() else case.expected in ids.take(case.within)
            if (ok) {
                null
            } else {
                "[${case.lang}] '${case.query}': expected ${case.expected ?: "nothing"} in top ${case.within}, got ${ids.take(
                    5
                )}"
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of ${cases.size} cases failed:\n" + failures.joinToString("\n")
            )
        }
    }

    @Test
    fun `favourites and own foods rank first among equal matches, and new foods are found at once`() = runTest {
        val repository = repository()
        assertEquals("usda-171265", repository.search("leche", "es").first().id)
        val shake = repository.createFood(
            FoodInput(nameEn = "", nameEs = "Batido de leche", per100 = Nutrients(kcal = 70.0))
        )
        assertTrue(shake.id in repository.search("leche", "es").map { it.id })
        assertEquals(shake.id, repository.search("batdo leche", "es").first().id)
        repository.setFavourite(shake.id, true)
        assertEquals(shake.id, repository.search("leche", "es").first().id)
    }

    @Test
    fun `benchmark - ranking every food stays far below a frame`() = runTest {
        val db = BundledData.database(foods = true)
        val foods = db.foodEntityQueries.selectSearchable().executeAsList().map { it.toModel() }
        assertTrue(foods.size >= 700)
        val search = FoodSearch(foods, "es")
        val queries = cases.map { it.query }
        repeat(50) { search.search(queries[it % queries.size], 50) }
        val start = System.nanoTime()
        repeat(1_000) { search.search(queries[it % queries.size], 50) }
        val totalMs = (System.nanoTime() - start) / 1e6
        println("FoodSearch benchmark: ${foods.size} foods, 1000 queries in ${"%.0f".format(totalMs)} ms")
        assertTrue(totalMs < 4_000, "1000 queries took $totalMs ms")
    }
}
