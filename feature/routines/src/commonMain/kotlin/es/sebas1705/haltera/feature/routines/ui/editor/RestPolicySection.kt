package es.sebas1705.haltera.feature.routines.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_cancel
import es.sebas1705.haltera.feature.routines.resources.routines_close
import es.sebas1705.haltera.feature.routines.resources.routines_rest_count_hint
import es.sebas1705.haltera.feature.routines.resources.routines_rest_days_per_week
import es.sebas1705.haltera.feature.routines.resources.routines_rest_day_default_name
import es.sebas1705.haltera.feature.routines.resources.routines_rest_fewer
import es.sebas1705.haltera.feature.routines.resources.routines_rest_knows_days
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_fixed
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_fixed_hint
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_flexible
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_flexible_hint
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_info
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_info_text
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_info_title
import es.sebas1705.haltera.feature.routines.resources.routines_rest_mode_title
import es.sebas1705.haltera.feature.routines.resources.routines_rest_more
import es.sebas1705.haltera.feature.routines.resources.routines_rest_weekdays_hint
import es.sebas1705.haltera.feature.routines.resources.routines_to_fixed_add
import es.sebas1705.haltera.feature.routines.resources.routines_to_fixed_message
import es.sebas1705.haltera.feature.routines.resources.routines_to_fixed_none
import es.sebas1705.haltera.feature.routines.resources.routines_to_fixed_title
import es.sebas1705.haltera.feature.routines.resources.routines_to_flexible_confirm
import es.sebas1705.haltera.feature.routines.resources.routines_to_flexible_message
import es.sebas1705.haltera.feature.routines.resources.routines_to_flexible_title
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_1
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_2
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_3
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_4
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_5
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_6
import es.sebas1705.haltera.feature.routines.resources.routines_weekday_7
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Fixed or flexible rest days (docs/designs/2026-10-16-flexible-rest-days.md): a segmented control with a one-line
 * explanation and an â dialog; flexible adds "I know my rest days" with weekday chips, or a 0â6 stepper.
 */
@Composable
internal fun RestPolicySection(state: RoutineEditorState, send: (RoutineEditorIntent) -> Unit) {
    val policy = state.restPolicy
    var info by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.routines_rest_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { info = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.routines_rest_mode_info))
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RestMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = policy.mode == mode,
                        onClick = { send(RoutineEditorIntent.SetRestMode(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, RestMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (mode == RestMode.FIXED) {
                                    Res.string.routines_rest_mode_fixed
                                } else {
                                    Res.string.routines_rest_mode_flexible
                                },
                            ),
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (policy.isFlexible) {
                        Res.string.routines_rest_mode_flexible_hint
                    } else {
                        Res.string.routines_rest_mode_fixed_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (policy.isFlexible) FlexibleRestControls(policy, state.weekdayMode, send)
        }
    }
    if (info) RestModeInfoDialog(onDismiss = { info = false })
    RestChangeDialog(state.pendingRestChange, send)
}

@Composable
private fun FlexibleRestControls(policy: RestPolicy, weekdayMode: Boolean, send: (RoutineEditorIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = weekdayMode,
            role = Role.Switch,
            onValueChange = { send(RoutineEditorIntent.SetKnowsWeekdays(it)) },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.routines_rest_knows_days),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = weekdayMode, onCheckedChange = null)
    }
    if (weekdayMode) WeekdayChips(policy, send) else RestDaysStepper(policy.restDaysCount, send)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(policy: RestPolicy, send: (RoutineEditorIntent) -> Unit) {
    val chosen = policy.weekdays
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAYS.forEachIndexed { index, label ->
            val isoDay = index + 1
            val selected = isoDay in chosen
            FilterChip(
                selected = selected,
                enabled = selected || chosen.size < RestPolicy.MAX_REST_DAYS,
                onClick = { send(RoutineEditorIntent.ToggleRestWeekday(isoDay)) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    Text(
        stringResource(Res.string.routines_rest_weekdays_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RestDaysStepper(count: Int, send: (RoutineEditorIntent) -> Unit) {
    val label = stringResource(Res.string.routines_rest_days_per_week)
    val fewer = stringResource(Res.string.routines_rest_fewer)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = "$label: $count" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { send(RoutineEditorIntent.SetRestDaysPerWeek(count - 1)) },
            enabled = count > 0
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = fewer }
            )
        }
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        FilledTonalIconButton(
            onClick = { send(RoutineEditorIntent.SetRestDaysPerWeek(count + 1)) },
            enabled = count < RestPolicy.MAX_REST_DAYS,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.routines_rest_more))
        }
    }
    Text(
        stringResource(Res.string.routines_rest_count_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RestModeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_rest_mode_info_title)) },
        text = {
            Text(
                stringResource(Res.string.routines_rest_mode_info_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.routines_close)) } },
    )
}

/** Confirms a conversion that changes the days: removing the empty rest days, or appending them back. */
@Composable
private fun RestChangeDialog(pending: PendingRestChange?, send: (RoutineEditorIntent) -> Unit) {
    val dismiss = { send(RoutineEditorIntent.DismissRestChange) }
    when (pending) {
        null -> Unit
        is PendingRestChange.ToFlexible -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(Res.string.routines_to_flexible_title)) },
            text = {
                Text(
                    pluralStringResource(
                        Res.plurals.routines_to_flexible_message,
                        pending.removedRestDays,
                        pending.removedRestDays,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { send(RoutineEditorIntent.ConfirmToFlexible) }) {
                    Text(stringResource(Res.string.routines_to_flexible_confirm))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.routines_cancel)) } },
        )
        is PendingRestChange.ToFixed -> {
            val restName = stringResource(Res.string.routines_rest_day_default_name)
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(Res.string.routines_to_fixed_title)) },
                text = {
                    Text(
                        pluralStringResource(Res.plurals.routines_to_fixed_message, pending.restDays, pending.restDays)
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { send(RoutineEditorIntent.ConfirmToFixed(true, restName)) }) {
                            Text(
                                pluralStringResource(
                                    Res.plurals.routines_to_fixed_add,
                                    pending.restDays,
                                    pending.restDays
                                )
                            )
                        }
                        TextButton(onClick = { send(RoutineEditorIntent.ConfirmToFixed(false, restName)) }) {
                            Text(stringResource(Res.string.routines_to_fixed_none))
                        }
                        TextButton(onClick = dismiss) { Text(stringResource(Res.string.routines_cancel)) }
                    }
                },
            )
        }
    }
}

private val WEEKDAYS: List<StringResource> = listOf(
    Res.string.routines_weekday_1,
    Res.string.routines_weekday_2,
    Res.string.routines_weekday_3,
    Res.string.routines_weekday_4,
    Res.string.routines_weekday_5,
    Res.string.routines_weekday_6,
    Res.string.routines_weekday_7,
)
