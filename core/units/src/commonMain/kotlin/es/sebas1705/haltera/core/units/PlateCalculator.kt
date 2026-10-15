package es.sebas1705.haltera.core.units

/** A plate size and how many pairs are available (one plate per side each); null means unlimited. */
data class PlateStock(val grams: Long, val pairs: Int? = null)

/**
 * Plates to load per side for a target total. [achievedGrams] is bar + 2 × sum([perSide]); it is below
 * the target by [missingGrams] when the target cannot be built exactly with the available plates.
 */
data class PlateLoadout(
    val barGrams: Long,
    /** Plates for one side, heaviest first. */
    val perSide: List<Long>,
    val achievedGrams: Long,
    val missingGrams: Long,
) {
    val isExact: Boolean get() = missingGrams == 0L
}

object PlateCalculator {
    /** lb plates converted to grams are rounded; a few grams of difference still count as a match. */
    const val TOLERANCE_GRAMS = 10L

    /** Common gym plates in kg (as grams). */
    val KG_PLATES: List<Long> = listOf(25_000, 20_000, 15_000, 10_000, 5_000, 2_500, 1_250)

    /** Common gym plates in lb (as grams). */
    val LB_PLATES: List<Long> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5).map { Weights.lbToGrams(it) }

    fun defaultPlates(unit: WeightUnit): List<Long> = if (unit == WeightUnit.KG) KG_PLATES else LB_PLATES

    /** Unlimited stock of each plate size. */
    fun platesPerSide(targetGrams: Long, barGrams: Long, plates: List<Long>): PlateLoadout =
        platesPerSideFromStock(targetGrams, barGrams, plates.map { PlateStock(it) })

    /**
     * Fills each side heaviest plate first, never exceeding the target. With standard plate sets this
     * is exact whenever the target is reachable; otherwise it returns the closest load below the target.
     * A target at or below the bar returns no plates.
     */
    fun platesPerSideFromStock(targetGrams: Long, barGrams: Long, stock: List<PlateStock>): PlateLoadout {
        var remainingPerSide = (targetGrams - barGrams).coerceAtLeast(0) / 2
        val perSide = mutableListOf<Long>()
        stock.filter { it.grams > 0 }.sortedByDescending { it.grams }.forEach { plate ->
            var available = plate.pairs ?: Int.MAX_VALUE
            while (available > 0 && plate.grams <= remainingPerSide + TOLERANCE_GRAMS) {
                perSide += plate.grams
                remainingPerSide -= plate.grams
                available--
            }
        }
        val achieved = barGrams + 2 * perSide.sum()
        return PlateLoadout(
            barGrams = barGrams,
            perSide = perSide,
            achievedGrams = achieved,
            missingGrams = (targetGrams - achieved).takeIf { it > TOLERANCE_GRAMS } ?: 0,
        )
    }

    /** Total load for a bar and the plates on one side. */
    fun total(barGrams: Long, perSide: List<Long>): Long = barGrams + 2 * perSide.sum()

    /** Smallest total jump the plates allow: two of the lightest plate. */
    fun smallestIncrement(plates: List<Long>): Long = (plates.filter { it > 0 }.minOrNull() ?: 0L) * 2
}
