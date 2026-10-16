package es.sebas1705.haltera.feature.session.ui

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.domain.usecases.session.LastTimeComparisons
import es.sebas1705.haltera.domain.usecases.stats.Stats
import es.sebas1705.haltera.feature.session.load.isMachinePlate

/**
 * Builds the finish summary of a finished session (spec §5): duration, sets, how each exercise compares with the
 * last time it was done, new records and the increases earned. No volume here: kg × reps is ambiguous in the
 * moment (it lives in the Progress statistics).
 */
object SummaryBuilder {

    /** What the summary needs to know about each session exercise (by its id). */
    data class Card(val name: ExerciseName, val trackingType: TrackingType, val loadType: LoadType)

    @Suppress("LongParameterList")
    suspend fun build(
        session: WorkoutSession,
        slots: Map<String, RoutineSlot>,
        cards: Map<String, Card>,
        dayOf: (Long) -> Int,
        previous: suspend (variantId: String) -> List<ExerciseHistoryEntry>,
        increase: suspend (slot: RoutineSlot, variantId: String) -> Suggestion.Increase?,
        /** Bilateral routine slots, so the "session volume" record follows the one volume rule (TrainingMath.volume). */
        bilateralSlots: Set<String> = emptySet(),
    ): FinishSummary {
        val exercises = session.exercises.sortedBy { it.position }
        val records = mutableListOf<RecordLine>()
        val increases = mutableListOf<IncreaseLine>()
        val comparisons = mutableListOf<ComparisonLine>()
        exercises.forEach { exercise ->
            val card = cards[exercise.id] ?: Card(ExerciseName(null, null, null), TrackingType.WEIGHT_REPS, LoadType.NONE)
            val done = Stats.countedSets(exercise.sets).isNotEmpty()
            val before = if (done) previous(exercise.variantId) else emptyList()
            // The first time an exercise is done is a baseline, not a record.
            val kinds = if (before.isEmpty()) {
                emptySet()
            } else {
                Stats.newRecords(before, ExerciseHistoryEntry(session.id, session.startedAt, exercise), bilateralSlots)
            }
            if (kinds.isNotEmpty()) records += RecordLine(card.name, kinds)
            LastTimeComparisons.compare(exercise, before, card.trackingType, card.loadType, dayOf)?.let {
                comparisons += ComparisonLine(card.name, card.trackingType, card.loadType, it)
            }
            val earned = exercise.slotId?.let { slots[it] }?.let { increase(it, exercise.variantId) }
            if (earned != null) {
                val plate = isMachinePlate(exercise.sets.lastOrNull { s -> s.completed }?.loadDetail)
                increases += IncreaseLine(card.name, card.loadType, earned.fromGrams, earned.toGrams, plate)
            }
        }
        return FinishSummary(
            durationMs = ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0),
            completedSets = exercises.sumOf { e -> e.sets.count { it.completed } },
            comparisons = comparisons,
            records = records,
            increases = increases,
        )
    }
}
