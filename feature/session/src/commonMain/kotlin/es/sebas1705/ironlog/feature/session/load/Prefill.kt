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
 * Prefilled set rows (spec §5 "Entry"): from the last performance of the slot/variant, or the
 * progression suggestion. Rows are created not completed; confirming one is a single tap.
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
        val lastWorking = last?.sets.orEmpty().filter { it.kind == SetKind.WORKING && it.completed }
        val warmups = List(slot.warmupSets.coerceAtLeast(0)) { i ->
            val from = lastWarmups.getOrNull(i) ?: lastWarmups.lastOrNull()
            fromSet(from, slot.prescription, trackingType, loadType).copy(kind = SetKind.WARMUP)
        }
        val working = List(slot.sets.coerceAtLeast(1)) { i ->
            val from = lastWorking.getOrNull(i) ?: lastWorking.lastOrNull()
            val base = fromSet(from, slot.prescription, trackingType, loadType)
            when (suggestion) {
                is Suggestion.Increase -> base.copy(
                    loadGrams = suggestion.toGrams.takeIf { tracksLoad(trackingType, loadType) },
                    reps = if (trackingType.tracksReps) lowReps(slot.prescription) else null,
                )
                is Suggestion.Keep -> if (from?.loadGrams == null && tracksLoad(trackingType, loadType)) {
                    base.copy(loadGrams = suggestion.loadGrams)
                } else {
                    base
                }
                Suggestion.FirstTime -> base
            }
        }
        return warmups + working
    }

    /** A new row added by hand: a copy of the card's last row, else of the last performance, else the target. */
    fun extraRow(
        existing: List<SetLog>,
        prescription: Prescription?,
        trackingType: TrackingType,
        loadType: LoadType,
        last: SessionExercise?,
    ): SetInput {
        val from = existing.lastOrNull { it.kind == SetKind.WORKING }
            ?: existing.lastOrNull()
            ?: last?.sets?.lastOrNull { it.kind == SetKind.WORKING && it.completed }
        return fromSet(from, prescription, trackingType, loadType).copy(kind = SetKind.WORKING)
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
