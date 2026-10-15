package es.sebas1705.ironlog.feature.nutrition.ui.goals

import es.sebas1705.ironlog.feature.nutrition.ui.components.nutritionContent
import es.sebas1705.ironlog.feature.nutrition.ui.components.isTwoColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.ActivityLevel
import es.sebas1705.ironlog.data.models.MacroMode
import es.sebas1705.ironlog.data.models.Objective
import es.sebas1705.ironlog.data.models.Sex
import es.sebas1705.ironlog.domain.usecases.nutrition.GoalSuggestion
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity_active
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity_light
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity_moderate
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity_sedentary
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_activity_very_active
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_birth_year
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_bmr
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_goal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_macros
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_maintenance
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_missing
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calc_use
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_calculator
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_carbs
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_clear_goals
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_daily_goals
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fat
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_field_invalid
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_goals
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_goals_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_height
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_kcal_goal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_macros_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_macros_percent_sum
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_mode_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_mode_percent
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_no_weight
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_objective
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_objective_gain
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_objective_lose
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_objective_maintain
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_protein
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_save
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_sex
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_sex_female
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_sex_male
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_weight_latest
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.ironlog.feature.nutrition.ui.components.SectionCard
import es.sebas1705.ironlog.feature.nutrition.ui.components.gramsText
import es.sebas1705.ironlog.feature.nutrition.ui.components.kcalText
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GoalsScreen(viewModel: GoalsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (GoalsIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    NutritionScaffold(title = stringResource(Res.string.nutrition_goals), onBack = onBack) { modifier ->
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
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { GoalsCard(state, onEvent) }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { CalculatorCard(state, onEvent) }
                    }
                } else {
                    GoalsCard(state, onEvent)
                    CalculatorCard(state, onEvent)
                }
            }
        }
    }
}

@Suppress("LongMethod") // One form card; splitting it would only pass the state around.
@Composable
private fun GoalsCard(state: GoalsState, onEvent: (GoalsIntent) -> Unit) {
    SectionCard(
        stringResource(Res.string.nutrition_daily_goals),
        info = stringResource(Res.string.nutrition_goals_info)
    ) {
        OutlinedTextField(
            value = state.kcalText,
            onValueChange = { onEvent(GoalsIntent.KcalChanged(it)) },
            label = { Text(stringResource(Res.string.nutrition_kcal_goal)) },
            singleLine = true,
            isError = state.kcalError,
            supportingText = if (state.kcalError) {
                (
                    {
                        Text(
                            stringResource(Res.string.nutrition_field_invalid)
                        )
                    }
                    )
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MacroMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onEvent(GoalsIntent.ModeChanged(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, MacroMode.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (mode == MacroMode.GRAMS) Res.string.nutrition_mode_grams else Res.string.nutrition_mode_percent
                        )
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Macro.PROTEIN to Res.string.nutrition_protein,
                Macro.CARBS to Res.string.nutrition_carbs,
                Macro.FAT to Res.string.nutrition_fat
            )
                .forEach { (macro, label) ->
                    OutlinedTextField(
                        value = state.macroText(macro),
                        onValueChange = { onEvent(GoalsIntent.MacroChanged(macro, it)) },
                        label = { Text(stringResource(label), maxLines = 1) },
                        suffix = { Text(if (state.mode == MacroMode.GRAMS) "g" else "%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
        }
        val summary = when (state.mode) {
            MacroMode.PERCENT -> stringResource(
                Res.string.nutrition_macros_percent_sum,
                Amounts.percent(state.percentSum.toDouble())
            )
            MacroMode.GRAMS -> state.kcalFromMacros?.let {
                stringResource(
                    Res.string.nutrition_macros_kcal,
                    kcalText(it.toDouble())
                )
            }
        }
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.mode == MacroMode.PERCENT && state.percentSum != GoalsState.PERCENT) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Button(
            onClick = { onEvent(GoalsIntent.Save) },
            enabled = !state.kcalError,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.nutrition_save))
        }
        OutlinedButton(onClick = { onEvent(GoalsIntent.Clear) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.nutrition_clear_goals))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod") // One form card; splitting it would only pass the state around.
@Composable
private fun CalculatorCard(state: GoalsState, onEvent: (GoalsIntent) -> Unit) {
    SectionCard(
        stringResource(Res.string.nutrition_calculator),
        info = stringResource(Res.string.nutrition_calc_info)
    ) {
        Text(stringResource(Res.string.nutrition_sex), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { index, sex ->
                SegmentedButton(
                    selected = state.sex == sex,
                    onClick = { onEvent(GoalsIntent.SexChanged(sex)) },
                    shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (sex == Sex.MALE) Res.string.nutrition_sex_male else Res.string.nutrition_sex_female
                        )
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.birthYearText,
                onValueChange = { onEvent(GoalsIntent.BirthYearChanged(it)) },
                label = { Text(stringResource(Res.string.nutrition_birth_year), maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.heightText,
                onValueChange = { onEvent(GoalsIntent.HeightChanged(it)) },
                label = { Text(stringResource(Res.string.nutrition_height), maxLines = 1) },
                suffix = { Text("cm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            state.weightGrams?.let {
                stringResource(Res.string.nutrition_weight_latest, Amounts.format(it / 1_000.0, 1, language()))
            }
                ?: stringResource(Res.string.nutrition_no_weight),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.weightGrams == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(Res.string.nutrition_activity), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.activity == level,
                    onClick = { onEvent(GoalsIntent.ActivityChanged(level)) },
                    label = { Text(stringResource(level.label)) },
                )
            }
        }
        Text(stringResource(Res.string.nutrition_objective), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Objective.entries.forEachIndexed { index, objective ->
                SegmentedButton(
                    selected = state.objective == objective,
                    onClick = { onEvent(GoalsIntent.ObjectiveChanged(objective)) },
                    shape = SegmentedButtonDefaults.itemShape(index, Objective.entries.size),
                ) { Text(stringResource(objective.label), maxLines = 1) }
            }
        }
        val suggestion = state.suggestion
        if (suggestion == null) {
            Text(
                stringResource(Res.string.nutrition_calc_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SuggestionLines(suggestion)
            Button(
                onClick = { onEvent(GoalsIntent.ApplySuggestion) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(Res.string.nutrition_calc_use)) }
        }
    }
}

@Composable
private fun SuggestionLines(s: GoalSuggestion) {
    val style = MaterialTheme.typography.bodyMedium.tabularFigures()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.nutrition_calc_bmr, kcalText(s.bmr)), style = style)
        Text(
            stringResource(
                Res.string.nutrition_calc_maintenance,
                Amounts.format(s.activityFactor, 3),
                kcalText(s.maintenance)
            ),
            style = style
        )
        Text(
            stringResource(Res.string.nutrition_calc_goal, kcalText(s.kcal.toDouble())),
            style = MaterialTheme.typography.titleMedium.tabularFigures()
        )
        Text(
            stringResource(
                Res.string.nutrition_calc_macros,
                gramsText(s.proteinG.toDouble(), 0),
                Amounts.format(s.proteinPerKg, 1),
                gramsText(s.carbsG.toDouble(), 0),
                gramsText(s.fatG.toDouble(), 0),
            ),
            style = style,
        )
    }
}

private val ActivityLevel.label: StringResource
    get() = when (this) {
        ActivityLevel.SEDENTARY -> Res.string.nutrition_activity_sedentary
        ActivityLevel.LIGHT -> Res.string.nutrition_activity_light
        ActivityLevel.MODERATE -> Res.string.nutrition_activity_moderate
        ActivityLevel.ACTIVE -> Res.string.nutrition_activity_active
        ActivityLevel.VERY_ACTIVE -> Res.string.nutrition_activity_very_active
    }

private val Objective.label: StringResource
    get() = when (this) {
        Objective.LOSE -> Res.string.nutrition_objective_lose
        Objective.MAINTAIN -> Res.string.nutrition_objective_maintain
        Objective.GAIN -> Res.string.nutrition_objective_gain
    }
