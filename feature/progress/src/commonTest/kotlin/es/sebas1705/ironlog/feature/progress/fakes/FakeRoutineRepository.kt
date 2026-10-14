package es.sebas1705.ironlog.feature.progress.fakes

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Routines from a list; only reads are implemented. */
@Suppress("TooManyFunctions") // Implements the whole interface.
class FakeRoutineRepository(initial: List<Routine> = emptyList()) : RoutineRepository {
    val routines = MutableStateFlow(initial)

    override fun observeRoutines(): Flow<List<Routine>> = routines

    override fun observeRoutine(id: String): Flow<Routine?> = routines.map { list -> list.firstOrNull { it.id == id } }

    override fun observeActive(): Flow<Routine?> = routines.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getRoutine(id: String): Routine? = routines.value.firstOrNull { it.id == id }

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

    override fun templates(): List<RoutineTemplate> = emptyList()

    override suspend fun copyTemplate(templateId: String, language: String): String = unused()
}
