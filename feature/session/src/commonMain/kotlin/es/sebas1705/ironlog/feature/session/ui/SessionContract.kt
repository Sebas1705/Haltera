package es.sebas1705.ironlog.feature.session.ui

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.datastore.UserSettings
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.domain.usecases.progression.Suggestion
import es.sebas1705.ironlog.domain.usecases.stats.RecordKind
import es.sebas1705.ironlog.feature.session.timer.TimerSnapshot

/** An exercise of the session with everything its card shows. Names are resolved by the UI's language. */
data class ExerciseCard(
    val id: String,
    val position: Int,
    val variantId: String,
    val slotId: String?,
    val name: ExerciseName,
    val trackingType: TrackingType,
    val loadType: LoadType,
    /** The routine slot it came from; null for an exercise added during the session. */
    val slot: RoutineSlot?,
    /** Progression suggestion for this slot and variant; an [Suggestion.Increase] shows "↑". */
    val suggestion: Suggestion?,
    val sets: List<SetLog>,
    /** Variants a flexible slot can switch to, with their last load; empty for fixed slots. */
    val variantOptions: List<VariantOption> = emptyList(),
) {
    val prescription: Prescription? get() = slot?.prescription
    val done: Boolean get() = sets.isNotEmpty() && sets.all { it.completed }
    val started: Boolean get() = sets.any { it.completed }
}

/** Exercise name plus the variant name override or its equipment. */
data class ExerciseName(val exercise: LocalizedText?, val variant: LocalizedText?, val equipment: Equipment?) {
    /** The variant name override, else the exercise name, in [language]. */
    fun plain(language: String): String = (variant ?: exercise)?.get(language).orEmpty()
}

data class VariantOption(
    val variantId: String,
    val name: ExerciseName,
    val loadType: LoadType,
    val lastLoadGrams: Long?,
)

/** A set being edited in the bottom sheet; [draft] is applied on save. */
data class SetEditor(val cardId: String, val setId: String, val draft: SetInput)

/** Add-exercise picker: query and matching exercises. */
data class ExercisePicker(val query: String = "", val results: List<Exercise> = emptyList())

sealed interface SessionDialog {
    data object ConfirmFinish : SessionDialog
    data object ConfirmDiscard : SessionDialog

    /** Log mode: deleting a saved session. */
    data object ConfirmDelete : SessionDialog
    data object AddExercise : SessionDialog
}

/** Finish summary (spec §5): duration, volume, new PRs and progression increases earned. */
data class FinishSummary(
    val durationMs: Long,
    val volumeGrams: Long,
    val completedSets: Int,
    val records: List<RecordLine>,
    val increases: List<IncreaseLine>,
)

data class RecordLine(val name: ExerciseName, val kinds: Set<RecordKind>)

data class IncreaseLine(val name: ExerciseName, val loadType: LoadType, val fromGrams: Long, val toGrams: Long, val plate: Boolean)

data class SessionState(
    val loading: Boolean = true,
    /** The session does not exist (deleted elsewhere). */
    val missing: Boolean = false,
    val sessionId: String = "",
    val dayName: String? = null,
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    /**
     * Log mode: the session is being logged or edited after the fact (already saved, or started more than
     * [SessionViewModel.LOG_MODE_AFTER_MS] ago). No rest timer, notifications, foreground service or
     * keep-screen-on; "Save" instead of "Finish"; the title shows the date. Decided once, when the session
     * is first loaded.
     */
    val logMode: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val cards: List<ExerciseCard> = emptyList(),
    /** The persisted timer, only when it belongs to this session (always idle in log mode). */
    val timer: TimerSnapshot = TimerSnapshot(),
    val editor: SetEditor? = null,
    val dialog: SessionDialog? = null,
    val picker: ExercisePicker = ExercisePicker(),
    val summary: FinishSummary? = null,
    /** Finishing or saving: writes in flight. */
    val busy: Boolean = false,
    /** Finished live in this screen: "Done" leaves via `onFinished` (back to Today). */
    val finishedHere: Boolean = false,
    /** Discarded (or deleted, in log mode) in this screen: the screen leaves. */
    val discarded: Boolean = false,
) : MVIBaseState {
    val inProgress: Boolean get() = !loading && !missing && endedAt == null

    /** A live session in progress: rest timer, notifications and keep-screen-on apply. */
    val live: Boolean get() = inProgress && !logMode

    /** Sets and exercises can be changed: a live session, or any session in log mode until it is saved. */
    val editable: Boolean get() = !loading && !missing && summary == null && (endedAt == null || logMode)

    /** The exercise being worked: the first one with a pending set, else the first without sets. */
    val currentCardId: String?
        get() = (cards.firstOrNull { c -> c.sets.any { !it.completed } } ?: cards.firstOrNull { it.sets.isEmpty() })?.id
}

sealed interface SessionIntent : MVIBaseIntent {
    /** UI language ("en"/"es") for exercise names in the notification and the search. */
    data class SetLanguage(val language: String) : SessionIntent

    /** One tap: completes (and starts the rest) or reopens a set. */
    data class ToggleSet(val setId: String) : SessionIntent

    /** TIME sets: 3 s countdown, work, rest. Logs the set as completed with its target duration. */
    data class StartTimedSet(val setId: String) : SessionIntent

    /** Ends the running timed set early; its duration becomes the time actually worked. */
    data object StopTimedSet : SessionIntent

    data class EditSet(val setId: String) : SessionIntent
    data class ChangeDraft(val draft: SetInput) : SessionIntent
    data object SaveDraft : SessionIntent
    data object DeleteEditedSet : SessionIntent
    data object CloseEditor : SessionIntent

    data class AddSet(val cardId: String) : SessionIntent
    data class ChooseVariant(val cardId: String, val variantId: String) : SessionIntent
    data class RemoveExercise(val cardId: String) : SessionIntent
    data class MoveExercise(val cardId: String, val delta: Int) : SessionIntent

    data object OpenAddExercise : SessionIntent
    data class SearchExercises(val query: String) : SessionIntent
    data class AddExercise(val variantId: String) : SessionIntent

    data class AdjustRest(val deltaSeconds: Int) : SessionIntent
    data object SkipRest : SessionIntent

    data object RequestFinish : SessionIntent

    /** Log mode: keeps the completed sets and shows the summary (no confirmation). */
    data object Save : SessionIntent

    data object ConfirmFinish : SessionIntent
    data object RequestDiscard : SessionIntent
    data object ConfirmDiscard : SessionIntent
    data object DismissDialog : SessionIntent
}
