package es.sebas1705.haltera.feature.today.ui

import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// In-memory fakes of the repositories the Today tab reads. Operations it never calls throw.

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the Today tab")

@Suppress("TooManyFunctions")
internal class FakeRoutineRepository(active: Routine? = null) : RoutineRepository {
    val active = MutableStateFlow(active)
    val pointerChanges = mutableListOf<Pair<String, Int>>()

    override fun observeActive(): Flow<Routine?> = active

    override suspend fun setNextDayIndex(routineId: String, index: Int) {
        pointerChanges += routineId to index
        active.update { routine -> routine?.takeIf { it.id == routineId }?.copy(nextDayIndex = index) ?: routine }
    }

    /** Every routine: the active one plus [others]. */
    val others = MutableStateFlow<List<Routine>>(emptyList())

    override fun observeRoutines(): Flow<List<Routine>> =
        active.combine(others) { a, rest -> listOfNotNull(a) + rest }
    override fun observeRoutine(id: String): Flow<Routine?> = unused()
    override suspend fun getRoutine(id: String): Routine? = unused()
    override suspend fun createRoutine(name: String, description: String): String = unused()
    override suspend fun updateRoutine(id: String, name: String, description: String) = unused()
    override suspend fun deleteRoutine(id: String) = unused()
    override suspend fun setActive(id: String?) = unused()
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

@Suppress("TooManyFunctions")
internal class FakeSessionRepository(
    inProgress: WorkoutSession? = null,
    sessions: List<WorkoutSession> = emptyList(),
) : SessionRepository {
    val inProgress = MutableStateFlow(inProgress)
    val sessions = MutableStateFlow(sessions)
    val started = mutableListOf<Pair<String, Int>?>()

    /** Start time of each start ([started] order); null for a live start. */
    val startedAt = mutableListOf<Long?>()
    val finished = mutableListOf<Pair<String, Long?>>()
    var failStart = false

    override fun observeInProgress(): Flow<WorkoutSession?> = inProgress

    override fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>> =
        sessions.map { list -> list.filter { it.startedAt in fromMillis until toMillis }.sortedBy { it.startedAt } }

    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> =
        sessions.map { list -> list.filter { it.routineId == routineId }.maxByOrNull { it.startedAt } }

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long?): String {
        if (failStart) error("Database unavailable")
        started += routineId to dayIndex
        this.startedAt += startedAt
        val id = "session-$routineId-$dayIndex"
        if (startedAt != null) {
            sessions.value += WorkoutSession(id, startedAt, routineId = routineId, routineDayIndex = dayIndex)
        }
        return id
    }

    override suspend fun startFree(startedAt: Long?): String {
        if (failStart) error("Database unavailable")
        started += null
        this.startedAt += startedAt
        if (startedAt != null) sessions.value += WorkoutSession("session-free", startedAt)
        return "session-free"
    }

    override suspend fun finish(sessionId: String, endedAt: Long?) {
        finished += sessionId to endedAt
        sessions.value = sessions.value.map { if (it.id == sessionId) it.copy(endedAt = endedAt) else it }
    }

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
    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = unused()
    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> =
        unused()
}

internal class FakeExerciseRepository(
    private val exercises: List<Exercise> = emptyList(),
) : ExerciseRepository {
    override suspend fun getExercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    override suspend fun getVariant(id: String): ExerciseVariant? =
        exercises.flatMap { it.variants }.firstOrNull { it.id == id }

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> =
        exercises.flatMap { it.variants }.filter { it.id in ids }

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> = unused()
    override fun observeExercise(id: String): Flow<Exercise?> = unused()
    override suspend fun createCustom(input: NewExercise): Exercise = unused()
    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = unused()
    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = unused()
}

internal class FakeBodyWeightRepository : BodyWeightRepository {
    val entries = MutableStateFlow<List<BodyWeightEntry>>(emptyList())

    override fun observeAll(): Flow<List<BodyWeightEntry>> = entries

    override fun observeLatest(): Flow<BodyWeightEntry?> = entries.map { list -> list.maxByOrNull { it.measuredAt } }

    override suspend fun add(weightGrams: Long, measuredAt: Long?, notes: String): String {
        val id = "bw-${entries.value.size + 1}"
        entries.update { it + BodyWeightEntry(id, measuredAt ?: (it.size + 1L), weightGrams, notes) }
        return id
    }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<BodyWeightEntry>> = unused()
    override suspend fun update(entry: BodyWeightEntry) = unused()
    override suspend fun delete(id: String) = unused()
}

internal class FakeSettingsRepository(unit: WeightUnit = WeightUnit.KG) : SettingsRepository {
    private val state = MutableStateFlow(UserSettings(unit = unit))
    override val settings: StateFlow<UserSettings> = state

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) = state.update { it.copy(unit = unit) }
    override fun setBarWeight(grams: Long) = unused()
    override fun setAvailablePlates(grams: List<Long>) = unused()
    override fun setKeepScreenOn(enabled: Boolean) = unused()
    override fun setRestSound(enabled: Boolean) = unused()
    override fun setRestVibration(enabled: Boolean) = unused()
}
