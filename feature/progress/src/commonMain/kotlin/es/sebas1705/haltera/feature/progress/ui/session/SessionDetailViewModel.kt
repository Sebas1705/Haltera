package es.sebas1705.haltera.feature.progress.ui.session

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.ui.components.PastSessionForm
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.domain.usecases.session.LogPastSession
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath
import es.sebas1705.haltera.feature.progress.model.ExerciseLookup
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.model.ProgressMath
import es.sebas1705.haltera.feature.progress.model.SessionSummary
import es.sebas1705.haltera.feature.progress.model.VariantInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

/** One performed exercise of the detail. [variant] is null if the variant is no longer known. */
internal data class ExerciseDetail(
    val id: String,
    val variant: VariantInfo?,
    val notes: String,
    val sets: List<SetLog>,
    val volumeGrams: Long,
)

/** The detail's dialogs: changing the times of the session, or confirming its deletion. */
internal sealed interface DetailDialog {
    data class EditTimes(val initial: PastSessionForm, val maxDateUtcMillis: Long) : DetailDialog

    data object ConfirmDelete : DetailDialog
}

internal data class SessionDetailState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val summary: SessionSummary? = null,
    val endedAt: Long? = null,
    val notes: String = "",
    val exercises: List<ExerciseDetail> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val dialog: DetailDialog? = null,
    /** The session was deleted (here or while editing it): the screen leaves. */
    val deleted: Boolean = false,
    /** The last change could not be saved. */
    val failed: Boolean = false,
) : MVIBaseState

internal sealed interface SessionDetailIntent : MVIBaseIntent {
    data object EditTimes : SessionDetailIntent

    /** New start (date and time) and duration from the dialog. */
    data class SaveTimes(val form: PastSessionForm) : SessionDetailIntent

    data object RequestDelete : SessionDetailIntent

    data object ConfirmDelete : SessionDetailIntent

    data object DismissDialog : SessionDetailIntent
}

/**
 * Detail of one saved session with its exercises and sets. Everything stays editable: its sets (in
 * feature:session, log mode), its date, start and duration ([SessionRepository.updateTimes]), and it can
 * be deleted after a confirmation. The detail, the calendar and Today follow the database reactively.
 */
@Suppress("LongParameterList") // One dependency per data source.
internal class SessionDetailViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock,
) : MVIBaseViewModel<SessionDetailState, SessionDetailIntent>(SessionDetailState()) {

    private val lookup = ExerciseLookup(exercises)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: SessionDetailIntent): Job = execute {
        when (intent) {
            SessionDetailIntent.EditTimes -> currentState.summary?.let { summary ->
                val form = PastSessionTime.editForm(
                    summary.startedAt,
                    currentState.endedAt,
                    clock.timeZone,
                    LogPastSession.DEFAULT_DURATION_MINUTES,
                )
                val maxDate = PastSessionTime.utcMillis(clock.today())
                updateState { it.copy(dialog = DetailDialog.EditTimes(form, maxDate), failed = false) }
            }
            is SessionDetailIntent.SaveTimes -> saveTimes(intent.form)
            SessionDetailIntent.RequestDelete -> updateState { it.copy(dialog = DetailDialog.ConfirmDelete) }
            SessionDetailIntent.ConfirmDelete -> delete()
            SessionDetailIntent.DismissDialog -> updateState { it.copy(dialog = null) }
        }
    }

    private suspend fun saveTimes(form: PastSessionForm) {
        updateState { it.copy(dialog = null) }
        val start = PastSessionTime.startMillis(form, clock.timeZone)
        val saved = attempt { sessions.updateTimes(sessionId, start, start + PastSessionTime.durationMillis(form)) }
        if (saved == null) updateState { it.copy(failed = true) }
    }

    private suspend fun delete() {
        updateState { it.copy(dialog = null) }
        if (attempt { sessions.deleteSession(sessionId) } == null) {
            updateState { it.copy(failed = true) }
        } else {
            updateState { it.copy(deleted = true) }
        }
    }

    private suspend fun observe() {
        combine(
            sessions.observeSession(sessionId),
            routines.observeRoutines(),
            settings.settings,
        ) { session, allRoutines, userSettings ->
            if (session == null) {
                SessionDetailState(loading = false, notFound = true, unit = userSettings.unit)
            } else {
                val routineNames = allRoutines.associate { it.id to it.name }
                val variants = lookup.resolve(session.exercises.map { it.variantId })
                SessionDetailState(
                    loading = false,
                    summary = ProgressMath.summarize(session, routineNames),
                    endedAt = session.endedAt,
                    notes = session.notes,
                    // Older saves may keep exercises without a completed set: nothing was logged, so hide them.
                    exercises = session.exercises
                        .filter { e -> session.inProgress || e.sets.any { it.completed } }
                        .sortedBy { it.position }
                        .map { exercise ->
                            ExerciseDetail(
                                id = exercise.id,
                                variant = variants[exercise.variantId],
                                notes = exercise.notes,
                                sets = exercise.sets.sortedBy { it.position },
                                // The one volume rule (TrainingMath.volume): the same numbers as the calendar and the overview.
                                volumeGrams = TrainingMath.volume(exercise),
                            )
                        },
                    unit = userSettings.unit,
                )
            }
        }.collect { next ->
            updateState { state ->
                // A session that existed and vanished was deleted (e.g. from the editor): leave.
                val vanished = next.notFound && state.summary != null
                next.copy(dialog = state.dialog, failed = state.failed, deleted = state.deleted || vanished)
            }
        }
    }

    private companion object {
        /** Runs [block]; a failure (other than cancellation) becomes null. */
        suspend fun <T> attempt(block: suspend () -> T): T? =
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
}
