package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.domain.usecases.stats.Comparison
import es.sebas1705.ironlog.domain.usecases.stats.Metric
import es.sebas1705.ironlog.domain.usecases.stats.StatsPeriod
import es.sebas1705.ironlog.feature.progress.resources.*
import org.jetbrains.compose.resources.stringResource

/** Cards narrower than this share a row: one column on phones, two or more on tablets. */
private val MIN_COLUMN_WIDTH = 340.dp

/**
 * The scrolling body of a statistics tab: a staggered grid of cards that becomes two columns on wide
 * screens. Use [fullWidth] for headers and wide content.
 */
@Composable
internal fun StatsGrid(modifier: Modifier = Modifier, content: LazyStaggeredGridScope.() -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(MIN_COLUMN_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        content = content,
    )
}

internal fun LazyStaggeredGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }

/** A titled card with an optional ⓘ for [metric] and a [subtitle] line. */
@Composable
internal fun StatCard(
    title: String,
    modifier: Modifier = Modifier,
    metric: Metric? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                metric?.let { InfoButton(it, label = title) }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(end = 12.dp), content = content)
        }
    }
}

/** Period chips (4 weeks / 3 months / 12 months / all). */
@Composable
internal fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(periodLabel(period)) },
            )
        }
    }
}

@Composable
internal fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.FOUR_WEEKS -> Res.string.progress_period_4w
        StatsPeriod.THREE_MONTHS -> Res.string.progress_period_3m
        StatsPeriod.TWELVE_MONTHS -> Res.string.progress_period_12m
        StatsPeriod.ALL -> Res.string.progress_period_all
    },
)

/** Tiles in rows of two on phones and three on wider screens. */
@Composable
internal fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 560.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * A comparison tile: [title] with ⓘ, the [value] and, when there is a comparison period, the change as
 * "▲ +3 (+20 %)" in a neutral colour (a change is described, not judged).
 */
@Composable
internal fun ComparisonTile(
    title: String,
    value: String,
    comparison: Comparison?,
    formatDelta: (Double) -> String,
    metric: Metric,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    /** Shown when there is neither a change nor a [footnote]; "No previous period" by default. */
    noComparison: String? = null,
) {
    val change = comparison?.let { changeText(it, formatDelta) } ?: footnote
    val description = listOfNotNull(title, value, change).joinToString(", ")
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                )
                InfoButton(metric, label = title)
            }
            // Long values ("−0.17 kg/week") shrink to fit the tile instead of being cut.
            BasicText(
                value,
                style = MaterialTheme.typography.headlineSmall.tabularFigures().copy(color = LocalContentColor.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                ),
                modifier = Modifier.padding(end = 12.dp).clearAndSetSemantics { },
            )
            Text(
                change ?: noComparison ?: stringResource(Res.string.progress_change_none),
                style = MaterialTheme.typography.bodySmall.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(end = 12.dp).clearAndSetSemantics { },
            )
        }
    }
}

/** "▲ +2.5 kg (+4 %)", "▼ −1 (−10 %)", "= no change" or "▲ +3 (new)" when the previous value was 0. */
@Composable
internal fun changeText(comparison: Comparison, formatDelta: (Double) -> String): String? {
    val delta = comparison.delta ?: return null
    val percent = comparison.percent
    return when (comparison.direction) {
        Comparison.Direction.NONE -> null
        Comparison.Direction.SAME -> stringResource(Res.string.progress_change_same)
        else -> {
            val arrow = if (comparison.direction == Comparison.Direction.UP) "▲" else "▼"
            val amount = signed(delta, formatDelta)
            if (percent == null) {
                stringResource(Res.string.progress_change_from_zero, arrow, amount)
            } else {
                stringResource(Res.string.progress_change_value, arrow, amount, signedPercent(percent))
            }
        }
    }
}

/** "+2.5 kg" / "−2.5 kg". */
internal fun signed(value: Double, format: (Double) -> String): String =
    (if (value > 0) "+" else if (value < 0) "−" else "") + format(kotlin.math.abs(value))

internal fun signedPercent(percent: Double): String = signed(percent) { formatNumber(it, 0) + " %" }
