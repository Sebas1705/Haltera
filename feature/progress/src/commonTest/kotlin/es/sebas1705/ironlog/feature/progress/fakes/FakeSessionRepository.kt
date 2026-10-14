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

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int): String = unused()

    override suspend fun startFree(): String = unused()

    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String = unused()

    override suspend fun removeExercise(sessionExerciseId: String) = unused()

    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) = unused()

    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) = unused()

    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) = unused()

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = unused()

    override suspend fun updateSet(setId: String, input: SetInput) = unused()

    override suspend fun deleteSet(setId: String) = unused()

    override suspend fun updateSessionNotes(sessionId: String, notes: String) = unused()

    override suspend fun finish(sessionId: String) = unused()

    override suspend fun deleteSession(sessionId: String) = unused()

    override suspend fun lastForSlotAndVariant(slotId: String, variantId: String, limit: Int): List<ExerciseHistoryEntry> = unused()

    override suspend fun lastForVariant(variantId: String, limit: Int): List<ExerciseHistoryEntry> = unused()
}

internal fun unused(): Nothing = error("Not used by the progress feature")
