package es.sebas1705.ironlog.feature.nutrition.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_abbr_carbs
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_abbr_fat
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_abbr_protein
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_carbs
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fat
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_protein
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_range_month
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_range_quarter
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_range_week
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_adherence
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_adherence_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_adherence_no_goal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_adherence_value
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_average
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_average_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_days_logged
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_donut
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_donut_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_empty
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_goal_line
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_kcal_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_macros
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_macros_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_not_enough
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_protein_kg
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_protein_kg_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_protein_kg_no_weight
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_protein_kg_value
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_trend
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_trend_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_trend_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_trend_weight
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_week_of
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_weekly
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_weekly_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats_within
import es.sebas1705.ironlog.feature.nutrition.ui.components.MacroColors
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.ironlog.feature.nutrition.ui.components.SectionCard
import es.sebas1705.ironlog.feature.nutrition.ui.components.gramsText
import es.sebas1705.ironlog.feature.nutrition.ui.components.kcalText
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import es.sebas1705.ironlog.feature.nutrition.ui.components.shortDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NutritionScaffold(title = stringResource(Res.string.nutrition_stats), onBack = onBack) { modifier ->
        if (state.loading) {
            LoadingScreen(modifier)
            return@NutritionScaffold
        }
        BoxWithConstraints(modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RangeSelector(state.range) { viewModel.onEvent(StatsIntent.SetRange(it)) }
                if (state.daily.isEmpty()) {
                    Text(
                        stringResource(Res.string.nutrition_stats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                    return@Column
                }
                val cards: List<@Composable () -> Unit> = listOf(
                    { AverageCard(state) },
                    { KcalCard(state) },
                    { MacrosCard(state) },
                    { DonutCard(state) },
                    { AdherenceCard(state) },
                    { ProteinCard(state) },
                    { TrendCard(state) },
                    { WeeklyCard(state) },
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { cards.filterIndexed { i, _ -> i % 2 == 0 }.forEach { it() } }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { cards.filterIndexed { i, _ -> i % 2 == 1 }.forEach { it() } }
                    }
                } else {
                    cards.forEach { it() }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(range: StatsRange, onChange: (StatsRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { index, r ->
            SegmentedButton(
                selected = r == range,
                onClick = { onChange(r) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsRange.entries.size)
            ) {
                Text(
                    stringResource(
                        when (r) {
                            StatsRange.WEEK -> Res.string.nutrition_range_week
                            StatsRange.MONTH -> Res.string.nutrition_range_month
                            StatsRange.QUARTER -> Res.string.nutrition_range_quarter
                        },
                    ),
                )
            }
        }
    }
}

private fun StatsState.dates(): List<LocalDate> = List(range.days) { from.plus(DatePeriod(days = it)) }

@Composable
private fun AverageCard(state: StatsState) {
    val average = state.average ?: return
    SectionCard(
        stringResource(Res.string.nutrition_stats_average),
        info = stringResource(Res.string.nutrition_stats_average_info)
    ) {
        Text(
            kcalText(average.kcal),
            style = MaterialTheme.typography.headlineMedium.tabularFigures(),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(Res.string.nutrition_stats_days_logged, state.daily.size, state.range.days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MacroFigure(
                stringResource(Res.string.nutrition_protein),
                gramsText(average.protein, 0),
                Modifier.weight(1f)
            )
            MacroFigure(stringResource(Res.string.nutrition_carbs), gramsText(average.carbs, 0), Modifier.weight(1f))
            MacroFigure(stringResource(Res.string.nutrition_fat), gramsText(average.fat, 0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabularFigures())
    }
}

@Composable
private fun KcalCard(state: StatsState) {
    val title = stringResource(Res.string.nutrition_stats_kcal)
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_kcal_info)) {
        KcalBarsChart(state.dates(), state.daily.associate { it.date to it.nutrients.kcal }, state.goals.kcal, title)
        LegendRow(
            buildList {
                add(MacroColors.kcal to stringResource(Res.string.nutrition_kcal))
                val goal = state.goals.kcal
                if (goal != null) {
                    add(MacroColors.withinGoal to stringResource(Res.string.nutrition_stats_within))
                    add(
                        MaterialTheme.colorScheme.onSurface to stringResource(Res.string.nutrition_stats_goal_line, goal)
                    )
                }
            },
        )
    }
}

@Composable
private fun MacrosCard(state: StatsState) {
    val title = stringResource(Res.string.nutrition_stats_macros)
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_macros_info)) {
        StackedMacroChart(state.dates(), state.daily.associate { it.date to it.nutrients }, title)
        LegendRow(
            listOf(
                MacroColors.protein to stringResource(Res.string.nutrition_protein),
                MacroColors.carbs to stringResource(Res.string.nutrition_carbs),
                MacroColors.fat to stringResource(Res.string.nutrition_fat),
            ),
        )
    }
}

@Composable
private fun DonutCard(state: StatsState) {
    val split = state.split ?: return
    val title = stringResource(Res.string.nutrition_stats_donut)
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_donut_info)) {
        MacroDonut(
            split,
            listOf(
                stringResource(Res.string.nutrition_protein),
                stringResource(Res.string.nutrition_carbs),
                stringResource(Res.string.nutrition_fat)
            ),
            title,
        )
    }
}

@Composable
private fun AdherenceCard(state: StatsState) {
    SectionCard(
        stringResource(Res.string.nutrition_stats_adherence),
        info = stringResource(Res.string.nutrition_stats_adherence_info)
    ) {
        val adherence = state.adherence
        if (adherence == null) {
            Text(
                stringResource(Res.string.nutrition_stats_adherence_no_goal),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                Amounts.format(adherence.fraction * 100, 0) + " %",
                style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(Res.string.nutrition_stats_adherence_value, adherence.daysWithin, adherence.daysLogged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProteinCard(state: StatsState) {
    SectionCard(
        stringResource(Res.string.nutrition_stats_protein_kg),
        info = stringResource(Res.string.nutrition_stats_protein_kg_info)
    ) {
        val perKg = state.proteinPerKg
        val weight = state.latestWeightGrams
        if (perKg == null || weight == null) {
            Text(
                stringResource(Res.string.nutrition_stats_protein_kg_no_weight),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val lang = language()
            Text(
                Amounts.format(perKg, 2, lang) + " g/kg",
                style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(
                    Res.string.nutrition_stats_protein_kg_value,
                    gramsText(state.average?.protein ?: 0.0, 0),
                    Amounts.format(weight / 1_000.0, 1, lang),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendCard(state: StatsState) {
    val title = stringResource(Res.string.nutrition_stats_trend)
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_trend_info)) {
        if (state.trend.kcal.size < 2) {
            Text(stringResource(Res.string.nutrition_stats_not_enough), style = MaterialTheme.typography.bodyMedium)
        } else {
            DualAxisChart(
                kcal = state.trend.kcal,
                weight = state.trend.weightKg,
                kcalLabel = stringResource(Res.string.nutrition_stats_trend_kcal),
                weightLabel = stringResource(Res.string.nutrition_stats_trend_weight),
                description = title,
            )
        }
    }
}

@Suppress("LongMethod") // A small table: header and rows.
@Composable
private fun WeeklyCard(state: StatsState) {
    SectionCard(
        stringResource(Res.string.nutrition_stats_weekly),
        info = stringResource(Res.string.nutrition_stats_weekly_info)
    ) {
        val header = MaterialTheme.typography.labelMedium
        val cell = MaterialTheme.typography.bodyMedium.tabularFigures()
        Row {
            Text("", style = header, modifier = Modifier.weight(1.4f))
            Text(
                stringResource(Res.string.nutrition_kcal),
                style = header,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                stringResource(Res.string.nutrition_abbr_protein),
                style = header,
                color = MacroColors.protein,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.End
            )
            Text(
                stringResource(Res.string.nutrition_abbr_carbs),
                style = header,
                color = MacroColors.carbs,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.End
            )
            Text(
                stringResource(Res.string.nutrition_abbr_fat),
                style = header,
                color = MacroColors.fat,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.End
            )
        }
        state.weekly.asReversed().forEach { week ->
            HorizontalDivider()
            Row {
                Column(Modifier.weight(1.4f)) {
                    Text(
                        stringResource(Res.string.nutrition_stats_week_of, shortDate(week.weekStart)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(Res.string.nutrition_stats_days_logged, week.daysLogged, 7),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Amounts.format(week.kcal, 0),
                    style = cell,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Text(
                    Amounts.format(week.protein, 0),
                    style = cell,
                    modifier = Modifier.weight(0.8f),
                    textAlign = TextAlign.End
                )
                Text(
                    Amounts.format(week.carbs, 0),
                    style = cell,
                    modifier = Modifier.weight(0.8f),
                    textAlign = TextAlign.End
                )
                Text(
                    Amounts.format(week.fat, 0),
                    style = cell,
                    modifier = Modifier.weight(0.8f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
