package es.sebas1705.ironlog.domain.usecases.stats

/** A body-weight entry on its local day ([at] keeps the exact time for the trend). */
data class DatedWeight(val day: Int, val at: Long, val grams: Long)

/** Average of one calendar week (Metric.BODY_WEIGHT_WEEKLY); [change] is against the previous week with entries. */
data class WeeklyWeight(
    val weekStart: Int,
    val averageGrams: Double,
    val entries: Int,
    val minGrams: Long,
    val maxGrams: Long,
    val change: Double?,
)

/** Change per week (Metric.BODY_WEIGHT_RATE) fitted over [entries] entries of the last [days] days. */
data class WeightRate(val gramsPerWeek: Double, val entries: Int, val days: Int, val r2: Double)

object BodyWeightStats {
    const val RATE_WINDOW_DAYS = 28
    const val MIN_RATE_ENTRIES = 3
    const val MIN_RATE_SPAN_DAYS = 7
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** Weekly (Monday to Sunday) averages of [entries], latest week first; weeks without entries are skipped. */
    fun weeklyAverages(entries: List<DatedWeight>): List<WeeklyWeight> {
        val weeks = entries.groupBy { EpochDays.weekStart(it.day) }.toList().sortedBy { it.first }
        var previous: Double? = null
        return weeks.map { (start, list) ->
            val average = list.sumOf { it.grams }.toDouble() / list.size
            val week = WeeklyWeight(
                weekStart = start,
                averageGrams = average,
                entries = list.size,
                minGrams = list.minOf { it.grams },
                maxGrams = list.maxOf { it.grams },
                change = previous?.let { average - it },
            )
            previous = average
            week
        }.reversed()
    }

    /**
     * Least-squares slope of the entries of the last [days] days (today included) × 7. Null with fewer
     * than [MIN_RATE_ENTRIES] entries or when they span less than [MIN_RATE_SPAN_DAYS] days.
     */
    fun ratePerWeek(entries: List<DatedWeight>, today: Int, days: Int = RATE_WINDOW_DAYS): WeightRate? {
        val recent = entries.filter { it.day in (today - days + 1)..today }
        if (recent.size < MIN_RATE_ENTRIES) return null
        if (recent.maxOf { it.day } - recent.minOf { it.day } < MIN_RATE_SPAN_DAYS) return null
        val trend = LinearRegression.fit(recent.map { it.at / MILLIS_PER_DAY to it.grams.toDouble() }) ?: return null
        return WeightRate(trend.slope * EpochDays.DAYS_PER_WEEK, recent.size, days, trend.r2)
    }
}
