package es.sebas1705.ironlog.core.units

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The app language code ("en", "es"). It follows the same source as Compose's `Locale.current`
 * (the per-app locale on Android, the first preferred language on iOS).
 */
expect fun appLanguage(): String

/**
 * The one locale-aware number formatter of the app. Spanish uses a decimal comma and groups thousands
 * from five digits with a narrow no-break space ("79,6", "15 090", "1250"); English uses a decimal point and
 * groups with commas from four digits ("79.6", "15,090", "1,250"). Serialised data never goes through
 * this formatter.
 */
object Numbers {
    private const val GROUP_SIZE = 3
    private const val DECIMAL_BASE = 10L

    /** Spanish (RAE) groups only numbers with five or more integer digits. */
    private const val SPANISH_GROUP_DIGITS = 5
    private const val ENGLISH_GROUP_DIGITS = 4

    /** Narrow no-break space used to group thousands in Spanish. */
    const val GROUP_SPACE = ' '

    /** Typographic minus sign, used for signed values on screen. */
    const val MINUS = '−'

    fun decimalSeparator(language: String = appLanguage()): Char = if (isCommaLanguage(language)) ',' else '.'

    /**
     * [value] with at most [maxDecimals] decimals (at least [minDecimals]; trailing zeros beyond that are
     * dropped). [grouping] groups thousands (turn it off for text that goes back into an input field).
     * [typographicMinus] uses "−" instead of "-".
     */
    @Suppress("LongParameterList")
    fun format(
        value: Double,
        maxDecimals: Int = 2,
        minDecimals: Int = 0,
        language: String = appLanguage(),
        grouping: Boolean = true,
        typographicMinus: Boolean = false,
    ): String {
        var factor = 1L
        repeat(maxDecimals) { factor *= DECIMAL_BASE }
        val scaled = abs(value * factor).roundToLong()
        val whole = (scaled / factor).toString()
        val digits = (scaled % factor).toString().padStart(maxDecimals, '0')
        var fraction = if (maxDecimals > 0) digits.trimEnd('0') else ""
        if (fraction.length < minDecimals) fraction = fraction.padEnd(minDecimals, '0')
        val comma = isCommaLanguage(language)
        val integer = if (grouping) group(whole, comma) else whole
        val negative = value < 0 && scaled != 0L
        val sign = if (!negative) "" else if (typographicMinus) MINUS.toString() else "-"
        return if (fraction.isEmpty()) "$sign$integer" else "$sign$integer${if (comma) ',' else '.'}$fraction"
    }

    /** Integer [value] with grouped thousands: "15 090" / "15,090". */
    fun format(value: Long, language: String = appLanguage(), grouping: Boolean = true): String {
        val comma = isCommaLanguage(language)
        val digits = abs(value).toString()
        val integer = if (grouping) group(digits, comma) else digits
        return if (value < 0) "-$integer" else integer
    }

    /**
     * Parses a number typed by the user, accepting both "," and "." as the decimal separator and ignoring
     * spaces. When both appear, the last one is the decimal separator and the others group thousands.
     */
    fun parse(text: String): Double? {
        val clean = text.trim().filterNot { it == ' ' || it == GROUP_SPACE || it == ' ' }
            .replace(MINUS, '-')
        if (clean.isEmpty()) return null
        val lastSeparator = clean.indexOfLast { it == ',' || it == '.' }
        val normalised = if (lastSeparator < 0) {
            clean
        } else {
            clean.substring(0, lastSeparator).filterNot { it == ',' || it == '.' } + "." +
                clean.substring(lastSeparator + 1)
        }
        return normalised.toDoubleOrNull()
    }

    private fun isCommaLanguage(language: String): Boolean = language.lowercase().startsWith("es")

    private fun group(digits: String, comma: Boolean): String {
        val minDigits = if (comma) SPANISH_GROUP_DIGITS else ENGLISH_GROUP_DIGITS
        if (digits.length < minDigits) return digits
        val separator = if (comma) GROUP_SPACE.toString() else ","
        return digits.reversed().chunked(GROUP_SIZE).joinToString(separator).reversed()
    }
}
