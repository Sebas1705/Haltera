package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.WorkoutSessionEntity
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // One method per session operation of the contract.
internal class SqlSessionRepository(
    private val db: AppDatabase,
    private val history: HistoryLoader,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : SessionRepository {

    override fun observeInProgress(): Flow<WorkoutSession?> =
        history.changes().conflate().map { loadInProgress() }.flowOn(dispatcher)

    override suspend fun getInProgress(): WorkoutSession? = io { loadInProgress() }

    override fun observeSession(id: String): Flow<WorkoutSession?> =
        history.changes().conflate().map { loadSession(id) }.flowOn(dispatcher)

    override suspend fun getSession(id: String): WorkoutSession? = io { loadSession(id) }

    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> =
        history.changes().conflate()
            .map {
                history.session(
                    db.workoutSessionEntityQueries.selectLatestForRoutine(routineId).executeAsOneOrNull()
                )
            }
            .flowOn(dispatcher)

    override suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long?): String = io {
        db.transactionWithResult {
            if (startedAt == null) liveInProgressId()?.let { return@transactionWithResult it }
            val day = requireNotNull(
                db.routineDayEntityQueries.selectByRoutine(routineId).executeAsList().getOrNull(dayIndex),
            ) { "Routine $routineId has no day $dayIndex" }
            val sessionId = newId()
            val at = startedAt ?: now()
            db.workoutSessionEntityQueries.insert(sessionId, at, routineId, dayIndex.toLong(), day.name)
            db.routineSlotEntityQueries.selectByDay(day.id).executeAsList()
                .mapNotNull { it.toModel() }
                .mapNotNull { slot -> variantFor(slot.id, slot.target, before = at)?.let { slot.id to it } }
                .forEachIndexed { position, (slotId, variantId) ->
                    db.sessionExerciseEntityQueries.insert(newId(), sessionId, position.toLong(), variantId, slotId)
                }
            sessionId
        }
    }

    override suspend fun startFree(startedAt: Long?): String = io {
        db.transactionWithResult {
            val existing = if (startedAt == null) liveInProgressId() else null
            existing ?: newId().also { id ->
                db.workoutSessionEntityQueries.insert(id, startedAt ?: now(), null, null, null)
            }
        }
    }

    /** The session a live start resumes instead of creating a new one. */
    private fun liveInProgressId(): String? = db.workoutSessionEntityQueries.selectInProgress().executeAsOneOrNull()?.id

    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String = io {
        val id = newId()
        db.transaction {
            val position = db.sessionExerciseEntityQueries.selectBySession(sessionId).executeAsList().size.toLong()
            db.sessionExerciseEntityQueries.insert(id, sessionId, position, variantId, slotId)
        }
        id
    }

    override suspend fun removeExercise(sessionExerciseId: String) = write {
        db.transaction {
            val exercise = db.sessionExerciseEntityQueries.selectById(sessionExerciseId).executeAsOneOrNull()
                ?: return@transaction
            db.sessionExerciseEntityQueries.delete(sessionExerciseId)
            db.sessionExerciseEntityQueries.selectBySession(
                exercise.sessionId
            ).executeAsList().forEachIndexed { index, e ->
                db.sessionExerciseEntityQueries.setPosition(index.toLong(), e.id)
            }
        }
    }

    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) = write {
        db.sessionExerciseEntityQueries.setVariant(variantId = variantId, id = sessionExerciseId)
    }

    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) = write {
        db.transaction {
            val exercise = db.sessionExerciseEntityQueries.selectById(sessionExerciseId).executeAsOneOrNull()
                ?: return@transaction
            val ids = db.sessionExerciseEntityQueries.selectBySession(exercise.sessionId).executeAsList().map { it.id }
            reorder(ids, sessionExerciseId, toPosition).forEachIndexed { index, id ->
                db.sessionExerciseEntityQueries.setPosition(index.toLong(), id)
            }
        }
    }

    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) = write {
        db.sessionExerciseEntityQueries.updateNotes(notes = notes, id = sessionExerciseId)
    }

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = io {
        val id = newId()
        db.transaction {
            val position = db.setLogEntityQueries.selectBySessionExercise(
                sessionExerciseId
            ).executeAsList().size.toLong()
            db.setLogEntityQueries.insert(
                id = id,
                sessionExerciseId = sessionExerciseId,
                position = position,
                kind = input.kind.name,
                reps = input.reps?.toLong(),
                loadGrams = input.loadGrams,
                loadDetail = input.loadDetail,
                durationS = input.durationS?.toLong(),
                distanceM = input.distanceM?.toLong(),
                rpe = input.rpe,
                completed = input.completed.toDb(),
                completedAt = if (input.completed) now() else null,
            )
        }
        id
    }

    override suspend fun updateSet(setId: String, input: SetInput) = write {
        db.transaction {
            val existing = db.setLogEntityQueries.selectById(setId).executeAsOneOrNull() ?: return@transaction
            db.setLogEntityQueries.update(
                kind = input.kind.name,
                reps = input.reps?.toLong(),
                loadGrams = input.loadGrams,
                loadDetail = input.loadDetail,
                durationS = input.durationS?.toLong(),
                distanceM = input.distanceM?.toLong(),
                rpe = input.rpe,
                completed = input.completed.toDb(),
                completedAt = if (input.completed) existing.completedAt ?: now() else null,
                id = setId,
            )
        }
    }

    override suspend fun deleteSet(setId: String) = write {
        db.transaction {
            val set = db.setLogEntityQueries.selectById(setId).executeAsOneOrNull() ?: return@transaction
            db.setLogEntityQueries.delete(setId)
            db.setLogEntityQueries.selectBySessionExercise(
                set.sessionExerciseId
            ).executeAsList().forEachIndexed { index, s ->
                db.setLogEntityQueries.setPosition(index.toLong(), s.id)
            }
        }
    }

    override suspend fun updateSessionNotes(sessionId: String, notes: String) = write {
        db.workoutSessionEntityQueries.updateNotes(notes = notes, id = sessionId)
    }

    override suspend fun finish(sessionId: String, endedAt: Long?) = write {
        db.transaction {
            val session = db.workoutSessionEntityQueries.selectById(sessionId).executeAsOneOrNull() ?: return@transaction
            if (session.endedAt != null) return@transaction
            db.workoutSessionEntityQueries.finish(endedAt = endedAt ?: now(), id = sessionId)
        }
    }

    override suspend fun dropIncomplete(sessionId: String) = write {
        db.transaction {
            val exercises = db.sessionExerciseEntityQueries.selectBySession(sessionId).executeAsList()
            val sets = exercises.map { it.id }.chunked(MAX_IN_ARGS).flatMap { ids ->
                db.setLogEntityQueries.selectBySessionExercises(ids).executeAsList()
            }.groupBy { it.sessionExerciseId }
            exercises.forEach { exercise ->
                val (done, pending) = sets[exercise.id].orEmpty().partition { it.completed != 0L }
                if (done.isEmpty()) {
                    db.sessionExerciseEntityQueries.delete(exercise.id)
                } else {
                    pending.forEach { db.setLogEntityQueries.delete(it.id) }
                    done.sortedBy { it.position }.forEachIndexed { index, set ->
                        if (set.position != index.toLong()) db.setLogEntityQueries.setPosition(index.toLong(), set.id)
                    }
                }
            }
            db.sessionExerciseEntityQueries.selectBySession(sessionId).executeAsList().forEachIndexed { index, e ->
                if (e.position != index.toLong()) db.sessionExerciseEntityQueries.setPosition(index.toLong(), e.id)
            }
        }
    }

    override suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?) = write {
        db.workoutSessionEntityQueries.updateTimes(
            startedAt = startedAt,
            endedAt = endedAt?.coerceAtLeast(startedAt),
            id = sessionId,
        )
    }

    override suspend fun deleteSession(sessionId: String) = write { db.workoutSessionEntityQueries.delete(sessionId) }

    override fun observeHistory(limit: Int): Flow<List<WorkoutSession>> = history.changes().conflate()
        .map {
            history.sessions(
                db.workoutSessionEntityQueries.selectFinished(
                    limit.toLong()
                ) { id, startedAt, endedAt, routineId, dayIndex, dayName, notes, bw ->
                    WorkoutSessionEntity(id, startedAt, endedAt, routineId, dayIndex, dayName, notes, bw)
                }.executeAsList(),
            ).map { it.withoutEmptyExercises() }
        }
        .flowOn(dispatcher)

    override fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>> =
        history.changes().conflate()
            .map {
                history.sessions(
                    db.workoutSessionEntityQueries.selectStartedBetween(fromMillis, toMillis).executeAsList()
                ).map { it.withoutEmptyExercises() }
            }
            .flowOn(dispatcher)

    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = io { history.bySlotAndVariant(slotId, variantId, limit, before) }

    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> =
        io { history.byVariant(variantId, limit, before) }

    private fun loadInProgress(): WorkoutSession? =
        history.session(db.workoutSessionEntityQueries.selectInProgress().executeAsOneOrNull())

    private fun loadSession(id: String): WorkoutSession? =
        history.session(db.workoutSessionEntityQueries.selectById(id).executeAsOneOrNull())

    /**
     * The variant to use for a slot in a session starting at [before] (only earlier sessions count); null
     * if the exercise has no variants.
     */
    private fun variantFor(slotId: String, target: SlotTarget, before: Long): String? = when (target) {
        is SlotTarget.Fixed -> target.variantId
        is SlotTarget.Flexible -> {
            val variants = db.exerciseVariantEntityQueries.selectByExercise(target.exerciseId).executeAsList()
            val allowed = if (target.allowedVariantIds.isEmpty()) {
                variants
            } else {
                target.allowedVariantIds.mapNotNull { id -> variants.firstOrNull { it.id == id } }
            }
            val lastUsed = allowed.mapNotNull { variant ->
                db.sessionExerciseEntityQueries.historyBySlotAndVariant(
                    slotId,
                    variant.id,
                    before,
                    1,
                ) { _, _, _, _, _, _, _, at -> at }
                    .executeAsOneOrNull()?.let { at -> variant.id to at }
            }.maxByOrNull { it.second }
            // No history in this slot: the allowed variant used most recently anywhere (free sessions, other
            // routines, a routine copied afresh), so the user's real history drives the default.
            val usedAnywhere = lastUsed?.first ?: allowed.takeIf { it.isNotEmpty() }?.let { candidates ->
                db.sessionExerciseEntityQueries.lastUsedVariant(candidates.map { it.id }, before).executeAsOneOrNull()
            }
            usedAnywhere
                ?: allowed.firstOrNull { it.deprecated == 0L }?.id
                ?: target.allowedVariantIds.firstOrNull()
                ?: variants.firstOrNull()?.id
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(dispatcher) { block() }

    private suspend fun write(block: () -> Unit) = withContext(dispatcher) { block() }

    /**
     * A finished session without the exercises that have no completed set. Sessions saved before saving
     * pruned them ([dropIncomplete]) may still hold some; they must not count in stats or pickers.
     */
    private fun WorkoutSession.withoutEmptyExercises(): WorkoutSession = if (inProgress) {
        this
    } else {
        copy(exercises = exercises.filter { e -> e.sets.any { it.completed } })
    }

    private companion object {
        const val MAX_IN_ARGS = 500
    }
}
