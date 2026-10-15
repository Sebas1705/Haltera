package es.sebas1705.ironlog.feature.routines.ui.catalogue

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScore
import es.sebas1705.ironlog.domain.usecases.routinescore.toScoringRoutine
import es.sebas1705.ironlog.feature.routines.model.AppLanguage
import es.sebas1705.ironlog.feature.routines.model.ExerciseIndex
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.model.indexFor
import es.sebas1705.ironlog.feature.routines.model.scoreRoutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

internal data class CatalogueState(
    val language: String = "en",
    val templates: List<RoutineTemplate> = emptyList(),
    /** Advice-only score per template id, filled once the exercises are loaded. */
    val scores: Map<String, RoutineScore> = emptyMap(),
) : MVIBaseState

internal sealed interface CatalogueIntent : MVIBaseIntent

/** The famous routines catalogue (spec §6.3). Templates are bundled, so they are read once. */
internal class CatalogueViewModel(
    routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    language: AppLanguage,
) : MVIBaseViewModel<CatalogueState, CatalogueIntent>(
    CatalogueState(language = language.code(), templates = routines.templates()),
) {
    init {
        execute {
            val templates = currentState.templates
            val targets = templates.flatMap { t -> t.days.flatMap { day -> day.slots.map { it.target } } }
            attempt { exercises.indexFor(targets) }.onSuccess { index ->
                val scores = templates.associate { it.id to scoreRoutine(it.toScoringRoutine(currentState.language), index) }
                updateState { it.copy(scores = scores) }
            }
        }
    }

    override fun intentHandler(intent: CatalogueIntent): Job = execute { }
}

internal data class TemplatePreviewState(
    val language: String = "en",
    val template: RoutineTemplate? = null,
    val index: ExerciseIndex = ExerciseIndex(),
    /** Advice-only score of the template, once its exercises are loaded. */
    val score: RoutineScore? = null,
    /** Make the copy the active routine. */
    val makeActive: Boolean = false,
    val copying: Boolean = false,
    /** One-shot: the copy just created, to open in the editor. */
    val createdRoutineId: String? = null,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface TemplatePreviewIntent : MVIBaseIntent {
    data class SetMakeActive(val active: Boolean) : TemplatePreviewIntent
    data object AddToMyRoutines : TemplatePreviewIntent
    data object OpenHandled : TemplatePreviewIntent
    data object ErrorShown : TemplatePreviewIntent
}

/** Preview of one famous routine and "Add to my routines" (`copyTemplate`, then optionally `setActive`). */
internal class TemplatePreviewViewModel(
    private val templateId: String,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    language: AppLanguage,
) : MVIBaseViewModel<TemplatePreviewState, TemplatePreviewIntent>(
    TemplatePreviewState(
        language = language.code(),
        template = routines.templates().firstOrNull { it.id == templateId },
    ),
) {
    init {
        execute {
            // Suggest making it active when the user has no active routine yet.
            val noActive = attempt { routines.observeActive().first() == null }.getOrDefault(false)
            updateState { it.copy(makeActive = noActive) }
        }
        execute {
            val targets = currentState.template?.days.orEmpty().flatMap { day -> day.slots.map { it.target } }
            attempt { exercises.indexFor(targets) }.onSuccess { index ->
                val score = currentState.template?.let {
                    scoreRoutine(
                        it.toScoringRoutine(currentState.language),
                        index
                    )
                }
                updateState { it.copy(index = index, score = score) }
            }
        }
    }

    override fun intentHandler(intent: TemplatePreviewIntent): Job = execute {
        when (intent) {
            is TemplatePreviewIntent.SetMakeActive -> updateState { it.copy(makeActive = intent.active) }
            TemplatePreviewIntent.AddToMyRoutines -> copy()
            TemplatePreviewIntent.OpenHandled -> updateState { it.copy(createdRoutineId = null) }
            TemplatePreviewIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun copy() {
        val state = currentState
        if (state.copying || state.template == null) return
        updateState { it.copy(copying = true) }
        attempt {
            val id = routines.copyTemplate(templateId, state.language)
            if (state.makeActive) routines.setActive(id)
            id
        }.onSuccess { id ->
            updateState { it.copy(copying = false, createdRoutineId = id) }
        }.onFailure {
            updateState { it.copy(copying = false, error = true) }
        }
    }
}
