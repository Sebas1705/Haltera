package es.sebas1705.haltera.domain.usecases.routinescore

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One slot with what the catalogue says about its exercise. */
internal class SlotWork(val slot: RoutineSlot, val exercise: Exercise?) {
    /** Resistance work counts towards muscle volume; cardio and distance work do not. */
    val resistance: Boolean =
        exercise != null && exercise.category in RESISTANCE && slot.prescription !is Prescription.Distance

    val primary: Set<MuscleGroup> = exercise?.primaryMuscles.orEmpty().mapNotNull(MuscleGroup::of).toSet()
    val secondary: Set<MuscleGroup> =
        exercise?.secondaryMuscles.orEmpty().mapNotNull(MuscleGroup::of).toSet() - primary

    /** Multi-joint: at least two major groups involved (bench press, overhead press, squat, row…). */
    val compound: Boolean = (primary + secondary).count { it.major } >= 2

    val sets: Int = slot.sets.coerceAtLeast(0)

    val zone: RepZone? = when (val p = slot.prescription) {
        is Prescription.Reps -> zoneOf(p.reps.toDouble())
        is Prescription.RepRange -> zoneOf((p.min + p.max) / 2.0)
        else -> null
    }

    /** Rear-delt work (face pulls): shoulders primary with back secondary. A pull, not a press. */
    private val rearDelt = MuscleGroup.SHOULDERS in primary && MuscleGroup.BACK in secondary

    val push: Boolean = MuscleGroup.CHEST in primary ||
        (MuscleGroup.SHOULDERS in primary && MuscleGroup.TRICEPS in secondary) ||
        (MuscleGroup.TRICEPS in primary && MuscleGroup.CHEST in secondary)

    val pull: Boolean = MuscleGroup.BACK in primary || rearDelt

    val quadDominant: Boolean = MuscleGroup.QUADS in primary

    val hipDominant: Boolean = MuscleGroup.HAMSTRINGS in primary || MuscleGroup.GLUTES in primary ||
        (
            MuscleGroup.LOWER_BACK in primary &&
                (MuscleGroup.HAMSTRINGS in secondary || MuscleGroup.GLUTES in secondary)
            )

    /**
     * Secondary muscles that earn volume credit. Pulling movements (rows, pulldowns, pull-ups: back primary)
     * credit no shoulder volume: the catalogue tags shoulders as secondary on them, but the rear delt is a minor
     * helper there and counting it inflated shoulder volume by about a third in push/pull/legs routines.
     */
    val secondaryCredit: Set<MuscleGroup> =
        if (MuscleGroup.BACK in primary) secondary - MuscleGroup.SHOULDERS else secondary

    /** Fractional sets per group: primary muscles count 1, secondary muscles 0.5 (Pelland et al. 2024). */
    fun fractionalSets(): Map<MuscleGroup, Double> = sumSets(listOf(primarySets(), secondarySets()))

    /** Sets of the groups this exercise trains directly (primary muscles), one each. */
    fun primarySets(): Map<MuscleGroup, Double> =
        if (resistance) primary.associateWith { sets.toDouble() } else emptyMap()

    /** Half-set credit of the secondary muscles ([secondaryCredit]). */
    fun secondarySets(): Map<MuscleGroup, Double> =
        if (resistance) secondaryCredit.associateWith { sets * SECONDARY_SHARE } else emptyMap()

    /** Direct (primary) sets per group, used for recovery. Rear-delt work does not load the pressing shoulder. */
    fun directSets(): Map<MuscleGroup, Double> = buildMap {
        if (!resistance) return@buildMap
        primary.forEach { if (!(it == MuscleGroup.SHOULDERS && rearDelt)) put(it, sets.toDouble()) }
    }

    companion object {
        private val RESISTANCE =
            setOf(ExerciseCategory.STRENGTH, ExerciseCategory.CALISTHENICS, ExerciseCategory.PLYOMETRICS)
        const val SECONDARY_SHARE = 0.5
        const val HEAVY_MAX_REPS = 7.0
        const val MODERATE_MAX_REPS = 12.5

        fun zoneOf(reps: Double): RepZone = when {
            reps <= HEAVY_MAX_REPS -> RepZone.HEAVY
            reps <= MODERATE_MAX_REPS -> RepZone.MODERATE
            else -> RepZone.LIGHT
        }
    }
}

/** A training day with its slots resolved. */
internal class DayWork(val index: Int, val day: RoutineDay, val slots: List<SlotWork>) {
    val name: String get() = day.name

    val sets: Map<MuscleGroup, Double> = sumSets(slots.map { it.fractionalSets() })
    val primarySets: Map<MuscleGroup, Double> = sumSets(slots.map { it.primarySets() })
    val secondarySets: Map<MuscleGroup, Double> = sumSets(slots.map { it.secondarySets() })
    val direct: Map<MuscleGroup, Double> = sumSets(slots.map { it.directSets() })
    val minutes: Int = SessionTime.minutes(slots.map { it.slot })
    val hasResistance: Boolean = slots.any { it.resistance }

    /** The day counts as a stimulus for [group] with at least two effective sets. */
    fun trains(group: MuscleGroup): Boolean = (sets[group] ?: 0.0) >= RoutinePlan.STIMULUS_SETS
}

/** Adds up per-group set counts. */
internal fun sumSets(maps: List<Map<MuscleGroup, Double>>): Map<MuscleGroup, Double> =
    maps.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }

/**
 * The routine as the scorer sees it: training days, the assumed schedule and weekly numbers.
 *
 * Schedule: a cycle with rest days maps one cycle day to one calendar day, so N days run 7/N times a week. A cycle
 * without rest days (A/B programmes) says nothing about the calendar; the scorer assumes three sessions a week
 * for cycles of up to four days and one cycle a week (at most six sessions) for longer ones.
 */
internal class RoutinePlan(routine: Routine, catalogue: ScoringCatalogue) {
    private val cycleLength = routine.days.size

    val days: List<DayWork> = routine.days.mapIndexedNotNull { index, day ->
        if (day.kind == DayKind.TRAINING && day.slots.isNotEmpty()) {
            DayWork(index, day, day.slots.map { SlotWork(it, catalogue.exerciseOf(it.target)) })
        } else {
            null
        }
    }

    private val hasRestDays = days.size < cycleLength

    val sessionsPerWeek: Double = when {
        days.isEmpty() -> 0.0
        hasRestDays -> days.size * WEEK_DAYS / cycleLength
        cycleLength >= LONG_CYCLE -> min(cycleLength, MAX_SESSIONS).toDouble()
        else -> ASSUMED_SESSIONS
    }

    /** How many times a week the whole cycle runs. */
    private val cyclesPerWeek: Double = if (days.isEmpty()) 0.0 else sessionsPerWeek / days.size

    val slots: List<SlotWork> = days.flatMap { it.slots }

    val weeklySets: Map<MuscleGroup, Double> = MuscleGroup.entries.associateWith { group ->
        days.sumOf { it.sets[group] ?: 0.0 } * cyclesPerWeek
    }

    /** The direct part of [weeklySets]: sets where the group is a primary muscle. */
    val weeklyDirect: Map<MuscleGroup, Double> = MuscleGroup.entries.associateWith { group ->
        days.sumOf { it.primarySets[group] ?: 0.0 } * cyclesPerWeek
    }

    /** The secondary part of [weeklySets]: half a set per set where the group is a credited secondary muscle. */
    val weeklySecondary: Map<MuscleGroup, Double> = MuscleGroup.entries.associateWith { group ->
        days.sumOf { it.secondarySets[group] ?: 0.0 } * cyclesPerWeek
    }

    fun weekly(group: MuscleGroup): Double = weeklySets[group] ?: 0.0

    /** [weekly] split into direct and secondary sets, rounded to one decimal. */
    fun groupSets(group: MuscleGroup): GroupSets = GroupSets(
        group,
        round1(weekly(group)),
        direct = round1(weeklyDirect[group] ?: 0.0),
        secondary = round1(weeklySecondary[group] ?: 0.0),
    )

    fun frequency(group: MuscleGroup): Double = days.count { it.trains(group) } * cyclesPerWeek

    /** Weekly sets of the slots matching [predicate]. */
    fun weeklySetsWhere(predicate: (SlotWork) -> Boolean): Double =
        slots.filter { it.resistance && predicate(it) }.sumOf { it.sets } * cyclesPerWeek

    /** Sets of one cycle that the weekly numbers multiply; a per-cycle change of x sets is x × this per week. */
    fun perWeek(cycleSets: Double): Double = cycleSets * cyclesPerWeek

    fun perCycle(weeklySets: Double): Double = if (cyclesPerWeek > 0) weeklySets / cyclesPerWeek else weeklySets

    /**
     * Training days that happen on consecutive calendar days. With rest days in the cycle, neighbours in the
     * cycle (wrapping around); without them, every neighbour pair once more than 3.5 sessions a week force
     * back-to-back days.
     */
    val consecutivePairs: List<Pair<DayWork, DayWork>> = run {
        val backToBack = hasRestDays || sessionsPerWeek > HALF_WEEK
        if (days.size < 2 || !backToBack) return@run emptyList()
        val byIndex = days.associateBy { it.index }
        days.mapNotNull { a -> byIndex[(a.index + 1) % cycleLength]?.let { b -> a to b } }
            .filter { (a, b) -> a.index != b.index }
            .distinctBy { (a, b) -> setOf(a.index, b.index) }
    }

    /** Neighbouring training days of [day] on the calendar. */
    fun neighbours(day: DayWork): List<DayWork> =
        consecutivePairs.mapNotNull { (a, b) -> if (a === day) b else if (b === day) a else null }

    /**
     * The day to suggest for more work on [group]: a resistance day of the same body region, preferring one that
     * does not train [group] yet and has no neighbour training it when [newDay] (to raise frequency), else one
     * that already trains it; the shortest such day wins.
     */
    fun dayFor(group: MuscleGroup, newDay: Boolean): String? {
        val candidates = days.filter { it.hasResistance }.ifEmpty { return null }
        val sameRegion = candidates.filter { day -> day.sets.any { (g, s) -> g.region == group.region && s >= STIMULUS_SETS } }
        var pool = sameRegion.ifEmpty { candidates }
        pool = if (newDay) {
            pool.filter { !it.trains(group) && neighbours(it).none { n -> n.trains(group) } }
                .ifEmpty { pool.filter { !it.trains(group) } }
                .ifEmpty { candidates.filter { !it.trains(group) } }
                .ifEmpty { pool }
        } else {
            pool.filter { it.trains(group) }.ifEmpty { pool }
        }
        return pool.minBy { it.minutes }.name
    }

    companion object {
        const val WEEK_DAYS = 7.0
        const val ASSUMED_SESSIONS = 3.0
        const val LONG_CYCLE = 5
        const val MAX_SESSIONS = 6
        const val HALF_WEEK = 3.5
        const val STIMULUS_SETS = 2.0
    }
}

/**
 * Estimated duration of a day: every set (warm-ups included) takes about 4 s a rep plus 10 s to set up, or its
 * prescribed time; rest is counted between sets and after each slot; supersets rest once per round; and each
 * slot adds 45 s to change exercise.
 */
internal object SessionTime {
    private const val SECONDS_PER_REP = 4
    private const val SET_SETUP_S = 10
    private const val MIN_SET_S = 20
    private const val TRANSITION_S = 45
    private const val WARMUP_REST_S = 60
    private const val METERS_PER_SECOND = 2.8
    private const val DEFAULT_REPS = 10

    fun minutes(slots: List<RoutineSlot>): Int {
        var seconds = 0
        var i = 0
        while (i < slots.size) {
            val group = slots[i].supersetGroup
            val members = if (group == null) {
                listOf(slots[i])
            } else {
                slots.drop(i).takeWhile { it.supersetGroup == group }
            }
            seconds += block(members)
            i += members.size
        }
        return (seconds / SECONDS_PER_MINUTE).roundToInt()
    }

    /** One slot, or a superset whose members alternate and rest once per round. */
    private fun block(members: List<RoutineSlot>): Int {
        val work = members.sumOf { slot -> (slot.sets + slot.warmupSets) * setSeconds(slot.prescription) }
        val rounds = members.maxOf { it.sets }
        val warmups = members.maxOf { it.warmupSets }
        val rest = members.maxOf { it.restBetweenSetsS }
        val restBetween = max(0, rounds - 1) * rest + warmups * min(rest, WARMUP_REST_S)
        return work + restBetween + members.last().restAfterS + members.size * TRANSITION_S
    }

    private fun setSeconds(prescription: Prescription): Int = when (prescription) {
        is Prescription.Reps -> max(MIN_SET_S, prescription.reps * SECONDS_PER_REP + SET_SETUP_S)
        is Prescription.RepRange ->
            max(MIN_SET_S, (prescription.min + prescription.max) / 2 * SECONDS_PER_REP + SET_SETUP_S)
        is Prescription.Duration -> prescription.seconds
        is Prescription.Distance -> (prescription.meters / METERS_PER_SECOND).roundToInt()
    }.let { if (it <= 0) DEFAULT_REPS * SECONDS_PER_REP else it }

    private const val SECONDS_PER_MINUTE = 60.0
}
