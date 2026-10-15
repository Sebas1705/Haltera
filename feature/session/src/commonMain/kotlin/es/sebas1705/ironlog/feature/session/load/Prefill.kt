package es.sebas1705.ironlog.feature.session.load

import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.domain.usecases.progression.Suggestion

/**
 * Prefilled set rows (spec §5 "Entry"). Rows are created not completed; confirming one is a single tap.
 *
 * - The first working set starts at the **heaviest** completed working set of the last performance (the
 *   caller passes the slot's latest session with this variant, else the variant's latest in any slot), with
 *   the prescribed reps; a suggested increase replaces that load.
 * - Every later working set starts as a copy of the set before it, and [followers] keeps the pending rows
 *   below a changed set in step with it.
 * - Warm-ups come from the last warm-ups only, never from working sets.
 */
object Prefill {

    /** The rows of a routine slot reached for the first time in this session: warm-ups then working sets. */
    fun slotRows(
        slot: RoutineSlot,
        trackingType: TrackingType,
        loadType: LoadType,
        suggestion: Suggestion,
        last: SessionExercise?,
    ): List<SetInput> {
        val lastWarmups = last?.sets.orEmpty().filter { it.kind == SetKind.WARMUP && it.completed }
        val warmups = List(slot.warmupSets.coerceAtLeast(0)) { i ->
            val from = lastWarmups.getOrNull(i) ?: lastWarmups.lastOrNull()
            fromSet(from, slot.prescription, trackingType, loadType).copy(kind = SetKind.WARMUP)
        }
        val base = firstWorkingRow(slot.prescription, trackingType, loadType, last)
        val first = when (suggestion) {
            is Suggestion.Increase -> base.copy(
                loadGrams = if (tracksLoad(trackingType, loadType)) suggestion.toGrams else null,
                reps = if (trackingType.tracksReps) lowReps(slot.prescription) ?: base.reps else null,
            )
            is Suggestion.Keep -> if (base.loadGrams == null && tracksLoad(trackingType, loadType)) {
                base.copy(loadGrams = suggestion.loadGrams)
            } else {
                base
            }
            Suggestion.FirstTime -> base
        }
        return warmups + List(slot.sets.coerceAtLeast(1)) { first }
    }

    /**
     * A new working row added by hand (or to an exercise added during the session): a copy of the card's
     * last working row, else the first working row of a new session ([firstWorkingRow]).
     */
    fun extraRow(
        existing: List<SetLog>,
        prescription: Prescription?,
        trackingType: TrackingType,
        loadType: LoadType,
        last: SessionExercise?,
    ): SetInput {
        val previous = existing.lastOrNull { it.kind == SetKind.WORKING }
            ?: return firstWorkingRow(prescription, trackingType, loadType, last)
        return fromSet(previous, prescription, trackingType, loadType).copy(kind = SetKind.WORKING)
    }

    /**
     * The first working set of a session: the load (and how it was entered) of the heaviest completed working
     * set of [last], the prescribed reps (that set's reps when nothing is prescribed).
     */
    fun firstWorkingRow(
        prescription: Prescription?,
        trackingType: TrackingType,
        loadType: LoadType,
        last: SessionExercise?,
    ): SetInput {
        val reference = referenceSet(last)
        val row = fromSet(reference, prescription, trackingType, loadType).copy(kind = SetKind.WORKING)
        return if (trackingType.tracksReps) row.copy(reps = lowReps(prescription) ?: reference?.reps) else row
    }

    /** The heaviest completed working set of [last]; without loads (time, reps only), its last one. */
    fun referenceSet(last: SessionExercise?): SetLog? {
        val done = last?.sets.orEmpty().filter { it.kind == SetKind.WORKING && it.completed }
        return done.filter { it.loadGrams != null }.maxByOrNull { requireNotNull(it.loadGrams) } ?: done.lastOrNull()
    }

    /**
     * The pending rows to update after the user changed [before] into [after]: the working rows below it that
     * still follow it (same load and reps as the old values), stopping at the first row that is completed,
     * was edited by the user ([edited]) or holds other values. Warm-ups and other kinds are skipped. Each
     * follower takes the new load, its entry detail and reps.
     */
    fun followers(
        sets: List<SetLog>,
        before: SetLog,
        after: SetInput,
        edited: Set<String>
    ): List<Pair<String, SetInput>> {
        if (before.kind != SetKind.WORKING || after.kind != SetKind.WORKING) return emptyList()
        if (before.loadGrams == after.loadGrams && before.reps == after.reps && before.loadDetail == after.loadDetail) {
            return emptyList()
        }
        return sets.sortedBy { it.position }
            .dropWhile { it.id != before.id }
            .drop(1)
            .filter { it.kind == SetKind.WORKING }
            .takeWhile { row ->
                !row.completed && row.id !in edited && row.loadGrams == before.loadGrams && row.reps == before.reps
            }
            .map { row ->
                row.id to row.toInput().copy(loadGrams = after.loadGrams, loadDetail = after.loadDetail, reps = after.reps)
            }
    }

    /** Values of [from] (or the [prescription] target) restricted to what [trackingType] records. */
    fun fromSet(from: SetLog?, prescription: Prescription?, trackingType: TrackingType, loadType: LoadType) = SetInput(
        kind = from?.kind ?: SetKind.WORKING,
        reps = if (trackingType.tracksReps) from?.reps ?: lowReps(prescription) else null,
        loadGrams = if (tracksLoad(trackingType, loadType)) from?.loadGrams else null,
        loadDetail = if (tracksLoad(trackingType, loadType)) from?.loadDetail else null,
        durationS = if (trackingType.tracksDuration) from?.durationS ?: (prescription as? Prescription.Duration)?.seconds else null,
        distanceM = if (trackingType.tracksDistance) from?.distanceM ?: (prescription as? Prescription.Distance)?.meters else null,
        completed = false,
    )

    fun tracksLoad(trackingType: TrackingType, loadType: LoadType): Boolean =
        trackingType.tracksLoad && loadType != LoadType.NONE

    /** The reps a row starts at: the bottom of a range, or the fixed count. */
    fun lowReps(prescription: Prescription?): Int? = when (prescription) {
        is Prescription.RepRange -> prescription.min
        is Prescription.Reps -> prescription.reps
        else -> null
    }
}

/** The values of a logged set, for [es.sebas1705.ironlog.data.repository.SessionRepository.updateSet]. */
fun SetLog.toInput() = SetInput(
    kind = kind,
    reps = reps,
    loadGrams = loadGrams,
    loadDetail = loadDetail,
    durationS = durationS,
    distanceM = distanceM,
    rpe = rpe,
    completed = completed,
)
