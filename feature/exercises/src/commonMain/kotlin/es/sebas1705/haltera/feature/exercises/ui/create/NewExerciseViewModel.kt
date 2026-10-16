package es.sebas1705.haltera.feature.exercises.ui.create

import androidx.compose.runtime.Immutable
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseEdit
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.VariantEdit
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.feature.exercises.model.defaultLoadType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** A variant in the editor. [id] null: new. [locked]: it has history, so equipment and load type are fixed. */
@Immutable
data class VariantDraft(
    val id: String?,
    val equipment: Equipment,
    val loadType: LoadType,
    val name: LocalizedText? = null,
    val locked: Boolean = false,
)

@Immutable
data class NewExerciseState(
    /** Null when creating; the exercise being edited otherwise. */
    val exerciseId: String? = null,
    /** Editing: true until the exercise is loaded. */
    val loading: Boolean = false,
    /** Editing: the exercise is missing or not a custom one. */
    val notFound: Boolean = false,
    /** App language; [name] is in it, [otherName] in the other UI language. */
    val language: String = "en",
    val name: String = "",
    /** Optional name in the other UI language; blank means the same name. */
    val otherName: String = "",
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val level: Level = Level.BEGINNER,
    /** Selected primary muscles, in selection order. */
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    /** One variant per selected equipment, in selection order. */
    val variants: List<VariantDraft> = emptyList(),
    /** Instructions, one step per line, in [language]. */
    val instructions: String = "",
    /** Editing: the exercise has history, so its tracking type is fixed. */
    val trackingLocked: Boolean = false,
    /** Validation messages show only after the first save attempt. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once saved; the screen then opens (or returns to) the exercise. */
    val savedExerciseId: String? = null,
    /** A variant with history was removed: it will be hidden, not deleted (its history stays). */
    val removesLoggedVariant: Boolean = false,
) : MVIBaseState {
    val editing: Boolean get() = exerciseId != null
    val equipment: List<Equipment> get() = variants.map { it.equipment }
    val nameError: Boolean get() = name.isBlank()
    val equipmentError: Boolean get() = variants.isEmpty()
    val canSave: Boolean get() = !nameError && !equipmentError && !saving && !loading && !notFound
}

sealed interface NewExerciseIntent : MVIBaseIntent {
    data class SetLanguage(val language: String) : NewExerciseIntent
    data class NameChanged(val name: String) : NewExerciseIntent
    data class OtherNameChanged(val name: String) : NewExerciseIntent
    data class CategorySelected(val category: ExerciseCategory) : NewExerciseIntent
    data class TrackingSelected(val trackingType: TrackingType) : NewExerciseIntent
    data class LevelSelected(val level: Level) : NewExerciseIntent
    data class MuscleToggled(val muscle: Muscle) : NewExerciseIntent
    data class SecondaryMuscleToggled(val muscle: Muscle) : NewExerciseIntent
    data class EquipmentToggled(val equipment: Equipment) : NewExerciseIntent
    data class LoadTypeSelected(val index: Int, val loadType: LoadType) : NewExerciseIntent
    data class InstructionsChanged(val text: String) : NewExerciseIntent
    data object Save : NewExerciseIntent
}

/**
 * Custom exercise editor (core design §3.1.1): creates one, or edits [exerciseId] at any time. One variant per
 * equipment. Editing keeps every id, so history and routines stay linked; a variant with history keeps its
 * equipment and load type, and removing it hides it instead of deleting it (the repository enforces both).
 */
class NewExerciseViewModel(
    private val exerciseId: String?,
    private val exercises: ExerciseRepository,
) : MVIBaseViewModel<NewExerciseState, NewExerciseIntent>(
    NewExerciseState(exerciseId = exerciseId, loading = exerciseId != null),
) {
    /** The exercise as loaded (editing), to keep what the form does not show. */
    private var original: Exercise? = null

    /** Variants of [original] removed in this edit, so toggling their equipment back restores the same id. */
    private var removed: List<VariantDraft> = emptyList()

    /** Variants that were already hidden (deprecated) when the editor opened. */
    private var hiddenBefore: Set<String?> = emptySet()
    private var instructionsEdited = false
    private var loaded = false

    constructor(exercises: ExerciseRepository) : this(null, exercises)

    override fun intentHandler(intent: NewExerciseIntent): Job = execute {
        when (intent) {
            is NewExerciseIntent.SetLanguage -> setLanguage(intent.language)
            is NewExerciseIntent.NameChanged -> updateState { it.copy(name = intent.name.take(MAX_NAME_LENGTH)) }
            is NewExerciseIntent.OtherNameChanged ->
                updateState { it.copy(otherName = intent.name.take(MAX_NAME_LENGTH)) }
            is NewExerciseIntent.CategorySelected -> updateState { it.copy(category = intent.category) }
            is NewExerciseIntent.TrackingSelected ->
                updateState { if (it.trackingLocked) it else it.copy(trackingType = intent.trackingType) }
            is NewExerciseIntent.LevelSelected -> updateState { it.copy(level = intent.level) }
            is NewExerciseIntent.MuscleToggled ->
                updateState { it.copy(primaryMuscles = it.primaryMuscles.toggle(intent.muscle)) }
            is NewExerciseIntent.SecondaryMuscleToggled ->
                updateState { it.copy(secondaryMuscles = it.secondaryMuscles.toggle(intent.muscle)) }
            is NewExerciseIntent.EquipmentToggled -> toggleEquipment(intent.equipment)
            is NewExerciseIntent.LoadTypeSelected -> updateState { s ->
                s.copy(
                    variants = s.variants.mapIndexed { i, v ->
                        if (i == intent.index && !v.locked) v.copy(loadType = intent.loadType) else v
                    },
                )
            }
            is NewExerciseIntent.InstructionsChanged -> {
                instructionsEdited = true
                updateState { it.copy(instructions = intent.text.take(MAX_INSTRUCTIONS_LENGTH)) }
            }
            NewExerciseIntent.Save -> save()
        }
    }

    private suspend fun setLanguage(language: String) {
        if (language == currentState.language && (loaded || exerciseId == null)) return
        updateState { it.copy(language = language) }
        if (exerciseId != null && !loaded) load(exerciseId, language)
    }

    private suspend fun load(id: String, language: String) {
        loaded = true
        val result = runCatching {
            val exercise = exercises.getExercise(id)?.takeIf { it.source == ExerciseSource.USER }
            exercise?.let { it to exercises.usage(id) }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        if (result == null) {
            updateState { it.copy(loading = false, notFound = true) }
            return
        }
        val (exercise, usage) = result
        original = exercise
        val name = exercise.name.get(language)
        val other = exercise.name.get(otherLanguage(language))
        updateState {
            it.copy(
                loading = false,
                name = name,
                otherName = if (other == name) "" else other,
                category = exercise.category,
                trackingType = exercise.trackingType,
                level = exercise.level,
                primaryMuscles = exercise.primaryMuscles,
                secondaryMuscles = exercise.secondaryMuscles,
                variants = exercise.variants.filterNot { v -> v.deprecated }.map { v ->
                    VariantDraft(v.id, v.equipment, v.loadType, v.name, locked = v.id in usage.variantsWithHistory)
                },
                instructions = exercise.instructions.get(language).joinToString("\n"),
                trackingLocked = usage.hasHistory,
            )
        }
        hiddenBefore = exercise.variants.filter { it.deprecated }.map { it.id }.toSet()
        removed = exercise.variants.filter { it.deprecated }.map { v ->
            VariantDraft(v.id, v.equipment, v.loadType, v.name, locked = v.id in usage.variantsWithHistory)
        }
    }

    /** Whether this edit removed a variant with history (not one that was already hidden). */
    private fun removesLogged() = removed.any { it.locked && it.id !in hiddenBefore }

    private suspend fun toggleEquipment(equipment: Equipment) {
        val current = currentState.variants
        if (current.any { it.equipment == equipment }) {
            val (gone, kept) = current.partition { it.equipment == equipment }
            removed = removed + gone.filter { it.id != null }
            updateState { it.copy(variants = kept, removesLoggedVariant = removesLogged()) }
        } else {
            // Re-selecting the equipment of a removed variant brings that variant back (same id, same history).
            val restored = removed.firstOrNull { it.equipment == equipment }
            removed = removed - listOfNotNull(restored)
            val draft = restored ?: VariantDraft(null, equipment, equipment.defaultLoadType())
            updateState { it.copy(variants = current + draft, removesLoggedVariant = removesLogged()) }
        }
    }

    private suspend fun save() {
        val state = currentState
        if (state.saving || state.savedExerciseId != null) return
        if (!state.canSave) {
            updateState { it.copy(showErrors = true) }
            return
        }
        updateState { it.copy(saving = true, saveFailed = false, showErrors = true) }
        // Any storage failure is shown as "could not save"; cancellation still propagates.
        val savedId = runCatching {
            val id = state.exerciseId
            if (id == null) {
                exercises.createCustom(
                    newExercise(state)
                ).id
            } else {
                exercises.updateCustom(id, edit(state)).id
            }
        }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        updateState {
            if (savedId == null) {
                it.copy(
                    saving = false,
                    saveFailed = true
                )
            } else {
                it.copy(saving = false, savedExerciseId = savedId)
            }
        }
    }

    private fun names(state: NewExerciseState): LocalizedText {
        val name = state.name.trim()
        val other = state.otherName.trim().ifEmpty { name }
        return if (isSpanish(
                state.language
            )
        ) {
            LocalizedText(en = other, es = name)
        } else {
            LocalizedText(en = name, es = other)
        }
    }

    /**
     * The typed steps go to the app language. The other language keeps its own steps when it had different ones,
     * and follows the typed ones otherwise. Untouched instructions stay exactly as they were.
     */
    private fun instructions(state: NewExerciseState): LocalizedSteps {
        val before = original?.instructions
        if (before != null && !instructionsEdited) return before
        val steps = state.instructions.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val spanish = isSpanish(state.language)
        val beforeHere = before?.get(state.language)
        val beforeOther = before?.get(otherLanguage(state.language))
        val other = if (beforeOther != null && beforeOther != beforeHere) beforeOther else steps
        return if (spanish) LocalizedSteps(en = other, es = steps) else LocalizedSteps(en = steps, es = other)
    }

    private fun newExercise(state: NewExerciseState) = NewExercise(
        name = names(state),
        category = state.category,
        trackingType = state.trackingType,
        level = state.level,
        primaryMuscles = state.primaryMuscles,
        secondaryMuscles = state.secondaryMuscles,
        instructions = instructions(state),
        variants = state.variants.map { NewVariant(equipment = it.equipment, loadType = it.loadType, name = it.name) },
    )

    private fun edit(state: NewExerciseState) = ExerciseEdit(
        name = names(state),
        category = state.category,
        trackingType = state.trackingType,
        level = state.level,
        primaryMuscles = state.primaryMuscles,
        secondaryMuscles = state.secondaryMuscles,
        instructions = instructions(state),
        variants = state.variants.map { VariantEdit(it.id, it.equipment, it.loadType, it.name) },
    )

    private companion object {
        const val MAX_NAME_LENGTH = 80
        const val MAX_INSTRUCTIONS_LENGTH = 4_000

        fun isSpanish(language: String) = language.lowercase().startsWith("es")

        fun otherLanguage(language: String) = if (isSpanish(language)) "en" else "es"
    }
}

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
