package es.sebas1705.ironlog.feature.nutrition.model

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The UI language ("en", "es"…) used for food names. */
internal fun interface AppLanguage {
    fun code(): String
}

/** Today's local date; replaceable in tests. */
internal fun interface Today {
    fun date(): LocalDate

    companion object {
        @OptIn(ExperimentalTime::class)
        val System = Today { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    }
}

/** How an amount is typed: grams (or ml) or servings of the food. */
internal enum class AmountMode { GRAMS, SERVINGS }

internal object Amounts {
    private const val MAX_AMOUNT = 100_000.0
    private const val DECIMAL_BASE = 10

    /** Parses a positive amount typed as "150", "1.5" or "1,5"; null when it is not one. */
    fun parse(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return value.takeIf { it > 0 && it < MAX_AMOUNT && it.isFinite() }
    }

    /** Parses an optional non-negative value (nutrients): blank is null, invalid is [Invalid]. */
    fun parseOptional(text: String): ParsedValue {
        if (text.isBlank()) return ParsedValue.Blank
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return ParsedValue.Invalid
        return if (value < 0 || !value.isFinite() || value >= MAX_AMOUNT) {
            ParsedValue.Invalid
        } else {
            ParsedValue.Value(
                value
            )
        }
    }

    /** Grams for an [amount] in [mode]; servings need the food's serving size. */
    fun grams(amount: Double, mode: AmountMode, servingGrams: Double?): Double? = when (mode) {
        AmountMode.GRAMS -> amount
        AmountMode.SERVINGS -> servingGrams?.takeIf { it > 0 }?.let { amount * it }
    }

    /** "1.5", "150", "0.25" — at most [decimals] decimals, trailing zeros removed, comma for Spanish. */
    fun format(value: Double, decimals: Int = 1, language: String = "en"): String {
        var factor = 1L
        repeat(decimals) { factor *= DECIMAL_BASE }
        val scaled = (value * factor).roundToLong()
        val sign = if (scaled < 0) "-" else ""
        val whole = abs(scaled) / factor
        val fraction = if (decimals == 0) "" else (abs(scaled) % factor).toString().padStart(decimals, '0').trimEnd('0')
        val separator = if (language == "es") "," else "."
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole$separator$fraction"
    }
}

internal sealed interface ParsedValue {
    data object Blank : ParsedValue

    data object Invalid : ParsedValue

    data class Value(val value: Double) : ParsedValue
}

/** Runs [block], turning failures into a [Result] but letting cancellation through. */
@Suppress("TooGenericExceptionCaught") // Repository failures of any kind become a message, never a crash.
internal suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
