package es.sebas1705.haltera.feature.progress.ui.measurements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.HealthDisclaimer
import es.sebas1705.haltera.core.ui.components.HealthDisclaimerKind
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.units.BodyFat
import es.sebas1705.haltera.core.units.LengthUnit
import es.sebas1705.haltera.core.units.Lengths
import es.sebas1705.haltera.core.units.Numbers
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.domain.usecases.stats.Metric
import es.sebas1705.haltera.feature.progress.model.ChartPoint
import es.sebas1705.haltera.feature.progress.model.MeasurementMath
import es.sebas1705.haltera.feature.progress.model.MeasurementSeries
import es.sebas1705.haltera.feature.progress.resources.*
import es.sebas1705.haltera.feature.progress.ui.components.ChartEmpty
import es.sebas1705.haltera.feature.progress.ui.components.ComparisonTile
import es.sebas1705.haltera.feature.progress.ui.components.DateField
import es.sebas1705.haltera.feature.progress.ui.components.DateFormat
import es.sebas1705.haltera.feature.progress.ui.components.LineChart
import es.sebas1705.haltera.feature.progress.ui.components.LineSeries
import es.sebas1705.haltera.feature.progress.ui.components.StatCard
import es.sebas1705.haltera.feature.progress.ui.components.StatsGrid
import es.sebas1705.haltera.feature.progress.ui.components.TileGrid
import es.sebas1705.haltera.feature.progress.ui.components.fullWidth
import es.sebas1705.haltera.feature.progress.ui.components.lineDescription
import es.sebas1705.haltera.feature.progress.ui.components.rememberDateFormat
import es.sebas1705.haltera.feature.progress.ui.components.signed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Body measurements: one chart per type or custom name, with its latest value, 30-day change and entries. */
@Composable
internal fun MeasurementsTab(viewModel: MeasurementsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (MeasurementsIntent) -> Unit = { viewModel.onEvent(it) }
    if (state.loading) {
        LoadingScreen()
        return
    }
    val dates = rememberDateFormat()
    val selected = state.selected
    StatsGrid {
        fullWidth("header") { Header(state, onEvent) }
        if (selected != null) {
            item("summary") { SummaryTiles(selected, state.unit, dates) }
            item("chart") { SeriesChart(selected, state.unit, dates) }
            fullWidth("entries") { EntriesCard(selected, state.unit, dates, onEvent) }
            fullWidth("disclaimer") { HealthDisclaimer(HealthDisclaimerKind.STATISTICS) }
        }
    }
    if (state.dialog != null) MeasurementDialogContent(state, onEvent)
}

@Composable
private fun Header(state: MeasurementsState, onEvent: (MeasurementsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.series.isEmpty()) {
            ChartEmpty(stringResource(Res.string.progress_m_empty))
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.series.forEach { series ->
                    val label = seriesLabel(series)
                    val description = stringResource(Res.string.progress_m_series_label, label)
                    FilterChip(
                        selected = series.key == state.selectedKey,
                        onClick = { onEvent(MeasurementsIntent.SelectSeries(series.key)) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
            }
        }
        Button(onClick = { onEvent(MeasurementsIntent.OpenAdd) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(Res.string.progress_m_log), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SummaryTiles(series: MeasurementSeries, unit: LengthUnit, dates: DateFormat) {
    val latest = series.latest
    TileGrid(
        listOf(
            { m ->
                ComparisonTile(
                    stringResource(Res.string.progress_m_latest_title),
                    measurementValue(latest.value, series.type, unit),
                    null,
                    { it.toString() },
                    Metric.BODY_MEASUREMENT_CHANGE,
                    m,
                    footnote = stringResource(Res.string.progress_m_latest_date, dates.full(latest.measuredAt)),
                )
            },
            { m ->
                val change = series.change30Days
                ComparisonTile(
                    stringResource(Res.string.progress_metric_m_change_title),
                    change?.let { signedValue(it, series.type, unit) } ?: "—",
                    null,
                    { it.toString() },
                    Metric.BODY_MEASUREMENT_CHANGE,
                    m,
                    footnote = if (change == null) stringResource(Res.string.progress_m_change_none) else null,
                    noComparison = "",
                )
            },
        ),
    )
}

@Composable
private fun SeriesChart(series: MeasurementSeries, unit: LengthUnit, dates: DateFormat) {
    val title = seriesLabel(series)
    val label = unitLabel(series.type, unit)
    val points = series.points.map { ChartPoint(it.x, MeasurementMath.toShown(it.y, series.type, unit)) }
    val decimals = if (series.type.isLength) Lengths.decimals(unit) else 1
    val format: (Double) -> String = { "${Numbers.format(it, decimals)} $label" }
    StatCard(title, subtitle = stringResource(Res.string.progress_m_chart_subtitle, label)) {
        if (points.size < 2) {
            ChartEmpty(stringResource(Res.string.progress_m_chart_empty))
            return@StatCard
        }
        val line = LineSeries(points, MaterialTheme.colorScheme.primary, title)
        LineChart(
            series = listOf(line),
            format = { Numbers.format(it, decimals) },
            unit = label,
            dateLabel = { dates.short(it) },
            tooltip = { _, p -> "${dates.short(points[p].x)}\n${format(points[p].y)}" },
            description = lineDescription(
                title,
                dates.short(points.first().x),
                dates.short(points.last().x),
                points.map { it.y },
                format,
            ),
        )
    }
}

@Composable
private fun EntriesCard(
    series: MeasurementSeries,
    unit: LengthUnit,
    dates: DateFormat,
    onEvent: (MeasurementsIntent) -> Unit,
) {
    StatCard(stringResource(Res.string.progress_m_entries)) {
        series.entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().clickable {
                    onEvent(
                        MeasurementsIntent.OpenEdit(entry)
                    )
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(measurementValue(entry.value, entry.type, unit), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(
                            dates.full(entry.measuredAt),
                            entry.notes
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onEvent(MeasurementsIntent.Delete(entry.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.progress_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasurementDialogContent(state: MeasurementsState, onEvent: (MeasurementsIntent) -> Unit) {
    val dialog = state.dialog ?: return
    val form = state.form
    AlertDialog(
        onDismissRequest = { onEvent(MeasurementsIntent.Dismiss) },
        title = {
            Text(
                stringResource(
                    if (dialog is MeasurementDialog.Edit) Res.string.progress_m_edit_title else Res.string.progress_m_add_title,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.progress_m_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasurementType.entries.forEach { type ->
                        FilterChip(
                            selected = form.type == type,
                            onClick = { onEvent(MeasurementsIntent.TypeChanged(type)) },
                            label = { Text(typeLabel(type)) },
                        )
                    }
                }
                if (form.type == MeasurementType.CUSTOM) CustomName(state, onEvent)
                ValueField(form, state.unit, onEvent)
                DateField(form.dateUtcMillis, form.maxDateUtcMillis) { onEvent(MeasurementsIntent.DateChanged(it)) }
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onEvent(MeasurementsIntent.NotesChanged(it)) },
                    label = { Text(stringResource(Res.string.progress_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(MeasurementsIntent.Save) }
            ) { Text(stringResource(Res.string.progress_save)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dialog is MeasurementDialog.Edit) {
                    TextButton(onClick = { onEvent(MeasurementsIntent.Delete(dialog.measurement.id)) }) {
                        Text(stringResource(Res.string.progress_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = { onEvent(MeasurementsIntent.Dismiss) }
                ) { Text(stringResource(Res.string.progress_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomName(state: MeasurementsState, onEvent: (MeasurementsIntent) -> Unit) {
    val form = state.form
    OutlinedTextField(
        value = form.customName,
        onValueChange = { onEvent(MeasurementsIntent.CustomNameChanged(it)) },
        label = { Text(stringResource(Res.string.progress_m_name)) },
        placeholder = { Text(stringResource(Res.string.progress_m_name_hint)) },
        isError = form.nameError,
        supportingText = if (form.nameError) {
            { Text(stringResource(Res.string.progress_m_name_invalid)) }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        ),
    )
    val others = state.customNames.filterNot { BodyMeasurements.nameKey(it) == BodyMeasurements.nameKey(form.customName) }
    if (others.isNotEmpty()) {
        Text(stringResource(Res.string.progress_m_names_used), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { name ->
                AssistChip(onClick = { onEvent(MeasurementsIntent.CustomNameChanged(name)) }, label = { Text(name) })
            }
        }
    }
}

@Composable
private fun ValueField(form: MeasurementForm, unit: LengthUnit, onEvent: (MeasurementsIntent) -> Unit) {
    val label = unitLabel(form.type, unit)
    val max = measurementValue(BodyMeasurements.maxValue(form.type), form.type, unit)
    OutlinedTextField(
        value = form.input,
        onValueChange = { onEvent(MeasurementsIntent.InputChanged(it)) },
        label = { Text(stringResource(Res.string.progress_m_field, label)) },
        isError = form.valueError,
        supportingText = if (form.valueError) {
            { Text(stringResource(Res.string.progress_m_invalid, max)) }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

/** The type's name, or the custom name of a custom series. */
@Composable
internal fun seriesLabel(series: MeasurementSeries): String =
    if (series.type == MeasurementType.CUSTOM) series.customName else typeLabel(series.type)

@Composable
internal fun typeLabel(type: MeasurementType): String = stringResource(
    when (type) {
        MeasurementType.WAIST -> Res.string.progress_m_type_waist
        MeasurementType.CHEST -> Res.string.progress_m_type_chest
        MeasurementType.HIPS -> Res.string.progress_m_type_hips
        MeasurementType.ARM -> Res.string.progress_m_type_arm
        MeasurementType.THIGH -> Res.string.progress_m_type_thigh
        MeasurementType.NECK -> Res.string.progress_m_type_neck
        MeasurementType.BODY_FAT -> Res.string.progress_m_type_body_fat
        MeasurementType.CUSTOM -> Res.string.progress_m_type_custom
    },
)

/** "cm", "in" or "%". */
@Composable
internal fun unitLabel(type: MeasurementType, unit: LengthUnit): String = stringResource(
    when {
        !type.isLength -> Res.string.progress_unit_percent
        unit == LengthUnit.CM -> Res.string.progress_unit_cm
        else -> Res.string.progress_unit_in
    },
)

/** "82.5 cm", "32.48 in" or "18.5 %" (the stored value in the shown unit). */
@Composable
internal fun measurementValue(value: Long, type: MeasurementType, unit: LengthUnit): String =
    if (type.isLength) {
        stringResource(Res.string.progress_weight_value, Lengths.format(value, unit), unitLabel(type, unit))
    } else {
        Numbers.percent(BodyFat.toPercent(value), maxDecimals = 1, typographicMinus = false)
    }

/** "+1.5 cm" / "−0.3 %". */
@Composable
private fun signedValue(delta: Long, type: MeasurementType, unit: LengthUnit): String {
    val label = unitLabel(type, unit)
    val decimals = if (type.isLength) Lengths.decimals(unit) else 1
    return signed(MeasurementMath.toShown(delta.toDouble(), type, unit)) {
        if (type.isLength) "${Numbers.format(it, decimals)} $label" else Numbers.percent(it, maxDecimals = 1)
    }
}
