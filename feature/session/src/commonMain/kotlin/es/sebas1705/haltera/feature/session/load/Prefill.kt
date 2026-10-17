package es.sebas1705.haltera.feature.session.load

import es.sebas1705.haltera.core.units.PlateCalculator
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.domain.usecases.session.LastPerformance

/**
 * Prefilled set rows (spec §5 "Entry"). Rows are created not completed; confirming one is a single tap.
 *
 * - The first working set starts at the **best** set of the most recent day the variant was done, in any
 *   context ([LastPerformance.best]: the heaviest completed set that is not a warm-up), with the prescribed
 *   reps. A suggested increase replaces that load only when it is heavier: a slot's progression never starts
 *   below what was lifted more recently elsewhere.
 * - Every later working set starts as a copy of the set before it, and [followers] keeps the pending rows
 *   below a changed set in step with it.
 * - Warm-ups come from the last warm-ups, never from working sets; without warm-up history they start at
 *   percentages of the first working load ([Warmups]).
 */
object Prefill {

    /** The rows of a routine slot reached for the first time in this session: warm-ups then working sets. */
    fun slotRows(
        slot: RoutineSlot,
        trackingType: TrackingType,
        loadType: LoadType,
        suggestion: Suggestion,
        last: LastPerformance?,
        warmupRounding: WarmupRounding? = null,
    ): List<SetInput> {
        val lastWarmups = LastPerformance.warmups(last?.latest)
        val warmupCount = slot.warmupSets.coerceAtLeast(0)
        val working = workingRowFor(slot, trackingType, loadType, suggestion, last)
        val generated = if (lastWarmups.isEmpty() && warmupRounding != null) {
            Warmups.generated(warmupCount, working, loadType, warmupRounding)
        } else {
            null
        }
        val warmups = List(warmupCount) { i ->
            val from = lastWarmups.getOrNull(i) ?: lastWarmups.lastOrNull()
            val row = fromSet(from, slot.prescription, trackingType, loadType).copy(kind = SetKind.WARMUP)
            generated?.getOrNull(i)?.let { grams -> row.copy(loadGrams = grams, loadDetail = working.loadDetail) } ?: row
        }
        return warmups + List(slot.sets.coerceAtLeast(1)) { working }
    }

    private fun workingRowFor(
        slot: RoutineSlot,
        trackingType: TrackingType,
        loadType: LoadType,
        suggestion: Suggestion,
        last: LastPerformance?,
    ): SetInput {
        val base = firstWorkingRow(slot.prescription, trackingType, loadType, last)
        val first = when (suggestion) {
            is Suggestion.Increase -> if (suggestion.toGrams > (base.loadGrams ?: Long.MIN_VALUE)) {
                base.copy(
                    loadGrams = if (tracksLoad(trackingType, loadType)) suggestion.toGrams else null,
                    reps = if (trackingType.tracksReps) lowReps(slot.prescription) ?: base.reps else null,
                )
            } else {
                base
            }
            is Suggestion.Keep -> if (base.loadGrams == null && tracksLoad(trackingType, loadType)) {
                base.copy(loadGrams = suggestion.loadGrams)
            } else {
                base
            }
            Suggestion.FirstTime -> base
        }
        return first
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
        last: LastPerformance?,
    ): SetInput {
        val previous = existing.lastOrNull { it.kind == SetKind.WORKING }
            ?: return firstWorkingRow(prescription, trackingType, loadType, last)
        return fromSet(previous, prescription, trackingType, loadType).copy(kind = SetKind.WORKING)
    }

    /**
     * The first working set of a session: the load (and how it was entered) of the best set of the last day
     * the variant was done ([LastPerformance.best]), the prescribed reps (that set's reps when nothing is
     * prescribed).
     */
    fun firstWorkingRow(
        prescription: Prescription?,
        trackingType: TrackingType,
        loadType: LoadType,
        last: LastPerformance?,
    ): SetInput {
        val reference = last?.best
        val row = fromSet(reference, prescription, trackingType, loadType).copy(kind = SetKind.WORKING)
        return if (trackingType.tracksReps) row.copy(reps = lowReps(prescription) ?: reference?.reps) else row
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

    /**
     * The warm-ups to regenerate after the user changed the first working set [before] into [after] (e.g. a
     * first-time exercise, whose warm-ups start without a load): the pending warm-ups not edited by the user
     * ([edited]) that have no load or still hold the loads generated from the old working load. Each takes
     * the [Warmups] percentage of the new working load.
     */
    fun warmupFollowers(
        sets: List<SetLog>,
        before: SetLog,
        after: SetInput,
        edited: Set<String>,
        loadType: LoadType,
        rounding: WarmupRounding,
    ): List<Pair<String, SetInput>> {
        if (before.kind != SetKind.WORKING || after.kind != SetKind.WORKING) return emptyList()
        if (before.loadGrams == after.loadGrams) return emptyList()
        val sorted = sets.sortedBy { it.position }
        if (sorted.firstOrNull { it.kind == SetKind.WORKING }?.id != before.id) return emptyList()
        val warmups = sorted.takeWhile { it.id != before.id }.filter { it.kind == SetKind.WARMUP }
        val loads = Warmups.generated(warmups.size, after, loadType, rounding) ?: return emptyList()
        val old = Warmups.generated(warmups.size, before.toInput(), loadType, rounding)
        return warmups.mapIndexedNotNull { i, row ->
            val pending = !row.completed && row.id !in edited
            val follows = row.loadGrams == null || row.loadGrams == old?.getOrNull(i)
            if (!pending || !follows || row.loadGrams == loads[i]) return@mapIndexedNotNull null
            row.id to row.toInput().copy(loadGrams = loads[i], loadDetail = after.loadDetail)
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

    /**
     * [input] with the load the set editor shows when the set has none, so saving stores what was on screen (QA
     * alpha.5: a barbell set showed "Total 20 kg / bar only", only the reps were changed, and it was saved without a
     * load). A barbell shows the bar ([barGrams]), a band level 1, a machine by stack position plate 1. A dumbbell,
     * a machine weight or a bodyweight load shows 0, which is no load, so those stay empty.
     */
    fun shownInEditor(input: SetInput, trackingType: TrackingType, loadType: LoadType, barGrams: Long): SetInput {
        if (input.loadGrams != null || !tracksLoad(trackingType, loadType)) return input
        return when (loadType) {
            LoadType.BARBELL -> input.copy(
                loadGrams = barGrams,
                loadDetail = input.loadDetail ?: LoadDetail.encode(LoadDetail.Barbell(barGrams)),
            )
            LoadType.BAND -> input.copy(loadGrams = 1, loadDetail = input.loadDetail ?: LoadDetail.encode(LoadDetail.Band))
            LoadType.MACHINE -> if (isMachinePlate(input.loadDetail)) input.copy(loadGrams = 1) else input
            else -> input
        }
    }

    /** The reps a row starts at: the bottom of a range, or the fixed count. */
    fun lowReps(prescription: Prescription?): Int? = when (prescription) {
        is Prescription.RepRange -> prescription.min
        is Prescription.Reps -> prescription.reps
        else -> null
    }
}

/**
 * Warm-up loads when the exercise has no warm-up history but the first working load is known: 60 % for one
 * warm-up, 50 % and 70 % for two, evenly from 40 % to 80 % for more. Each is rounded down to the variant's step
 * (and to what the plates can build on a barbell) and never exceeds the working load.
 */
object Warmups {
    private const val PERCENT = 100L
    private const val SINGLE = 60L
    private const val FIRST_OF_TWO = 50L
    private const val SECOND_OF_TWO = 70L
    private const val LOWEST = 40L
    private const val HIGHEST = 80L

    /** The loads of [count] warm-ups before [working]; null unless it is a weight (not a stack position, band…). */
    fun generated(count: Int, working: SetInput, loadType: LoadType, rounding: WarmupRounding): List<Long>? {
        val workingGrams = working.loadGrams?.takeIf { it > 0 } ?: return null
        val weighted = loadType == LoadType.BARBELL || loadType == LoadType.DUMBBELL ||
            (loadType == LoadType.MACHINE && !isMachinePlate(working.loadDetail))
        if (!weighted || count <= 0) return null
        return percents(count).map { percent ->
            load(workingGrams * percent / PERCENT, rounding, loadType).coerceAtMost(workingGrams)
        }
    }

    /** 60 for one warm-up, 50/70 for two, evenly spaced from 40 to 80 for more. */
    fun percents(count: Int): List<Long> = when (count) {
        0 -> emptyList()
        1 -> listOf(SINGLE)
        2 -> listOf(FIRST_OF_TWO, SECOND_OF_TWO)
        else -> List(count) { i -> LOWEST + (HIGHEST - LOWEST) * i / (count - 1) }
    }

    private fun load(target: Long, rounding: WarmupRounding, loadType: LoadType): Long {
        val step = rounding.stepGrams.coerceAtLeast(1)
        val stepped = (target / step * step).coerceAtLeast(step)
        if (loadType != LoadType.BARBELL) return stepped
        val onBar = stepped.coerceAtLeast(rounding.barGrams)
        if (rounding.plates.isEmpty()) return onBar
        return PlateCalculator.platesPerSide(onBar, rounding.barGrams, rounding.plates).achievedGrams
    }
}

/**
 * How generated warm-up loads are rounded: the variant's stepper step and, for barbells, the bar and the
 * plates the user owns.
 */
data class WarmupRounding(val stepGrams: Long, val barGrams: Long = 0, val plates: List<Long> = emptyList())

/** The values of a logged set, for [es.sebas1705.haltera.data.repository.SessionRepository.updateSet]. */
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
