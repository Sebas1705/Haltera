package es.sebas1705.ironlog.data.repository.off

import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.data.models.Nutrients
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** A product read from Open Food Facts. Values per 100 g (or 100 ml when [unit] is ML). */
data class OffProduct(
    val barcode: String,
    val nameEn: String,
    val nameEs: String,
    val brand: String?,
    val per100: Nutrients,
    val unit: FoodUnit,
    val servingName: String?,
    val servingGrams: Double?,
)

sealed interface OffResult {
    data class Found(val product: OffProduct) : OffResult

    data object NotFound : OffResult

    data object Unavailable : OffResult
}

/**
 * Open Food Facts product lookup (API v3, `GET /api/v3/product/{barcode}`), read only and without an account.
 * Sends the User-Agent Open Food Facts asks for and keeps under its limit of 15 product reads per minute
 * (over it, a lookup is reported unavailable instead of waiting). Any network or server failure is
 * [OffResult.Unavailable]; a product without the basic nutrition facts is [OffResult.NotFound].
 */
class OpenFoodFactsClient(
    private val http: HttpClient,
    appVersion: String,
    private val now: () -> Long,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) {
    private val userAgent = "Ironlog/$appVersion (+https://github.com/Sebas1705/Ironlog)"
    private val recent = ArrayDeque<Long>()

    suspend fun product(barcode: String): OffResult {
        if (!isBarcode(barcode)) return OffResult.NotFound
        if (!takePermit()) return OffResult.Unavailable
        return try {
            val response = http.get("$baseUrl/api/v3/product/$barcode?fields=$FIELDS") {
                header(HttpHeaders.UserAgent, userAgent)
            }
            when (response.status) {
                HttpStatusCode.OK -> parse(barcode, response.bodyAsText())
                HttpStatusCode.NotFound -> OffResult.NotFound
                else -> OffResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // Offline, DNS, TLS, timeout: the engines throw different types.
            OffResult.Unavailable
        }
    }

    private fun takePermit(): Boolean {
        val t = now()
        while (recent.isNotEmpty() && t - recent.first() >= WINDOW_MILLIS) recent.removeFirst()
        if (recent.size >= MAX_READS_PER_WINDOW) return false
        recent.addLast(t)
        return true
    }

    companion object {
        const val MAX_READS_PER_WINDOW = 15
        private const val WINDOW_MILLIS = 60_000L
        private const val KJ_PER_KCAL = 4.184
        private const val FIELDS =
            "code,product_name,product_name_en,product_name_es,brands,nutriments,serving_size,serving_quantity," +
                "product_quantity_unit,quantity,nutrition_data_per"
        private val Lenient = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        private val BARCODE = Regex("^\\d{8,14}$")
        private val LIQUID = Regex("\\d\\s*(ml|cl|l)\\b", RegexOption.IGNORE_CASE)

        fun isBarcode(text: String): Boolean = BARCODE.matches(text)

        /** Parses a v3 product response; NotFound when it has no product or lacks energy and macros. */
        internal fun parse(barcode: String, body: String): OffResult {
            val root = runCatching { Lenient.parseToJsonElement(body).jsonObject }.getOrNull() ?: return OffResult.Unavailable
            val product = root["product"] as? JsonObject ?: return OffResult.NotFound
            val per100 = (product["nutriments"] as? JsonObject)?.let(::nutrients) ?: return OffResult.NotFound
            val generic = product.text("product_name")
            val en = product.text("product_name_en") ?: generic
            val es = product.text("product_name_es") ?: generic
            if (en == null && es == null) return OffResult.NotFound
            return OffResult.Found(
                OffProduct(
                    barcode = barcode,
                    nameEn = en ?: es.orEmpty(),
                    nameEs = es ?: en.orEmpty(),
                    brand = product.text("brands")?.split(',')?.firstOrNull()?.trim()?.ifBlank { null },
                    per100 = per100,
                    unit = if (isLiquid(product)) FoodUnit.ML else FoodUnit.G,
                    servingName = product.text("serving_size"),
                    servingGrams = product.number("serving_quantity")?.takeIf { it > 0 },
                ),
            )
        }

        /** Nutrients per 100 g; null without energy and the three macros. Energy falls back to kJ / 4.184. */
        private fun nutrients(n: JsonObject): Nutrients? {
            val kcal = n.number("energy-kcal_100g") ?: n.number("energy_100g")?.div(KJ_PER_KCAL) ?: return null
            return Nutrients(
                kcal = kcal,
                protein = n.number("proteins_100g") ?: return null,
                carbs = n.number("carbohydrates_100g") ?: return null,
                fat = n.number("fat_100g") ?: return null,
                fiber = n.number("fiber_100g"),
                sugar = n.number("sugars_100g"),
                salt = n.number("salt_100g"),
            )
        }

        private fun isLiquid(product: JsonObject): Boolean =
            product.text("nutrition_data_per") == "100ml" ||
                product.text("product_quantity_unit")?.lowercase() == "ml" ||
                product.text("quantity")?.let { LIQUID.containsMatchIn(it) } == true

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }

        /** OFF sends numbers as numbers or strings. Negative values are treated as missing. */
        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)
            ?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
            ?.takeIf { it >= 0 && it.isFinite() }
    }
}

/** The HTTP client for [OpenFoodFactsClient]: the platform engine with a 10 s timeout. */
fun openFoodFactsHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) { requestTimeoutMillis = OFF_TIMEOUT_MILLIS }
}

private const val OFF_TIMEOUT_MILLIS = 10_000L
