package es.sebas1705.ironlog.feature.progress.fakes

import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory sessions; only the read side used by the progress feature is implemented. */
@Suppress("TooManyFunctions") // Implements the whole interface.
class FakeSessionRepository(initial: List<WorkoutSession> = emptyList()) : SessionRepository {
    val sessions = MutableStateFlow(initial)

    /** Every [fromMillis, toMillis) range requested through [observeSessionsBetween]. */
    val requestedRanges = mutableListOf<Pair<Long, Long>>()

    /** Makes [updateTimes] and [deleteSession] fail. */
    var failWrites = false

    override fun observeInProgress(): Flow<WorkoutSession?> = sessions.map { list -> list.firstOrNull { it.inProgress } }

    override suspend fun getInProgress(): WorkoutSession? = sessions.value.firstOrNull { it.inProgress }

    override fun observeSession(id: String): Flow<WorkoutSession?> =
        sessions.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getSession(id: String): WorkoutSession? = sessions.value.firstOrNull { it.id == id }

    override fun observeHistory(limit: Int): Flow<List<WorkoutSession>> = sessions.map { list ->
        list.filter { !it.inProgress }.sortedByDescending { it.startedAt }.take(limit)
    }

    override fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>> {
        requestedRanges += fromMillis to toMillis
        return sessions.map { list -> list.filter { it.startedAt in fromMillis until toMillis }.sortedBy { it.startedAt } }
    }

    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> =
        sessions.map { list -> list.filter { it.routineId == routineId }.maxByOrNull { it.startedAt } }

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long?): String {
        val id = "logged-${sessions.value.size}"
        sessions.value += WorkoutSession(id, startedAt ?: 0, routineId = routineId, routineDayIndex = dayIndex)
        return id
    }

    override suspend fun startFree(startedAt: Long?): String {
        val id = "logged-${sessions.value.size}"
        sessions.value += WorkoutSession(id, startedAt ?: 0)
        return id
    }

    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String = unused()

    override suspend fun removeExercise(sessionExerciseId: String) = unused()

    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) = unused()

    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) = unused()

    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) = unused()

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = unused()

    override suspend fun updateSet(setId: String, input: SetInput) = unused()

    override suspend fun deleteSet(setId: String) = unused()

    override suspend fun updateSessionNotes(sessionId: String, notes: String) = unused()

    override suspend fun finish(sessionId: String, endedAt: Long?) = edit(sessionId) { it.copy(endedAt = endedAt) }

    override suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?) {
        if (failWrites) error("Database unavailable")
        edit(sessionId) { it.copy(startedAt = startedAt, endedAt = endedAt) }
    }

    override suspend fun deleteSession(sessionId: String) {
        if (failWrites) error("Database unavailable")
        sessions.value = sessions.value.filterNot { it.id == sessionId }
    }

    private fun edit(id: String, change: (WorkoutSession) -> WorkoutSession) {
        sessions.value = sessions.value.map { if (it.id == id) change(it) else it }
    }

    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = unused()

    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> =
        unused()
}

internal fun unused(): Nothing = error("Not used by the progress feature")
