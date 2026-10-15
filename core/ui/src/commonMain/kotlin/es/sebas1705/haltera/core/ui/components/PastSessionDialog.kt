package es.sebas1705.ironlog.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The values of [PastSessionDialog]. [dateUtcMillis] is the chosen calendar date as UTC midnight (what
 * the Material date picker uses); [hour]:[minute] is the local start time; [choiceKey] is the chosen
 * [PastSessionChoice.key] (null when the dialog has no choices).
 */
data class PastSessionForm(
    val dateUtcMillis: Long,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    val choiceKey: String?,
)

/** One option of "what was trained". [group] (e.g. the routine name) is shown as a header when it changes. */
data class PastSessionChoice(val key: String, val group: String?, val label: String)

/** The dialog's texts, resolved by the calling feature (core:ui keeps its resources private). */
data class PastSessionLabels(
    val title: String,
    val date: String,
    val startTime: String,
    val durationMinutes: String,
    val trained: String,
    val confirm: String,
    val cancel: String,
    val ok: String,
)

private const val MINUTES_PER_HOUR = 60
private const val MAX_DURATION_MINUTES = 24 * MINUTES_PER_HOUR

/**
 * Date, start time, duration and (when [choices] is not empty) what was trained, for logging a past
 * session or changing a saved one's times. Nothing is restricted beyond a date not in the future and a
 * duration between 0 and 24 h. [formatDate] renders a [PastSessionForm.dateUtcMillis].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList") // One dialog with its pickers; plain values in and out.
fun PastSessionDialog(
    initial: PastSessionForm,
    choices: List<PastSessionChoice>,
    labels: PastSessionLabels,
    maxDateUtcMillis: Long,
    formatDate: (Long) -> String,
    onConfirm: (PastSessionForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableLongStateOf(initial.dateUtcMillis) }
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    var duration by rememberSaveable { mutableStateOf(initial.durationMinutes.toString()) }
    var choice by rememberSaveable { mutableStateOf(initial.choiceKey ?: choices.firstOrNull()?.key) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val minutes = duration.trim().toIntOrNull()?.takeIf { it in 0..MAX_DURATION_MINUTES }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledButton(labels.date, formatDate(date)) { pickingDate = true }
                LabeledButton(labels.startTime, clock(hour, minute)) { pickingTime = true }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { text -> duration = text.filter { it.isDigit() }.take(4) },
                    label = { Text(labels.durationMinutes) },
                    singleLine = true,
                    isError = minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (choices.isNotEmpty()) {
                    Text(labels.trained, style = MaterialTheme.typography.titleSmall)
                    Choices(choices, choice) { choice = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null,
                onClick = { onConfirm(PastSessionForm(date, hour, minute, minutes ?: 0, choice)) },
            ) { Text(labels.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxDateUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it }
                    pickingDate = false
                }) { Text(labels.ok) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(labels.cancel) } },
        ) { DatePicker(state = state) }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(labels.startTime) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    pickingTime = false
                }) { Text(labels.ok) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(labels.cancel) } },
        )
    }
}

@Composable
private fun LabeledButton(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun Choices(choices: List<PastSessionChoice>, selected: String?, onSelect: (String) -> Unit) {
    Column {
        choices.forEachIndexed { index, option ->
            if (option.group != null && option.group != choices.getOrNull(index - 1)?.group) {
                Text(
                    option.group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = option.key == selected, role = Role.RadioButton) { onSelect(option.key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option.key == selected, onClick = null)
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun clock(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
