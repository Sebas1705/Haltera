package es.sebas1705.haltera.feature.exercises.ui.create

import androidx.compose.runtime.Immutable
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.feature.exercises.model.defaultLoadType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

@Immutable
data class NewExerciseState(
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    /** Selected primary muscles, in selection order. */
    val primaryMuscles: List<Muscle> = emptyList(),
    /** One variant per selected equipment, in selection order. */
    val equipment: List<Equipment> = emptyList(),
    /** Validation messages show only after the first save attempt. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once saved; the screen then opens the new exercise. */
    val savedExerciseId: String? = null,
) : MVIBaseState {
    val nameError: Boolean get() = name.isBlank()
    val equipmentError: Boolean get() = equipment.isEmpty()
    val canSave: Boolean get() = !nameError && !equipmentError && !saving
}

sealed interface NewExerciseIntent : MVIBaseIntent {
    data class NameChanged(val name: String) : NewExerciseIntent
    data class CategorySelected(val category: ExerciseCategory) : NewExerciseIntent
    data class TrackingSelected(val trackingType: TrackingType) : NewExerciseIntent
    data class MuscleToggled(val muscle: Muscle) : NewExerciseIntent
    data class EquipmentToggled(val equipment: Equipment) : NewExerciseIntent
    data object Save : NewExerciseIntent
}

/** Custom exercise editor: one name for both languages, one variant per equipment (spec §3.1). */
class NewExerciseViewModel(
    private val exercises: ExerciseRepository,
) : MVIBaseViewModel<NewExerciseState, NewExerciseIntent>(NewExerciseState()) {

    override fun intentHandler(intent: NewExerciseIntent): Job = execute {
        when (intent) {
            is NewExerciseIntent.NameChanged -> updateState { it.copy(name = intent.name.take(MAX_NAME_LENGTH)) }
            is NewExerciseIntent.CategorySelected -> updateState { it.copy(category = intent.category) }
            is NewExerciseIntent.TrackingSelected -> updateState { it.copy(trackingType = intent.trackingType) }
            is NewExerciseIntent.MuscleToggled -> updateState {
                it.copy(
                    primaryMuscles = it.primaryMuscles.toggle(intent.muscle)
                )
            }
            is NewExerciseIntent.EquipmentToggled -> updateState {
                it.copy(
                    equipment = it.equipment.toggle(intent.equipment)
                )
            }
            NewExerciseIntent.Save -> save()
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
        val name = state.name.trim()
        val input = NewExercise(
            name = LocalizedText.same(name),
            category = state.category,
            trackingType = state.trackingType,
            primaryMuscles = state.primaryMuscles,
            variants = state.equipment.map { NewVariant(equipment = it, loadType = it.defaultLoadType()) },
        )
        // Any storage failure is shown as "could not save"; cancellation still propagates.
        val created = runCatching { exercises.createCustom(input) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        updateState {
            if (created == null) {
                it.copy(
                    saving = false,
                    saveFailed = true
                )
            } else {
                it.copy(saving = false, savedExerciseId = created.id)
            }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
