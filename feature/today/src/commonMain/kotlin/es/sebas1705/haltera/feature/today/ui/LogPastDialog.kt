package es.sebas1705.haltera.feature.today.ui

import androidx.compose.runtime.Composable
import es.sebas1705.haltera.core.ui.components.PastSessionChoice
import es.sebas1705.haltera.core.ui.components.PastSessionDialog
import es.sebas1705.haltera.core.ui.components.PastSessionLabels
import es.sebas1705.haltera.feature.today.resources.Res
import es.sebas1705.haltera.feature.today.resources.today_cancel
import es.sebas1705.haltera.feature.today.resources.today_free_session_name
import es.sebas1705.haltera.feature.today.resources.today_log_past_confirm
import es.sebas1705.haltera.feature.today.resources.today_log_past_date
import es.sebas1705.haltera.feature.today.resources.today_log_past_date_value
import es.sebas1705.haltera.feature.today.resources.today_log_past_day
import es.sebas1705.haltera.feature.today.resources.today_log_past_duration
import es.sebas1705.haltera.feature.today.resources.today_log_past_other
import es.sebas1705.haltera.feature.today.resources.today_log_past_start
import es.sebas1705.haltera.feature.today.resources.today_log_past_title
import es.sebas1705.haltera.feature.today.resources.today_log_past_trained
import es.sebas1705.haltera.feature.today.resources.today_months_short
import es.sebas1705.haltera.feature.today.resources.today_ok
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** "Log past session": date, start, duration and what was trained (spec: any date, any day, or free). */
@Composable
internal fun LogPastDialog(dialog: LogPastUi, onIntent: (TodayIntent) -> Unit) {
    val choices = dialog.options.mapIndexed { index, option ->
        PastSessionChoice(
            key = index.toString(),
            group = option.routineName,
            label = stringResource(Res.string.today_log_past_day, option.target.dayIndex + 1, option.dayName),
        )
    } + PastSessionChoice(
        LogPastUi.FREE_CHOICE,
        stringResource(Res.string.today_log_past_other),
        stringResource(Res.string.today_free_session_name),
    )
    val months = stringArrayResource(Res.array.today_months_short)
    val dateFormat = stringResource(Res.string.today_log_past_date_value)
    PastSessionDialog(
        initial = dialog.initial,
        choices = choices,
        labels = PastSessionLabels(
            title = stringResource(Res.string.today_log_past_title),
            date = stringResource(Res.string.today_log_past_date),
            startTime = stringResource(Res.string.today_log_past_start),
            durationMinutes = stringResource(Res.string.today_log_past_duration),
            trained = stringResource(Res.string.today_log_past_trained),
            confirm = stringResource(Res.string.today_log_past_confirm),
            cancel = stringResource(Res.string.today_cancel),
            ok = stringResource(Res.string.today_ok),
        ),
        maxDateUtcMillis = dialog.maxDateUtcMillis,
        formatDate = { millis ->
            val date = PastSessionTime.dateOf(millis)
            dateFormat.replace("%1\$d", date.day.toString())
                .replace("%2\$s", months.getOrElse(date.month.ordinal) { "" })
                .replace("%3\$d", date.year.toString())
        },
        onConfirm = { onIntent(TodayIntent.ConfirmLogPast(it)) },
        onDismiss = { onIntent(TodayIntent.DismissLogPast) },
    )
}
