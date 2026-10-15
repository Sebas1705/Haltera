package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.runtime.Composable
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_bars_description
import es.sebas1705.ironlog.feature.progress.resources.progress_chart_line_description
import org.jetbrains.compose.resources.stringResource

/** Screen-reader summary of a bar chart: count, range, highest and latest bar. */
@Composable
internal fun barsDescription(
    title: String,
    labels: List<String>,
    values: List<Double>,
    format: (Double) -> String
): String {
    if (values.isEmpty()) return title
    val highest = values.indices.maxBy { values[it] }
    return stringResource(
        Res.string.progress_chart_bars_description,
        title,
        values.size,
        labels.first(),
        labels.last(),
        format(values[highest]),
        labels[highest],
        format(values.last()),
    )
}

/** Screen-reader summary of a line: count, dates, first, latest, lowest and highest value. */
@Composable
internal fun lineDescription(
    title: String,
    firstDate: String,
    lastDate: String,
    values: List<Double>,
    format: (Double) -> String
): String {
    if (values.isEmpty()) return title
    return stringResource(
        Res.string.progress_chart_line_description,
        title,
        values.size,
        firstDate,
        lastDate,
        format(values.first()),
        format(values.last()),
        format(values.min()),
        format(values.max()),
    )
}
