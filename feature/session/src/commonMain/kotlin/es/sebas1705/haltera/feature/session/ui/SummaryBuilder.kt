package es.sebas1705.haltera.feature.session.ui

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.domain.usecases.stats.Stats
import es.sebas1705.haltera.feature.session.load.isMachinePlate

/** Builds the finish summary of a finished session (spec §5). */
object SummaryBuilder {

    @Suppress("LongParameterList")
    suspend fun build(
        session: WorkoutSession,
        slots: Map<String, RoutineSlot>,
        names: Map<String, ExerciseName>,
        loadTypes: Map<String, LoadType>,
        previous: suspend (variantId: String) -> List<ExerciseHistoryEntry>,
        increase: suspend (slot: RoutineSlot, variantId: String) -> Suggestion.Increase?,
    ): FinishSummary {
        val exercises = session.exercises.sortedBy { it.position }
        val records = mutableListOf<RecordLine>()
        val increases = mutableListOf<IncreaseLine>()
        exercises.forEach { exercise ->
            val name = names[exercise.id] ?: ExerciseName(null, null, null)
            // The first time an exercise is done is a baseline, not a record.
            val before = if (Stats.countedSets(exercise.sets).isEmpty()) emptyList() else previous(exercise.variantId)
            val kinds = if (before.isEmpty()) {
                emptySet()
            } else {
                Stats.newRecords(before, ExerciseHistoryEntry(session.id, session.startedAt, exercise))
            }
            if (kinds.isNotEmpty()) records += RecordLine(name, kinds)
            val earned = exercise.slotId?.let { slots[it] }?.let { increase(it, exercise.variantId) }
            if (earned != null) {
                val plate = isMachinePlate(exercise.sets.lastOrNull { s -> s.completed }?.loadDetail)
                increases += IncreaseLine(name, loadTypes[exercise.id] ?: LoadType.NONE, earned.fromGrams, earned.toGrams, plate)
            }
        }
        return FinishSummary(
            durationMs = ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0),
            volumeGrams = exercises.sumOf {
                Stats.volume(
                    it,
                    bilateral = it.slotId?.let { id -> slots[id] }?.bilateral == true
                )
            },
            completedSets = exercises.sumOf { e -> e.sets.count { it.completed } },
            records = records,
            increases = increases,
        )
    }
}
