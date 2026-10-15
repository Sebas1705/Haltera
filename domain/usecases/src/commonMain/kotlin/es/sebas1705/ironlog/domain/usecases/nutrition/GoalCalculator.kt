package es.sebas1705.ironlog.domain.usecases.nutrition

import es.sebas1705.ironlog.data.models.ActivityLevel
import es.sebas1705.ironlog.data.models.NutritionProfile
import es.sebas1705.ironlog.data.models.Objective
import es.sebas1705.ironlog.data.models.Sex
import kotlin.math.roundToInt

/** A goal suggestion with the steps of the calculation, so the UI can explain it. */
data class GoalSuggestion(
    /** Resting energy (Mifflin–St Jeor), kcal/day. */
    val bmr: Double,
    /** Maintenance energy: [bmr] × activity factor, kcal/day. */
    val maintenance: Double,
    val activityFactor: Double,
    /** Objective adjustment applied to [maintenance] (−0.20 = 20 % less). */
    val adjustment: Double,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** Protein per kg of body weight used for [proteinG]. */
    val proteinPerKg: Double,
)

/**
 * Daily goal suggestion (nutrition design §1). Guidance only: the user can override every number.
 *
 * 1. Resting energy, Mifflin–St Jeor: 10 × kg + 6.25 × cm − 5 × age + 5 (men) or − 161 (women).
 * 2. Maintenance = resting energy × activity factor (1.2 sedentary … 1.9 very active).
 * 3. Goal = maintenance × (1 + adjustment): −20 % to lose, 0 to maintain, +10 % to gain; rounded to 10 kcal.
 * 4. Macros: protein by body weight (2.0 g/kg losing, 1.6 maintaining, 1.8 gaining), fat 25 % of the kcal,
 *    carbohydrates the rest (4 kcal/g protein and carbohydrate, 9 kcal/g fat).
 */
object GoalCalculator {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0
    const val FAT_SHARE = 0.25
    private const val ROUND_TO = 10
    private const val MIN_AGE = 14
    private const val MAX_AGE = 100

    fun proteinPerKg(objective: Objective): Double = when (objective) {
        Objective.LOSE -> 2.0
        Objective.MAINTAIN -> 1.6
        Objective.GAIN -> 1.8
    }

    /** Mifflin–St Jeor resting energy in kcal/day. */
    @Suppress("MagicNumber") // the published coefficients
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double =
        10.0 * weightKg + 6.25 * heightCm - 5.0 * age + if (sex == Sex.MALE) 5.0 else -161.0

    /**
     * The suggestion for [profile] and [weightKg] (latest body weight), or null while sex, birth year, height or
     * weight are missing or implausible. [currentYear] gives the age.
     */
    fun suggest(profile: NutritionProfile, weightKg: Double?, currentYear: Int): GoalSuggestion? {
        val sex = profile.sex ?: return null
        val height = profile.heightCm?.takeIf { it in 100..250 } ?: return null
        val weight = weightKg?.takeIf { it in 30.0..300.0 } ?: return null
        val age = profile.birthYear?.let { currentYear - it }?.takeIf { it in MIN_AGE..MAX_AGE } ?: return null
        return suggest(sex, weight, height.toDouble(), age, profile.activity, profile.objective)
    }

    fun suggest(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        activity: ActivityLevel,
        objective: Objective,
    ): GoalSuggestion {
        val bmr = bmr(sex, weightKg, heightCm, age)
        val maintenance = bmr * activity.factor
        val kcal = ((maintenance * (1 + objective.adjustment)) / ROUND_TO).roundToInt() * ROUND_TO
        val perKg = proteinPerKg(objective)
        val protein = (weightKg * perKg).roundToInt()
        val fat = (kcal * FAT_SHARE / KCAL_PER_G_FAT).roundToInt()
        val carbs = ((kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) / KCAL_PER_G_CARBS).roundToInt().coerceAtLeast(0)
        return GoalSuggestion(bmr, maintenance, activity.factor, objective.adjustment, kcal, protein, carbs, fat, perKg)
    }

    /** Grams of a macro that give [percent] % of [kcal]. */
    fun gramsFromPercent(kcal: Int, percent: Int, kcalPerGram: Double): Int = (kcal * percent / 100.0 / kcalPerGram).roundToInt()

    /** Percentage of [kcal] that [grams] of a macro give. */
    fun percentFromGrams(kcal: Int, grams: Int, kcalPerGram: Double): Int =
        if (kcal <= 0) 0 else (grams * kcalPerGram * 100.0 / kcal).roundToInt()
}
