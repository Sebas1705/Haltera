package es.sebas1705.ironlog.domain.usecases.stats

/** One day of the training heatmap. [level] is 0 (no session) to 4; [future] days are after today. */
data class HeatCell(val day: Int, val sessions: Int, val effectiveSets: Int, val level: Int, val future: Boolean)

/**
 * Training days in calendar weeks (Metric.TRAINING_HEATMAP): [weeks] are columns oldest first, each
 * Monday to Sunday. [thresholds] are the effective-set limits of levels 1–3 (level 4 is above the last).
 */
data class Heatmap(val weeks: List<List<HeatCell>>, val thresholds: List<Int>) {
    val trainingDays: Int get() = weeks.sumOf { w -> w.count { it.sessions > 0 } }
}

object Heatmaps {
    const val MIN_WEEKS = 13
    const val MAX_WEEKS = 53
    const val MAX_LEVEL = 4
    private const val QUARTILES = 4

    /** Weeks shown for a period: its length in weeks, at least [MIN_WEEKS], at most [MAX_WEEKS]. */
    fun weeksFor(period: StatsPeriod): Int =
        period.days?.let { it / EpochDays.DAYS_PER_WEEK }?.coerceIn(MIN_WEEKS, MAX_WEEKS) ?: MAX_WEEKS

    /**
     * The last [weeks] calendar weeks ending with [today]'s week. A day with sessions is level 1–4 by
     * its effective sets: the training days shown are split into quartiles (nearest rank), so the
     * levels adapt to how much you usually do. A day with sessions but no effective set is level 1.
     */
    fun build(sessions: List<DatedSession>, today: Int, weeks: Int = MIN_WEEKS): Heatmap {
        require(weeks > 0) { "weeks must be positive" }
        val firstDay = EpochDays.weekStart(today) - (weeks - 1) * EpochDays.DAYS_PER_WEEK
        val byDay = sessions.filter { !it.session.inProgress && it.day in firstDay..today }.groupBy { it.day }
        val setsByDay = byDay.mapValues { (_, list) -> list.sumOf { TrainingMath.effectiveSets(it.session) } }
        val thresholds = quartiles(setsByDay.values.filter { it > 0 })
        val columns = (0 until weeks).map { w ->
            (0 until EpochDays.DAYS_PER_WEEK).map { d ->
                val day = firstDay + w * EpochDays.DAYS_PER_WEEK + d
                val count = byDay[day]?.size ?: 0
                val sets = setsByDay[day] ?: 0
                HeatCell(day, count, sets, level(count, sets, thresholds), future = day > today)
            }
        }
        return Heatmap(columns, thresholds)
    }

    /** The 25th, 50th and 75th percentiles (nearest rank) of [values]; empty when there are none. */
    fun quartiles(values: List<Int>): List<Int> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        return (1 until QUARTILES).map { q ->
            val rank = (q * sorted.size + QUARTILES - 1) / QUARTILES // ceil(q / 4 × n)
            sorted[(rank - 1).coerceIn(0, sorted.lastIndex)]
        }
    }

    fun level(sessions: Int, effectiveSets: Int, thresholds: List<Int>): Int = when {
        sessions == 0 -> 0
        effectiveSets <= 0 || thresholds.isEmpty() -> 1
        else -> 1 + thresholds.count { effectiveSets > it }
    }
}
