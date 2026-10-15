package es.sebas1705.haltera.feature.routines.model

import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.LoadIncrement
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType

/** The shape of a [Prescription], as picked in the slot editor. */
internal enum class PrescriptionKind { REP_RANGE, REPS, DURATION, DISTANCE }

/** The progression rule types the editor offers. */
internal enum class ProgressionKind { NONE, DOUBLE, LINEAR }

/** Limits and steps of the slot editor's numeric fields. Every value it saves is inside them. */
internal object SlotLimits {
    val SETS = 1..20
    val WARMUP_SETS = 0..10
    val REPS = 1..100
    val DURATION_S = 5..7_200
    val DISTANCE_M = 10..100_000
    val REST_S = 0..900
    val SUPERSET_GROUP = 0..9
    val SESSIONS = 1..10
    val INCREMENT_STEPS = 1..10
    const val DURATION_STEP_S = 5
    const val DISTANCE_STEP_M = 100
    const val REST_STEP_S = 15
    const val DEFAULT_DURATION_S = 30
    const val DEFAULT_DISTANCE_M = 1_000
    const val MAX_INCREMENT_GRAMS = 50_000L
}

/** Which prescriptions fit an exercise and how to keep them valid (spec §3.2: consistent with `trackingType`). */
internal object PrescriptionRules {

    fun allowedKinds(trackingType: TrackingType): List<PrescriptionKind> = when (trackingType) {
        TrackingType.WEIGHT_REPS, TrackingType.BODYWEIGHT_REPS, TrackingType.REPS ->
            listOf(PrescriptionKind.REP_RANGE, PrescriptionKind.REPS)
        TrackingType.TIME, TrackingType.WEIGHT_TIME -> listOf(PrescriptionKind.DURATION)
        TrackingType.DISTANCE_TIME -> listOf(PrescriptionKind.DISTANCE, PrescriptionKind.DURATION)
    }

    fun kindOf(prescription: Prescription): PrescriptionKind = when (prescription) {
        is Prescription.RepRange -> PrescriptionKind.REP_RANGE
        is Prescription.Reps -> PrescriptionKind.REPS
        is Prescription.Duration -> PrescriptionKind.DURATION
        is Prescription.Distance -> PrescriptionKind.DISTANCE
    }

    fun isCompatible(prescription: Prescription, trackingType: TrackingType): Boolean =
        kindOf(prescription) in allowedKinds(trackingType)

    fun defaultFor(trackingType: TrackingType): Prescription = of(allowedKinds(trackingType).first())

    /** A sensible prescription of [kind]: 8–12 reps, 10 reps, 30 s or 1 km. */
    fun of(kind: PrescriptionKind): Prescription = when (kind) {
        PrescriptionKind.REP_RANGE -> Prescription.DEFAULT
        PrescriptionKind.REPS -> Prescription.Reps(DEFAULT_REPS)
        PrescriptionKind.DURATION -> Prescription.Duration(SlotLimits.DEFAULT_DURATION_S)
        PrescriptionKind.DISTANCE -> Prescription.Distance(SlotLimits.DEFAULT_DISTANCE_M)
    }

    /** [current] changed to [kind], keeping the numbers that carry over (a range becomes its top, and back). */
    fun convert(current: Prescription, kind: PrescriptionKind): Prescription = when {
        kindOf(current) == kind -> current
        current is Prescription.RepRange && kind == PrescriptionKind.REPS -> Prescription.Reps(current.max)
        current is Prescription.Reps && kind == PrescriptionKind.REP_RANGE ->
            Prescription.RepRange(current.reps, current.reps)
        else -> of(kind)
    }

    /** Raising the bottom above the top lifts the top with it. */
    fun withMin(range: Prescription.RepRange, min: Int): Prescription.RepRange {
        val newMin = min.coerceIn(SlotLimits.REPS)
        return Prescription.RepRange(newMin, range.max.coerceAtLeast(newMin))
    }

    /** Lowering the top below the bottom pulls the bottom down with it. */
    fun withMax(range: Prescription.RepRange, max: Int): Prescription.RepRange {
        val newMax = max.coerceIn(SlotLimits.REPS)
        return Prescription.RepRange(range.min.coerceAtMost(newMax), newMax)
    }

    /** [prescription] with every number inside [SlotLimits] and `min <= max`. */
    fun normalize(prescription: Prescription): Prescription = when (prescription) {
        is Prescription.RepRange -> {
            val min = prescription.min.coerceIn(SlotLimits.REPS)
            Prescription.RepRange(min, prescription.max.coerceIn(SlotLimits.REPS).coerceAtLeast(min))
        }
        is Prescription.Reps -> Prescription.Reps(prescription.reps.coerceIn(SlotLimits.REPS))
        is Prescription.Duration -> Prescription.Duration(prescription.seconds.coerceIn(SlotLimits.DURATION_S))
        is Prescription.Distance -> Prescription.Distance(prescription.meters.coerceIn(SlotLimits.DISTANCE_M))
    }

    private const val DEFAULT_REPS = 10
}

internal fun ProgressionRule.kind(): ProgressionKind = when (this) {
    ProgressionRule.None -> ProgressionKind.NONE
    is ProgressionRule.DoubleProgression -> ProgressionKind.DOUBLE
    is ProgressionRule.Linear -> ProgressionKind.LINEAR
}

internal fun ProgressionRule.incrementOrNull(): LoadIncrement? = when (this) {
    ProgressionRule.None -> null
    is ProgressionRule.DoubleProgression -> increment
    is ProgressionRule.Linear -> increment
}

/**
 * [current] switched to [kind]. The increment carries over; when there is none it defaults from
 * [loadType] (`LoadIncrement.defaultFor`).
 */
internal fun progressionOf(kind: ProgressionKind, current: ProgressionRule, loadType: LoadType): ProgressionRule {
    if (current.kind() == kind) return current
    val increment = current.incrementOrNull() ?: LoadIncrement.defaultFor(loadType)
    return when (kind) {
        ProgressionKind.NONE -> ProgressionRule.None
        ProgressionKind.DOUBLE -> ProgressionRule.DoubleProgression(sessionsAtTop = 1, increment = increment)
        ProgressionKind.LINEAR -> ProgressionRule.Linear(increment = increment, everyNSessions = 1)
    }
}

/** [rule] with its increment replaced (no-op for [ProgressionRule.None]). */
internal fun ProgressionRule.withIncrement(increment: LoadIncrement): ProgressionRule = when (this) {
    ProgressionRule.None -> this
    is ProgressionRule.DoubleProgression -> copy(increment = increment)
    is ProgressionRule.Linear -> copy(increment = increment)
}

/**
 * A new slot for [exercise] with [target]: 3 sets, a prescription that fits its tracking type, both hands
 * counted for dumbbells, and double progression when it is loaded.
 */
internal fun defaultSpec(exercise: Exercise, target: SlotTarget): SlotSpec {
    val loadType = loadTypeOf(target, exercise)
    val loaded = exercise.trackingType.tracksLoad && loadType != LoadType.NONE
    return SlotSpec(
        target = target,
        sets = DEFAULT_SETS,
        warmupSets = 0,
        prescription = PrescriptionRules.defaultFor(exercise.trackingType),
        restBetweenSetsS = RoutineSlot.DEFAULT_REST_S,
        restAfterS = RoutineSlot.DEFAULT_REST_S,
        bilateral = loadType == LoadType.DUMBBELL,
        progression = if (loaded) {
            ProgressionRule.DoubleProgression(sessionsAtTop = 1, increment = LoadIncrement.defaultFor(loadType))
        } else {
            ProgressionRule.None
        },
    )
}

private const val DEFAULT_SETS = 3
