package es.sebas1705.haltera.feature.routines.ui.editor

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.domain.usecases.cycle.FlexibleRest
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineScore
import es.sebas1705.haltera.feature.routines.model.AppLanguage
import es.sebas1705.haltera.feature.routines.model.ExerciseIndex
import es.sebas1705.haltera.feature.routines.model.attempt
import es.sebas1705.haltera.feature.routines.model.indexFor
import es.sebas1705.haltera.feature.routines.model.movedIndex
import es.sebas1705.haltera.feature.routines.model.scoreRoutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull

/** Something waiting for the delete confirmation. */
internal sealed interface PendingDelete {
    data class Day(val day: RoutineDay) : PendingDelete
    data class Slot(val slot: RoutineSlot) : PendingDelete
}

/** A fixed ↔ flexible conversion waiting for the user (docs/designs/2026-10-16-flexible-rest-days.md). */
internal sealed interface PendingRestChange {
    /** [removedRestDays] empty rest days leave the cycle; the routine starts with [policy]. */
    data class ToFlexible(val removedRestDays: Int, val policy: RestPolicy) : PendingRestChange

    /** Back to fixed: offer to append [restDays] rest days. */
    data class ToFixed(val restDays: Int) : PendingRestChange
}

internal data class RoutineEditorState(
    val loading: Boolean = true,
    /** The routine no longer exists (deleted elsewhere). */
    val missing: Boolean = false,
    val language: String = "en",
    /** Drafts of the text fields; saved as they change. */
    val name: String = "",
    val description: String = "",
    val routine: Routine? = null,
    val index: ExerciseIndex = ExerciseIndex(),
    val pendingDelete: PendingDelete? = null,
    /** Advice-only score of the routine, refreshed as it changes; null until its exercises are loaded. */
    val score: RoutineScore? = null,
    /** One-shot: a day just created by duplication, to scroll to and highlight. */
    val highlightDayId: String? = null,
    val pendingRestChange: PendingRestChange? = null,
    /** "I know my rest days" of a flexible routine: weekday chips instead of the 0–6 stepper. */
    val weekdayMode: Boolean = false,
    val error: Boolean = false,
) : MVIBaseState {
    val days: List<RoutineDay> get() = routine?.days.orEmpty()
    val restPolicy: RestPolicy get() = routine?.restPolicy ?: RestPolicy.FIXED
}

internal sealed interface RoutineEditorIntent : MVIBaseIntent {
    data class SetName(val name: String) : RoutineEditorIntent
    data class SetDescription(val description: String) : RoutineEditorIntent
    data class SetActive(val active: Boolean) : RoutineEditorIntent
    data class AddDay(val name: String, val kind: DayKind) : RoutineEditorIntent
    data class RenameDay(val dayId: String, val name: String) : RoutineEditorIntent
    data class SetDayKind(val dayId: String, val kind: DayKind) : RoutineEditorIntent

    /** Copies a day with its slots right after it. */
    data class DuplicateDay(val dayId: String) : RoutineEditorIntent
    data object HighlightShown : RoutineEditorIntent

    /** Moves a day one place up (-1) or down (+1). */
    data class MoveDay(val dayId: String, val delta: Int) : RoutineEditorIntent

    /** Moves a slot one place up (-1) or down (+1) within its day. */
    data class MoveSlot(val slotId: String, val delta: Int) : RoutineEditorIntent
    data class RequestDeleteDay(val dayId: String) : RoutineEditorIntent
    data class RequestDeleteSlot(val slotId: String) : RoutineEditorIntent
    data object ConfirmDelete : RoutineEditorIntent
    data object DismissDelete : RoutineEditorIntent

    /** Fixed or flexible; converting asks first when it changes the days. */
    data class SetRestMode(val mode: RestMode) : RoutineEditorIntent
    data object ConfirmToFlexible : RoutineEditorIntent

    /** Back to fixed, appending the rest days ([addRestDays]) named [restDayName], or none. */
    data class ConfirmToFixed(val addRestDays: Boolean, val restDayName: String) : RoutineEditorIntent
    data object DismissRestChange : RoutineEditorIntent
    data class SetRestDaysPerWeek(val count: Int) : RoutineEditorIntent
    data class SetKnowsWeekdays(val known: Boolean) : RoutineEditorIntent

    /** Adds or removes ISO weekday [isoDay] (1 = Monday) from the rest weekdays; at most six. */
    data class ToggleRestWeekday(val isoDay: Int) : RoutineEditorIntent
    data object ErrorShown : RoutineEditorIntent
}

/**
 * Routine editor: name, description, the ordered days (the cycle) and their slots. Every change is written
 * through the repository straight away; there is no save button.
 */
@Suppress("TooManyFunctions") // One handler per intent.
internal class RoutineEditorViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    language: AppLanguage,
) : MVIBaseViewModel<RoutineEditorState, RoutineEditorIntent>(RoutineEditorState(language = language.code())) {

    /** Latest name and description to save; conflated so fast typing writes only the newest text, in order. */
    private val textSaves = MutableStateFlow<Pair<String, String>?>(null)

    init {
        execute {
            routines.observeRoutine(routineId)
                .catch { updateState { it.copy(loading = false, error = true) } }
                .collect { routine -> onRoutine(routine) }
        }
        execute {
            textSaves.filterNotNull().collect { (name, description) ->
                safely { routines.updateRoutine(routineId, name, description) }
            }
        }
    }

    private suspend fun onRoutine(routine: Routine?) {
        if (routine == null) {
            updateState { it.copy(loading = false, missing = true, routine = null) }
            return
        }
        val first = currentState.routine == null
        updateState {
            it.copy(
                loading = false,
                missing = false,
                routine = routine,
                name = if (first) routine.name else it.name,
                description = if (first) routine.description else it.description,
                weekdayMode = it.weekdayMode || routine.restPolicy.knowsWeekdays,
            )
        }
        val targets = routine.days.flatMap { day -> day.slots.map { it.target } }
        val known = currentState.index
        attempt { exercises.indexFor(targets, known) }.onSuccess { index ->
            val score = scoreRoutine(routine, index)
            updateState { it.copy(index = index, score = score) }
        }
    }

    override fun intentHandler(intent: RoutineEditorIntent): Job = execute {
        when (intent) {
            is RoutineEditorIntent.SetName -> {
                updateState { it.copy(name = intent.name) }
                textSaves.value = intent.name to currentState.description
            }
            is RoutineEditorIntent.SetDescription -> {
                updateState { it.copy(description = intent.description) }
                textSaves.value = currentState.name to intent.description
            }
            is RoutineEditorIntent.SetActive ->
                safely { routines.setActive(routineId.takeIf { intent.active }) }
            is RoutineEditorIntent.AddDay -> safely { routines.addDay(routineId, intent.name, intent.kind) }
            is RoutineEditorIntent.RenameDay -> day(intent.dayId)?.let { day ->
                val name = intent.name.trim()
                if (name.isNotEmpty() && name != day.name) safely { routines.updateDay(day.id, name, day.kind) }
            }
            is RoutineEditorIntent.SetDayKind -> day(intent.dayId)?.let { day ->
                if (day.kind != intent.kind) safely { routines.updateDay(day.id, day.name, intent.kind) }
            }
            is RoutineEditorIntent.DuplicateDay -> duplicateDay(intent.dayId)
            RoutineEditorIntent.HighlightShown -> updateState { it.copy(highlightDayId = null) }
            is RoutineEditorIntent.MoveDay -> moveDay(intent.dayId, intent.delta)
            is RoutineEditorIntent.MoveSlot -> moveSlot(intent.slotId, intent.delta)
            is RoutineEditorIntent.RequestDeleteDay ->
                day(intent.dayId)?.let { day -> updateState { it.copy(pendingDelete = PendingDelete.Day(day)) } }
            is RoutineEditorIntent.RequestDeleteSlot ->
                slot(intent.slotId)?.let { slot -> updateState { it.copy(pendingDelete = PendingDelete.Slot(slot)) } }
            RoutineEditorIntent.ConfirmDelete -> confirmDelete()
            RoutineEditorIntent.DismissDelete -> updateState { it.copy(pendingDelete = null) }
            RoutineEditorIntent.ErrorShown -> updateState { it.copy(error = false) }
            is RoutineEditorIntent.SetRestMode -> setRestMode(intent.mode)
            RoutineEditorIntent.ConfirmToFlexible -> confirmToFlexible()
            is RoutineEditorIntent.ConfirmToFixed -> confirmToFixed(intent.addRestDays, intent.restDayName)
            RoutineEditorIntent.DismissRestChange -> updateState { it.copy(pendingRestChange = null) }
            is RoutineEditorIntent.SetRestDaysPerWeek -> setRestDays(intent.count)
            is RoutineEditorIntent.SetKnowsWeekdays -> setKnowsWeekdays(intent.known)
            is RoutineEditorIntent.ToggleRestWeekday -> toggleWeekday(intent.isoDay)
        }
    }

    private suspend fun setRestMode(mode: RestMode) {
        val routine = currentState.routine ?: return
        val current = routine.restPolicy
        when {
            mode == RestMode.FLEXIBLE && !current.isFlexible -> {
                val policy = FlexibleRest.policyFromCycle(routine)
                val removed = routine.days.count { it.kind == DayKind.REST && it.slots.isEmpty() }
                if (removed > 0) {
                    updateState { it.copy(pendingRestChange = PendingRestChange.ToFlexible(removed, policy)) }
                } else {
                    safely { routines.setRestPolicy(routineId, policy) }
                }
            }
            mode == RestMode.FIXED && current.isFlexible -> {
                val restDays = current.restDaysCount
                if (restDays > 0) {
                    updateState { it.copy(pendingRestChange = PendingRestChange.ToFixed(restDays)) }
                } else {
                    safely { routines.setRestPolicy(routineId, RestPolicy.FIXED) }
                }
            }
            else -> Unit
        }
    }

    private suspend fun confirmToFlexible() {
        val pending = currentState.pendingRestChange as? PendingRestChange.ToFlexible ?: return
        updateState { it.copy(pendingRestChange = null, weekdayMode = false) }
        safely { routines.setRestPolicy(routineId, pending.policy) }
    }

    private suspend fun confirmToFixed(addRestDays: Boolean, restDayName: String) {
        val pending = currentState.pendingRestChange as? PendingRestChange.ToFixed ?: return
        updateState { it.copy(pendingRestChange = null, weekdayMode = false) }
        val count = if (addRestDays) pending.restDays else 0
        safely { routines.setRestPolicy(routineId, RestPolicy.FIXED, count, restDayName) }
    }

    private suspend fun setRestDays(count: Int) {
        if (!currentState.restPolicy.isFlexible) return
        val policy = RestPolicy.flexible(count)
        if (policy != currentState.restPolicy) safely { routines.setRestPolicy(routineId, policy) }
    }

    /** Off goes back to the number (keeping the weekday count); on shows the chips, saved once one is picked. */
    private suspend fun setKnowsWeekdays(known: Boolean) {
        val policy = currentState.restPolicy
        if (!policy.isFlexible) return
        updateState { it.copy(weekdayMode = known) }
        if (!known && policy.knowsWeekdays) {
            safely { routines.setRestPolicy(routineId, RestPolicy.flexible(policy.restDaysCount)) }
        }
    }

    private suspend fun toggleWeekday(isoDay: Int) {
        val policy = currentState.restPolicy
        if (!policy.isFlexible || isoDay !in 1..RestPolicy.DAYS_IN_WEEK) return
        val days = policy.weekdays.toMutableSet()
        if (!days.remove(isoDay)) {
            if (days.size >= RestPolicy.MAX_REST_DAYS) return
            days += isoDay
        }
        updateState { it.copy(weekdayMode = true) }
        safely { routines.setRestPolicy(routineId, RestPolicy.onWeekdays(days)) }
    }

    private suspend fun duplicateDay(dayId: String) {
        val day = day(dayId) ?: return
        val copyId = safely { routines.duplicateDay(day.id, currentState.language) } ?: return
        updateState { it.copy(highlightDayId = copyId) }
    }

    private suspend fun moveDay(dayId: String, delta: Int) {
        val days = currentState.days
        val from = days.indexOfFirst { it.id == dayId }.takeIf { it >= 0 } ?: return
        val to = movedIndex(from, delta, days.size) ?: return
        safely { routines.moveDay(dayId, to) }
    }

    private suspend fun moveSlot(slotId: String, delta: Int) {
        val slots = currentState.days.firstOrNull { day -> day.slots.any { it.id == slotId } }?.slots ?: return
        val from = slots.indexOfFirst { it.id == slotId }
        val to = movedIndex(from, delta, slots.size) ?: return
        safely { routines.moveSlot(slotId, to) }
    }

    private suspend fun confirmDelete() {
        val pending = currentState.pendingDelete ?: return
        updateState { it.copy(pendingDelete = null) }
        when (pending) {
            is PendingDelete.Day -> safely { routines.deleteDay(pending.day.id) }
            is PendingDelete.Slot -> safely { routines.deleteSlot(pending.slot.id) }
        }
    }

    private fun day(id: String): RoutineDay? = currentState.days.firstOrNull { it.id == id }

    private fun slot(id: String): RoutineSlot? =
        currentState.days.firstNotNullOfOrNull { day -> day.slots.firstOrNull { it.id == id } }

    private suspend inline fun <T> safely(block: () -> T): T? =
        attempt(block).onFailure { updateState { it.copy(error = true) } }.getOrNull()
}
