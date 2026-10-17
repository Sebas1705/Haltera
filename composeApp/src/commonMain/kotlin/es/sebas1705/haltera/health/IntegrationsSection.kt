package es.sebas1705.haltera.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.units.Numbers
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthSyncError
import es.sebas1705.haltera.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Settings → Integrations → Health Connect: per-type switches, connect, status, steps goal, disconnect. */
@Composable
fun IntegrationsSection(viewModel: IntegrationsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (IntegrationsIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(Unit) { onEvent(IntegrationsIntent.Refresh) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.health_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.health_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state.availability) {
            null -> Unit
            HealthAvailability.UNSUPPORTED -> Note(stringResource(Res.string.health_unsupported))
            HealthAvailability.PROVIDER_MISSING -> {
                Note(stringResource(Res.string.health_missing))
                Button(
                    onClick = { onEvent(IntegrationsIntent.Install) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(stringResource(Res.string.health_install)) }
            }
            HealthAvailability.AVAILABLE -> Available(state, onEvent)
        }
    }
    if (state.confirmDisconnect) DisconnectDialog(onEvent)
    state.goalInput?.let { GoalDialog(it, state.goalInvalid, onEvent) }
}

@Composable
private fun Available(state: IntegrationsState, onEvent: (IntegrationsIntent) -> Unit) {
    Header(Res.string.health_read_header)
    READ_TYPES.forEach { (type, label, detail) -> TypeRow(type, label, detail, state, onEvent) }
    Header(Res.string.health_write_header)
    WRITE_TYPES.forEach { (type, label, detail) -> TypeRow(type, label, detail, state, onEvent) }
    if (state.connected) Connected(state, onEvent) else NotConnected(state, onEvent)
}

@Composable
private fun NotConnected(state: IntegrationsState, onEvent: (IntegrationsIntent) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onEvent(IntegrationsIntent.Connect) },
                enabled = state.enabled.isNotEmpty() && !state.requesting,
            ) { Text(stringResource(Res.string.health_connect)) }
            if (state.requesting) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (state.deniedAll) Line(stringResource(Res.string.health_denied), MaterialTheme.colorScheme.error)
        if (state.deniedAll && state.nothingGranted) NotAskedHint(onEvent)
    }
}

/** Health Connect may have stopped asking (two denials): its own settings are then the only way to allow Haltera. */
@Composable
private fun NotAskedHint(onEvent: (IntegrationsIntent) -> Unit) {
    Line(stringResource(Res.string.health_not_asked), MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { onEvent(IntegrationsIntent.ManagePermissions) }) {
        Text(stringResource(Res.string.health_open_settings))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Connected(state: IntegrationsState, onEvent: (IntegrationsIntent) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.health_step_goal)) },
        supportingContent = {
            Text(stringResource(Res.string.health_step_goal_value, Numbers.format(state.stepGoal.toLong())))
        },
        trailingContent = {
            TextButton(
                onClick = { onEvent(IntegrationsIntent.OpenGoal) }
            ) { Text(stringResource(Res.string.health_change)) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(statusText(state), style = MaterialTheme.typography.bodyMedium)
        }
        when {
            state.noAccess -> Line(stringResource(Res.string.health_no_access), MaterialTheme.colorScheme.error)
            state.lastError == HealthSyncError.PERMISSION_MISSING ->
                Line(stringResource(Res.string.health_error_permission), MaterialTheme.colorScheme.error)
            state.lastError == HealthSyncError.FAILED ->
                Line(stringResource(Res.string.health_error_failed), MaterialTheme.colorScheme.error)
        }
        if (state.nothingGranted && state.missing.isNotEmpty()) NotAskedHint(onEvent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEvent(IntegrationsIntent.ReadNow) }, enabled = !state.syncing) {
                Text(stringResource(Res.string.health_read_now))
            }
            OutlinedButton(onClick = { onEvent(IntegrationsIntent.ManagePermissions) }) {
                Text(stringResource(Res.string.health_manage))
            }
            TextButton(onClick = { onEvent(IntegrationsIntent.AskDisconnect) }) {
                Text(stringResource(Res.string.health_disconnect), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun statusText(state: IntegrationsState): String {
    val lastRead = rememberLastReadFormat()
    return when {
        state.syncing -> stringResource(Res.string.health_reading)
        state.lastReadAt > 0 -> stringResource(
            Res.string.health_last_read,
            lastRead.format(state.lastReadAt, Clock.System.now().toEpochMilliseconds()),
        )
        else -> stringResource(Res.string.health_connected)
    }
}

@Composable
private fun TypeRow(
    type: HealthDataType,
    label: StringResource,
    detail: StringResource,
    state: IntegrationsState,
    onEvent: (IntegrationsIntent) -> Unit,
) {
    val missing = type in state.missing && !state.requesting
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = {
            Column {
                Text(stringResource(detail))
                if (missing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.health_not_allowed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TextButton(onClick = { onEvent(IntegrationsIntent.Allow(type)) }) {
                            Text(stringResource(Res.string.health_allow))
                        }
                    }
                }
            }
        },
        trailingContent = {
            Switch(
                checked = type in state.enabled,
                onCheckedChange = { onEvent(IntegrationsIntent.Toggle(type, it)) },
                enabled = !state.requesting,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Header(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DisconnectDialog(onEvent: (IntegrationsIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(IntegrationsIntent.DismissDisconnect) },
        title = { Text(stringResource(Res.string.health_disconnect_title)) },
        text = { Text(stringResource(Res.string.health_disconnect_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(IntegrationsIntent.ConfirmDisconnect) }) {
                Text(stringResource(Res.string.health_disconnect), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(IntegrationsIntent.DismissDisconnect) }) {
                Text(stringResource(Res.string.health_cancel))
            }
        },
    )
}

@Composable
private fun GoalDialog(input: String, invalid: Boolean, onEvent: (IntegrationsIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(IntegrationsIntent.DismissGoal) },
        title = { Text(stringResource(Res.string.health_step_goal)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { onEvent(IntegrationsIntent.GoalChanged(it)) },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    {
                        Text(
                            stringResource(
                                Res.string.health_goal_invalid,
                                Numbers.format(HealthSettings.MIN_STEP_GOAL.toLong()),
                                Numbers.format(HealthSettings.MAX_STEP_GOAL.toLong()),
                            ),
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(IntegrationsIntent.SaveGoal) }
            ) { Text(stringResource(Res.string.health_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(IntegrationsIntent.DismissGoal) }
            ) { Text(stringResource(Res.string.health_cancel)) }
        },
    )
}

private data class TypeTexts(val type: HealthDataType, val label: StringResource, val detail: StringResource)

private val READ_TYPES = listOf(
    TypeTexts(HealthDataType.STEPS, Res.string.health_type_steps, Res.string.health_type_steps_detail),
    TypeTexts(HealthDataType.CALORIES, Res.string.health_type_calories, Res.string.health_type_calories_detail),
    TypeTexts(
        HealthDataType.WEIGHT_IMPORT,
        Res.string.health_type_weight_import,
        Res.string.health_type_weight_import_detail
    ),
    TypeTexts(HealthDataType.RESTING_HEART_RATE, Res.string.health_type_heart, Res.string.health_type_heart_detail),
    TypeTexts(HealthDataType.SLEEP, Res.string.health_type_sleep, Res.string.health_type_sleep_detail),
)

private val WRITE_TYPES = listOf(
    TypeTexts(HealthDataType.WORKOUTS_EXPORT, Res.string.health_type_workouts, Res.string.health_type_workouts_detail),
    TypeTexts(
        HealthDataType.WEIGHT_EXPORT,
        Res.string.health_type_weight_export,
        Res.string.health_type_weight_export_detail
    ),
)
