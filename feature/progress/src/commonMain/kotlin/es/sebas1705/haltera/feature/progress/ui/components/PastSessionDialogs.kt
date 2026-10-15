package es.sebas1705.haltera.feature.progress.ui.components

import androidx.compose.runtime.Composable
import es.sebas1705.haltera.core.ui.components.PastSessionChoice
import es.sebas1705.haltera.core.ui.components.PastSessionDialog
import es.sebas1705.haltera.core.ui.components.PastSessionForm
import es.sebas1705.haltera.core.ui.components.PastSessionLabels
import es.sebas1705.haltera.domain.usecases.session.PastSessionOption
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_cancel
import es.sebas1705.haltera.feature.progress.resources.progress_date_full
import es.sebas1705.haltera.feature.progress.resources.progress_free_session
import es.sebas1705.haltera.feature.progress.resources.progress_log_confirm
import es.sebas1705.haltera.feature.progress.resources.progress_log_date
import es.sebas1705.haltera.feature.progress.resources.progress_log_day
import es.sebas1705.haltera.feature.progress.resources.progress_log_duration
import es.sebas1705.haltera.feature.progress.resources.progress_log_other
import es.sebas1705.haltera.feature.progress.resources.progress_log_start
import es.sebas1705.haltera.feature.progress.resources.progress_log_title
import es.sebas1705.haltera.feature.progress.resources.progress_log_trained
import es.sebas1705.haltera.feature.progress.resources.progress_months_short
import es.sebas1705.haltera.feature.progress.resources.progress_ok
import es.sebas1705.haltera.feature.progress.resources.progress_save
import es.sebas1705.haltera.feature.progress.resources.progress_edit_times
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Key of the free-session choice in [LogSessionDialog]; the others are option indexes. */
internal const val FREE_CHOICE_KEY = "free"

/** "Add session on this day": date, start, duration and what was trained. */
@Composable
internal fun LogSessionDialog(
    initial: PastSessionForm,
    options: List<PastSessionOption>,
    maxDateUtcMillis: Long,
    onConfirm: (PastSessionForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = options.mapIndexed { index, option ->
        PastSessionChoice(
            key = index.toString(),
            group = option.routineName,
            label = stringResource(Res.string.progress_log_day, option.target.dayIndex + 1, option.dayName),
        )
    } + PastSessionChoice(
        FREE_CHOICE_KEY,
        stringResource(Res.string.progress_log_other),
        stringResource(Res.string.progress_free_session),
    )
    TimesDialog(
        title = stringResource(Res.string.progress_log_title),
        confirm = stringResource(Res.string.progress_log_confirm),
        initial = initial,
        choices = choices,
        maxDateUtcMillis = maxDateUtcMillis,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** "Edit date and time" of a saved session: start date and time plus duration. */
@Composable
internal fun EditTimesDialog(
    initial: PastSessionForm,
    maxDateUtcMillis: Long,
    onConfirm: (PastSessionForm) -> Unit,
    onDismiss: () -> Unit,
) = TimesDialog(
    title = stringResource(Res.string.progress_edit_times),
    confirm = stringResource(Res.string.progress_save),
    initial = initial,
    choices = emptyList(),
    maxDateUtcMillis = maxDateUtcMillis,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

@Composable
@Suppress("LongParameterList")
private fun TimesDialog(
    title: String,
    confirm: String,
    initial: PastSessionForm,
    choices: List<PastSessionChoice>,
    maxDateUtcMillis: Long,
    onConfirm: (PastSessionForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val months = stringArrayResource(Res.array.progress_months_short)
    val dateFormat = stringResource(Res.string.progress_date_full)
    PastSessionDialog(
        initial = initial,
        choices = choices,
        labels = PastSessionLabels(
            title = title,
            date = stringResource(Res.string.progress_log_date),
            startTime = stringResource(Res.string.progress_log_start),
            durationMinutes = stringResource(Res.string.progress_log_duration),
            trained = stringResource(Res.string.progress_log_trained),
            confirm = confirm,
            cancel = stringResource(Res.string.progress_cancel),
            ok = stringResource(Res.string.progress_ok),
        ),
        maxDateUtcMillis = maxDateUtcMillis,
        formatDate = { millis ->
            val date = PastSessionTime.dateOf(millis)
            dateFormat.replace("%1\$s", date.day.toString())
                .replace("%2\$s", months.getOrElse(date.month.number - 1) { "" })
                .replace("%3\$s", date.year.toString())
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
