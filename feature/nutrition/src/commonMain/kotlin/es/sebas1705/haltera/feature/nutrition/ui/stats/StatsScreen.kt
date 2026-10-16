package es.sebas1705.haltera.feature.nutrition.ui.stats

import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_trend_kcal
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_trend_weight
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_trend_no_weight
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_chart_trend_description
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_chart_donut_description
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_chart_bars_description
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_g_per_kg
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_unit_kg
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_unit_kcal
import es.sebas1705.haltera.domain.usecases.nutrition.NutritionStats
import es.sebas1705.haltera.domain.usecases.nutrition.GoalCalculator
import es.sebas1705.haltera.data.models.Nutrients
import org.jetbrains.compose.resources.pluralStringResource
import es.sebas1705.haltera.feature.nutrition.ui.components.nutritionContent
import es.sebas1705.haltera.feature.nutrition.ui.components.isTwoColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.feature.nutrition.model.Amounts
import es.sebas1705.haltera.feature.nutrition.resources.Res
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_abbr_carbs
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_abbr_fat
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_abbr_protein
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_carbs
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_fat
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_kcal
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_protein
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_range_month
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_range_quarter
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_range_week
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_adherence
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_adherence_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_adherence_no_goal
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_adherence_value
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_average
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_average_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_days_logged
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_donut
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_donut_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_empty
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_goal_line
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_kcal
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_kcal_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_macros
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_macros_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_not_enough
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_protein_kg
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_protein_kg_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_protein_kg_no_weight
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_protein_kg_value
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_trend
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_trend_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_week_of
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_weekly
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_weekly_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_within
import es.sebas1705.haltera.feature.nutrition.ui.components.MacroColors
import es.sebas1705.haltera.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.haltera.feature.nutrition.ui.components.SectionCard
import es.sebas1705.haltera.feature.nutrition.ui.components.gramsText
import es.sebas1705.haltera.feature.nutrition.ui.components.kcalText
import es.sebas1705.haltera.feature.nutrition.ui.components.language
import es.sebas1705.haltera.feature.nutrition.ui.components.shortDate
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
        Box(modifier.fillMaxSize()) {
            val wide = isTwoColumn()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).nutritionContent(),
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
                    { EnergyCard(state) },
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
            pluralStringResource(
                Res.plurals.nutrition_stats_days_logged,
                state.range.days,
                state.daily.size,
                state.range.days
            ),
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
    val unit = stringResource(Res.string.nutrition_unit_kcal)
    val dates = state.dates()
    val byDate = state.daily.associate { it.date to it.nutrients.kcal }
    val labels = dates.map { shortDate(it) }
    val goal = state.goals.kcal
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_kcal_info)) {
        val tooltips = dates.map { d -> byDate[d]?.let { "${shortDate(d)} · ${kcalText(it)}" }.orEmpty() }
        DailyBarsChart(
            slots = dates.mapIndexed { i, d -> BarSlot(labels[i], byDate[d]?.let(::listOf)) },
            colors = listOf(MacroColors.kcal),
            unit = unit,
            tooltip = { tooltips[it] },
            description = barsDescription(title, state, state.daily.map { it.nutrients.kcal }) { kcalText(it) },
            goal = goal?.toDouble(),
            withinGoal = goal?.let { g -> { v: Double -> NutritionStats.withinGoal(v, g) } },
        )
        LegendRow(
            buildList {
                add(MacroColors.kcal to stringResource(Res.string.nutrition_kcal))
                if (goal != null) {
                    add(MacroColors.withinGoal to stringResource(Res.string.nutrition_stats_within))
                    val goalLabel = stringResource(Res.string.nutrition_stats_goal_line, kcalText(goal.toDouble()))
                    add(MaterialTheme.colorScheme.onSurface to goalLabel)
                }
            },
        )
    }
}

@Composable
private fun MacrosCard(state: StatsState) {
    val title = stringResource(Res.string.nutrition_stats_macros)
    val unit = stringResource(Res.string.nutrition_unit_kcal)
    val dates = state.dates()
    val byDate = state.daily.associate { it.date to it.nutrients }
    val protein = stringResource(Res.string.nutrition_abbr_protein)
    val carbs = stringResource(Res.string.nutrition_abbr_carbs)
    val fat = stringResource(Res.string.nutrition_abbr_fat)
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_macros_info)) {
        val energy = dates.map { d -> byDate[d]?.let { macroEnergy(it) } }
        val tooltips = dates.mapIndexed { i, d ->
            val n = byDate[d]
            val e = energy[i]
            if (n == null || e == null) {
                ""
            } else {
                "${shortDate(d)} · $protein ${gramsText(n.protein, 0)} · $carbs ${gramsText(n.carbs, 0)} · " +
                    "$fat ${gramsText(n.fat, 0)} (${kcalText(e.first + e.second + e.third)})"
            }
        }
        DailyBarsChart(
            slots = dates.mapIndexed { i, d -> BarSlot(shortDate(d), energy[i]?.toList()) },
            colors = listOf(MacroColors.protein, MacroColors.carbs, MacroColors.fat),
            unit = unit,
            tooltip = { tooltips[it] },
            description = barsDescription(
                title,
                state,
                energy.filterNotNull().map { it.first + it.second + it.third }
            ) {
                kcalText(it)
            },
        )
        LegendRow(
            listOf(
                MacroColors.protein to stringResource(Res.string.nutrition_protein),
                MacroColors.carbs to stringResource(Res.string.nutrition_carbs),
                MacroColors.fat to stringResource(Res.string.nutrition_fat),
            ),
        )
    }
}

/** Energy from protein, carbohydrates and fat (4/4/9 kcal per gram). */
private fun macroEnergy(n: Nutrients) = Triple(
    n.protein * GoalCalculator.KCAL_PER_G_PROTEIN,
    n.carbs * GoalCalculator.KCAL_PER_G_CARBS,
    n.fat * GoalCalculator.KCAL_PER_G_FAT,
)

/** Screen-reader summary of a daily bar chart: logged days, range, highest and average. */
@Composable
private fun barsDescription(
    title: String,
    state: StatsState,
    values: List<Double>,
    format: @Composable (Double) -> String
): String {
    if (values.isEmpty()) return title
    val highest = state.daily.zip(values).maxBy { it.second }
    return pluralStringResource(
        Res.plurals.nutrition_chart_bars_description,
        values.size,
        title,
        values.size,
        shortDate(state.from),
        shortDate(state.to),
        format(highest.second),
        shortDate(highest.first.date),
        format(values.average()),
    )
}

@Composable
private fun DonutCard(state: StatsState) {
    val split = state.split ?: return
    val title = stringResource(Res.string.nutrition_stats_donut)
    val names = listOf(
        stringResource(Res.string.nutrition_protein),
        stringResource(Res.string.nutrition_carbs),
        stringResource(Res.string.nutrition_fat),
    )
    SectionCard(title, info = stringResource(Res.string.nutrition_stats_donut_info)) {
        MacroDonut(
            split,
            names,
            stringResource(
                Res.string.nutrition_chart_donut_description,
                title,
                Amounts.percent(split.proteinPct),
                Amounts.percent(split.carbsPct),
                Amounts.percent(split.fatPct),
            ),
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
                Amounts.percent(adherence.fraction * 100),
                style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                pluralStringResource(
                    Res.plurals.nutrition_stats_adherence_value,
                    adherence.daysLogged,
                    adherence.daysWithin,
                    adherence.daysLogged
                ),
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
                stringResource(Res.string.nutrition_g_per_kg, Amounts.format(perKg, 2)),
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
        val trend = state.trend
        if (trend.kcal.size < 2) {
            ChartEmpty(stringResource(Res.string.nutrition_stats_not_enough))
            return@SectionCard
        }
        val kcalByDate = trend.kcal.associate { it.date to it.value }
        val kgByDate = trend.weightKg.associate { it.date to it.value }
        val kg = stringResource(Res.string.nutrition_unit_kg)
        val dates = (trend.kcal + trend.weightKg).map { it.date }.distinct()
        val tooltips = dates.associateWith { d ->
            listOfNotNull(
                shortDate(d),
                kcalByDate[d]?.let { kcalText(it) },
                kgByDate[d]?.let { "${Amounts.format(it, 1)} $kg" },
            ).joinToString(" · ")
        }
        val shortLabels = dates.associateWith { shortDate(it) }
        DualAxisChart(
            kcal = trend.kcal,
            weight = trend.weightKg,
            kcalUnit = stringResource(Res.string.nutrition_unit_kcal),
            weightUnit = kg,
            kcalLegend = stringResource(Res.string.nutrition_stats_trend_kcal),
            weightLegend = stringResource(Res.string.nutrition_stats_trend_weight),
            dateLabel = { shortLabels.getValue(it) },
            tooltip = { tooltips.getValue(it) },
            description = stringResource(
                Res.string.nutrition_chart_trend_description,
                title,
                kcalText(trend.kcal.first().value),
                kcalText(trend.kcal.last().value),
                trend.weightKg.firstOrNull()?.let { Amounts.format(it.value, 1) } ?: "—",
                trend.weightKg.lastOrNull()?.let { Amounts.format(it.value, 1) } ?: "—",
            ),
        )
        if (trend.weightKg.isEmpty()) ChartEmpty(stringResource(Res.string.nutrition_stats_trend_no_weight))
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
                        pluralStringResource(
                            Res.plurals.nutrition_stats_days_logged,
                            DAYS_PER_WEEK,
                            week.daysLogged,
                            DAYS_PER_WEEK
                        ),
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

private const val DAYS_PER_WEEK = 7
