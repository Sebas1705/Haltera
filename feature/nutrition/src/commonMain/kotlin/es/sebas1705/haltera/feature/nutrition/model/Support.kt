package es.sebas1705.haltera.feature.nutrition.model

import es.sebas1705.haltera.core.units.Numbers
import es.sebas1705.haltera.core.units.appLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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

    /** Parses a positive amount typed as "150", "1.5" or "1,5"; null when it is not one. */
    fun parse(text: String): Double? {
        val value = Numbers.parse(text) ?: return null
        return value.takeIf { it > 0 && it < MAX_AMOUNT && it.isFinite() }
    }

    /** Parses an optional non-negative value (nutrients): blank is null, invalid is [Invalid]. */
    fun parseOptional(text: String): ParsedValue {
        if (text.isBlank()) return ParsedValue.Blank
        val value = Numbers.parse(text) ?: return ParsedValue.Invalid
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

    /**
     * A number for display through core:units [Numbers] (decimal comma and grouping in Spanish): at most
     * [decimals] decimals, trailing zeros removed.
     */
    fun format(value: Double, decimals: Int = 1, language: String = appLanguage()): String =
        Numbers.format(value, maxDecimals = decimals, language = language)

    /** A number to put back into an input field: like [format] but without thousands grouping. */
    fun input(value: Double, decimals: Int = 2): String =
        Numbers.format(value, maxDecimals = decimals, grouping = false)

    /** A percentage ("31 %" / "31%"); [value] is already in percent. */
    fun percent(value: Double, decimals: Int = 0): String = Numbers.percent(value, decimals)
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
