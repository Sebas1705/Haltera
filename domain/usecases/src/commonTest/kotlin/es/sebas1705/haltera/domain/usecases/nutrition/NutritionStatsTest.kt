package es.sebas1705.haltera.domain.usecases.nutrition

import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.Food
import es.sebas1705.haltera.data.models.FoodDay
import es.sebas1705.haltera.data.models.FoodSource
import es.sebas1705.haltera.data.models.Meal
import es.sebas1705.haltera.data.models.MealEntry
import es.sebas1705.haltera.data.models.Nutrients
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionStatsTest {
    private val food = Food(
        id = "f",
        source = FoodSource.USER,
        nameEn = "Food",
        nameEs = "Comida",
        per100 = Nutrients(kcal = 100.0, protein = 10.0, carbs = 10.0, fat = 2.0),
    )

    private fun date(day: Int) = LocalDate(2026, 10, day)

    private fun day(day: Int, grams: Double) = FoodDay(
        date(day),
        if (grams == 0.0) {
            emptyList()
        } else {
            listOf(
                MealEntry("e$day", date(day), Meal.LUNCH, "f", grams, null, 0, 0, food)
            )
        },
    )

    private fun intake(day: Int, kcal: Double, protein: Double = 0.0, carbs: Double = 0.0, fat: Double = 0.0) =
        DailyIntake(date(day), Nutrients(kcal = kcal, protein = protein, carbs = carbs, fat = fat))

    @Test
    fun `daily totals keep only logged days, in date order`() {
        val daily = NutritionStats.daily(listOf(day(14, 2_000.0), day(12, 1_500.0), day(13, 0.0)))
        assertEquals(listOf(date(12), date(14)), daily.map { it.date })
        assertEquals(1_500.0, daily[0].nutrients.kcal, 1e-9)
        assertEquals(1_750.0, NutritionStats.averagePerDay(daily)!!.kcal, 1e-9)
        assertNull(NutritionStats.averagePerDay(emptyList()))
    }

    @Test
    fun `macro split is the energy share of each macro`() {
        // 100 g protein (400 kcal), 200 g carbs (800 kcal), 89 g fat (801 kcal)
        val split = NutritionStats.macroSplit(Nutrients(protein = 100.0, carbs = 200.0, fat = 88.888_888_9))!!
        assertEquals(20.0, split.proteinPct, 1e-6)
        assertEquals(40.0, split.carbsPct, 1e-6)
        assertEquals(40.0, split.fatPct, 1e-6)
        assertNull(NutritionStats.macroSplit(Nutrients(kcal = 50.0)))
    }

    @Test
    fun `weekly averages group Monday to Sunday over logged days`() {
        // 2026-10-12 is a Monday; 10-18 a Sunday; 10-19 the next Monday.
        val weeks = NutritionStats.weeklyAverages(
            listOf(intake(12, 2_000.0, protein = 100.0), intake(18, 1_000.0, protein = 50.0), intake(19, 3_000.0)),
        )
        assertEquals(listOf(date(12), date(19)), weeks.map { it.weekStart })
        assertEquals(2, weeks[0].daysLogged)
        assertEquals(1_500.0, weeks[0].kcal, 1e-9)
        assertEquals(75.0, weeks[0].protein, 1e-9)
        assertEquals(date(12), NutritionStats.weekStart(date(15)))
    }

    @Test
    fun `adherence counts logged days within 10 percent of the goal`() {
        val daily = listOf(intake(12, 1_800.0), intake(13, 2_200.0), intake(14, 2_250.0), intake(15, 1_700.0))
        val adherence = NutritionStats.adherence(daily, goalKcal = 2_000)!!
        assertEquals(2, adherence.daysWithin)
        assertEquals(4, adherence.daysLogged)
        assertEquals(0.5, adherence.fraction, 1e-9)
        assertNull(NutritionStats.adherence(daily, goalKcal = null))
        assertEquals(0.0, NutritionStats.adherence(emptyList(), 2_000)!!.fraction)
    }

    @Test
    fun `protein per kg uses the body weight in grams`() {
        assertEquals(2.0, NutritionStats.proteinPerKg(160.0, 80_000)!!, 1e-9)
        assertNull(NutritionStats.proteinPerKg(160.0, null))
    }

    @Test
    fun `moving average spans the calendar window and skips missing days`() {
        val points = listOf(
            DatedValue(date(1), 10.0),
            DatedValue(date(2), 20.0),
            DatedValue(date(5), 30.0),
            DatedValue(date(9), 40.0),
        )
        val avg = NutritionStats.movingAverage(points)
        assertEquals(listOf(10.0, 15.0, 20.0, 35.0), avg.map { it.value }) // day 9 window: days 3–9
    }

    @Test
    fun `intake and weight trends cover the logged period`() {
        val utc = TimeZone.UTC
        val dayMillis = 86_400_000L
        val oct1 = 1_790_812_800_000L // 2026-10-01T00:00Z
        val weights = listOf(
            BodyWeightEntry("w0", oct1 - dayMillis * 10, 90_000, ""), // before the logged period
            BodyWeightEntry("w1", oct1 + dayMillis, 80_000, ""), // 2026-10-02
            BodyWeightEntry("w2", oct1 + dayMillis + 3_600_000, 81_000, ""), // same day: averaged
            BodyWeightEntry("w3", oct1 + dayMillis * 2, 79_000, ""), // 2026-10-03
        )
        val trend = NutritionStats.intakeVsWeight(listOf(intake(2, 2_000.0), intake(3, 1_000.0)), weights, utc)
        assertEquals(listOf(2_000.0, 1_500.0), trend.kcal.map { it.value })
        assertEquals(listOf(date(2), date(3)), trend.weightKg.map { it.date })
        assertEquals(80.5, trend.weightKg[0].value, 1e-9)
        assertEquals((80.5 + 79.0) / 2, trend.weightKg[1].value, 1e-9)
        assertEquals(emptyList(), NutritionStats.intakeVsWeight(emptyList(), weights, utc).weightKg)
    }

    @Test
    fun `within goal is symmetric around the goal`() {
        assertEquals(true, NutritionStats.withinGoal(2_200.0, 2_000))
        assertEquals(false, NutritionStats.withinGoal(2_201.0, 2_000))
        assertEquals(false, NutritionStats.withinGoal(100.0, 0))
    }
}
