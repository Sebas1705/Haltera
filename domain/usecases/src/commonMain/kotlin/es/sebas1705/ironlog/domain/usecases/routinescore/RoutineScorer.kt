package es.sebas1705.ironlog.domain.usecases.routinescore

import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.WorkoutSession
import kotlin.math.roundToInt

/**
 * Scores a routine against training research and suggests changes (docs/designs/2026-10-15-routine-scoring.md).
 * Pure and deterministic. The score is advice only: Ironlog is a logbook and never enforces or blocks a routine.
 */
object RoutineScorer {
    const val EXCELLENT_FROM = 90
    const val GOOD_FROM = 75
    const val FAIR_FROM = 60

    /** Criterion weights; the session criteria only count when recent sessions exist. Plan weights sum to 100. */
    val WEIGHTS: Map<CriterionId, Int> = mapOf(
        CriterionId.VOLUME to 25,
        CriterionId.FREQUENCY to 15,
        CriterionId.COVERAGE to 15,
        CriterionId.BALANCE to 10,
        CriterionId.REP_RANGES to 10,
        CriterionId.RECOVERY to 10,
        CriterionId.PROGRESSION to 10,
        CriterionId.SESSION_LENGTH to 5,
        CriterionId.ADHERENCE to 10,
        CriterionId.LOAD_PROGRESS to 10,
    )

    /** Catalogue exercises suggested to add work for each group, best first. Checked against the catalogue by a test. */
    val SUGGESTED_EXERCISES: Map<MuscleGroup, List<String>> = mapOf(
        MuscleGroup.CHEST to listOf("bench-press", "incline-bench-press", "pushups"),
        MuscleGroup.BACK to listOf("bent-over-row", "lat-pulldown", "seated-row"),
        MuscleGroup.SHOULDERS to listOf("overhead-press", "lateral-raise"),
        MuscleGroup.QUADS to listOf("squat", "leg-press", "leg-extensions"),
        MuscleGroup.HAMSTRINGS to listOf("romanian-deadlift", "leg-curl"),
        MuscleGroup.GLUTES to listOf("romanian-deadlift", "single-leg-glute-bridge", "lunge"),
        MuscleGroup.BICEPS to listOf("biceps-curl", "hammer-curl"),
        MuscleGroup.TRICEPS to listOf("triceps-pushdown", "overhead-triceps-extension"),
        MuscleGroup.CALVES to listOf("standing-calf-raise", "seated-calf-raise"),
        MuscleGroup.ABS to listOf("hanging-leg-raise", "plank"),
        MuscleGroup.TRAPS to listOf("shrug"),
        MuscleGroup.LOWER_BACK to listOf("romanian-deadlift"),
        MuscleGroup.FOREARMS to listOf("hammer-curl"),
        MuscleGroup.HIPS to listOf("lunge"),
        MuscleGroup.NECK to emptyList(),
    )

    /** Suggestions for the weaker side of a balance ratio. */
    val BALANCE_EXERCISES: Map<BalanceSide, List<String>> = mapOf(
        BalanceSide.PUSH to listOf("bench-press", "overhead-press"),
        BalanceSide.PULL to listOf("bent-over-row", "lat-pulldown", "face-pull"),
        BalanceSide.QUADS to listOf("squat", "leg-press"),
        BalanceSide.POSTERIOR to listOf("romanian-deadlift", "leg-curl"),
    )

    /** Heavy multi-joint lifts suggested when a routine has none. */
    val HEAVY_COMPOUNDS: List<String> = listOf("squat", "bench-press", "deadlift", "overhead-press")

    /**
     * Scores [routine]. [catalogue] resolves its exercises (muscles, category). [sessions] may hold any recent
     * sessions; only finished ones of this routine are used, for adherence and load progress, relative to [now]
     * (epoch millis). Without them those two criteria are left out and the plan alone is scored.
     */
    fun score(
        routine: Routine,
        catalogue: ScoringCatalogue,
        sessions: List<WorkoutSession> = emptyList(),
        now: Long = 0L,
    ): RoutineScore {
        val plan = RoutinePlan(routine, catalogue)
        val weekly = plan.weeklySets.filterValues { it > 0.0 }.mapValues { (_, v) -> round1(v) }
        val split = weekly.keys.associateWith(plan::groupSets)
        val focus = focusOf(plan)
        if (focus == RoutineFocus.CONDITIONING || focus == RoutineFocus.EMPTY) {
            return RoutineScore(0, Verdict.NOT_SCORED, focus, emptyList(), weekly, plan.sessionsPerWeek, split)
        }
        val criteria = PlanCriteria(plan, focus).all() + SessionCriteria(routine, plan, catalogue, sessions, now).all()
        val weightSum = criteria.sumOf { it.weight }
        val total = if (weightSum == 0) 0 else (criteria.sumOf { it.score * it.weight }.toDouble() / weightSum).roundToInt()
        return RoutineScore(total, Verdict.of(total), focus, criteria, weekly, plan.sessionsPerWeek, split)
    }

    private fun focusOf(plan: RoutinePlan): RoutineFocus {
        val allSets = plan.slots.sumOf { it.sets }
        if (plan.days.isEmpty() || allSets == 0) return RoutineFocus.EMPTY
        val resistance = plan.slots.filter { it.resistance }
        if (resistance.sumOf { it.sets } < allSets * MIN_RESISTANCE_SHARE) return RoutineFocus.CONDITIONING
        val zoned = resistance.filter { it.zone != null }
        val heavy = zoned.filter { it.zone == RepZone.HEAVY }.sumOf { it.sets }
        return if (zoned.isNotEmpty() && heavy >= zoned.sumOf { it.sets } * STRENGTH_SHARE) {
            RoutineFocus.STRENGTH
        } else {
            RoutineFocus.HYPERTROPHY
        }
    }

    private const val MIN_RESISTANCE_SHARE = 0.4
    private const val STRENGTH_SHARE = 0.5
}

private const val TENTHS = 10.0

internal fun round1(value: Double): Double = (value * TENTHS).roundToInt() / TENTHS

internal fun percent(fraction: Double): Int = (fraction.coerceIn(0.0, 1.0) * 100).roundToInt()
