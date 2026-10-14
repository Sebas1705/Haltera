package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.domain.usecases.stats.RecordKind
import es.sebas1705.ironlog.feature.session.load.LoadDetail
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_record_heaviest
import es.sebas1705.ironlog.feature.session.resources.session_record_one_rm
import es.sebas1705.ironlog.feature.session.resources.session_record_reps
import es.sebas1705.ironlog.feature.session.resources.session_record_volume
import es.sebas1705.ironlog.feature.session.resources.session_summary_done
import es.sebas1705.ironlog.feature.session.resources.session_summary_duration
import es.sebas1705.ironlog.feature.session.resources.session_summary_increase_line
import es.sebas1705.ironlog.feature.session.resources.session_summary_increases
import es.sebas1705.ironlog.feature.session.resources.session_summary_no_records
import es.sebas1705.ironlog.feature.session.resources.session_summary_records
import es.sebas1705.ironlog.feature.session.resources.session_summary_sets
import es.sebas1705.ironlog.feature.session.resources.session_summary_title
import es.sebas1705.ironlog.feature.session.resources.session_summary_volume
import es.sebas1705.ironlog.feature.session.timer.TimerMath
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Finish summary: duration, volume, sets, new PRs and progression increases earned. */
@Composable
internal fun SummaryView(summary: FinishSummary, unit: WeightUnit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.session_summary_title), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(
                Res.string.session_summary_duration,
                TimerMath.formatElapsed(summary.durationMs),
                Modifier.weight(1f)
            )
            Metric(Res.string.session_summary_volume, weightText(summary.volumeGrams, unit), Modifier.weight(1f))
            Metric(Res.string.session_summary_sets, summary.completedSets.toString(), Modifier.weight(1f))
        }
        Text(stringResource(Res.string.session_summary_records), style = MaterialTheme.typography.titleMedium)
        if (summary.records.isEmpty()) {
            Text(stringResource(Res.string.session_summary_no_records), style = MaterialTheme.typography.bodyMedium)
        }
        summary.records.forEach { line ->
            Column {
                Text(line.name.title(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    line.kinds.sortedBy { it.ordinal }.map { stringResource(recordLabel(it)) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (summary.increases.isNotEmpty()) {
            Text(stringResource(Res.string.session_summary_increases), style = MaterialTheme.typography.titleMedium)
            summary.increases.forEach { line ->
                val detail = if (line.plate) LoadDetail.encode(LoadDetail.MachinePlate) else null
                Text(
                    "↑ ${line.name.title()}: " + stringResource(
                        Res.string.session_summary_increase_line,
                        loadText(line.fromGrams, detail, line.loadType, unit),
                        loadText(line.toGrams, detail, line.loadType, unit),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(Res.string.session_summary_done)) }
    }
}

@Composable
private fun Metric(label: StringResource, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun recordLabel(kind: RecordKind): StringResource = when (kind) {
    RecordKind.HEAVIEST_LOAD -> Res.string.session_record_heaviest
    RecordKind.ESTIMATED_ONE_RM -> Res.string.session_record_one_rm
    RecordKind.MOST_REPS -> Res.string.session_record_reps
    RecordKind.SESSION_VOLUME -> Res.string.session_record_volume
}
