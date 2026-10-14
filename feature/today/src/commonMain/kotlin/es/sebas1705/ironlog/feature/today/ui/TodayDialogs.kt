package es.sebas1705.ironlog.feature.today.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.feature.today.resources.Res
import es.sebas1705.ironlog.feature.today.resources.today_cancel
import es.sebas1705.ironlog.feature.today.resources.today_pick_day_hint
import es.sebas1705.ironlog.feature.today.resources.today_pick_day_next
import es.sebas1705.ironlog.feature.today.resources.today_pick_day_rest
import es.sebas1705.ironlog.feature.today.resources.today_pick_day_start
import es.sebas1705.ironlog.feature.today.resources.today_pick_day_title
import es.sebas1705.ironlog.feature.today.resources.today_save
import es.sebas1705.ironlog.feature.today.resources.today_weight_dialog_title
import es.sebas1705.ironlog.feature.today.resources.today_weight_invalid
import es.sebas1705.ironlog.feature.today.resources.today_weight_label
import org.jetbrains.compose.resources.stringResource

/** "Train another day": tap a day to make it the next one, or start it right away. Rest days are shown disabled. */
@Composable
internal fun DayPickerDialog(plan: PlanUi, startEnabled: Boolean, onIntent: (TodayIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(TodayIntent.DismissDayPicker) },
        title = { Text(stringResource(Res.string.today_pick_day_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.today_pick_day_hint), style = MaterialTheme.typography.bodyMedium)
                plan.days.forEach { day ->
                    DayOption(
                        day,
                        isNext = day.index == plan.next?.dayIndex,
                        startEnabled,
                        onIntent
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = { onIntent(TodayIntent.DismissDayPicker) }
            ) { Text(stringResource(Res.string.today_cancel)) }
        },
    )
}

@Composable
private fun DayOption(day: DayOptionUi, isNext: Boolean, startEnabled: Boolean, onIntent: (TodayIntent) -> Unit) {
    val rowModifier = if (day.isRest) Modifier else Modifier.clickable { onIntent(TodayIntent.ChooseDay(day.index)) }
    ListItem(
        modifier = rowModifier,
        overlineContent = { Text("${day.index + 1}") },
        headlineContent = { Text(day.name) },
        supportingContent = if (day.isRest) {
            { Text(stringResource(Res.string.today_pick_day_rest)) }
        } else {
            null
        },
        trailingContent = when {
            day.isRest -> null
            isNext -> {
                {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(Res.string.today_pick_day_next)) },
                        enabled = false
                    )
                }
            }
            else -> {
                {
                    IconButton(onClick = { onIntent(TodayIntent.StartDay(day.index)) }, enabled = startEnabled) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(Res.string.today_pick_day_start)
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun WeightDialog(dialog: WeightDialogUi, unit: WeightUnit, onIntent: (TodayIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(TodayIntent.DismissWeightDialog) },
        title = { Text(stringResource(Res.string.today_weight_dialog_title)) },
        text = {
            OutlinedTextField(
                value = dialog.input,
                onValueChange = { onIntent(TodayIntent.WeightInputChanged(it)) },
                label = { Text(stringResource(Res.string.today_weight_label, unitLabel(unit))) },
                singleLine = true,
                isError = dialog.invalid,
                supportingText = if (dialog.invalid) {
                    { Text(stringResource(Res.string.today_weight_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(TodayIntent.SaveWeight) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(TodayIntent.SaveWeight) }) { Text(stringResource(Res.string.today_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(TodayIntent.DismissWeightDialog) }
            ) { Text(stringResource(Res.string.today_cancel)) }
        },
    )
}
