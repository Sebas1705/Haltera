package es.sebas1705.ironlog.domain.usecases.nutrition

import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.models.FoodDay
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_CARBS
import es.sebas1705.ironlog.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_FAT
import es.sebas1705.ironlog.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_PROTEIN
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What was eaten on one logged day. */
data class DailyIntake(val date: LocalDate, val nutrients: Nutrients)

/** Share of the macro energy from each macro, in percent (sums to 100). */
data class MacroSplit(val proteinPct: Double, val carbsPct: Double, val fatPct: Double)

/** Means over the logged days of one Monday-to-Sunday week. */
data class WeeklyAverage(
    val weekStart: LocalDate,
    val daysLogged: Int,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class Adherence(val daysWithin: Int, val daysLogged: Int) {
    val fraction: Double get() = if (daysLogged == 0) 0.0 else daysWithin.toDouble() / daysLogged
}

data class DatedValue(val date: LocalDate, val value: Double)

/** Two 7-day moving averages to compare eating with weight change. */
data class IntakeWeightTrend(val kcal: List<DatedValue>, val weightKg: List<DatedValue>)

/**
 * Nutrition statistics (design §1). Descriptive only, and over **logged days**: a day without entries is
 * unknown, not a day of zero kcal, so it never lowers an average or counts against adherence.
 */
@Suppress("TooManyFunctions") // One small pure function per statistic.
object NutritionStats {
    const val ADHERENCE_TOLERANCE = 0.10
    const val MOVING_AVERAGE_DAYS = 7
    private const val GRAMS_PER_KG = 1_000.0
    private const val PERCENT = 100.0

    fun daily(days: List<FoodDay>): List<DailyIntake> =
        days.filter { it.entries.isNotEmpty() }.map { DailyIntake(it.date, it.totals) }.sortedBy { it.date }

    /** Mean nutrients per logged day, or null without logged days. */
    fun averagePerDay(daily: List<DailyIntake>): Nutrients? =
        if (daily.isEmpty()) null else daily.fold(Nutrients.ZERO) { sum, d -> sum + d.nutrients } * (1.0 / daily.size)

    /** Energy share of protein, carbohydrate and fat (4/4/9 kcal per g); null when there is none. */
    fun macroSplit(n: Nutrients): MacroSplit? {
        val p = n.protein * KCAL_PER_G_PROTEIN
        val c = n.carbs * KCAL_PER_G_CARBS
        val f = n.fat * KCAL_PER_G_FAT
        val total = p + c + f
        if (total <= 0) return null
        return MacroSplit(p / total * PERCENT, c / total * PERCENT, f / total * PERCENT)
    }

    /** Weekly means (weeks start on Monday), oldest first. */
    fun weeklyAverages(daily: List<DailyIntake>): List<WeeklyAverage> =
        daily.groupBy { weekStart(it.date) }
            .map { (start, days) ->
                val n = days.size.toDouble()
                WeeklyAverage(
                    weekStart = start,
                    daysLogged = days.size,
                    kcal = days.sumOf { it.nutrients.kcal } / n,
                    protein = days.sumOf { it.nutrients.protein } / n,
                    carbs = days.sumOf { it.nutrients.carbs } / n,
                    fat = days.sumOf { it.nutrients.fat } / n,
                )
            }
            .sortedBy { it.weekStart }

    fun weekStart(date: LocalDate): LocalDate = date.minus(
        DatePeriod(days = date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal)
    )

    /** Whether [kcal] is within ±[tolerance] of [goal]. */
    fun withinGoal(kcal: Double, goal: Int, tolerance: Double = ADHERENCE_TOLERANCE): Boolean =
        goal > 0 && abs(kcal - goal) <= goal * tolerance

    /** Logged days within ±10 % of the kcal goal; null without a goal. */
    fun adherence(daily: List<DailyIntake>, goalKcal: Int?, tolerance: Double = ADHERENCE_TOLERANCE): Adherence? {
        if (goalKcal == null || goalKcal <= 0) return null
        return Adherence(daily.count { withinGoal(it.nutrients.kcal, goalKcal, tolerance) }, daily.size)
    }

    /** Protein in g per kg of body weight, or null without a weight. */
    fun proteinPerKg(proteinG: Double, weightGrams: Long?): Double? =
        weightGrams?.takeIf { it > 0 }?.let { proteinG / (it / GRAMS_PER_KG) }

    /**
     * Calendar moving average: for each point, the mean of the points within the [days] days ending on its date
     * (missing days are skipped, not counted as zero).
     */
    fun movingAverage(points: List<DatedValue>, days: Int = MOVING_AVERAGE_DAYS): List<DatedValue> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { point ->
            val from = point.date.minus(DatePeriod(days = days - 1))
            val window = sorted.filter { it.date in from..point.date }
            DatedValue(point.date, window.sumOf { it.value } / window.size)
        }
    }

    /** One body-weight value per day (the mean of that day's entries), in kg, by local date. */
    @OptIn(ExperimentalTime::class)
    fun dailyWeights(entries: List<BodyWeightEntry>, timeZone: TimeZone): List<DatedValue> =
        entries.groupBy { Instant.fromEpochMilliseconds(it.measuredAt).toLocalDateTime(timeZone).date }
            .map { (date, list) -> DatedValue(date, list.sumOf { it.weightGrams } / list.size / GRAMS_PER_KG) }
            .sortedBy { it.date }

    /** 7-day moving averages of kcal intake and of body weight over the same period. */
    fun intakeVsWeight(
        daily: List<DailyIntake>,
        weights: List<BodyWeightEntry>,
        timeZone: TimeZone
    ): IntakeWeightTrend {
        val kcal = movingAverage(daily.map { DatedValue(it.date, it.nutrients.kcal) })
        val range = daily.firstOrNull()?.date?.let { first -> first..daily.last().date }
        val weight = movingAverage(dailyWeights(weights, timeZone))
            .let { all -> if (range == null) emptyList() else all.filter { it.date in range } }
        return IntakeWeightTrend(kcal, weight)
    }
}
