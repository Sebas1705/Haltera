package es.sebas1705.haltera.domain.usecases.stats

import kotlin.math.abs

/** A fitted line y = slope × x + intercept; [r2] is the share of the variation the line explains (0–1). */
data class Trend(val slope: Double, val intercept: Double, val r2: Double, val n: Int) {
    fun at(x: Double): Double = slope * x + intercept
}

/** Ordinary least squares on (x, y) points (Metric.ONE_RM_TREND, Metric.BODY_WEIGHT_RATE). */
object LinearRegression {
    private const val EPSILON = 1e-12

    /** Null with fewer than two points or when every x is the same. */
    fun fit(points: List<Pair<Double, Double>>): Trend? {
        if (points.size < 2) return null
        val meanX = points.sumOf { it.first } / points.size
        val meanY = points.sumOf { it.second } / points.size
        val sxx = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        if (sxx < EPSILON) return null
        val sxy = points.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val syy = points.sumOf { (_, y) -> (y - meanY) * (y - meanY) }
        val slope = sxy / sxx
        val intercept = meanY - slope * meanX
        val r2 = if (abs(syy) < EPSILON) 1.0 else (sxy * sxy) / (sxx * syy)
        return Trend(slope, intercept, r2.coerceIn(0.0, 1.0), points.size)
    }
}
