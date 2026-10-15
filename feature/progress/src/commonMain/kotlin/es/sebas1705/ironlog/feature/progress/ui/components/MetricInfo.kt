@file:Suppress("MatchingDeclarationName") // The metric texts, the info button and its dialog live together.

package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The texts explaining one [Metric]: the single source of every info dialog. */
internal data class MetricText(
    val title: StringResource,
    val summary: StringResource,
    val formula: StringResource,
    val counts: StringResource,
    val limits: StringResource,
)

/** Metric → its explanation (English and Spanish in the feature's string resources). */
@Suppress("LongMethod", "CyclomaticComplexMethod") // One branch per metric, by design.
internal fun Metric.text(): MetricText = when (this) {
    Metric.ESTIMATED_ONE_RM -> MetricText(
        Res.string.progress_metric_e1rm_title,
        Res.string.progress_metric_e1rm_summary,
        Res.string.progress_metric_e1rm_formula,
        Res.string.progress_metric_e1rm_counts,
        Res.string.progress_metric_e1rm_limits,
    )
    Metric.TOP_SET -> MetricText(
        Res.string.progress_metric_top_set_title,
        Res.string.progress_metric_top_set_summary,
        Res.string.progress_metric_top_set_formula,
        Res.string.progress_metric_top_set_counts,
        Res.string.progress_metric_top_set_limits,
    )
    Metric.VOLUME -> MetricText(
        Res.string.progress_metric_volume_title,
        Res.string.progress_metric_volume_summary,
        Res.string.progress_metric_volume_formula,
        Res.string.progress_metric_volume_counts,
        Res.string.progress_metric_volume_limits,
    )
    Metric.EFFECTIVE_SETS -> MetricText(
        Res.string.progress_metric_sets_title,
        Res.string.progress_metric_sets_summary,
        Res.string.progress_metric_sets_formula,
        Res.string.progress_metric_sets_counts,
        Res.string.progress_metric_sets_limits,
    )
    Metric.PERSONAL_RECORDS -> MetricText(
        Res.string.progress_metric_prs_title,
        Res.string.progress_metric_prs_summary,
        Res.string.progress_metric_prs_formula,
        Res.string.progress_metric_prs_counts,
        Res.string.progress_metric_prs_limits,
    )
    Metric.NEW_PRS -> MetricText(
        Res.string.progress_metric_new_prs_title,
        Res.string.progress_metric_new_prs_summary,
        Res.string.progress_metric_new_prs_formula,
        Res.string.progress_metric_new_prs_counts,
        Res.string.progress_metric_new_prs_limits,
    )
    Metric.SESSIONS -> MetricText(
        Res.string.progress_metric_sessions_title,
        Res.string.progress_metric_sessions_summary,
        Res.string.progress_metric_sessions_formula,
        Res.string.progress_metric_sessions_counts,
        Res.string.progress_metric_sessions_limits,
    )
    Metric.SESSION_DURATION -> MetricText(
        Res.string.progress_metric_duration_title,
        Res.string.progress_metric_duration_summary,
        Res.string.progress_metric_duration_formula,
        Res.string.progress_metric_duration_counts,
        Res.string.progress_metric_duration_limits,
    )
    Metric.TRAINING_TIME -> MetricText(
        Res.string.progress_metric_time_title,
        Res.string.progress_metric_time_summary,
        Res.string.progress_metric_time_formula,
        Res.string.progress_metric_time_counts,
        Res.string.progress_metric_time_limits,
    )
    Metric.FREQUENCY -> MetricText(
        Res.string.progress_metric_frequency_title,
        Res.string.progress_metric_frequency_summary,
        Res.string.progress_metric_frequency_formula,
        Res.string.progress_metric_frequency_counts,
        Res.string.progress_metric_frequency_limits,
    )
    Metric.MOVING_AVERAGE -> MetricText(
        Res.string.progress_metric_moving_title,
        Res.string.progress_metric_moving_summary,
        Res.string.progress_metric_moving_formula,
        Res.string.progress_metric_moving_counts,
        Res.string.progress_metric_moving_limits,
    )
    Metric.PERCENT_CHANGE -> MetricText(
        Res.string.progress_metric_change_title,
        Res.string.progress_metric_change_summary,
        Res.string.progress_metric_change_formula,
        Res.string.progress_metric_change_counts,
        Res.string.progress_metric_change_limits,
    )
    Metric.ADHERENCE -> MetricText(
        Res.string.progress_metric_adherence_title,
        Res.string.progress_metric_adherence_summary,
        Res.string.progress_metric_adherence_formula,
        Res.string.progress_metric_adherence_counts,
        Res.string.progress_metric_adherence_limits,
    )
    Metric.TRAINING_HEATMAP -> MetricText(
        Res.string.progress_metric_heatmap_title,
        Res.string.progress_metric_heatmap_summary,
        Res.string.progress_metric_heatmap_formula,
        Res.string.progress_metric_heatmap_counts,
        Res.string.progress_metric_heatmap_limits,
    )
    Metric.ONE_RM_TREND -> MetricText(
        Res.string.progress_metric_trend_title,
        Res.string.progress_metric_trend_summary,
        Res.string.progress_metric_trend_formula,
        Res.string.progress_metric_trend_counts,
        Res.string.progress_metric_trend_limits,
    )
    Metric.REP_RANGES -> MetricText(
        Res.string.progress_metric_reps_title,
        Res.string.progress_metric_reps_summary,
        Res.string.progress_metric_reps_formula,
        Res.string.progress_metric_reps_counts,
        Res.string.progress_metric_reps_limits,
    )
    Metric.PROGRESSION_TABLE -> MetricText(
        Res.string.progress_metric_table_title,
        Res.string.progress_metric_table_summary,
        Res.string.progress_metric_table_formula,
        Res.string.progress_metric_table_counts,
        Res.string.progress_metric_table_limits,
    )
    Metric.MUSCLE_SETS -> MetricText(
        Res.string.progress_metric_muscle_sets_title,
        Res.string.progress_metric_muscle_sets_summary,
        Res.string.progress_metric_muscle_sets_formula,
        Res.string.progress_metric_muscle_sets_counts,
        Res.string.progress_metric_muscle_sets_limits,
    )
    Metric.MUSCLE_VOLUME -> MetricText(
        Res.string.progress_metric_muscle_volume_title,
        Res.string.progress_metric_muscle_volume_summary,
        Res.string.progress_metric_muscle_volume_formula,
        Res.string.progress_metric_muscle_volume_counts,
        Res.string.progress_metric_muscle_volume_limits,
    )
    Metric.MUSCLE_BALANCE -> MetricText(
        Res.string.progress_metric_balance_title,
        Res.string.progress_metric_balance_summary,
        Res.string.progress_metric_balance_formula,
        Res.string.progress_metric_balance_counts,
        Res.string.progress_metric_balance_limits,
    )
    Metric.BODY_WEIGHT_WEEKLY -> MetricText(
        Res.string.progress_metric_bw_weekly_title,
        Res.string.progress_metric_bw_weekly_summary,
        Res.string.progress_metric_bw_weekly_formula,
        Res.string.progress_metric_bw_weekly_counts,
        Res.string.progress_metric_bw_weekly_limits,
    )
    Metric.BODY_WEIGHT_CHANGE -> MetricText(
        Res.string.progress_metric_bw_change_title,
        Res.string.progress_metric_bw_change_summary,
        Res.string.progress_metric_bw_change_formula,
        Res.string.progress_metric_bw_change_counts,
        Res.string.progress_metric_bw_change_limits,
    )
    Metric.BODY_WEIGHT_RATE -> MetricText(
        Res.string.progress_metric_bw_rate_title,
        Res.string.progress_metric_bw_rate_summary,
        Res.string.progress_metric_bw_rate_formula,
        Res.string.progress_metric_bw_rate_counts,
        Res.string.progress_metric_bw_rate_limits,
    )
    Metric.COMPARE -> MetricText(
        Res.string.progress_metric_compare_title,
        Res.string.progress_metric_compare_summary,
        Res.string.progress_metric_compare_formula,
        Res.string.progress_metric_compare_counts,
        Res.string.progress_metric_compare_limits,
    )
}

/** The ⓘ button: opens the explanation of [metric]. */
@Composable
internal fun InfoButton(metric: Metric, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(metric.text().title)
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.progress_info_about, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) MetricInfoDialog(metric) { open = false }
}

/** How [metric] is calculated: summary, formula, what counts and limitations. */
@Composable
internal fun MetricInfoDialog(metric: Metric, onDismiss: () -> Unit) {
    val text = metric.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(text.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(text.summary), style = MaterialTheme.typography.bodyLarge)
                InfoSection(stringResource(Res.string.progress_info_formula), stringResource(text.formula))
                InfoSection(stringResource(Res.string.progress_info_counts), stringResource(text.counts))
                InfoSection(stringResource(Res.string.progress_info_limits), stringResource(text.limits))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.progress_ok)) } },
    )
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
