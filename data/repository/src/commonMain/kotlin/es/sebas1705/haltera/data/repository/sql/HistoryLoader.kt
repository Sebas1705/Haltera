package es.sebas1705.ironlog.data.repository.sql

import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.local.db.WorkoutSessionEntity
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.WorkoutSession
import kotlinx.coroutines.flow.Flow

/** Loads sessions and performed exercises with their sets. Blocking: call on the IO dispatcher. */
internal class HistoryLoader(private val db: AppDatabase) {

    /** Emits now and after any write to sessions, session exercises or sets. */
    fun changes(): Flow<Unit> = tablesChanged(
        db.workoutSessionEntityQueries.selectById(""),
        db.sessionExerciseEntityQueries.selectById(""),
        db.setLogEntityQueries.selectById(""),
    )

    fun session(row: WorkoutSessionEntity?): WorkoutSession? = row?.let { sessions(listOf(it)).single() }

    fun sessions(rows: List<WorkoutSessionEntity>): List<WorkoutSession> {
        if (rows.isEmpty()) return emptyList()
        val exercises = rows.map { it.id }.chunked(MAX_IN_ARGS).flatMap { ids ->
            db.sessionExerciseEntityQueries.selectBySessions(ids) { id, sessionId, position, variantId, slotId, notes ->
                SessionExercise(id, sessionId, position.toInt(), variantId, slotId, notes)
            }.executeAsList()
        }
        val withSets = withSets(exercises).groupBy { it.sessionId }
        return rows.map { row ->
            WorkoutSession(
                id = row.id,
                startedAt = row.startedAt,
                endedAt = row.endedAt,
                routineId = row.routineId,
                routineDayIndex = row.routineDayIndex?.toInt(),
                dayName = row.dayName,
                notes = row.notes,
                bodyWeightGrams = row.bodyWeightGrams,
                exercises = withSets[row.id].orEmpty().sortedBy { it.position },
            )
        }
    }

    // History is ordered by session start (not insertion) and limited to sessions started before [before].

    fun byVariant(variantId: String, limit: Int, before: Long? = null): List<ExerciseHistoryEntry> = entries(
        db.sessionExerciseEntityQueries.historyByVariant(variantId, bound(before), limit.toLong(), ::HistoryRow)
            .executeAsList(),
    )

    fun byExercise(exerciseId: String, limit: Int): List<ExerciseHistoryEntry> = entries(
        db.sessionExerciseEntityQueries.historyByExercise(exerciseId, bound(null), limit.toLong(), ::HistoryRow)
            .executeAsList(),
    )

    fun bySlotAndVariant(slotId: String, variantId: String, limit: Int, before: Long? = null): List<ExerciseHistoryEntry> =
        entries(
            db.sessionExerciseEntityQueries.historyBySlotAndVariant(
                slotId,
                variantId,
                bound(before),
                limit.toLong(),
                ::HistoryRow,
            ).executeAsList(),
        )

    private fun bound(before: Long?): Long = before ?: Long.MAX_VALUE

    private fun entries(rows: List<HistoryRow>): List<ExerciseHistoryEntry> {
        val exercises = withSets(rows.map { it.exercise }).associateBy { it.id }
        return rows.map { row ->
            ExerciseHistoryEntry(
                row.exercise.sessionId,
                row.startedAt,
                exercises.getValue(row.exercise.id)
            )
        }
    }

    private fun withSets(exercises: List<SessionExercise>): List<SessionExercise> {
        if (exercises.isEmpty()) return emptyList()
        val sets = exercises.map { it.id }.chunked(MAX_IN_ARGS).flatMap { ids ->
            db.setLogEntityQueries.selectBySessionExercises(ids).executeAsList()
        }.map { it.toModel() }.groupBy { it.sessionExerciseId }
        return exercises.map { it.copy(sets = sets[it.id].orEmpty().sortedBy { set -> set.position }) }
    }

    private class HistoryRow(
        id: String,
        sessionId: String,
        position: Long,
        variantId: String,
        slotId: String?,
        notes: String,
        val startedAt: Long,
    ) {
        val exercise = SessionExercise(id, sessionId, position.toInt(), variantId, slotId, notes)
    }

    private companion object {
        const val MAX_IN_ARGS = 500
    }
}
