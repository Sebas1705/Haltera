package es.sebas1705.haltera.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.export_action
import es.sebas1705.haltera.resources.export_dismiss
import es.sebas1705.haltera.resources.export_done
import es.sebas1705.haltera.resources.export_failed
import es.sebas1705.haltera.resources.export_section
import es.sebas1705.haltera.resources.export_text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings → Data: export everything the user logged as CSV and JSON files in one zip. */
@Composable
fun DataExportSection(viewModel: DataExportViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.export_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(Res.string.export_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { viewModel.onEvent(DataExportIntent.Export) },
                enabled = !state.busy,
            ) { Text(stringResource(Res.string.export_action)) }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        when (val outcome = state.outcome) {
            is ExportOutcome.Done -> Outcome(
                stringResource(
                    Res.string.export_done,
                    outcome.counts.sets,
                    outcome.counts.bodyWeightEntries,
                    outcome.counts.mealEntries,
                    outcome.counts.bodyMeasurements,
                ),
                MaterialTheme.colorScheme.primary,
            ) { viewModel.onEvent(DataExportIntent.DismissOutcome) }
            ExportOutcome.Failed -> Outcome(
                stringResource(Res.string.export_failed),
                MaterialTheme.colorScheme.error,
            ) { viewModel.onEvent(DataExportIntent.DismissOutcome) }
            null -> Unit
        }
    }
}

@Composable
private fun Outcome(text: String, color: Color, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Line(text, color)
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.export_dismiss)) }
    }
}

@Composable
private fun RowScope.Line(text: String, color: Color) {
    Text(text, color = color, modifier = Modifier.weight(1f))
}
