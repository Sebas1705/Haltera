package es.sebas1705.ironlog.domain.usecases.nutrition

import es.sebas1705.ironlog.data.models.ActivityLevel
import es.sebas1705.ironlog.data.models.NutritionProfile
import es.sebas1705.ironlog.data.models.Objective
import es.sebas1705.ironlog.data.models.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalCalculatorTest {
    @Test
    fun `Mifflin-St Jeor resting energy for men and women`() {
        // 10 × 80 + 6.25 × 180 − 5 × 30 + 5 = 1780
        assertEquals(1_780.0, GoalCalculator.bmr(Sex.MALE, 80.0, 180.0, 30), 1e-9)
        // 10 × 60 + 6.25 × 165 − 5 × 25 − 161 = 1345.25
        assertEquals(1_345.25, GoalCalculator.bmr(Sex.FEMALE, 60.0, 165.0, 25), 1e-9)
    }

    @Test
    fun `maintenance applies the activity factor and the goal the objective adjustment`() {
        val maintain = GoalCalculator.suggest(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Objective.MAINTAIN)
        assertEquals(1_780.0 * 1.55, maintain.maintenance, 1e-9)
        assertEquals(2_760, maintain.kcal) // 2759 rounded to 10
        val lose = GoalCalculator.suggest(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Objective.LOSE)
        assertEquals(2_210, lose.kcal) // 2759 × 0.8 = 2207.2
        val gain = GoalCalculator.suggest(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Objective.GAIN)
        assertEquals(3_030, gain.kcal) // 2759 × 1.1 = 3034.9
    }

    @Test
    fun `macros are protein by weight, a quarter of the energy from fat and the rest carbohydrates`() {
        val s = GoalCalculator.suggest(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Objective.LOSE)
        assertEquals(160, s.proteinG) // 2.0 g/kg
        assertEquals(61, s.fatG) // 2210 × 0.25 / 9 = 61.4
        assertEquals(255, s.carbsG) // (2210 − 640 − 549) / 4 = 255.25
        assertEquals(2.0, s.proteinPerKg)
        assertEquals(1.6, GoalCalculator.proteinPerKg(Objective.MAINTAIN))
    }

    @Test
    fun `carbohydrates never go negative`() {
        val s = GoalCalculator.suggest(Sex.FEMALE, 250.0, 100.0, 100, ActivityLevel.SEDENTARY, Objective.LOSE)
        assertEquals(0, s.carbsG)
    }

    @Test
    fun `a profile without the data gives no suggestion`() {
        val full = NutritionProfile(Sex.FEMALE, birthYear = 2000, heightCm = 165, ActivityLevel.LIGHT, Objective.MAINTAIN)
        assertEquals(1_340.25, GoalCalculator.suggest(full, 60.0, currentYear = 2026)!!.bmr, 1e-9)
        assertNull(GoalCalculator.suggest(full.copy(sex = null), 60.0, 2026))
        assertNull(GoalCalculator.suggest(full.copy(heightCm = null), 60.0, 2026))
        assertNull(GoalCalculator.suggest(full.copy(birthYear = 2020), 60.0, 2026))
        assertNull(GoalCalculator.suggest(full, null, 2026))
        assertNull(GoalCalculator.suggest(full, 10.0, 2026))
    }

    @Test
    fun `percentages and grams convert through the kcal goal`() {
        assertEquals(150, GoalCalculator.gramsFromPercent(2_000, 30, GoalCalculator.KCAL_PER_G_PROTEIN))
        assertEquals(67, GoalCalculator.gramsFromPercent(2_000, 30, GoalCalculator.KCAL_PER_G_FAT))
        assertEquals(30, GoalCalculator.percentFromGrams(2_000, 150, GoalCalculator.KCAL_PER_G_CARBS))
        assertEquals(0, GoalCalculator.percentFromGrams(0, 150, GoalCalculator.KCAL_PER_G_CARBS))
    }
}
