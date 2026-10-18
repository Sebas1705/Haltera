package es.sebas1705.haltera.core.units

import kotlin.math.roundToLong

/**
 * Display and entry unit for body measurements (waist, arm…). Lengths are always stored as whole millimetres
 * (`Long`); there is no separate setting: it follows the weight unit ([forWeightUnit]), metric with kg and imperial
 * with lb.
 */
enum class LengthUnit {
    CM, IN;

    companion object {
        fun forWeightUnit(unit: WeightUnit): LengthUnit = if (unit == WeightUnit.LB) IN else CM
    }
}

/**
 * Conversions between stored millimetres and the display units. Centimetres are shown with one decimal (exact: a
 * millimetre is 0.1 cm) and inches with two (0.01 in = 0.254 mm, under half a millimetre), so a stored value shown
 * and typed back unchanged always gives the same millimetres.
 */
object Lengths {
    const val MM_PER_CM = 10L

    /** International inch. */
    const val MM_PER_IN = 25.4

    /** Decimals shown for [unit]: the fewest that round-trip every whole millimetre. */
    fun decimals(unit: LengthUnit): Int = when (unit) {
        LengthUnit.CM -> 1
        LengthUnit.IN -> 2
    }

    fun toMillimetres(value: Double, unit: LengthUnit): Long = when (unit) {
        LengthUnit.CM -> (value * MM_PER_CM).roundToLong()
        LengthUnit.IN -> (value * MM_PER_IN).roundToLong()
    }

    fun fromMillimetres(mm: Long, unit: LengthUnit): Double = when (unit) {
        LengthUnit.CM -> mm.toDouble() / MM_PER_CM
        LengthUnit.IN -> mm / MM_PER_IN
    }

    /** [mm] in [unit], no trailing zeros, in the app [language]: 825 mm -> "82.5" (cm) / "32.48" (in). */
    fun format(mm: Long, unit: LengthUnit, language: String = appLanguage(), grouping: Boolean = true): String =
        Numbers.format(
            fromMillimetres(mm, unit),
            maxDecimals = decimals(unit),
            language = language,
            grouping = grouping
        )

    /** Parses a typed length ("82,5" or "82.5") in [unit] to millimetres, or null when it is not a number. */
    fun parse(text: String, unit: LengthUnit): Long? =
        Numbers.parse(text)?.takeIf { it.isFinite() }?.let { toMillimetres(it, unit) }
}

/** Body fat is stored in tenths of a percent (`Long`): 18.5 % is 185. */
object BodyFat {
    const val TENTHS_PER_PERCENT = 10L

    fun toTenths(percent: Double): Long = (percent * TENTHS_PER_PERCENT).roundToLong()

    fun toPercent(tenths: Long): Double = tenths.toDouble() / TENTHS_PER_PERCENT

    /** "18.5" / "18,5" (no percent sign; [Numbers.percent] adds one where it is wanted). */
    fun format(tenths: Long, language: String = appLanguage()): String =
        Numbers.format(toPercent(tenths), maxDecimals = 1, language = language, grouping = false)

    fun parse(text: String): Long? = Numbers.parse(text)?.takeIf { it.isFinite() }?.let(::toTenths)
}
