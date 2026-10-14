package es.sebas1705.ironlog.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A training routine whose cycle is [days] (length N >= 1, spec §3.2). At most one routine is active.
 * [nextDayIndex] is the cycle pointer (spec §3.4): the day after the last completed one. It may point
 * to a REST day; the `cycle` use case resolves the next training day.
 */
data class Routine(
    val id: String,
    val name: String,
    val description: String = "",
    val days: List<RoutineDay> = emptyList(),
    val origin: RoutineOrigin? = null,
    val isActive: Boolean = false,
    val nextDayIndex: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** The famous-routine template a routine was copied from. */
data class RoutineOrigin(val templateId: String, val version: Int)

data class RoutineDay(
    val id: String,
    val routineId: String,
    val position: Int,
    val name: String,
    val kind: DayKind = DayKind.TRAINING,
    val slots: List<RoutineSlot> = emptyList(),
)

/** One exercise of a routine day with its prescription and progression (spec §3.2). */
data class RoutineSlot(
    val id: String,
    val dayId: String,
    val position: Int,
    val target: SlotTarget,
    val sets: Int = 3,
    val warmupSets: Int = 0,
    val prescription: Prescription = Prescription.RepRange(8, 12),
    val restBetweenSetsS: Int = DEFAULT_REST_S,
    val restAfterS: Int = DEFAULT_REST_S,
    /** Slots sharing a group alternate sets and rest only after the round. */
    val supersetGroup: Int? = null,
    /** Dumbbells: volume counts both hands. */
    val bilateral: Boolean = false,
    val notes: String = "",
    val progression: ProgressionRule = ProgressionRule.None,
) {
    fun toSpec() = SlotSpec(
        target, sets, warmupSets, prescription, restBetweenSetsS, restAfterS, supersetGroup, bilateral, notes, progression,
    )

    companion object {
        const val DEFAULT_REST_S = 90
    }
}

/** The content of a slot without ids or position, used to add or update slots and in templates. */
data class SlotSpec(
    val target: SlotTarget,
    val sets: Int = 3,
    val warmupSets: Int = 0,
    val prescription: Prescription = Prescription.RepRange(8, 12),
    val restBetweenSetsS: Int = RoutineSlot.DEFAULT_REST_S,
    val restAfterS: Int = RoutineSlot.DEFAULT_REST_S,
    val supersetGroup: Int? = null,
    val bilateral: Boolean = false,
    val notes: String = "",
    val progression: ProgressionRule = ProgressionRule.None,
)

/** What a slot trains: a pinned variant, or an exercise whose variant is chosen during the session. */
@Serializable
sealed interface SlotTarget {
    @Serializable
    @SerialName("fixed")
    data class Fixed(val variantId: String) : SlotTarget

    /** [allowedVariantIds] empty means any variant of the exercise. */
    @Serializable
    @SerialName("flexible")
    data class Flexible(val exerciseId: String, val allowedVariantIds: List<String> = emptyList()) : SlotTarget
}

/** What each working set aims for; consistent with the exercise's [TrackingType]. */
@Serializable
sealed interface Prescription {
    @Serializable
    @SerialName("repRange")
    data class RepRange(val min: Int, val max: Int) : Prescription

    @Serializable
    @SerialName("reps")
    data class Reps(val reps: Int) : Prescription

    @Serializable
    @SerialName("duration")
    data class Duration(val seconds: Int) : Prescription

    @Serializable
    @SerialName("distance")
    data class Distance(val meters: Int) : Prescription
}

/** How much load a progression step adds. */
@Serializable
sealed interface LoadIncrement {
    /** Weight in grams (barbell, dumbbell, machine in kg, added bodyweight load). */
    @Serializable
    @SerialName("weight")
    data class Weight(val grams: Long) : LoadIncrement

    /** Positions on a machine stack or band levels. */
    @Serializable
    @SerialName("steps")
    data class Steps(val steps: Int) : LoadIncrement

    companion object {
        /** Defaults by load type (spec §3.2). */
        fun defaultFor(loadType: LoadType): LoadIncrement = when (loadType) {
            LoadType.BARBELL -> Weight(BARBELL_GRAMS)
            LoadType.DUMBBELL -> Weight(DUMBBELL_GRAMS)
            LoadType.MACHINE -> Weight(MACHINE_GRAMS)
            LoadType.BODYWEIGHT -> Weight(BARBELL_GRAMS)
            LoadType.BAND -> Steps(1)
            LoadType.NONE -> Weight(0)
        }

        private const val BARBELL_GRAMS = 2_500L
        private const val DUMBBELL_GRAMS = 2_000L
        private const val MACHINE_GRAMS = 5_000L
    }
}

/** Progression rule of a slot (spec §3.2, §3.5). New rules are new subtypes; storage is JSON. */
@Serializable
sealed interface ProgressionRule {
    @Serializable
    @SerialName("none")
    data object None : ProgressionRule

    /** Increase when every working set reached the top of the range for [sessionsAtTop] sessions at the same load. */
    @Serializable
    @SerialName("double")
    data class DoubleProgression(val sessionsAtTop: Int = 1, val increment: LoadIncrement) : ProgressionRule

    /** Increase after every [everyNSessions] sessions in which all working sets hit the target. */
    @Serializable
    @SerialName("linear")
    data class Linear(val increment: LoadIncrement, val everyNSessions: Int = 1) : ProgressionRule
}
