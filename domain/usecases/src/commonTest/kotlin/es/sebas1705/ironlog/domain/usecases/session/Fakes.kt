package es.sebas1705.ironlog.domain.usecases.session

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private fun unused(): Nothing = error("unused in this test")

/** Sessions kept in a list (insertion order); history entries per slot for the suggestion. */
@Suppress("TooManyFunctions")
internal class FakeSessions : SessionRepository {
    val sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val slotHistory = mutableListOf<ExerciseHistoryEntry>()
    val finished = mutableListOf<Pair<String, Long?>>()
    val starts = mutableListOf<String>()
    private var nextId = 0

    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> =
        sessions.map { list -> list.filter { it.routineId == routineId }.maxByOrNull { it.startedAt } }

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long?): String {
        val id = "s${nextId++}"
        starts += "day:$routineId:$dayIndex@$startedAt"
        sessions.value += WorkoutSession(id, startedAt ?: 0, routineId = routineId, routineDayIndex = dayIndex)
        return id
    }

    override suspend fun startFree(startedAt: Long?): String {
        val id = "s${nextId++}"
        starts += "free@$startedAt"
        sessions.value += WorkoutSession(id, startedAt ?: 0)
        return id
    }

    override suspend fun finish(sessionId: String, endedAt: Long?) {
        finished += sessionId to endedAt
        sessions.value = sessions.value.map { if (it.id == sessionId) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = slotHistory
        .filter { it.exercise.slotId == slotId && it.exercise.variantId == variantId }
        .filter { before == null || it.sessionStartedAt < before }
        .sortedByDescending { it.sessionStartedAt }
        .take(limit)

    override fun observeInProgress(): Flow<WorkoutSession?> = unused()
    override suspend fun getInProgress(): WorkoutSession? = unused()
    override fun observeSession(id: String): Flow<WorkoutSession?> = unused()
    override suspend fun getSession(id: String): WorkoutSession? = unused()
    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String = unused()
    override suspend fun removeExercise(sessionExerciseId: String) = unused()
    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) = unused()
    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) = unused()
    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) = unused()
    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = unused()
    override suspend fun updateSet(setId: String, input: SetInput) = unused()
    override suspend fun deleteSet(setId: String) = unused()
    override suspend fun updateSessionNotes(sessionId: String, notes: String) = unused()
    override suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?) = unused()
    override suspend fun deleteSession(sessionId: String) = unused()
    override fun observeHistory(limit: Int): Flow<List<WorkoutSession>> = unused()
    override fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>> = unused()
    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> =
        unused()
}

/** Only the active routine matters here. */
@Suppress("TooManyFunctions")
internal class FakeRoutines(routine: Routine?) : RoutineRepository {
    val active = MutableStateFlow(routine)

    override fun observeActive(): Flow<Routine?> = active

    override fun observeRoutines(): Flow<List<Routine>> = unused()
    override fun observeRoutine(id: String): Flow<Routine?> = unused()
    override suspend fun getRoutine(id: String): Routine? = unused()
    override suspend fun createRoutine(name: String, description: String): String = unused()
    override suspend fun updateRoutine(id: String, name: String, description: String) = unused()
    override suspend fun deleteRoutine(id: String) = unused()
    override suspend fun setActive(id: String?) = unused()
    override suspend fun setNextDayIndex(routineId: String, index: Int) = unused()
    override suspend fun addDay(routineId: String, name: String, kind: DayKind): String = unused()
    override suspend fun updateDay(dayId: String, name: String, kind: DayKind) = unused()
    override suspend fun deleteDay(dayId: String) = unused()
    override suspend fun moveDay(dayId: String, toPosition: Int) = unused()
    override suspend fun addSlot(dayId: String, spec: SlotSpec): String = unused()
    override suspend fun updateSlot(slotId: String, spec: SlotSpec) = unused()
    override suspend fun deleteSlot(slotId: String) = unused()
    override suspend fun moveSlot(slotId: String, toPosition: Int) = unused()
    override fun templates(): List<RoutineTemplate> = unused()
    override suspend fun copyTemplate(templateId: String, language: String): String = unused()
}
