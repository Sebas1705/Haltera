package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.core.datastore.LoadStepStore
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.feature.session.platform.LiveSessionIndicator
import es.sebas1705.haltera.feature.session.platform.RestAlert
import es.sebas1705.haltera.feature.session.timer.TimerSnapshot
import es.sebas1705.haltera.feature.session.timer.TimerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TestClock(var now: Long = 1_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }
}

/** In-memory sessions. [history] holds finished performances returned by the `lastFor…` queries. */
@Suppress("TooManyFunctions")
class FakeSessionRepository(private val clock: TestClock) : SessionRepository {
    val sessions = MutableStateFlow<Map<String, WorkoutSession>>(emptyMap())
    val history = mutableListOf<ExerciseHistoryEntry>()
    private var nextId = 0

    private fun id(prefix: String) = "$prefix-${nextId++}"

    fun put(session: WorkoutSession) {
        sessions.value = sessions.value + (session.id to session)
    }

    private fun editSession(id: String, change: (WorkoutSession) -> WorkoutSession) {
        val session = sessions.value[id] ?: return
        sessions.value = sessions.value + (id to change(session))
    }

    private fun editExercise(exerciseId: String, change: (SessionExercise) -> SessionExercise) {
        val session = sessions.value.values.firstOrNull { s -> s.exercises.any { it.id == exerciseId } } ?: return
        editSession(
            session.id
        ) { s -> s.copy(exercises = s.exercises.map { if (it.id == exerciseId) change(it) else it }) }
    }

    private fun exerciseOfSet(setId: String): SessionExercise? =
        sessions.value.values.flatMap { it.exercises }.firstOrNull { e -> e.sets.any { it.id == setId } }

    fun set(setId: String): SetLog? = sessions.value.values.flatMap {
        it.exercises
    }.flatMap { it.sets }.firstOrNull { it.id == setId }

    override fun observeInProgress(): Flow<WorkoutSession?> = sessions.map { m -> m.values.firstOrNull { it.inProgress } }

    override suspend fun getInProgress(): WorkoutSession? = sessions.value.values.firstOrNull { it.inProgress }

    override fun observeSession(id: String): Flow<WorkoutSession?> = sessions.map { it[id] }

    override suspend fun getSession(id: String): WorkoutSession? = sessions.value[id]

    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> = error("unused")

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long?): String =
        error("unused")

    override suspend fun startFree(startedAt: Long?): String = error("unused")

    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String {
        val newId = id("ex")
        editSession(sessionId) { s ->
            s.copy(exercises = s.exercises + SessionExercise(newId, sessionId, s.exercises.size, variantId, slotId))
        }
        return newId
    }

    override suspend fun removeExercise(sessionExerciseId: String) {
        val session = sessions.value.values.firstOrNull { s -> s.exercises.any { it.id == sessionExerciseId } } ?: return
        editSession(session.id) { s ->
            s.copy(
                exercises = s.exercises.filterNot { it.id == sessionExerciseId }.mapIndexed { i, e ->
                    e.copy(
                        position = i
                    )
                }
            )
        }
    }

    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) =
        editExercise(sessionExerciseId) { it.copy(variantId = variantId) }

    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) {
        val session = sessions.value.values.firstOrNull { s -> s.exercises.any { it.id == sessionExerciseId } } ?: return
        editSession(session.id) { s ->
            val list = s.exercises.sortedBy { it.position }.toMutableList()
            val item = list.first { it.id == sessionExerciseId }
            list.remove(item)
            list.add(toPosition.coerceIn(0, list.size), item)
            s.copy(exercises = list.mapIndexed { i, e -> e.copy(position = i) })
        }
    }

    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) =
        editExercise(sessionExerciseId) { it.copy(notes = notes) }

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String {
        val setId = id("set")
        editExercise(
            sessionExerciseId
        ) { e -> e.copy(sets = e.sets + input.toLog(setId, sessionExerciseId, e.sets.size)) }
        return setId
    }

    override suspend fun updateSet(setId: String, input: SetInput) {
        val exercise = exerciseOfSet(setId) ?: return
        editExercise(exercise.id) { e ->
            e.copy(sets = e.sets.map { if (it.id == setId) input.toLog(setId, e.id, it.position) else it })
        }
    }

    override suspend fun deleteSet(setId: String) {
        val exercise = exerciseOfSet(setId) ?: return
        editExercise(exercise.id) { e ->
            e.copy(
                sets = e.sets.filterNot {
                    it.id == setId
                }.mapIndexed { i, s -> s.copy(position = i) }
            )
        }
    }

    override suspend fun updateSessionNotes(sessionId: String, notes: String) = editSession(sessionId) {
        it.copy(notes = notes)
    }

    var finished = 0
        private set

    override suspend fun finish(sessionId: String, endedAt: Long?) {
        val session = sessions.value[sessionId] ?: return
        if (!session.inProgress) return
        finished++
        val ended = session.copy(endedAt = endedAt ?: clock.now)
        editSession(sessionId) { ended }
        ended.exercises.forEach { history.add(0, ExerciseHistoryEntry(sessionId, ended.startedAt, it)) }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessions.value = sessions.value - sessionId
    }

    override fun observeHistory(limit: Int): Flow<List<WorkoutSession>> = flowOf(emptyList())

    override fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>> = flowOf(
        emptyList()
    )

    override suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?) =
        editSession(sessionId) { it.copy(startedAt = startedAt, endedAt = endedAt) }

    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = history(before).filter {
        it.exercise.slotId == slotId && it.exercise.variantId == variantId
    }.take(limit)

    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> =
        history(before).filter { it.exercise.variantId == variantId }.take(limit)

    /** Like the SQL: latest start first, only sessions started before [before]. */
    private fun history(before: Long?) =
        history.filter { before == null || it.sessionStartedAt < before }.sortedByDescending { it.sessionStartedAt }

    private fun SetInput.toLog(id: String, exerciseId: String, position: Int) = SetLog(
        id = id,
        sessionExerciseId = exerciseId,
        position = position,
        kind = kind,
        reps = reps,
        loadGrams = loadGrams,
        loadDetail = loadDetail,
        durationS = durationS,
        distanceM = distanceM,
        rpe = rpe,
        completed = completed,
        completedAt = if (completed) clock.now else null,
    )
}

class FakeExerciseRepository(private val exercises: List<Exercise>) : ExerciseRepository {
    private val variants = exercises.flatMap { it.variants }.associateBy { it.id }

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> =
        flowOf(exercises.filter { it.name.get(language).contains(filter.query, ignoreCase = true) }.take(limit))

    override fun observeExercise(id: String): Flow<Exercise?> = flowOf(exercises.firstOrNull { it.id == id })

    override suspend fun getExercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    override suspend fun getVariant(id: String): ExerciseVariant? = variants[id]

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> = ids.mapNotNull { variants[it] }

    override suspend fun createCustom(input: NewExercise): Exercise = error("unused")

    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = flowOf(
        emptyList()
    )

    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = flowOf(
        emptyList()
    )
}

@Suppress("TooManyFunctions")
class FakeRoutineRepository(private val routines: List<Routine>) : RoutineRepository {
    override fun observeRoutines(): Flow<List<Routine>> = flowOf(routines)
    override fun observeRoutine(id: String): Flow<Routine?> = flowOf(routines.firstOrNull { it.id == id })
    override fun observeActive(): Flow<Routine?> = flowOf(routines.firstOrNull { it.isActive })
    override suspend fun getRoutine(id: String): Routine? = routines.firstOrNull { it.id == id }
    override suspend fun createRoutine(name: String, description: String): String = error("unused")
    override suspend fun updateRoutine(id: String, name: String, description: String) = error("unused")
    override suspend fun deleteRoutine(id: String) = error("unused")
    override suspend fun setActive(id: String?) = error("unused")
    override suspend fun setNextDayIndex(routineId: String, index: Int) = error("unused")
    override suspend fun setRestPolicy(
        routineId: String,
        policy: RestPolicy,
        appendRestDays: Int,
        restDayName: String
    ) =
        error("unused")
    override suspend fun addDay(routineId: String, name: String, kind: DayKind): String = error("unused")
    override suspend fun updateDay(dayId: String, name: String, kind: DayKind) = error("unused")
    override suspend fun deleteDay(dayId: String) = error("unused")
    override suspend fun moveDay(dayId: String, toPosition: Int) = error("unused")
    override suspend fun addSlot(dayId: String, spec: SlotSpec): String = error("unused")
    override suspend fun updateSlot(slotId: String, spec: SlotSpec) = error("unused")
    override suspend fun deleteSlot(slotId: String) = error("unused")
    override suspend fun moveSlot(slotId: String, toPosition: Int) = error("unused")
    override fun templates(): List<RoutineTemplate> = emptyList()
    override suspend fun copyTemplate(templateId: String, language: String): String = error("unused")
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<UserSettings> = state

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) {
        state.value = state.value.copy(unit = unit)
    }

    override fun setBarWeight(grams: Long) {
        state.value = state.value.copy(barWeightGrams = grams)
    }

    override fun setAvailablePlates(grams: List<Long>) {
        state.value = state.value.copy(availablePlatesGrams = grams)
    }

    override fun setKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(keepScreenOn = enabled)
    }

    override fun setRestSound(enabled: Boolean) {
        state.value = state.value.copy(restSound = enabled)
    }

    override fun setRestVibration(enabled: Boolean) {
        state.value = state.value.copy(restVibration = enabled)
    }
}

class FakeTimerStore(initial: TimerSnapshot = TimerSnapshot()) : TimerStore {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<TimerSnapshot> = mutable

    override fun save(snapshot: TimerSnapshot) {
        mutable.value = snapshot
    }
}

class FakeRestAlert : RestAlert {
    var scheduled: List<Long> = emptyList()
        private set
    var lastSound: Boolean? = null
        private set
    var cancels = 0
        private set

    override fun schedule(instants: List<Long>, label: String, sound: Boolean, vibrate: Boolean) {
        scheduled = instants
        lastSound = sound
    }

    override fun cancel() {
        scheduled = emptyList()
        cancels++
    }
}

class FakeIndicator : LiveSessionIndicator {
    var shown: String? = null
        private set
    var hidden = 0
        private set

    override fun show(sessionId: String) {
        shown = sessionId
    }

    override fun hide() {
        shown = null
        hidden++
    }
}

class FakeLoadStepStore : LoadStepStore {
    val steps = mutableMapOf<Pair<String, Boolean>, Long>()

    override fun step(variantId: String, plates: Boolean): Long? = steps[variantId to plates]

    override fun setStep(variantId: String, plates: Boolean, step: Long?) {
        if (step == null || step <= 0) steps.remove(variantId to plates) else steps[variantId to plates] = step
    }
}
