package es.sebas1705.haltera.feature.progress.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_bw_date
import es.sebas1705.haltera.feature.progress.resources.progress_cancel
import es.sebas1705.haltera.feature.progress.resources.progress_ok
import org.jetbrains.compose.resources.stringResource

/**
 * "Date: 14 Oct 2026" with a date picker; [maxDateUtcMillis] (today) is the latest selectable date, so nothing can be
 * logged in the future (body weight, measurements and photos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(dateUtcMillis: Long, maxDateUtcMillis: Long, onPick: (Long) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.progress_bw_date),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { picking = true }) {
            Text(formatDate(PastSessionTime.dateOf(dateUtcMillis), withYear = true))
        }
    }
    if (!picking) return
    val state = rememberDatePickerState(
        initialSelectedDateMillis = dateUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxDateUtcMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPick)
                picking = false
            }) { Text(stringResource(Res.string.progress_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.progress_cancel)) }
        },
    ) { DatePicker(state = state) }
}
