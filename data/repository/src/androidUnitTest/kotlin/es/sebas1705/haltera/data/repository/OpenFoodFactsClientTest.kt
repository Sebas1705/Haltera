package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.FoodUnit
import es.sebas1705.haltera.data.repository.off.OffResult
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OpenFoodFactsClientTest {
    private val requests = mutableListOf<Pair<String, String?>>()
    private var clock = 0L

    private fun client(handler: () -> Any): OpenFoodFactsClient {
        val engine = MockEngine { request ->
            requests += request.url.toString() to request.headers[HttpHeaders.UserAgent]
            when (val r = handler()) {
                is Throwable -> throw r
                is HttpStatusCode -> respondError(r)
                else -> respond(r.toString(), HttpStatusCode.OK)
            }
        }
        return OpenFoodFactsClient(HttpClient(engine), appVersion = "1.2.3", now = { clock })
    }

    private val drink = """
        {"code":"5449000000996","product":{"product_name":"Cola","product_name_es":"Refresco de cola",
        "quantity":"330 ml","nutriments":{"energy_100g":180,"proteins_100g":0,"carbohydrates_100g":10.6,"fat_100g":"0",
        "sugars_100g":10.6}}}
    """.trimIndent()

    @Test
    fun `asks the v3 product endpoint with the Haltera User-Agent`() = runTest {
        client { drink }.product("5449000000996")
        val (url, agent) = requests.single()
        assertEquals(true, url.startsWith("https://world.openfoodfacts.org/api/v3/product/5449000000996?fields="))
        assertEquals("Haltera/1.2.3 (+https://github.com/Sebas1705/Haltera)", agent)
    }

    @Test
    fun `parses names, kJ energy, string numbers and liquids`() = runTest {
        val product = assertIs<OffResult.Found>(client { drink }.product("5449000000996")).product
        assertEquals("Cola", product.nameEn)
        assertEquals("Refresco de cola", product.nameEs)
        assertEquals(180 / 4.184, product.per100.kcal, 1e-9)
        assertEquals(0.0, product.per100.fat)
        assertEquals(FoodUnit.ML, product.unit)
        assertNull(product.servingGrams)
        assertNull(product.brand)
    }

    @Test
    fun `missing products, missing macros and invalid barcodes are not found`() = runTest {
        assertEquals(OffResult.NotFound, client { HttpStatusCode.NotFound }.product("12345678"))
        val noMacros = """{"product":{"product_name":"x","nutriments":{"energy-kcal_100g":100}}}"""
        assertEquals(OffResult.NotFound, client { noMacros }.product("12345678"))
        assertEquals(OffResult.NotFound, client { drink }.product("abc"))
        assertEquals(1 + 1, requests.size) // the invalid barcode was never sent
    }

    @Test
    fun `network and server failures are unavailable`() = runTest {
        assertEquals(OffResult.Unavailable, client { IOException("offline") }.product("12345678"))
        assertEquals(OffResult.Unavailable, client { HttpStatusCode.InternalServerError }.product("12345678"))
        assertEquals(OffResult.Unavailable, client { "not json" }.product("12345678"))
    }

    @Test
    fun `stays under 15 reads per minute`() = runTest {
        val off = client { drink }
        repeat(OpenFoodFactsClient.MAX_READS_PER_WINDOW) { assertIs<OffResult.Found>(off.product("5449000000996")) }
        assertEquals(OffResult.Unavailable, off.product("5449000000996"))
        clock += 60_000
        assertIs<OffResult.Found>(off.product("5449000000996"))
    }
}
