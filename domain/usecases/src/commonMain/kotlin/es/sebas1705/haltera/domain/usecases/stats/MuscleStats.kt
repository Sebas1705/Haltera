package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.domain.usecases.routinescore.MuscleGroup

/**
 * Weekly effective sets and volume of one muscle group (the same 15 groups as the routine analysis), this
 * period against the previous one.
 */
data class MuscleComparison(val group: MuscleGroup, val setsPerWeek: Comparison, val volumePerWeek: Comparison)

/** The pairs of Metric.MUSCLE_BALANCE: which primary muscles count on each side. */
enum class BalanceKind(val first: Set<Muscle>, val second: Set<Muscle>) {
    PUSH_PULL(
        first = setOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS),
        second = setOf(Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.BICEPS, Muscle.TRAPS),
    ),
    UPPER_LOWER(
        first = setOf(
            Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS, Muscle.BICEPS, Muscle.FOREARMS,
            Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.TRAPS, Muscle.NECK,
        ),
        second = setOf(
            Muscle.QUADRICEPS,
            Muscle.HAMSTRINGS,
            Muscle.GLUTES,
            Muscle.CALVES,
            Muscle.ADDUCTORS,
            Muscle.ABDUCTORS,
        ),
    ),
    QUADS_HAMSTRINGS(first = setOf(Muscle.QUADRICEPS), second = setOf(Muscle.HAMSTRINGS)),
}

/** Effective sets per week on each side of a [kind]. */
data class Balance(val kind: BalanceKind, val first: Double, val second: Double) {
    /** first ÷ second, or null when the second side has no sets. */
    val ratio: Double? get() = if (second > 0) first / second else null

    /** The first side's share of both (0–1), or null without sets. */
    val firstShare: Double? get() = (first + second).takeIf { it > 0 }?.let { first / it }
}

/**
 * Per-muscle-group statistics (Metric.MUSCLE_SETS, Metric.MUSCLE_VOLUME, Metric.MUSCLE_BALANCE). Only the
 * exercise's primary muscles count, each in full: a set of an exercise with two primary muscles is one
 * set for each of them. Secondary muscles are not counted.
 */
object MuscleStats {

    private class Totals(
        val sets: MutableMap<MuscleGroup, Int> = mutableMapOf(),
        val volume: MutableMap<MuscleGroup, Long> = mutableMapOf(),
    )

    private fun totals(
        sessions: List<DatedSession>,
        musclesByVariant: Map<String, List<Muscle>>,
        window: DayWindow
    ): Totals {
        val totals = Totals()
        val exercises = sessions.filter { !it.session.inProgress && it.day in window }.flatMap { it.session.exercises }
        exercises.forEach { exercise ->
            val sets = TrainingMath.effectiveSets(exercise)
            val volume = TrainingMath.volume(exercise)
            // Lats and middle back are both "Back": a row set counts once for the group.
            val groups = musclesByVariant[exercise.variantId].orEmpty().mapNotNull(MuscleGroup::of).distinct()
            groups.forEach { muscle ->
                if (sets > 0) totals.sets[muscle] = (totals.sets[muscle] ?: 0) + sets
                if (volume > 0) totals.volume[muscle] = (totals.volume[muscle] ?: 0) + volume
            }
        }
        return totals
    }

    /**
     * Muscles trained in either window, per week, sorted by this period's sets then volume (highest
     * first). [previous] null gives comparisons without a previous value.
     */
    fun compare(
        sessions: List<DatedSession>,
        musclesByVariant: Map<String, List<Muscle>>,
        current: DayWindow,
        previous: DayWindow?
    ): List<MuscleComparison> {
        val now = totals(sessions, musclesByVariant, current)
        val before = previous?.let { totals(sessions, musclesByVariant, it) }
        val muscles = now.sets.keys + now.volume.keys + before?.sets?.keys.orEmpty() + before?.volume?.keys.orEmpty()
        return muscles.map { muscle ->
            MuscleComparison(
                group = muscle,
                setsPerWeek = Comparison(
                    (now.sets[muscle] ?: 0) / current.weeks,
                    previous?.let { (before?.sets?.get(muscle) ?: 0) / it.weeks },
                ),
                volumePerWeek = Comparison(
                    (now.volume[muscle] ?: 0) / current.weeks,
                    previous?.let { (before?.volume?.get(muscle) ?: 0) / it.weeks },
                ),
            )
        }.sortedWith(
            compareByDescending<MuscleComparison> { it.setsPerWeek.current }
                .thenByDescending { it.volumePerWeek.current }
                .thenByDescending { it.setsPerWeek.previous ?: 0.0 }
                .thenBy { it.group.ordinal },
        )
    }

    /**
     * Effective sets per week on each side of every [BalanceKind] in [window]. A set counts once per
     * side even when several of its primary muscles are on that side (a bench press set is one push
     * set, not one for chest and one for triceps).
     */
    fun balance(
        sessions: List<DatedSession>,
        musclesByVariant: Map<String, List<Muscle>>,
        window: DayWindow
    ): List<Balance> {
        val performed = sessions.filter { !it.session.inProgress && it.day in window }
            .flatMap { it.session.exercises }
            .map { musclesByVariant[it.variantId].orEmpty().toSet() to TrainingMath.effectiveSets(it) }
        return BalanceKind.entries.map { kind ->
            val first = performed.sumOf { (muscles, sets) -> if (muscles.any { it in kind.first }) sets else 0 }
            val second = performed.sumOf { (muscles, sets) -> if (muscles.any { it in kind.second }) sets else 0 }
            Balance(kind, first / window.weeks, second / window.weeks)
        }
    }
}
