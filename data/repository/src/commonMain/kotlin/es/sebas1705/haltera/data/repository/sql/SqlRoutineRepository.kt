package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.RoutineEntity
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.ModelJson
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.repository.RoutineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // Implements the wide RoutineRepository contract.
internal class SqlRoutineRepository(
    private val db: AppDatabase,
    private val templates: List<RoutineTemplate>,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : RoutineRepository {

    private fun changes(): Flow<Unit> = tablesChanged(
        db.routineEntityQueries.selectById(""),
        db.routineDayEntityQueries.selectById(""),
        db.routineSlotEntityQueries.selectById(""),
    )

    override fun observeRoutines(): Flow<List<Routine>> =
        changes().conflate().map { load(db.routineEntityQueries.selectAll().executeAsList()) }.flowOn(dispatcher)

    override fun observeRoutine(id: String): Flow<Routine?> =
        changes().conflate().map { loadOne(id) }.flowOn(dispatcher)

    override fun observeActive(): Flow<Routine?> =
        changes().conflate()
            .map { load(listOfNotNull(db.routineEntityQueries.selectActive().executeAsOneOrNull())).firstOrNull() }
            .flowOn(dispatcher)

    override suspend fun getRoutine(id: String): Routine? = io { loadOne(id) }

    override suspend fun createRoutine(name: String, description: String): String = io {
        val id = newId()
        val at = now()
        db.routineEntityQueries.insert(id, name, description, null, null, at, at)
        id
    }

    override suspend fun updateRoutine(id: String, name: String, description: String) = write {
        db.routineEntityQueries.update(name = name, description = description, updatedAt = now(), id = id)
    }

    override suspend fun deleteRoutine(id: String) = write { db.routineEntityQueries.delete(id) }

    override suspend fun setActive(id: String?) = write {
        db.transaction {
            db.routineEntityQueries.clearActive()
            if (id != null) db.routineEntityQueries.markActive(id)
        }
    }

    override suspend fun setNextDayIndex(routineId: String, index: Int) = write {
        val count = db.routineDayEntityQueries.countByRoutine(routineId).executeAsOne().toInt()
        val clamped = if (count == 0) 0 else index.coerceIn(0, count - 1)
        db.routineEntityQueries.setNextDayIndex(nextDayIndex = clamped.toLong(), setAt = now(), id = routineId)
    }

    override suspend fun setRestPolicy(
        routineId: String,
        policy: RestPolicy,
        appendRestDays: Int,
        restDayName: String,
    ) = write {
        val target = policy.normalized()
        db.transaction {
            val row = db.routineEntityQueries.selectById(routineId).executeAsOneOrNull() ?: return@transaction
            val wasFlexible = RestMode.fromDb(row.restMode) == RestMode.FLEXIBLE
            when {
                target.isFlexible && !wasFlexible -> dropRestDays(routineId, row.nextDayIndex)
                !target.isFlexible && wasFlexible -> appendRestDays(routineId, appendRestDays, restDayName)
                else -> Unit
            }
            db.routineEntityQueries.setRestPolicy(
                restMode = target.mode.name,
                restDaysPerWeek = target.restDaysPerWeek.toLong(),
                restWeekdays = target.restWeekdays.toLong(),
                updatedAt = now(),
                id = routineId,
            )
        }
    }

    /**
     * Fixed → flexible: removes the REST days without slots, turns REST days with slots into training days and
     * remaps the sessions' day indexes and the override ([overrideIndex]) to the same days.
     */
    private fun dropRestDays(routineId: String, overrideIndex: Long) {
        val days = db.routineDayEntityQueries.selectByRoutine(routineId).executeAsList()
        val kept = days.filter { day ->
            day.kind != DayKind.REST.name || db.routineSlotEntityQueries.selectByDay(day.id).executeAsList().isNotEmpty()
        }
        if (kept.size == days.size && days.none { it.kind == DayKind.REST.name }) return
        val newIndex = days.map { day -> kept.indexOfFirst { it.id == day.id } }
        // Sessions of a removed (empty rest) day no longer point at a day; the others follow their day.
        newIndex.forEachIndexed { old, new ->
            if (new < 0) db.workoutSessionEntityQueries.clearDayIndex(routineId, old.toLong())
        }
        newIndex.forEachIndexed { old, new ->
            if (new >= 0 && new != old) {
                db.workoutSessionEntityQueries.markDayIndexRemap(new.toLong(), routineId, old.toLong())
            }
        }
        db.workoutSessionEntityQueries.applyDayIndexRemap(routineId)
        days.filter { day -> kept.none { it.id == day.id } }.forEach { db.routineDayEntityQueries.delete(it.id) }
        kept.forEachIndexed { index, day ->
            db.routineDayEntityQueries.setPosition(index.toLong(), day.id)
            if (day.kind == DayKind.REST.name) {
                db.routineDayEntityQueries.update(name = day.name, kind = DayKind.TRAINING.name, id = day.id)
            }
        }
        // The override follows its day; one on a removed rest day moves to the next kept day.
        val clampedOverride = if (days.isEmpty()) 0 else overrideIndex.toInt().coerceIn(0, days.lastIndex)
        val overrideDay = days.indices.map { (clampedOverride + it) % days.size }.firstOrNull { newIndex[it] >= 0 }
        val newOverride = overrideDay?.let { newIndex[it] } ?: 0
        db.routineEntityQueries.clampNextDayIndex(nextDayIndex = newOverride.toLong(), id = routineId)
    }

    /** Flexible → fixed: [count] REST days at the end; existing positions (and session indexes) do not move. */
    private fun appendRestDays(routineId: String, count: Int, name: String) {
        val start = db.routineDayEntityQueries.countByRoutine(routineId).executeAsOne()
        repeat(count.coerceIn(0, RestPolicy.MAX_REST_DAYS)) { offset ->
            db.routineDayEntityQueries.insert(newId(), routineId, start + offset, name, DayKind.REST.name)
        }
    }

    override suspend fun addDay(routineId: String, name: String, kind: DayKind): String = io {
        val id = newId()
        db.transaction {
            val position = db.routineDayEntityQueries.countByRoutine(routineId).executeAsOne()
            db.routineDayEntityQueries.insert(id, routineId, position, name, kind.name)
            touch(routineId)
        }
        id
    }

    override suspend fun updateDay(dayId: String, name: String, kind: DayKind) = write {
        db.transaction {
            db.routineDayEntityQueries.update(name = name, kind = kind.name, id = dayId)
            routineOfDay(dayId)?.let(::touch)
        }
    }

    override suspend fun deleteDay(dayId: String) = write {
        db.transaction {
            val routineId = routineOfDay(dayId) ?: return@transaction
            db.routineDayEntityQueries.delete(dayId)
            val days = db.routineDayEntityQueries.selectByRoutine(routineId).executeAsList()
            days.forEachIndexed { index, day -> db.routineDayEntityQueries.setPosition(index.toLong(), day.id) }
            val routine = db.routineEntityQueries.selectById(routineId).executeAsOne()
            val clamped = if (days.isEmpty()) 0L else routine.nextDayIndex.coerceIn(0L, days.lastIndex.toLong())
            db.routineEntityQueries.clampNextDayIndex(nextDayIndex = clamped, id = routineId)
            touch(routineId)
        }
    }

    override suspend fun moveDay(dayId: String, toPosition: Int) = write {
        db.transaction {
            val routineId = routineOfDay(dayId) ?: return@transaction
            val ids = db.routineDayEntityQueries.selectByRoutine(routineId).executeAsList().map { it.id }
            reorder(ids, dayId, toPosition).forEachIndexed { index, id ->
                db.routineDayEntityQueries.setPosition(index.toLong(), id)
            }
            touch(routineId)
        }
    }

    override suspend fun duplicateDay(dayId: String, language: String): String = io {
        val day = requireNotNull(db.routineDayEntityQueries.selectById(dayId).executeAsOneOrNull()) {
            "Unknown routine day $dayId"
        }
        val copyId = newId()
        db.transaction {
            // Make room right after the original.
            db.routineDayEntityQueries.selectByRoutine(day.routineId).executeAsList()
                .filter { it.position > day.position }
                .forEach { db.routineDayEntityQueries.setPosition(it.position + 1, it.id) }
            db.routineDayEntityQueries.insert(
                copyId,
                day.routineId,
                day.position + 1,
                RoutineRepository.copyName(day.name, language),
                day.kind,
            )
            // Copied column by column, so the JSON of target, prescription and progression is kept verbatim.
            db.routineSlotEntityQueries.selectByDay(dayId).executeAsList().forEach { slot ->
                db.routineSlotEntityQueries.insert(
                    id = newId(),
                    dayId = copyId,
                    position = slot.position,
                    target = slot.target,
                    sets = slot.sets,
                    warmupSets = slot.warmupSets,
                    prescription = slot.prescription,
                    restBetweenSetsS = slot.restBetweenSetsS,
                    restAfterS = slot.restAfterS,
                    supersetGroup = slot.supersetGroup,
                    bilateral = slot.bilateral,
                    notes = slot.notes,
                    progression = slot.progression,
                )
            }
            // A manual next-day override after the original keeps pointing at the same day.
            val routine = db.routineEntityQueries.selectById(day.routineId).executeAsOne()
            if (routine.nextDayIndex > day.position) {
                db.routineEntityQueries.clampNextDayIndex(nextDayIndex = routine.nextDayIndex + 1, id = day.routineId)
            }
            touch(day.routineId)
        }
        copyId
    }

    override suspend fun addSlot(dayId: String, spec: SlotSpec): String = io {
        val id = newId()
        db.transaction {
            val position = db.routineSlotEntityQueries.selectByDay(dayId).executeAsList().size.toLong()
            insertSlot(id, dayId, position, spec)
            routineOfDay(dayId)?.let(::touch)
        }
        id
    }

    override suspend fun updateSlot(slotId: String, spec: SlotSpec) = write {
        db.transaction {
            db.routineSlotEntityQueries.update(
                target = ModelJson.encodeTarget(spec.target),
                sets = spec.sets.toLong(),
                warmupSets = spec.warmupSets.toLong(),
                prescription = ModelJson.encodePrescription(spec.prescription),
                restBetweenSetsS = spec.restBetweenSetsS.toLong(),
                restAfterS = spec.restAfterS.toLong(),
                supersetGroup = spec.supersetGroup?.toLong(),
                bilateral = spec.bilateral.toDb(),
                notes = spec.notes,
                progression = ModelJson.encodeProgression(spec.progression),
                id = slotId,
            )
            routineOfSlot(slotId)?.let(::touch)
        }
    }

    override suspend fun deleteSlot(slotId: String) = write {
        db.transaction {
            val slot = db.routineSlotEntityQueries.selectById(slotId).executeAsOneOrNull() ?: return@transaction
            db.routineSlotEntityQueries.delete(slotId)
            db.routineSlotEntityQueries.selectByDay(slot.dayId).executeAsList().forEachIndexed { index, s ->
                db.routineSlotEntityQueries.setPosition(index.toLong(), s.id)
            }
            routineOfDay(slot.dayId)?.let(::touch)
        }
    }

    override suspend fun moveSlot(slotId: String, toPosition: Int) = write {
        db.transaction {
            val slot = db.routineSlotEntityQueries.selectById(slotId).executeAsOneOrNull() ?: return@transaction
            val ids = db.routineSlotEntityQueries.selectByDay(slot.dayId).executeAsList().map { it.id }
            reorder(ids, slotId, toPosition).forEachIndexed { index, id ->
                db.routineSlotEntityQueries.setPosition(index.toLong(), id)
            }
            routineOfDay(slot.dayId)?.let(::touch)
        }
    }

    override fun templates(): List<RoutineTemplate> = templates

    override suspend fun copyTemplate(templateId: String, language: String): String = io {
        val template =
            requireNotNull(templates.firstOrNull { it.id == templateId }) { "Unknown routine template $templateId" }
        val routineId = newId()
        val at = now()
        db.transaction {
            db.routineEntityQueries.insert(
                routineId,
                template.name.get(language),
                template.description.get(language),
                template.id,
                template.version.toLong(),
                at,
                at,
            )
            template.days.forEachIndexed { dayIndex, day ->
                val dayId = newId()
                db.routineDayEntityQueries.insert(
                    dayId,
                    routineId,
                    dayIndex.toLong(),
                    day.name.get(language),
                    day.kind.name
                )
                day.slots.forEachIndexed { slotIndex, spec -> insertSlot(newId(), dayId, slotIndex.toLong(), spec) }
            }
        }
        routineId
    }

    private fun insertSlot(id: String, dayId: String, position: Long, spec: SlotSpec) {
        db.routineSlotEntityQueries.insert(
            id = id,
            dayId = dayId,
            position = position,
            target = ModelJson.encodeTarget(spec.target),
            sets = spec.sets.toLong(),
            warmupSets = spec.warmupSets.toLong(),
            prescription = ModelJson.encodePrescription(spec.prescription),
            restBetweenSetsS = spec.restBetweenSetsS.toLong(),
            restAfterS = spec.restAfterS.toLong(),
            supersetGroup = spec.supersetGroup?.toLong(),
            bilateral = spec.bilateral.toDb(),
            notes = spec.notes,
            progression = ModelJson.encodeProgression(spec.progression),
        )
    }

    private fun touch(routineId: String) = db.routineEntityQueries.touch(updatedAt = now(), id = routineId)

    private fun routineOfDay(dayId: String): String? =
        db.routineDayEntityQueries.selectById(dayId).executeAsOneOrNull()?.routineId

    private fun routineOfSlot(slotId: String): String? =
        db.routineSlotEntityQueries.selectById(slotId).executeAsOneOrNull()?.let { routineOfDay(it.dayId) }

    private fun loadOne(id: String): Routine? =
        load(listOfNotNull(db.routineEntityQueries.selectById(id).executeAsOneOrNull())).firstOrNull()

    private fun load(rows: List<RoutineEntity>): List<Routine> {
        if (rows.isEmpty()) return emptyList()
        val days = db.routineDayEntityQueries.selectByRoutines(rows.map { it.id }).executeAsList()
        val slots = if (days.isEmpty()) {
            emptyMap()
        } else {
            db.routineSlotEntityQueries.selectByDays(days.map { it.id }).executeAsList()
                .mapNotNull { it.toModel() }
                .groupBy { it.dayId }
        }
        val daysByRoutine = days.groupBy { it.routineId }
        return rows.map { row ->
            row.toModel(
                daysByRoutine[row.id].orEmpty()
                    .sortedBy { it.position }
                    .map { day -> day.toModel(slots[day.id].orEmpty().sortedBy { it.position }) },
            )
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(dispatcher) { block() }

    private suspend fun write(block: () -> Unit) = withContext(dispatcher) { block() }
}

/** [ids] with [id] moved to [toPosition] (clamped). */
internal fun reorder(ids: List<String>, id: String, toPosition: Int): List<String> {
    if (id !in ids) return ids
    val rest = ids - id
    return rest.toMutableList().apply { add(toPosition.coerceIn(0, rest.size), id) }
}
