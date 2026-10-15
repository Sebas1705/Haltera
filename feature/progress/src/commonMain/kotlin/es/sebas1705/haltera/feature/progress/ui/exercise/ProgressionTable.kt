package es.sebas1705.haltera.feature.progress.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.ui.components.PrBadge
import es.sebas1705.haltera.core.ui.theme.HalteraTheme
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.domain.usecases.stats.BestSet
import es.sebas1705.haltera.domain.usecases.stats.ExerciseRecords
import es.sebas1705.haltera.domain.usecases.stats.Metric
import es.sebas1705.haltera.domain.usecases.stats.ProgressionRow
import es.sebas1705.haltera.domain.usecases.stats.Record
import es.sebas1705.haltera.feature.progress.model.ChartScale
import es.sebas1705.haltera.feature.progress.resources.*
import es.sebas1705.haltera.feature.progress.ui.components.ChartEmpty
import es.sebas1705.haltera.feature.progress.ui.components.DateFormat
import es.sebas1705.haltera.feature.progress.ui.components.StatCard
import es.sebas1705.haltera.feature.progress.ui.components.UnitFormat
import es.sebas1705.haltera.feature.progress.ui.components.formatNumber
import es.sebas1705.haltera.feature.progress.ui.components.signed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val COLLAPSED_ROWS = 8
private const val DATE_WEIGHT = 1.1f
private const val SET_WEIGHT = 1.3f
private const val NUMBER_WEIGHT = 1f
private const val DELTA_WEIGHT = 1.1f
private const val PR_WEIGHT = 0.7f

/** "102.5 × 5", or "15 reps" for an unloaded set. */
@Composable
private fun bestSetText(set: BestSet?, units: UnitFormat): String = when {
    set == null -> "—"
    set.loadGrams == null || set.loadGrams == 0L -> pluralStringResource(Res.plurals.progress_reps, set.reps, set.reps)
    else -> stringResource(
        Res.string.progress_set_value,
        ChartScale.trim(units.toUnit(set.loadGrams!!.toDouble()), 2),
        set.reps
    )
}

/** One row per session, newest first: date, best set, e1RM, volume, change of e1RM and PR mark. */
@Composable
internal fun ProgressionTableCard(rows: List<ProgressionRow>, units: UnitFormat, dates: DateFormat) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    StatCard(
        stringResource(Res.string.progress_table_title),
        metric = Metric.PROGRESSION_TABLE,
        subtitle = stringResource(Res.string.progress_table_subtitle, units.label),
    ) {
        if (rows.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_table_empty))
            return@StatCard
        }
        TableHeader()
        HorizontalDivider()
        val shown = if (expanded) rows else rows.take(COLLAPSED_ROWS)
        shown.forEachIndexed { index, row ->
            TableRow(row, units, dates)
            if (index < shown.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
        if (rows.size > COLLAPSED_ROWS) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(
                    if (expanded) {
                        stringResource(Res.string.progress_table_show_less)
                    } else {
                        stringResource(Res.string.progress_table_show_all, rows.size)
                    },
                )
            }
        }
    }
}

@Composable
private fun TableHeader() {
    val style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(stringResource(Res.string.progress_table_date), DATE_WEIGHT, style, TextAlign.Start)
        Cell(stringResource(Res.string.progress_table_best), SET_WEIGHT, style, TextAlign.Start)
        Cell(stringResource(Res.string.progress_table_e1rm), NUMBER_WEIGHT, style)
        Cell(stringResource(Res.string.progress_table_volume), NUMBER_WEIGHT, style)
        Cell(stringResource(Res.string.progress_table_delta), DELTA_WEIGHT, style)
        Cell("", PR_WEIGHT, style)
    }
}

@Composable
private fun TableRow(row: ProgressionRow, units: UnitFormat, dates: DateFormat) {
    val style = MaterialTheme.typography.bodySmall.tabularFigures()
    val date = dates.short(row.at)
    val best = bestSetText(row.bestSet, units)
    val oneRm = if (row.oneRm > 0) formatNumber(units.toUnit(row.oneRm.toDouble()), 1) else "—"
    val volume = if (row.volume > 0) formatNumber(units.toUnit(row.volume.toDouble()), 0) else "—"
    val delta = row.oneRmDelta?.let { d ->
        val arrow = when {
            d > 0 -> "▲ "
            d < 0 -> "▼ "
            else -> "= "
        }
        arrow + signed(units.toUnit(d.toDouble())) { formatNumber(it, 1) }
    } ?: "—"
    val pr = row.prs.isNotEmpty()
    val description = stringResource(Res.string.progress_table_row_description, date, best, oneRm, volume, delta) +
        if (pr) ", " + stringResource(Res.string.progress_table_pr) else ""
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {
            contentDescription = description
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(date, DATE_WEIGHT, style, TextAlign.Start)
        Cell(best, SET_WEIGHT, style.copy(color = MaterialTheme.colorScheme.onSurface), TextAlign.Start)
        Cell(oneRm, NUMBER_WEIGHT, style)
        Cell(volume, NUMBER_WEIGHT, style)
        Cell(delta, DELTA_WEIGHT, style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Box(Modifier.weight(PR_WEIGHT), contentAlignment = Alignment.CenterEnd) { if (pr) PrBadge() }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, style: TextStyle, align: TextAlign = TextAlign.End) {
    Text(
        text,
        style = style,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp)
    )
}

/** Records of the variant (Metric.PERSONAL_RECORDS) and the reps-at-a-load list. */
@Composable
internal fun RecordsCard(records: ExerciseRecords?, units: UnitFormat, dates: DateFormat) {
    StatCard(stringResource(Res.string.progress_records_title), metric = Metric.PERSONAL_RECORDS) {
        val rows = listOfNotNull(
            records?.heaviestLoad?.let {
                Triple(stringResource(Res.string.progress_record_heaviest), it, units.weight(it.value.toDouble(), 2))
            },
            records?.bestOneRm?.let {
                Triple(stringResource(Res.string.progress_record_one_rm), it, units.weight(it.value.toDouble()))
            },
            records?.bestVolume?.let {
                Triple(stringResource(Res.string.progress_record_volume), it, units.volume(it.value.toDouble()))
            },
        )
        if (rows.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_no_records))
            return@StatCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (label, record, value) -> RecordRow(label, record, value, dates) }
        }
        val reps = records?.repsAtLoad.orEmpty()
        if (reps.isNotEmpty()) {
            Text(
                stringResource(Res.string.progress_record_reps_at_load),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            reps.forEach { r ->
                RecordRow(
                    units.weight(r.loadGrams.toDouble(), 2),
                    Record(r.reps.toLong(), r.sessionId, r.at),
                    pluralStringResource(Res.plurals.progress_reps, r.reps, r.reps),
                    dates,
                )
            }
        }
    }
}

@Composable
private fun RecordRow(label: String, record: Record, value: String, dates: DateFormat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                dates.full(record.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(value, style = MaterialTheme.typography.titleMedium.tabularFigures(), color = HalteraTheme.colors.prGold)
    }
}
