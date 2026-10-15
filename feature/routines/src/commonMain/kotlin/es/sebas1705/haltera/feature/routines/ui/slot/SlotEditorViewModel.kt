package es.sebas1705.haltera.feature.routines.ui.slot

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.LoadIncrement
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.feature.routines.model.AppLanguage
import es.sebas1705.haltera.feature.routines.model.PrescriptionKind
import es.sebas1705.haltera.feature.routines.model.PrescriptionRules
import es.sebas1705.haltera.feature.routines.model.ProgressionKind
import es.sebas1705.haltera.feature.routines.model.SlotLimits
import es.sebas1705.haltera.feature.routines.model.attempt
import es.sebas1705.haltera.feature.routines.model.incrementOrNull
import es.sebas1705.haltera.feature.routines.model.loadTypeOf
import es.sebas1705.haltera.feature.routines.model.progressionOf
import es.sebas1705.haltera.feature.routines.model.withIncrement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

internal data class SlotEditorState(
    val loading: Boolean = true,
    /** The slot or its exercise could not be found. */
    val missing: Boolean = false,
    val language: String = "en",
    val unit: WeightUnit = WeightUnit.KG,
    val dayName: String = "",
    val exercise: Exercise? = null,
    val spec: SlotSpec? = null,
    val confirmDelete: Boolean = false,
    /** One-shot: the slot was deleted, close the editor. */
    val deleted: Boolean = false,
    val error: Boolean = false,
) : MVIBaseState {
    val loadType: LoadType get() = spec?.let { loadTypeOf(it.target, exercise) } ?: LoadType.NONE

    /** Prescription kinds that fit the exercise's tracking type. */
    val allowedKinds: List<PrescriptionKind>
        get() = exercise?.let { PrescriptionRules.allowedKinds(it.trackingType) }.orEmpty()

    /** The saved prescription does not fit the exercise (e.g. data from an older version). */
    val prescriptionMismatch: Boolean
        get() = spec != null && exercise != null && !PrescriptionRules.isCompatible(spec.prescription, exercise.trackingType)

    /** Dumbbell-style load: one implement per hand, so the bilateral toggle applies. */
    val bilateralApplies: Boolean
        get() = exercise?.variants.orEmpty().any { it.loadType == LoadType.DUMBBELL && isAllowed(it.id) }

    /** Whether the load-based progression rules make sense for this exercise. */
    val progressionApplies: Boolean get() = exercise?.trackingType?.tracksLoad == true

    fun isAllowed(variantId: String): Boolean = when (val target = spec?.target) {
        is SlotTarget.Fixed -> target.variantId == variantId
        is SlotTarget.Flexible -> target.allowedVariantIds.isEmpty() || variantId in target.allowedVariantIds
        null -> false
    }
}

internal sealed interface SlotEditorIntent : MVIBaseIntent {
    data class SetFixedVariant(val variantId: String) : SlotEditorIntent

    /** Flexible target: any variant, chosen during the session. */
    data object SetFlexible : SlotEditorIntent

    /** Flexible target that allows every variant again. */
    data object SetAnyVariant : SlotEditorIntent

    /** Adds or removes a variant from a flexible target's allowed set (empty set = any). */
    data class ToggleAllowedVariant(val variantId: String) : SlotEditorIntent
    data class SetSets(val sets: Int) : SlotEditorIntent
    data class SetWarmupSets(val sets: Int) : SlotEditorIntent
    data class SetPrescriptionKind(val kind: PrescriptionKind) : SlotEditorIntent
    data class SetRepMin(val reps: Int) : SlotEditorIntent
    data class SetRepMax(val reps: Int) : SlotEditorIntent
    data class SetReps(val reps: Int) : SlotEditorIntent
    data class SetDuration(val seconds: Int) : SlotEditorIntent
    data class SetDistance(val meters: Int) : SlotEditorIntent
    data class SetRestBetweenSets(val seconds: Int) : SlotEditorIntent
    data class SetRestAfter(val seconds: Int) : SlotEditorIntent

    /** 0 means no superset. */
    data class SetSupersetGroup(val group: Int) : SlotEditorIntent
    data class SetBilateral(val bilateral: Boolean) : SlotEditorIntent
    data class SetNotes(val notes: String) : SlotEditorIntent
    data class SetProgressionKind(val kind: ProgressionKind) : SlotEditorIntent
    data class SetSessionsAtTop(val sessions: Int) : SlotEditorIntent
    data class SetEveryNSessions(val sessions: Int) : SlotEditorIntent
    data class SetIncrement(val increment: LoadIncrement) : SlotEditorIntent
    data object RequestDelete : SlotEditorIntent
    data object DismissDelete : SlotEditorIntent
    data object ConfirmDelete : SlotEditorIntent
    data object ErrorShown : SlotEditorIntent
}

/**
 * Slot editor: target (fixed or flexible), sets, prescription, rests, superset, bilateral, notes and
 * progression. Each change is clamped to [SlotLimits] and saved straight away.
 */
internal class SlotEditorViewModel(
    private val routineId: String,
    private val slotId: String,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    settings: SettingsRepository,
    language: AppLanguage,
) : MVIBaseViewModel<SlotEditorState, SlotEditorIntent>(
    SlotEditorState(language = language.code(), unit = settings.settings.value.unit),
) {
    /** Latest spec to save; conflated so quick taps write only the newest spec, in order. */
    private val saves = MutableStateFlow<SlotSpec?>(null)

    init {
        execute { load() }
        execute { saves.filterNotNull().collect { spec -> safely { routines.updateSlot(slotId, spec) } } }
    }

    private suspend fun load() {
        val loaded = attempt {
            val routine = routines.getRoutine(routineId)
            val day = routine?.days?.firstOrNull { d -> d.slots.any { it.id == slotId } }
            val slot = day?.slots?.firstOrNull { it.id == slotId }
            val exerciseId = when (val target = slot?.target) {
                is SlotTarget.Fixed -> exercises.getVariant(target.variantId)?.exerciseId
                is SlotTarget.Flexible -> target.exerciseId
                null -> null
            }
            Triple(day?.name.orEmpty(), slot?.toSpec(), exerciseId?.let { exercises.getExercise(it) })
        }
        loaded.onSuccess { (dayName, spec, exercise) ->
            updateState {
                it.copy(
                    loading = false,
                    missing = spec == null || exercise == null,
                    dayName = dayName,
                    spec = spec,
                    exercise = exercise
                )
            }
        }.onFailure {
            updateState { it.copy(loading = false, missing = true, error = true) }
        }
    }

    override fun intentHandler(intent: SlotEditorIntent): Job = execute {
        when (intent) {
            SlotEditorIntent.RequestDelete -> updateState { it.copy(confirmDelete = true) }
            SlotEditorIntent.DismissDelete -> updateState { it.copy(confirmDelete = false) }
            SlotEditorIntent.ConfirmDelete -> {
                updateState { it.copy(confirmDelete = false) }
                if (safely { routines.deleteSlot(slotId) } != null) updateState { it.copy(deleted = true) }
            }
            SlotEditorIntent.ErrorShown -> updateState { it.copy(error = false) }
            else -> edit(intent)
        }
    }

    /** Applies [intent] to the spec (pure), shows it and queues the save. */
    private fun edit(intent: SlotEditorIntent) {
        val state = currentState
        val spec = state.spec ?: return
        val updated = reduce(spec, intent, state.exercise)
        if (updated == spec) return
        updateState { it.copy(spec = updated) }
        saves.value = updated
    }

    private suspend inline fun <T> safely(block: () -> T): T? =
        attempt(block).onFailure { updateState { it.copy(error = true) } }.getOrNull()
}

/** The spec after [intent], every number clamped to [SlotLimits]. */
@Suppress("CyclomaticComplexMethod") // One flat branch per editable field.
internal fun reduce(spec: SlotSpec, intent: SlotEditorIntent, exercise: Exercise?): SlotSpec = when (intent) {
    is SlotEditorIntent.SetFixedVariant -> retarget(spec, SlotTarget.Fixed(intent.variantId), exercise)
    SlotEditorIntent.SetFlexible -> retarget(spec, flexibleOf(spec.target, exercise), exercise)
    SlotEditorIntent.SetAnyVariant -> (flexibleOf(spec.target, exercise) as? SlotTarget.Flexible)
        ?.let { retarget(spec, it.copy(allowedVariantIds = emptyList()), exercise) } ?: spec
    is SlotEditorIntent.ToggleAllowedVariant ->
        retarget(spec, toggleAllowed(spec.target, intent.variantId, exercise), exercise)
    is SlotEditorIntent.SetSets -> spec.copy(sets = intent.sets.coerceIn(SlotLimits.SETS))
    is SlotEditorIntent.SetWarmupSets -> spec.copy(warmupSets = intent.sets.coerceIn(SlotLimits.WARMUP_SETS))
    is SlotEditorIntent.SetPrescriptionKind ->
        spec.copy(prescription = PrescriptionRules.convert(spec.prescription, intent.kind))
    is SlotEditorIntent.SetRepMin -> (spec.prescription as? Prescription.RepRange)
        ?.let { spec.copy(prescription = PrescriptionRules.withMin(it, intent.reps)) } ?: spec
    is SlotEditorIntent.SetRepMax -> (spec.prescription as? Prescription.RepRange)
        ?.let { spec.copy(prescription = PrescriptionRules.withMax(it, intent.reps)) } ?: spec
    is SlotEditorIntent.SetReps -> spec.copy(prescription = Prescription.Reps(intent.reps.coerceIn(SlotLimits.REPS)))
    is SlotEditorIntent.SetDuration ->
        spec.copy(prescription = Prescription.Duration(intent.seconds.coerceIn(SlotLimits.DURATION_S)))
    is SlotEditorIntent.SetDistance ->
        spec.copy(prescription = Prescription.Distance(intent.meters.coerceIn(SlotLimits.DISTANCE_M)))
    is SlotEditorIntent.SetRestBetweenSets -> spec.copy(restBetweenSetsS = intent.seconds.coerceIn(SlotLimits.REST_S))
    is SlotEditorIntent.SetRestAfter -> spec.copy(restAfterS = intent.seconds.coerceIn(SlotLimits.REST_S))
    is SlotEditorIntent.SetSupersetGroup ->
        spec.copy(supersetGroup = intent.group.coerceIn(SlotLimits.SUPERSET_GROUP).takeIf { it > 0 })
    is SlotEditorIntent.SetBilateral -> spec.copy(bilateral = intent.bilateral)
    is SlotEditorIntent.SetNotes -> spec.copy(notes = intent.notes)
    is SlotEditorIntent.SetProgressionKind ->
        spec.copy(progression = progressionOf(intent.kind, spec.progression, loadTypeOf(spec.target, exercise)))
    is SlotEditorIntent.SetSessionsAtTop -> (spec.progression as? ProgressionRule.DoubleProgression)
        ?.let { spec.copy(progression = it.copy(sessionsAtTop = intent.sessions.coerceIn(SlotLimits.SESSIONS))) } ?: spec
    is SlotEditorIntent.SetEveryNSessions -> (spec.progression as? ProgressionRule.Linear)
        ?.let { spec.copy(progression = it.copy(everyNSessions = intent.sessions.coerceIn(SlotLimits.SESSIONS))) } ?: spec
    is SlotEditorIntent.SetIncrement -> spec.copy(progression = spec.progression.withIncrement(clamp(intent.increment)))
    else -> spec
}

private fun clamp(increment: LoadIncrement): LoadIncrement = when (increment) {
    is LoadIncrement.Weight -> LoadIncrement.Weight(increment.grams.coerceIn(0L, SlotLimits.MAX_INCREMENT_GRAMS))
    is LoadIncrement.Steps -> LoadIncrement.Steps(increment.steps.coerceIn(SlotLimits.INCREMENT_STEPS))
}

/** [target] made flexible: any variant of its exercise. */
private fun flexibleOf(target: SlotTarget, exercise: Exercise?): SlotTarget = when (target) {
    is SlotTarget.Flexible -> target
    is SlotTarget.Fixed -> exercise?.let { SlotTarget.Flexible(it.id) } ?: target
}

/**
 * [target] with [variantId] allowed or not. "Any variant" (empty list) unticking one means every other
 * active variant; ticking them all goes back to "any".
 */
private fun toggleAllowed(target: SlotTarget, variantId: String, exercise: Exercise?): SlotTarget {
    if (target !is SlotTarget.Flexible) return target
    val all = exercise?.variants.orEmpty().filterNot { it.deprecated }.map { it.id }
    val current = target.allowedVariantIds.ifEmpty { all }
    val toggled = if (variantId in current) current - variantId else current + variantId
    val allowed = when {
        toggled.isEmpty() -> current // Keep at least one variant.
        all.isNotEmpty() && toggled.toSet() == all.toSet() -> emptyList()
        else -> all.filter { it in toggled } + toggled.filterNot { it in all }
    }
    return target.copy(allowedVariantIds = allowed)
}

/**
 * [spec] aimed at [target]. When the load type changes, an increment still at the old default moves to the
 * new default and dumbbells start counting both hands.
 */
private fun retarget(spec: SlotSpec, target: SlotTarget, exercise: Exercise?): SlotSpec {
    val before = loadTypeOf(spec.target, exercise)
    val after = loadTypeOf(target, exercise)
    if (before == after) return spec.copy(target = target)
    val increment = spec.progression.incrementOrNull()
    val progression = if (increment == LoadIncrement.defaultFor(before)) {
        spec.progression.withIncrement(LoadIncrement.defaultFor(after))
    } else {
        spec.progression
    }
    return spec.copy(
        target = target,
        progression = progression,
        bilateral = spec.bilateral || after == LoadType.DUMBBELL,
    )
}
