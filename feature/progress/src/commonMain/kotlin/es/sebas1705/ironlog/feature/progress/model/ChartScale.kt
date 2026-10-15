package es.sebas1705.ironlog.feature.progress.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A y axis from [min] to [max] with a tick every [step]. */
internal data class AxisScale(val min: Double, val max: Double, val step: Double) {
    private companion object {
        const val MIDDLE = 0.5
    }

    val ticks: List<Double>
        get() {
            val count = ((max - min) / step).roundToInt()
            return (0..count).map { min + it * step }
        }

    /** Position of [value] between min (0) and max (1). */
    fun fraction(value: Double): Double = if (max == min) MIDDLE else (value - min) / (max - min)
}

/** Axis maths for the Canvas charts: "nice" ticks (1, 2, 2.5 or 5 × 10ⁿ) and label thinning. */
internal object ChartScale {
    private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)
    private const val FLAT_PADDING = 0.1
    private const val DEFAULT_TICKS = 4
    private const val THOUSAND = 1_000.0
    private const val MILLION = 1_000_000.0
    private const val TEN = 10.0
    private const val EPSILON = 1e-9
    private const val TINY = 1e-12

    /**
     * A scale covering [minValue]..[maxValue] with about [targetTicks] intervals. [includeZero] starts
     * bars at 0. A flat range is padded so the line sits in the middle.
     */
    fun nice(
        minValue: Double,
        maxValue: Double,
        targetTicks: Int = DEFAULT_TICKS,
        includeZero: Boolean = false
    ): AxisScale {
        var low = if (includeZero) minOf(0.0, minValue) else minValue
        var high = if (includeZero) maxOf(0.0, maxValue) else maxValue
        if (high - low < EPSILON) {
            val pad = if (abs(high) < EPSILON) 1.0 else abs(high) * FLAT_PADDING
            high += pad
            low = if (includeZero && low >= 0) 0.0 else low - pad
        }
        val rough = (high - low) / targetTicks.coerceAtLeast(1)
        val magnitude = TEN.pow(floor(log10(rough)))
        val step = NICE_STEPS.first { it * magnitude >= rough - TINY } * magnitude
        return AxisScale(floor(low / step + EPSILON) * step, ceil(high / step - EPSILON) * step, step)
    }

    /** Indices of at most [maxLabels] evenly spread labels of [count] items, always with the first and last. */
    fun labelIndices(count: Int, maxLabels: Int): List<Int> {
        if (count <= 0 || maxLabels <= 0) return emptyList()
        if (count <= maxLabels) return (0 until count).toList()
        if (maxLabels == 1) return listOf(count - 1)
        val step = (count - 1).toDouble() / (maxLabels - 1)
        return (0 until maxLabels).map { (it * step).roundToInt() }.distinct()
    }

    /** "950", "12.5k", "1.2M": short numbers for axis ticks. */
    fun compact(value: Double): String {
        val absolute = abs(value)
        return when {
            absolute >= MILLION -> trim(value / MILLION) + "M"
            absolute >= TEN * THOUSAND -> trim(value / THOUSAND, decimals = 0) + "k"
            absolute >= THOUSAND -> trim(value / THOUSAND) + "k"
            else -> trim(value)
        }
    }

    /** [value] with up to [decimals] decimals and no trailing zeros. */
    fun trim(value: Double, decimals: Int = 1): String {
        val factor = TEN.pow(decimals)
        val rounded = (value * factor).roundToLong() / factor
        val text = rounded.toString()
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }
}
