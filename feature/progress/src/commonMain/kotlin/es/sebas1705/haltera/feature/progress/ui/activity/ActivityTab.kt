package es.sebas1705.haltera.feature.progress.ui.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.ui.components.HealthDisclaimer
import es.sebas1705.haltera.core.ui.components.HealthDisclaimerKind
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.ui.theme.HalteraTheme
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.domain.usecases.stats.Metric
import es.sebas1705.haltera.domain.usecases.stats.WeeklySteps
import es.sebas1705.haltera.feature.progress.model.ChartPoint
import es.sebas1705.haltera.feature.progress.model.startOfDayMillis
import es.sebas1705.haltera.feature.progress.resources.*
import es.sebas1705.haltera.feature.progress.ui.components.BarChart
import es.sebas1705.haltera.feature.progress.ui.components.ChartEmpty
import es.sebas1705.haltera.feature.progress.ui.components.ChartLegend
import es.sebas1705.haltera.feature.progress.ui.components.ComparisonTile
import es.sebas1705.haltera.feature.progress.ui.components.DateFormat
import es.sebas1705.haltera.feature.progress.ui.components.LineChart
import es.sebas1705.haltera.feature.progress.ui.components.LineSeries
import es.sebas1705.haltera.feature.progress.ui.components.StatCard
import es.sebas1705.haltera.feature.progress.ui.components.StatsGrid
import es.sebas1705.haltera.feature.progress.ui.components.TileGrid
import es.sebas1705.haltera.feature.progress.ui.components.barsDescription
import es.sebas1705.haltera.feature.progress.ui.components.formatNumber
import es.sebas1705.haltera.feature.progress.ui.components.fullWidth
import es.sebas1705.haltera.feature.progress.ui.components.lineDescription
import es.sebas1705.haltera.feature.progress.ui.components.rememberDateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val PERCENT = 100

/** Progress → Activity: daily steps against the goal, streaks, weekly totals and the optional trends. */
@Composable
internal fun ActivityTab(viewModel: ActivityViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (ActivityIntent) -> Unit = { viewModel.onEvent(it) }
    val summary = state.summary
    if (state.loading || summary == null) {
        LoadingScreen()
        return
    }
    val dates = rememberDateFormat()
    StatsGrid {
        if (!state.connected) {
            fullWidth("connect") { ChartEmpty(stringResource(Res.string.progress_activity_not_connected)) }
            return@StatsGrid
        }
        fullWidth("header") { Header(state, onEvent) }
        fullWidth("tiles") { Tiles(state, summary, dates) }
        item("steps") { StepsChart(state, summary, dates) }
        item("weekly") { WeeklyCard(summary, dates) }
        if (summary.activeKcal.isNotEmpty() || summary.totalKcal.isNotEmpty()) {
            item("calories") { CaloriesCard(summary, dates) }
        }
        if (summary.restingHeartRate.isNotEmpty()) {
            item("heart") {
                TrendCard(
                    Res.string.progress_activity_heart,
                    stringResource(
                        Res.string.progress_activity_heart_subtitle,
                        number(summary.restingHeartRateAverage, 0)
                    ),
                    Metric.RESTING_HEART_RATE,
                    summary.restingHeartRate,
                    dates,
                    axisDecimals = 0,
                ) { stringResource(Res.string.progress_activity_bpm, formatNumber(it, 0)) }
            }
        }
        if (summary.sleepHours.isNotEmpty()) {
            item("sleep") {
                TrendCard(
                    Res.string.progress_activity_sleep,
                    stringResource(Res.string.progress_activity_sleep_subtitle, number(summary.sleepHoursAverage, 1)),
                    Metric.SLEEP_DURATION,
                    summary.sleepHours,
                    dates,
                    axisDecimals = 1,
                ) { stringResource(Res.string.progress_activity_hours, formatNumber(it, 1)) }
            }
        }
        fullWidth("disclaimer") { HealthDisclaimer(HealthDisclaimerKind.STATISTICS) }
    }
    state.goalInput?.let { GoalDialog(it, state.goalInvalid, onEvent) }
}

@Composable
private fun number(value: Double?, decimals: Int): String =
    value?.let { formatNumber(it, decimals) } ?: stringResource(Res.string.progress_activity_none)

@Composable
private fun steps(
    value: Long?
): String = value?.let { formatNumber(it.toDouble(), 0) } ?: stringResource(Res.string.progress_activity_none)

@Composable
private fun Header(state: ActivityState, onEvent: (ActivityIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityRange.entries.forEach { r ->
            FilterChip(
                selected = r == state.range,
                onClick = { onEvent(ActivityIntent.SetRange(r)) },
                label = {
                    Text(
                        stringResource(
                            when (r) {
                                ActivityRange.WEEK -> Res.string.progress_activity_range_7
                                ActivityRange.MONTH -> Res.string.progress_activity_range_30
                                ActivityRange.QUARTER -> Res.string.progress_activity_range_90
                            },
                        ),
                    )
                },
            )
        }
        TextButton(onClick = { onEvent(ActivityIntent.OpenGoal) }) {
            Text(stringResource(Res.string.progress_activity_goal_button, formatNumber(state.stepGoal.toDouble(), 0)))
        }
        IconButton(onClick = { onEvent(ActivityIntent.Refresh) }) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.progress_activity_refresh))
        }
    }
}

@Suppress("LongMethod") // One tile per statistic.
@Composable
private fun Tiles(state: ActivityState, summary: ActivitySummary, dates: DateFormat) {
    val goal = state.stepGoal
    val todayPercent = summary.todaySteps?.let { (it * PERCENT / goal.coerceAtLeast(1)).toString() }
    val noDelta: (Double) -> String = { "" }
    val streak = summary.longestStreak
    val longest = pluralStringResource(Res.plurals.progress_activity_streak_longest, streak, streak)
    TileGrid(
        listOf(
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_activity_today),
                    steps(summary.todaySteps),
                    null,
                    noDelta,
                    Metric.STEPS_DAILY,
                    m,
                    footnote = todayPercent?.let {
                        stringResource(Res.string.progress_activity_today_goal, it, formatNumber(goal.toDouble(), 0))
                    },
                )
            },
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_activity_average),
                    number(summary.sevenDayAverage, 0),
                    null,
                    noDelta,
                    Metric.STEPS_AVERAGE,
                    m,
                    footnote = stringResource(Res.string.progress_activity_average_detail),
                )
            },
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_activity_streak),
                    stringResource(Res.string.progress_activity_streak_value, summary.currentStreak),
                    null,
                    noDelta,
                    Metric.STEPS_STREAK,
                    m,
                    footnote = longest,
                )
            },
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_activity_best),
                    steps(summary.best?.value),
                    null,
                    noDelta,
                    Metric.STEPS_BEST,
                    m,
                    footnote = summary.best?.let { dates.short(it.date) },
                )
            },
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_activity_at_goal),
                    stringResource(Res.string.progress_activity_at_goal_value, summary.daysAtGoal, state.range.days),
                    null,
                    noDelta,
                    Metric.STEPS_DAILY,
                    m,
                    footnote = stringResource(Res.string.progress_activity_at_goal_detail, state.range.days),
                )
            },
        ),
    )
}

@Composable
private fun StepsChart(state: ActivityState, summary: ActivitySummary, dates: DateFormat) {
    val title = stringResource(Res.string.progress_activity_steps_chart)
    StatCard(
        title,
        metric = Metric.STEPS_DAILY,
        subtitle = stringResource(Res.string.progress_activity_steps_subtitle)
    ) {
        if (!summary.hasSteps) {
            val why = if (state.stepsAllowed) Res.string.progress_activity_no_steps else Res.string.progress_activity_steps_off
            ChartEmpty(stringResource(why))
            return@StatCard
        }
        val labels = summary.daily.map { dates.short(it.first) }
        val values = summary.daily.map { (it.second ?: 0L).toDouble() }
        val tooltips = summary.daily.map { (date, value) ->
            stringResource(Res.string.progress_activity_steps_tooltip, dates.short(date), steps(value))
        }
        val goalColor = MaterialTheme.colorScheme.onSurface
        BarChart(
            values = values,
            labels = labels,
            format = { formatNumber(it, 0) },
            tooltip = { tooltips[it] },
            description = barsDescription(title, labels, values) { formatNumber(it, 0) },
            unit = stringResource(Res.string.progress_activity_steps_unit),
            highlightLast = true,
            goal = state.stepGoal.toDouble(),
            goalColor = goalColor,
        )
        ChartLegend(
            listOf(
                stringResource(Res.string.progress_activity_steps_legend) to MaterialTheme.colorScheme.primary,
                stringResource(Res.string.progress_activity_goal_legend, formatNumber(state.stepGoal.toDouble(), 0)) to goalColor,
            ),
        )
    }
}

@Composable
private fun WeeklyCard(summary: ActivitySummary, dates: DateFormat) {
    StatCard(
        stringResource(Res.string.progress_activity_weekly),
        metric = Metric.STEPS_WEEKLY,
        subtitle = stringResource(Res.string.progress_activity_weekly_subtitle),
    ) {
        val header = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        Cells(
            listOf(
                stringResource(Res.string.progress_activity_week),
                stringResource(Res.string.progress_activity_total),
                stringResource(Res.string.progress_activity_per_day),
            ),
            header,
        )
        HorizontalDivider()
        summary.weekly.forEach { week ->
            Cells(
                listOf(
                    dates.short(week.weekStart),
                    weekTotal(week),
                    number(week.average, 0),
                ),
                MaterialTheme.typography.bodySmall.tabularFigures(),
            )
        }
    }
}

@Composable
private fun weekTotal(week: WeeklySteps): String {
    val none = stringResource(Res.string.progress_activity_none)
    return if (week.daysWithData == 0) none else formatNumber(week.total.toDouble(), 0)
}

@Composable
private fun Cells(texts: List<String>, style: TextStyle) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        texts.forEachIndexed { i, text ->
            Text(
                text,
                style = style,
                modifier = Modifier.weight(1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End
            )
        }
    }
}

private fun points(values: List<Pair<LocalDate, Double>>, zone: TimeZone): List<ChartPoint> =
    values.map { (date, v) -> ChartPoint(date.startOfDayMillis(zone), v) }

@Composable
private fun CaloriesCard(summary: ActivitySummary, dates: DateFormat) {
    val title = stringResource(Res.string.progress_activity_calories)
    val zone = TimeZone.currentSystemDefault()
    val activeLabel = stringResource(Res.string.progress_activity_active)
    val totalLabel = stringResource(Res.string.progress_activity_total_kcal)
    val series = listOfNotNull(
        points(summary.totalKcal, zone).takeIf {
            it.isNotEmpty()
        }?.let { LineSeries(it, MaterialTheme.colorScheme.primary, totalLabel) },
        points(summary.activeKcal, zone).takeIf {
            it.isNotEmpty()
        }?.let { LineSeries(it, HalteraTheme.colors.prGold, activeLabel) },
    )
    val kcal: @Composable (
        Double
    ) -> String = { stringResource(Res.string.progress_activity_kcal, formatNumber(it, 0)) }
    val labels = series.map { s -> s.points.map { kcal(it.y) } }
    StatCard(
        title,
        metric = Metric.CALORIES_BURNED,
        subtitle = stringResource(Res.string.progress_activity_calories_subtitle)
    ) {
        val all = series.flatMap { it.points }
        LineChart(
            series = series,
            format = { formatNumber(it, 0) },
            dateLabel = { dates.short(it) },
            tooltip = { s, p -> "${dates.short(series[s].points[p].x)}\n${series[s].label}: ${labels[s][p]}" },
            description = lineDescription(
                title,
                dates.short(all.minOf { it.x }),
                dates.short(all.maxOf { it.x }),
                series.first().points.map { it.y },
            ) { formatNumber(it, 0) },
            unit = stringResource(Res.string.progress_activity_kcal_unit),
            includeZero = true,
        )
        ChartLegend(series.map { it.label.orEmpty() to it.color })
    }
}

@Composable
private fun TrendCard(
    title: StringResource,
    subtitle: String,
    metric: Metric,
    values: List<Pair<LocalDate, Double>>,
    dates: DateFormat,
    axisDecimals: Int,
    format: @Composable (Double) -> String,
) {
    val name = stringResource(title)
    val zone = TimeZone.currentSystemDefault()
    val line = LineSeries(points(values, zone), MaterialTheme.colorScheme.primary)
    val texts = line.points.map { format(it.y) }
    StatCard(name, metric = metric, subtitle = subtitle) {
        Column {
            LineChart(
                series = listOf(line),
                format = { formatNumber(it, axisDecimals) },
                dateLabel = { dates.short(it) },
                tooltip = { _, p -> "${dates.short(line.points[p].x)}\n${texts[p]}" },
                description = lineDescription(
                    name,
                    dates.short(line.points.first().x),
                    dates.short(line.points.last().x),
                    line.points.map { it.y },
                ) { formatNumber(it, 1) },
            )
        }
    }
}

@Composable
private fun GoalDialog(input: String, invalid: Boolean, onEvent: (ActivityIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ActivityIntent.DismissGoal) },
        title = { Text(stringResource(Res.string.progress_activity_goal_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { onEvent(ActivityIntent.GoalChanged(it)) },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    {
                        Text(
                            stringResource(
                                Res.string.progress_activity_goal_invalid,
                                formatNumber(HealthSettings.MIN_STEP_GOAL.toDouble(), 0),
                                formatNumber(HealthSettings.MAX_STEP_GOAL.toDouble(), 0),
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
                onClick = { onEvent(ActivityIntent.SaveGoal) }
            ) { Text(stringResource(Res.string.progress_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ActivityIntent.DismissGoal) }
            ) { Text(stringResource(Res.string.progress_cancel)) }
        },
    )
}
