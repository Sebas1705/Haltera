package es.sebas1705.ironlog.feature.progress.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.domain.usecases.stats.Adherence
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.domain.usecases.stats.OverviewStats
import es.sebas1705.ironlog.domain.usecases.stats.StatsBucket
import es.sebas1705.ironlog.feature.progress.model.startOfDayMillis
import es.sebas1705.ironlog.feature.progress.resources.*
import es.sebas1705.ironlog.feature.progress.ui.components.BarChart
import es.sebas1705.ironlog.feature.progress.ui.components.ChartEmpty
import es.sebas1705.ironlog.feature.progress.ui.components.ChartLegend
import es.sebas1705.ironlog.feature.progress.ui.components.ComparisonTile
import es.sebas1705.ironlog.feature.progress.ui.components.DateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.HeatmapChart
import es.sebas1705.ironlog.feature.progress.ui.components.LineChart
import es.sebas1705.ironlog.feature.progress.ui.components.LineSeries
import es.sebas1705.ironlog.feature.progress.ui.components.PeriodSelector
import es.sebas1705.ironlog.feature.progress.ui.components.StatCard
import es.sebas1705.ironlog.feature.progress.ui.components.StatsGrid
import es.sebas1705.ironlog.feature.progress.ui.components.TileGrid
import es.sebas1705.ironlog.feature.progress.ui.components.UnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.barsDescription
import es.sebas1705.ironlog.feature.progress.ui.components.formatNumber
import es.sebas1705.ironlog.feature.progress.ui.components.fullWidth
import es.sebas1705.ironlog.feature.progress.ui.components.lineDescription
import es.sebas1705.ironlog.feature.progress.ui.components.rememberDateFormat
import es.sebas1705.ironlog.feature.progress.ui.components.rememberUnitFormat
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.ui.components.fillPattern
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import es.sebas1705.ironlog.domain.usecases.stats.Comparison
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Overview tab: period comparison tiles, weekly charts, training heatmap and plan adherence. */
@Composable
internal fun OverviewTab(viewModel: OverviewViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.stats
    if (state.loading || stats == null) {
        LoadingScreen()
        return
    }
    val units = rememberUnitFormat(state.unit)
    val dates = rememberDateFormat()
    StatsGrid {
        fullWidth("period") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PeriodSelector(state.period, { viewModel.onEvent(OverviewIntent.SelectPeriod(it)) })
                Text(
                    periodCaption(stats, dates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!state.hasHistory) {
            fullWidth("empty") { ChartEmpty(stringResource(Res.string.progress_overview_empty)) }
        }
        fullWidth("tiles") { OverviewTiles(stats, units, dates) }
        item("volume") { WeeklyVolumeCard(stats, units, dates) }
        item("sessions") { WeeklyCountCard(stats, dates, sessions = true) }
        item("heatmap") { HeatmapCard(state, dates) }
        item("sets") { WeeklyCountCard(stats, dates, sessions = false) }
        item("duration") { DurationCard(stats, dates) }
        item("adherence") { AdherenceCard(stats.adherence, state.hasActiveRoutine) }
    }
}

@Composable
private fun periodCaption(stats: OverviewStats, dates: DateFormat): String {
    val current = dates.range(stats.current.first, stats.current.last)
    val previous = stats.previous ?: return stringResource(Res.string.progress_overview_caption_all, current)
    return stringResource(Res.string.progress_overview_caption, current, dates.range(previous.first, previous.last))
}

/** One comparison tile of the overview. */
private class TileSpec(
    val title: StringResource,
    val value: String,
    val comparison: Comparison?,
    val formatDelta: (Double) -> String,
    val metric: Metric,
)

@Composable
private fun OverviewTiles(stats: OverviewStats, units: UnitFormat, dates: DateFormat) {
    val count: (Double) -> String = { formatNumber(it, 0) }
    val average = stats.averageDurationMillis
    val specs = listOf(
        TileSpec(
            Res.string.progress_tile_sessions,
            count(stats.sessions.current),
            stats.sessions,
            count,
            Metric.SESSIONS
        ),
        TileSpec(
            Res.string.progress_tile_volume,
            units.volume(stats.volume.current),
            stats.volume,
            units::volume,
            Metric.VOLUME
        ),
        TileSpec(
            Res.string.progress_tile_sets,
            count(stats.effectiveSets.current),
            stats.effectiveSets,
            count,
            Metric.EFFECTIVE_SETS
        ),
        TileSpec(
            Res.string.progress_tile_time,
            dates.duration(stats.trainingTimeMillis.current),
            stats.trainingTimeMillis,
            dates::duration,
            Metric.TRAINING_TIME,
        ),
        TileSpec(Res.string.progress_tile_prs, count(stats.newPrs.current), stats.newPrs, count, Metric.NEW_PRS),
        TileSpec(
            Res.string.progress_tile_duration,
            average?.let { dates.duration(it.current) } ?: "—",
            average,
            dates::duration,
            Metric.SESSION_DURATION,
        ),
    )
    TileGrid(
        specs.map { spec ->
            @Composable { m: Modifier ->
                ComparisonTile(
                    stringResource(spec.title),
                    spec.value,
                    spec.comparison,
                    spec.formatDelta,
                    spec.metric,
                    m
                )
            }
        },
    )
}

/** Bar labels: the first day of each bucket. */
private fun labels(buckets: List<StatsBucket>, dates: DateFormat) = buckets.map { dates.epochDay(it.window.first) }

@Composable
private fun WeeklyVolumeCard(stats: OverviewStats, units: UnitFormat, dates: DateFormat) {
    val title = stringResource(Res.string.progress_chart_weekly_volume)
    val values = stats.buckets.map { units.toUnit(it.perWeek(it.volume.toDouble())) }
    val ghost = stats.previousBuckets.map { units.toUnit(it.perWeek(it.volume.toDouble())) }.takeIf { it.isNotEmpty() }
    val labels = labels(stats.buckets, dates)
    val previousLabel = stringResource(Res.string.progress_chart_previous)
    val format: (Double) -> String = { "${formatNumber(it, 0)} ${units.label}" }
    StatCard(title, metric = Metric.VOLUME, subtitle = bucketSubtitle(stats)) {
        if (stats.volume.current <= 0 && (stats.volume.previous ?: 0.0) <= 0) {
            ChartEmpty(stringResource(Res.string.progress_chart_volume_empty))
            return@StatCard
        }
        BarChart(
            values = values,
            labels = labels,
            format = units::axis,
            unit = units.label,
            ghost = ghost,
            tooltip = { i ->
                val window = stats.buckets[i].window
                val line = "${dates.range(window.first, window.last)}\n${format(values[i])}"
                ghost?.getOrNull(i)?.let { "$line\n${fillPattern(previousLabel, format(it))}" } ?: line
            },
            description = barsDescription(title, labels, values, format),
        )
        ChartLegend(
            listOfNotNull(
                stringResource(Res.string.progress_legend_this_period) to MaterialTheme.colorScheme.primary,
                ghost?.let { stringResource(Res.string.progress_legend_previous_period) to MaterialTheme.colorScheme.outline },
            ),
            outlinedLast = ghost != null,
        )
    }
}

@Composable
private fun bucketSubtitle(stats: OverviewStats): String = stringResource(
    if (stats.buckets.any { it.window.length > 7 }) {
        Res.string.progress_chart_per_week_of_4
    } else {
        Res.string.progress_chart_per_week
    },
)

@Composable
private fun WeeklyCountCard(stats: OverviewStats, dates: DateFormat, sessions: Boolean) {
    val title = stringResource(
        if (sessions) Res.string.progress_chart_sessions_week else Res.string.progress_chart_sets_week
    )
    val metric = if (sessions) Metric.FREQUENCY else Metric.EFFECTIVE_SETS
    fun value(
        bucket: StatsBucket
    ) = bucket.perWeek((if (sessions) bucket.sessions else bucket.effectiveSets).toDouble())
    val values = stats.buckets.map(::value)
    val ghost = stats.previousBuckets.map(::value).takeIf { it.isNotEmpty() }
    val labels = labels(stats.buckets, dates)
    val unit = stringResource(if (sessions) Res.string.progress_axis_sessions else Res.string.progress_axis_sets)
    val previousLabel = stringResource(Res.string.progress_chart_previous)
    val format: (Double) -> String = { "${formatNumber(it, if (it % 1.0 == 0.0) 0 else 1)} $unit" }
    StatCard(title, metric = metric, subtitle = bucketSubtitle(stats)) {
        if (values.all { it <= 0 } && ghost.orEmpty().all { it <= 0 }) {
            ChartEmpty(
                stringResource(
                    if (sessions) Res.string.progress_chart_sessions_empty else Res.string.progress_chart_sets_empty
                )
            )
            return@StatCard
        }
        BarChart(
            values = values,
            labels = labels,
            format = { formatNumber(it, if (it % 1.0 == 0.0) 0 else 1) },
            unit = unit,
            ghost = ghost,
            color = if (sessions) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
            tooltip = { i ->
                val window = stats.buckets[i].window
                val line = "${dates.range(window.first, window.last)}\n${format(values[i])}"
                ghost?.getOrNull(i)?.let { "$line\n${fillPattern(previousLabel, format(it))}" } ?: line
            },
            description = barsDescription(title, labels, values, format),
        )
    }
}

@Composable
private fun HeatmapCard(state: OverviewState, dates: DateFormat) {
    val heatmap = state.heatmap ?: return
    val title = stringResource(Res.string.progress_chart_heatmap)
    val weeks = heatmap.weeks.size
    val subtitle = pluralStringResource(
        Res.plurals.progress_heatmap_days,
        heatmap.trainingDays,
        heatmap.trainingDays,
        weeks
    )
    val sessionsPattern = stringResource(Res.string.progress_heatmap_tooltip)
    val restPattern = stringResource(Res.string.progress_heatmap_tooltip_rest)
    // "1 session" / "2 sessions" for every count a cell can show (the tooltip is built outside composition).
    val maxSessions = heatmap.weeks.maxOfOrNull { week -> week.maxOfOrNull { it.sessions } ?: 0 } ?: 0
    val sessionLabels = (1..maxSessions).map { pluralStringResource(Res.plurals.progress_session_count, it, it) }
    StatCard(title, metric = Metric.TRAINING_HEATMAP, subtitle = subtitle) {
        HeatmapChart(
            heatmap = heatmap,
            tooltip = { cell ->
                val day = dates.epochDayFull(cell.day)
                if (cell.sessions == 0) {
                    "$day\n$restPattern"
                } else {
                    val sessions = sessionLabels.getOrNull(cell.sessions - 1) ?: cell.sessions.toString()
                    "$day\n" + fillPattern(sessionsPattern, sessions, formatNumber(cell.effectiveSets.toDouble(), 0))
                }
            },
            description = "$title. $subtitle",
        )
    }
}

@Composable
private fun DurationCard(stats: OverviewStats, dates: DateFormat) {
    val title = stringResource(Res.string.progress_chart_duration)
    val timeZone = TimeZone.currentSystemDefault()
    val buckets = stats.buckets.filter { it.averageDurationMillis != null }
    val points = buckets.map { b ->
        ChartPoint(
            LocalDate.fromEpochDays(b.window.first.toLong()).startOfDayMillis(timeZone),
            b.averageDurationMillis!! / MINUTE_MS
        )
    }
    val minutes = stringResource(Res.string.progress_axis_minutes)
    StatCard(
        title,
        metric = Metric.SESSION_DURATION,
        subtitle = stringResource(Res.string.progress_chart_duration_subtitle)
    ) {
        if (points.size < 2) {
            ChartEmpty(stringResource(Res.string.progress_chart_duration_empty))
            return@StatCard
        }
        LineChart(
            series = listOf(LineSeries(points, MaterialTheme.colorScheme.primary)),
            format = { formatNumber(it, 0) },
            unit = minutes,
            dateLabel = { dates.short(it) },
            tooltip = { _, p ->
                val b = buckets[p]
                "${dates.range(b.window.first, b.window.last)}\n${dates.duration(b.averageDurationMillis ?: 0.0)}"
            },
            description = lineDescription(
                title,
                dates.short(points.first().x),
                dates.short(points.last().x),
                points.map { it.y },
            ) { dates.duration(it * MINUTE_MS) },
            includeZero = true,
        )
    }
}

private const val MINUTE_MS = 60_000.0

@Composable
private fun AdherenceCard(adherence: Adherence?, hasActiveRoutine: Boolean) {
    StatCard(stringResource(Res.string.progress_chart_adherence), metric = Metric.ADHERENCE) {
        if (adherence == null) {
            ChartEmpty(
                stringResource(
                    if (hasActiveRoutine) Res.string.progress_adherence_no_plan else Res.string.progress_adherence_no_routine
                ),
            )
            return@StatCard
        }
        Text(Numbers.percent(adherence.percent), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (adherence.percent / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                Res.string.progress_adherence_detail,
                formatNumber(adherence.loggedPerWeek, 1),
                formatNumber(adherence.plannedPerWeek, 1),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
