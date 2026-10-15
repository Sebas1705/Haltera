package es.sebas1705.haltera.domain.usecases.routinescore

import es.sebas1705.haltera.data.models.Muscle

// Result types of RoutineScorer (docs/designs/2026-10-15-routine-scoring.md). Everything here is data: the UI
// turns findings, advice and references into localised text. Scores are advice only and never block anything.

/** The score of a routine: [total] 0–100 is the weighted mean of the applicable [criteria]. */
data class RoutineScore(
    val total: Int,
    val verdict: Verdict,
    val focus: RoutineFocus,
    val criteria: List<Criterion>,
    /** Planned weekly working sets per muscle group (secondary muscles count half), for the chart. */
    val weeklySets: Map<MuscleGroup, Double>,
    /** The sessions per week the weekly numbers assume (see the design, "Schedule"). */
    val sessionsPerWeek: Double,
    /** [weeklySets] split into direct (primary) and secondary sets, for the stacked chart. */
    val weeklySplit: Map<MuscleGroup, GroupSets> = emptyMap(),
) {
    /** Conditioning or empty routines get no number: the criteria target resistance training. */
    val scored: Boolean get() = verdict != Verdict.NOT_SCORED
}

enum class Verdict {
    EXCELLENT, GOOD, FAIR, NEEDS_WORK, NOT_SCORED;

    companion object {
        fun of(total: Int): Verdict = when {
            total >= RoutineScorer.EXCELLENT_FROM -> EXCELLENT
            total >= RoutineScorer.GOOD_FROM -> GOOD
            total >= RoutineScorer.FAIR_FROM -> FAIR
            else -> NEEDS_WORK
        }
    }
}

/** What the routine mostly trains, from its rep targets. */
enum class RoutineFocus {
    /** Most working sets are heavy (about 7 reps or fewer). */
    STRENGTH,

    /** Most working sets are moderate or light: muscle growth and general fitness. */
    HYPERTROPHY,

    /** Mostly cardio or timed intervals: not scored. */
    CONDITIONING,

    /** No training slots yet: not scored. */
    EMPTY,
}

/** Muscle groups the scorer counts; catalogue muscles map onto them ([of]). */
enum class MuscleGroup(val region: Region, val large: Boolean, val major: Boolean) {
    CHEST(Region.UPPER, large = true, major = true),
    BACK(Region.UPPER, large = true, major = true),
    SHOULDERS(Region.UPPER, large = true, major = true),
    QUADS(Region.LOWER, large = true, major = true),
    HAMSTRINGS(Region.LOWER, large = true, major = true),
    GLUTES(Region.LOWER, large = true, major = true),
    BICEPS(Region.UPPER, large = false, major = true),
    TRICEPS(Region.UPPER, large = false, major = true),
    CALVES(Region.LOWER, large = false, major = true),
    ABS(Region.CORE, large = false, major = true),
    TRAPS(Region.UPPER, large = false, major = false),
    LOWER_BACK(Region.CORE, large = false, major = false),
    FOREARMS(Region.UPPER, large = false, major = false),
    HIPS(Region.LOWER, large = false, major = false),
    NECK(Region.UPPER, large = false, major = false),
    ;

    enum class Region { UPPER, LOWER, CORE }

    companion object {
        val majors: List<MuscleGroup> = entries.filter { it.major }

        /** The group a catalogue muscle counts for; null for FULL_BODY. */
        fun of(muscle: Muscle): MuscleGroup? = when (muscle) {
            Muscle.CHEST -> CHEST
            Muscle.LATS, Muscle.MIDDLE_BACK -> BACK
            Muscle.SHOULDERS -> SHOULDERS
            Muscle.QUADRICEPS -> QUADS
            Muscle.HAMSTRINGS -> HAMSTRINGS
            Muscle.GLUTES -> GLUTES
            Muscle.BICEPS -> BICEPS
            Muscle.TRICEPS -> TRICEPS
            Muscle.CALVES -> CALVES
            Muscle.ABDOMINALS -> ABS
            Muscle.TRAPS -> TRAPS
            Muscle.LOWER_BACK -> LOWER_BACK
            Muscle.FOREARMS -> FOREARMS
            Muscle.ADDUCTORS, Muscle.ABDUCTORS -> HIPS
            Muscle.NECK -> NECK
            Muscle.FULL_BODY -> null
        }
    }
}

enum class CriterionId {
    VOLUME, FREQUENCY, COVERAGE, BALANCE, REP_RANGES, RECOVERY, PROGRESSION, SESSION_LENGTH,

    /** Only with recent sessions. */
    ADHERENCE,

    /** Only with recent sessions. */
    LOAD_PROGRESS,
}

/**
 * One scored aspect: [score] 0–100, its [weight] in the total, what was measured ([finding]), concrete
 * suggestions ([advice], possibly none) and the [evidence] the thresholds come from.
 */
data class Criterion(
    val id: CriterionId,
    val score: Int,
    val weight: Int,
    val finding: Finding,
    val advice: List<Advice>,
    val evidence: List<Reference>,
)

/**
 * Weekly sets of one group, rounded to one decimal: [sets] = [direct] (the group is a primary muscle) +
 * [secondary] (half a set where it is a credited secondary muscle).
 */
data class GroupSets(val group: MuscleGroup, val sets: Double, val direct: Double = sets, val secondary: Double = 0.0)

/** How often a group is trained per week. */
data class GroupFrequency(val group: MuscleGroup, val perWeek: Double, val weeklySets: Double)

/** The two sides of a ratio; [ratio] is first / second, null when the second side is zero. */
data class SideRatio(val first: Double, val second: Double) {
    val ratio: Double? get() = if (second > 0.0) first / second else null
}

enum class RepZone { HEAVY, MODERATE, LIGHT }

enum class BalanceSide { PUSH, PULL, QUADS, POSTERIOR }

/** What a criterion measured. */
sealed interface Finding {
    data class Volume(val low: List<GroupSets>, val high: List<GroupSets>, val inRange: Int, val counted: Int) : Finding

    data class Frequency(val low: List<GroupFrequency>, val checked: Int) : Finding

    data class Coverage(val missing: List<MuscleGroup>) : Finding

    data class Balance(val pushPull: SideRatio?, val quadsPosterior: SideRatio?) : Finding

    /** Shares of working sets per zone (0–1) and whether some compound lift is heavy. */
    data class RepRanges(val shares: Map<RepZone, Double>, val heavyCompound: Boolean) : Finding

    data class Recovery(val conflicts: List<RecoveryConflict>) : Finding

    data class SessionLength(val longestDay: String, val longestMinutes: Int, val averageMinutes: Int) : Finding

    data class Progression(val withRule: Int, val total: Int) : Finding

    data class Adherence(val done: Int, val planned: Int, val setCompletion: Double, val weeks: Int) : Finding

    data class LoadProgress(val improved: Int, val tracked: Int, val weeks: Int) : Finding
}

/** A group trained hard on two training days in a row. */
data class RecoveryConflict(val group: MuscleGroup, val firstDay: String, val secondDay: String)

/** A concrete suggestion. Exercise ids are catalogue exercise ids; day names are the routine's. */
sealed interface Advice {
    /** "Hamstrings get 4 sets/week: add 3–6 sets of Romanian deadlift or leg curl on Lower A". */
    data class AddSets(
        val group: MuscleGroup,
        /** Rounded to one decimal, like the chart. */
        val weeklySets: Double,
        val addMin: Int,
        val addMax: Int,
        val exerciseIds: List<String>,
        val day: String?,
    ) : Advice

    /** "Chest gets 28 sets/week: drop 4–8 sets; more is unlikely to add much". */
    data class RemoveSets(
        val group: MuscleGroup,
        val weeklySets: Double,
        val removeMin: Int,
        val removeMax: Int
    ) : Advice

    /**
     * Shoulders over the flag: [sets] split into direct and secondary work. Shoulders lump three delt heads and
     * presses already credit the front one, so the advice points at that double counting and at pressing
     * volume, never at the only direct side and rear-delt work.
     */
    data class ShoulderOverlap(val sets: GroupSets) : Advice

    /** "Chest is trained 1×/week: move about half of its 12 sets to Upper B". */
    data class SpreadAcrossDays(val group: MuscleGroup, val perWeek: Double, val weeklySets: Double, val day: String?) :
        Advice

    /** "Calves are not trained: add 3 sets of standing calf raise to Legs". */
    data class Cover(val group: MuscleGroup, val exerciseIds: List<String>, val day: String?) : Advice

    /** "Pull work is low (push:pull 2.0): add 3–4 sets of bent-over row or lat pulldown on Day 3". */
    data class Rebalance(val weaker: BalanceSide, val ratio: Double, val exerciseIds: List<String>, val day: String?) :
        Advice

    /** "Add one compound lift in the 3–6 rep range, e.g. squat or bench press". */
    data class AddHeavyCompound(val exerciseIds: List<String>) : Advice

    /** "Every set is in the heavy range: add some accessory work at 8–12 reps". */
    data class AddRepZone(val only: RepZone, val add: RepZone) : Advice

    /** "Quads are trained hard on Legs and Lower B back to back: put a rest or upper day between them". */
    data class SeparateDays(val conflict: RecoveryConflict) : Advice

    /** "Legs takes about 105 min: superset accessories, trim rest or move an exercise to another day". */
    data class ShortenSession(val day: String, val minutes: Int) : Advice

    /** "6 of 9 exercises have no progression rule: add double progression to them". */
    data class AddProgressionRules(val without: Int, val total: Int) : Advice

    /** "You did 7 of 12 planned sessions: a shorter cycle or fewer days may fit your week better". */
    data class FitTheSchedule(val done: Int, val planned: Int) : Advice

    /** "Bench press has not progressed lately: consider a deload week or a different rep range". */
    data class BreakPlateau(val exerciseIds: List<String>) : Advice
}

/** A source the thresholds lean on, cited by author and year (proper names, so not translated). */
enum class Reference(val citation: String) {
    SCHOENFELD_2017_VOLUME("Schoenfeld, Ogborn & Krieger 2017"),
    PELLAND_2024("Pelland et al. 2024"),
    RALSTON_2017("Ralston et al. 2017"),
    SCHOENFELD_2016_FREQUENCY("Schoenfeld, Ogborn & Krieger 2016"),
    SCHOENFELD_2019_FREQUENCY("Schoenfeld, Grgic & Krieger 2019"),
    RATAMESS_2009_ACSM("Ratamess et al. 2009"),
    COOMBS_2002("Coombs & Garbutt 2002"),
    SCHOENFELD_2017_LOAD("Schoenfeld et al. 2017"),
    SCHOENFELD_2021_CONTINUUM("Schoenfeld et al. 2021"),
    MACDOUGALL_1995("MacDougall et al. 1995"),
    KRAEMER_2004("Kraemer & Ratamess 2004"),
}
