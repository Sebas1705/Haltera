package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.WorkoutSession

/** A session placed on the local calendar day it started on (an epoch day, see [EpochDays]). */
data class DatedSession(val day: Int, val session: WorkoutSession)

/**
 * Per-set and per-session building blocks shared by every statistic. Loads are grams; volume is
 * gram-reps (kg × reps once divided by 1000).
 */
@Suppress("TooManyFunctions") // Small per-set and per-session building blocks, kept together on purpose.
object TrainingMath {
    /** `loadDetail` of bands and machines logged by stack position: the load is a level, not a weight. */
    private val INDEX_LOAD = Regex(""""type"\s*:\s*"(band|machinePlate)"""")

    /** Whether the set's load is a band level or a machine plate number rather than grams. */
    fun isIndexLoad(set: SetLog): Boolean = set.loadDetail?.let { INDEX_LOAD.containsMatchIn(it) } == true

    /** Completed and not a warm-up (drop sets and sets to failure count). */
    fun isCounted(set: SetLog): Boolean = set.completed && set.kind != SetKind.WARMUP

    /** A counted set with something done: at least one rep, a duration or a distance (Metric.EFFECTIVE_SETS). */
    fun isEffective(set: SetLog): Boolean =
        isCounted(set) && ((set.reps ?: 0) > 0 || (set.durationS ?: 0) > 0 || (set.distanceM ?: 0) > 0)

    /** The weight a set's load and volume use, or null when it has none or it is not a weight. */
    fun weightGrams(set: SetLog): Long? = set.loadGrams?.takeUnless { isIndexLoad(set) }

    /**
     * Volume of one set (Metric.VOLUME): load × reps of a counted set, doubled for [bilateral] dumbbells.
     * Nothing for unloaded or assisted (negative) bodyweight sets, band levels and machine plate numbers.
     * Part of the one volume rule, see [volume].
     */
    fun setVolume(set: SetLog, bilateral: Boolean = false): Long {
        if (!isCounted(set)) return 0
        val load = weightGrams(set)?.coerceAtLeast(0) ?: return 0
        val volume = load * (set.reps ?: 0).coerceAtLeast(0)
        return if (bilateral) volume * 2 else volume
    }

    /**
     * Volume (tonnage) of one performed exercise, in gram-reps. This is the one volume rule of the app: the
     * Progress overview, muscles, calendar and saved-session detail, the exercise progression table and charts,
     * and the "Session volume" records (Exercises detail, Progress, finish summary) all sum through here
     * (core design §3.3, "Volume").
     *
     * - Σ [setVolume]: completed sets that are not warm-ups, load × reps.
     * - The load is what was stored: bar plus plates for a barbell, the machine weight, the added load of a
     *   bodyweight exercise. Assisted (negative) and unloaded bodyweight sets, band levels and machine stack
     *   positions are not weights and add nothing; timed and distance sets have no reps and add nothing.
     * - A DUMBBELL load is stored per implement (one dumbbell). It counts twice only when the exercise was
     *   logged from a routine slot flagged `bilateral` (one implement per hand; the slot editor turns the flag on
     *   for dumbbell slots): its slot id is in [bilateralSlots], built with [bilateralSlots] from the routines.
     *   Free sessions and exercises added outside the plan have no slot, so one implement is counted.
     *
     * Nothing stored changes: the flag is read when summing, so editing a slot's flag re-sums its history.
     */
    fun volume(exercise: SessionExercise, bilateralSlots: Set<String> = emptySet()): Long {
        val bilateral = exercise.slotId != null && exercise.slotId in bilateralSlots
        return exercise.sets.sumOf { setVolume(it, bilateral) }
    }

    /** Volume of a whole session: [volume] of each exercise. */
    fun volume(session: WorkoutSession, bilateralSlots: Set<String> = emptySet()): Long =
        session.exercises.sumOf { volume(it, bilateralSlots) }

    /** The slots of every routine whose dumbbell volume counts both hands: the input of [volume]. */
    fun bilateralSlots(routines: List<Routine>): Set<String> =
        routines.flatMap { routine -> routine.days.flatMap { day -> day.slots.filter { it.bilateral }.map { it.id } } }
            .toSet()

    fun effectiveSets(exercise: SessionExercise): Int = exercise.sets.count(::isEffective)

    fun effectiveSets(session: WorkoutSession): Int = session.exercises.sumOf(::effectiveSets)

    /** Epley estimate of a counted set with a weight (Metric.ESTIMATED_ONE_RM), else 0. */
    fun setOneRm(set: SetLog): Long =
        if (isCounted(set)) Stats.epleyOneRm(weightGrams(set) ?: 0, set.reps ?: 0) else 0

    /** The finished session's length (Metric.SESSION_DURATION); null while in progress. */
    fun durationMillis(
        session: WorkoutSession
    ): Long? = session.endedAt?.let { (it - session.startedAt).coerceAtLeast(0) }
}
