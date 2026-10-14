package es.sebas1705.ironlog.core.units

import kotlin.math.abs
import kotlin.math.roundToLong

/** Display and entry unit for weights. Loads are always stored as grams (`Long`). */
enum class WeightUnit {
    KG, LB;

    companion object {
        fun fromName(value: String?): WeightUnit = entries.firstOrNull { it.name == value } ?: KG
    }
}

/** Conversions between stored grams and the display units (spec §3.3: no floating point in storage). */
object Weights {
    const val GRAMS_PER_KG = 1_000L

    /** International avoirdupois pound. */
    const val GRAMS_PER_LB = 453.59237

    fun kgToGrams(kg: Double): Long = (kg * GRAMS_PER_KG).roundToLong()

    fun lbToGrams(lb: Double): Long = (lb * GRAMS_PER_LB).roundToLong()

    fun gramsToKg(grams: Long): Double = grams.toDouble() / GRAMS_PER_KG

    fun gramsToLb(grams: Long): Double = grams / GRAMS_PER_LB

    fun kgToLb(kg: Double): Double = kg * GRAMS_PER_KG / GRAMS_PER_LB

    fun lbToKg(lb: Double): Double = lb * GRAMS_PER_LB / GRAMS_PER_KG

    fun toGrams(value: Double, unit: WeightUnit): Long = when (unit) {
        WeightUnit.KG -> kgToGrams(value)
        WeightUnit.LB -> lbToGrams(value)
    }

    fun fromGrams(grams: Long, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> gramsToKg(grams)
        WeightUnit.LB -> gramsToLb(grams)
    }

    /**
     * [grams] in [unit] with at most [decimals] decimals and no trailing zeros: 62_500 g -> "62.5",
     * 60_000 g -> "60". Locale independent (always a dot).
     */
    fun format(grams: Long, unit: WeightUnit, decimals: Int = 2): String {
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = (fromGrams(grams, unit) * factor).roundToLong()
        val sign = if (scaled < 0) "-" else ""
        val whole = abs(scaled) / factor
        val fraction = (abs(scaled) % factor).toString().padStart(decimals, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }
}

enum class Rounding { NEAREST, DOWN, UP }

/**
 * Rounds [grams] to a multiple of [incrementGrams] (e.g. the smallest available jump: 2.5 kg on a
 * barbell with 1.25 kg plates). Ties round up with [Rounding.NEAREST]. A non-positive increment
 * returns [grams] unchanged.
 */
fun roundToIncrement(grams: Long, incrementGrams: Long, rounding: Rounding = Rounding.NEAREST): Long {
    if (incrementGrams <= 0) return grams
    val down = grams.floorDiv(incrementGrams) * incrementGrams
    if (down == grams) return grams
    val up = down + incrementGrams
    return when (rounding) {
        Rounding.DOWN -> down
        Rounding.UP -> up
        Rounding.NEAREST -> if (grams - down < up - grams) down else up
    }
}
