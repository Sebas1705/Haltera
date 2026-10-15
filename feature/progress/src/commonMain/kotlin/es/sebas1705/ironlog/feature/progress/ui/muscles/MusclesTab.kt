package es.sebas1705.ironlog.feature.progress.ui.muscles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.domain.usecases.stats.Balance
import es.sebas1705.ironlog.domain.usecases.stats.BalanceKind
import es.sebas1705.ironlog.domain.usecases.stats.Comparison
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.feature.progress.resources.*
import es.sebas1705.ironlog.feature.progress.ui.components.ChartEmpty
import es.sebas1705.ironlog.feature.progress.ui.components.ChartLegend
import es.sebas1705.ironlog.feature.progress.ui.components.ComparisonBarRow
import es.sebas1705.ironlog.feature.progress.ui.components.PeriodSelector
import es.sebas1705.ironlog.feature.progress.ui.components.StatCard
import es.sebas1705.ironlog.feature.progress.ui.components.StatsGrid
import es.sebas1705.ironlog.feature.progress.ui.components.UnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.changeText
import es.sebas1705.ironlog.feature.progress.ui.components.formatNumber
import es.sebas1705.ironlog.feature.progress.ui.components.fullWidth
import es.sebas1705.ironlog.feature.progress.ui.components.muscleLabel
import es.sebas1705.ironlog.feature.progress.ui.components.rememberDateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.rememberUnitFormat
import es.sebas1705.ironlog.domain.usecases.stats.MuscleComparison
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Muscles tab: weekly sets and volume per primary muscle (this period over the previous) and balance ratios. */
@Composable
internal fun MusclesTab(viewModel: MusclesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingScreen()
        return
    }
    val units = rememberUnitFormat(state.unit)
    val dates = rememberDateFormat()
    StatsGrid {
        fullWidth("period") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PeriodSelector(state.period, { viewModel.onEvent(MusclesIntent.SelectPeriod(it)) })
                val current = state.current
                if (current != null) {
                    val now = dates.range(current.first, current.last)
                    val previous = state.previous
                    Text(
                        if (previous == null) {
                            stringResource(Res.string.progress_overview_caption_all, now)
                        } else {
                            stringResource(
                                Res.string.progress_overview_caption,
                                now,
                                dates.range(previous.first, previous.last)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("sets") { MuscleSetsCard(state) }
        item("balance") { BalanceCard(state) }
        item("volume") { MuscleVolumeCard(state, units) }
    }
}

@Composable
private fun MuscleSetsCard(state: MusclesState) {
    val unit = stringResource(Res.string.progress_unit_sets_week)
    val format: (Double) -> String = { formatNumber(it, 1) }
    MuscleListCard(
        title = stringResource(Res.string.progress_chart_muscle_sets),
        metric = Metric.MUSCLE_SETS,
        rows = state.muscles.filter { it.setsPerWeek.current > 0 || (it.setsPerWeek.previous ?: 0.0) > 0 },
        value = { it.setsPerWeek },
        max = state.maxSets,
        format = { "${format(it)} $unit" },
        deltaFormat = format,
        hasPrevious = state.previous != null,
    )
}

@Composable
private fun MuscleVolumeCard(state: MusclesState, units: UnitFormat) {
    MuscleListCard(
        title = stringResource(Res.string.progress_chart_muscle_volume),
        metric = Metric.MUSCLE_VOLUME,
        rows = state.muscles.filter { it.volumePerWeek.current > 0 || (it.volumePerWeek.previous ?: 0.0) > 0 }
            .sortedByDescending { it.volumePerWeek.current },
        value = { it.volumePerWeek },
        max = state.maxVolume,
        format = units::volume,
        deltaFormat = units::volume,
        hasPrevious = state.previous != null,
    )
}

@Composable
private fun MuscleListCard(
    title: String,
    metric: Metric,
    rows: List<MuscleComparison>,
    value: (MuscleComparison) -> Comparison,
    max: Double,
    format: (Double) -> String,
    deltaFormat: (Double) -> String,
    hasPrevious: Boolean,
) {
    StatCard(title, metric = metric, subtitle = stringResource(Res.string.progress_chart_muscle_subtitle)) {
        if (rows.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_muscles_empty))
            return@StatCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                val comparison = value(row)
                ComparisonBarRow(
                    label = muscleLabel(row.muscle),
                    current = comparison.current,
                    previous = comparison.previous,
                    max = max,
                    value = format(comparison.current),
                    change = changeText(comparison, deltaFormat),
                )
            }
        }
        if (hasPrevious) {
            ChartLegend(
                listOf(
                    stringResource(Res.string.progress_legend_this_period) to MaterialTheme.colorScheme.primary,
                    stringResource(Res.string.progress_legend_previous_period) to MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun BalanceCard(state: MusclesState) {
    StatCard(stringResource(Res.string.progress_chart_balance), metric = Metric.MUSCLE_BALANCE) {
        if (state.balance.all { it.first + it.second <= 0 }) {
            ChartEmpty(stringResource(Res.string.progress_balance_empty))
            return@StatCard
        }
        state.balance.forEachIndexed { index, balance ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
            BalanceRow(balance, state.previousBalance.firstOrNull { it.kind == balance.kind })
        }
    }
}

@Composable
private fun BalanceRow(balance: Balance, previous: Balance?) {
    val (firstName, secondName) = sideNames(balance.kind)
    val ratio = ratioText(balance)
    val detail = stringResource(
        Res.string.progress_balance_detail,
        firstName,
        formatNumber(balance.first, 1),
        secondName,
        formatNumber(balance.second, 1),
    )
    val previousText = previous?.let { stringResource(Res.string.progress_balance_previous, ratioText(it)) }
    val description = listOfNotNull("$firstName : $secondName", ratio, detail, previousText).joinToString(", ")
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row {
            Text(
                "$firstName : $secondName",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(ratio, style = MaterialTheme.typography.titleSmall)
        }
        SplitBar(balance.firstShare)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        previousText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ratioText(balance: Balance): String {
    val ratio = balance.ratio
    return when {
        ratio != null -> "${formatNumber(ratio, 2)} : 1"
        balance.first > 0 -> stringResource(Res.string.progress_balance_only_first)
        else -> "—"
    }
}

/** A bar split between the first side (primary) and the second (tertiary); grey without sets. */
@Composable
private fun SplitBar(share: Double?) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (share == null) colors.surfaceVariant else colors.tertiary),
    ) {
        if (share != null && share > 0) {
            Box(Modifier.fillMaxWidth(share.toFloat()).height(12.dp).background(colors.primary))
        }
    }
}

@Composable
private fun sideNames(kind: BalanceKind): Pair<String, String> = when (kind) {
    BalanceKind.PUSH_PULL -> stringResource(Res.string.progress_balance_push) to stringResource(Res.string.progress_balance_pull)
    BalanceKind.UPPER_LOWER -> stringResource(Res.string.progress_balance_upper) to stringResource(Res.string.progress_balance_lower)
    BalanceKind.QUADS_HAMSTRINGS ->
        stringResource(Res.string.progress_muscle_quadriceps) to stringResource(Res.string.progress_muscle_hamstrings)
}
