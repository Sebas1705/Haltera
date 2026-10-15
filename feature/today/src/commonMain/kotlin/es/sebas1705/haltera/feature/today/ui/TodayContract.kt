package es.sebas1705.haltera.feature.today.ui

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.ui.components.PastSessionForm
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.domain.usecases.session.PastSessionOption

/** Everything the Today tab shows (spec §7). Loads are grams, times epoch millis. */
internal data class TodayState(
    /** False until the active routine (or its absence) is known. */
    val planLoaded: Boolean = false,
    /** The active routine, or null when there is none. */
    val plan: PlanUi? = null,
    val inProgress: InProgressUi? = null,
    val week: WeekUi = WeekUi(),
    val latestWeight: BodyWeightEntry? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val dayPickerOpen: Boolean = false,
    val weightDialog: WeightDialogUi? = null,
    /** The "Log past session" dialog, when open. */
    val logPast: LogPastUi? = null,
    /** A session is being created; start buttons are disabled. */
    val starting: Boolean = false,
    val error: TodayError? = null,
) : MVIBaseState

/** The session in progress, resumable. */
internal data class InProgressUi(val sessionId: String, val startedAt: Long, val dayName: String?)

/** The active routine with its next pending day and every day of its cycle (for the day picker). */
internal data class PlanUi(
    val routineId: String,
    val routineName: String,
    /** Null when the cycle has no training day. */
    val next: NextDayUi?,
    val days: List<DayOptionUi>,
)

internal data class NextDayUi(
    val dayIndex: Int,
    val dayCount: Int,
    val dayName: String,
    /** The pointer was on one or more REST days (spec §3.4): informational only. */
    val planSaysRest: Boolean,
    val exercises: List<SlotUi>,
)

/** One slot of the next day. [name] is null if the exercise is not in the catalogue. */
internal data class SlotUi(
    val slotId: String,
    /** The exercise name (never the variant's own name). */
    val name: LocalizedText?,
    /** Equipment of a fixed variant; null for a flexible slot (variant chosen in the session). */
    val equipment: Equipment?,
    val flexible: Boolean,
    val sets: Int,
    val prescription: Prescription,
    /** The fixed variant's own name ("Wide grip"), shown instead of the equipment. */
    val variantName: LocalizedText? = null,
)

internal data class DayOptionUi(val index: Int, val name: String, val isRest: Boolean)

/** The current week (Monday first) and its totals. [volumeGrams] is in gram-reps. */
internal data class WeekUi(
    val days: List<WeekDayUi> = emptyList(),
    val sessionCount: Int = 0,
    val volumeGrams: Long = 0,
)

/** [isoDayOfWeek] is 1 (Monday) to 7 (Sunday). */
internal data class WeekDayUi(
    val isoDayOfWeek: Int,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val trained: Boolean,
)

/**
 * "Log body weight": the typed weight and the chosen date ([dateUtcMillis], UTC midnight as the date picker
 * uses). [maxDateUtcMillis] is today (no future dates).
 */
internal data class WeightDialogUi(
    val input: String = "",
    val invalid: Boolean = false,
    val dateUtcMillis: Long = 0,
    val maxDateUtcMillis: Long = 0,
)

/**
 * "Log past session": the dialog's starting values and what can be chosen as trained: every training day
 * ([options], active routine first; the form's choice key is the option index) or a free session
 * ([FREE_CHOICE]). [maxDateUtcMillis] is today (no future dates).
 */
internal data class LogPastUi(
    val initial: PastSessionForm,
    val options: List<PastSessionOption>,
    val maxDateUtcMillis: Long,
) {
    companion object {
        const val FREE_CHOICE = "free"
    }
}

internal enum class TodayError { START_FAILED, SAVE_FAILED }

internal sealed interface TodayIntent : MVIBaseIntent {
    data object ResumeSession : TodayIntent

    data object StartNextDay : TodayIntent

    data object StartFree : TodayIntent

    data object OpenDayPicker : TodayIntent

    data object DismissDayPicker : TodayIntent

    /** Makes [dayIndex] the next day of the cycle (the pointer moves; nothing starts). */
    data class ChooseDay(val dayIndex: Int) : TodayIntent

    /** Starts [dayIndex] right away without moving the pointer first. */
    data class StartDay(val dayIndex: Int) : TodayIntent

    data object OpenWeightDialog : TodayIntent

    data class WeightInputChanged(val text: String) : TodayIntent

    /** A date picked for the body-weight entry (UTC midnight); future dates are clamped to today. */
    data class WeightDateChanged(val dateUtcMillis: Long) : TodayIntent

    data object SaveWeight : TodayIntent

    data object DismissWeightDialog : TodayIntent

    data object OpenLogPast : TodayIntent

    data object DismissLogPast : TodayIntent

    /** Creates the past session from the dialog and opens it in log mode. */
    data class ConfirmLogPast(val form: PastSessionForm) : TodayIntent

    data object ErrorShown : TodayIntent
}

/** One-off events for the screen. */
internal sealed interface TodayEffect {
    data class OpenSession(val sessionId: String) : TodayEffect
}
