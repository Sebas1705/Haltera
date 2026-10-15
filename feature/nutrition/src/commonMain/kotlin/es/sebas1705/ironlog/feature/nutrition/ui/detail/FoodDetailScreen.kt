package es.sebas1705.ironlog.feature.nutrition.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import es.sebas1705.ironlog.core.ui.components.ErrorScreen
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.feature.nutrition.model.AmountMode
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add_to_meal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_invalid
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_ml
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_delete_entry
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_edit_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_favourite_add
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_favourite_remove
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_food_not_found
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_for_amount
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100ml
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_save
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving_is
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_servings
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_source_generic
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_source_off
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_source_recipe
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_source_user
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutrientRows
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.ironlog.feature.nutrition.ui.components.SectionCard
import es.sebas1705.ironlog.feature.nutrition.ui.components.amountText
import es.sebas1705.ironlog.feature.nutrition.ui.components.displayName
import es.sebas1705.ironlog.feature.nutrition.ui.components.label
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FoodDetailScreen(
    viewModel: FoodDetailViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onEditFood: (Food) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (FoodDetailIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    val food = state.food
    NutritionScaffold(
        title = food?.displayName() ?: "",
        onBack = onBack,
        actions = {
            if (food != null) {
                IconButton(onClick = { onEvent(FoodDetailIntent.ToggleFavourite) }) {
                    Icon(
                        if (food.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (food.favourite) Res.string.nutrition_favourite_remove else Res.string.nutrition_favourite_add
                        ),
                    )
                }
                if (food.isEditable) {
                    IconButton(onClick = { onEditFood(food) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.nutrition_edit_food))
                    }
                }
            }
        },
    ) { modifier ->
        when {
            state.loading -> LoadingScreen(modifier)
            state.notFound || food == null -> ErrorScreen(
                stringResource(Res.string.nutrition_food_not_found),
                modifier = modifier
            )
            else -> Content(state, food, onEvent, modifier)
        }
    }
}

@Composable
private fun Content(state: FoodDetailState, food: Food, onEvent: (FoodDetailIntent) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { AmountCard(state, food, onEvent) }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { NutrientCards(state, food) }
                }
            } else {
                AmountCard(state, food, onEvent)
                NutrientCards(state, food)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod") // One form card; splitting it would only pass the state around.
@Composable
private fun AmountCard(state: FoodDetailState, food: Food, onEvent: (FoodDetailIntent) -> Unit) {
    val grams = state.grams
    SectionCard(stringResource(Res.string.nutrition_amount)) {
        if (food.hasServing) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AmountMode.GRAMS, AmountMode.SERVINGS).forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onEvent(FoodDetailIntent.ModeChanged(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(
                            if (mode == AmountMode.SERVINGS) {
                                stringResource(Res.string.nutrition_servings)
                            } else {
                                stringResource(
                                    if (food.unit == FoodUnit.ML) Res.string.nutrition_amount_ml else Res.string.nutrition_amount_grams
                                )
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(
                    Res.string.nutrition_serving_is,
                    food.servingName(language()) ?: "",
                    amountText(food.servingGrams ?: 0.0, food.unit)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = state.amountText,
            onValueChange = { onEvent(FoodDetailIntent.AmountChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    when {
                        state.mode == AmountMode.SERVINGS -> stringResource(Res.string.nutrition_servings)
                        food.unit == FoodUnit.ML -> stringResource(Res.string.nutrition_amount_ml)
                        else -> stringResource(Res.string.nutrition_amount_grams)
                    },
                )
            },
            isError = grams == null,
            supportingText = {
                when {
                    grams == null -> Text(stringResource(Res.string.nutrition_amount_invalid))
                    state.mode == AmountMode.SERVINGS -> Text(amountText(grams, food.unit))
                    else -> Unit
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(stringResource(Res.string.nutrition_meal), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meal.entries.forEach { meal ->
                FilterChip(
                    selected = state.meal == meal,
                    onClick = { onEvent(FoodDetailIntent.MealChanged(meal)) },
                    label = { Text(stringResource(meal.label)) },
                )
            }
        }
        Button(
            onClick = { onEvent(FoodDetailIntent.Save) },
            enabled = grams != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.isEdit) {
                    stringResource(
                        Res.string.nutrition_save
                    )
                } else {
                    stringResource(Res.string.nutrition_add_to_meal, stringResource(state.meal.label))
                }
            )
        }
        if (state.isEdit) {
            OutlinedButton(onClick = { onEvent(FoodDetailIntent.Delete) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.nutrition_delete_entry), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NutrientCards(state: FoodDetailState, food: Food) {
    val grams = state.grams
    val nutrients = state.nutrients
    if (grams != null && nutrients != null) {
        SectionCard(
            stringResource(Res.string.nutrition_for_amount, amountText(grams, food.unit))
        ) { NutrientRows(nutrients) }
    }
    SectionCard(
        stringResource(if (food.unit == FoodUnit.ML) Res.string.nutrition_per100ml else Res.string.nutrition_per100),
        info = stringResource(Res.string.nutrition_per_info),
    ) {
        NutrientRows(food.per100)
        Text(
            stringResource(
                when (food.source) {
                    FoodSource.GENERIC -> Res.string.nutrition_source_generic
                    FoodSource.OFF -> Res.string.nutrition_source_off
                    FoodSource.USER -> Res.string.nutrition_source_user
                    FoodSource.RECIPE -> Res.string.nutrition_source_recipe
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        food.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        food.barcode?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
