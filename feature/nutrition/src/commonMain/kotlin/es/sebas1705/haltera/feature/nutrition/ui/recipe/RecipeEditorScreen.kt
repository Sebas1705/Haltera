package es.sebas1705.ironlog.feature.nutrition.ui.recipe

import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add_ingredient
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_delete_recipe
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_field_invalid
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_ingredients
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_ingredients_empty
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_ingredients_weight
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_recipe_edit
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_recipe_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_recipe_name
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_recipe_new
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_recipe_total
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_remove_ingredient
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_save
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving_name
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_unknown_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_yield
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_yield_hint
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutrientRows
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.ironlog.feature.nutrition.ui.components.SectionCard
import es.sebas1705.ironlog.feature.nutrition.ui.components.displayName
import es.sebas1705.ironlog.feature.nutrition.ui.components.gramsText
import es.sebas1705.ironlog.feature.nutrition.ui.components.kcalText
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import org.jetbrains.compose.resources.stringResource

@Suppress("LongMethod") // One form; splitting it would only pass the state around.
@Composable
internal fun RecipeEditorScreen(
    viewModel: RecipeEditorViewModel,
    onBack: () -> Unit,
    onAddIngredient: () -> Unit,
    onFinished: (RecipeEditorState.Finished) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (RecipeEditorIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(state.finished) { state.finished?.let(onFinished) }
    NutritionScaffold(
        title = stringResource(if (state.isNew) Res.string.nutrition_recipe_new else Res.string.nutrition_recipe_edit),
        onBack = onBack,
    ) { modifier ->
        if (state.loading) {
            LoadingScreen(modifier)
            return@NutritionScaffold
        }
        val invalid = stringResource(Res.string.nutrition_field_invalid)
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).centeredContent().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onEvent(RecipeEditorIntent.NameChanged(it)) },
                    label = { Text(stringResource(Res.string.nutrition_recipe_name)) },
                    singleLine = true,
                    isError = state.showErrors && state.nameError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionCard(
                    stringResource(Res.string.nutrition_ingredients),
                    info = stringResource(Res.string.nutrition_recipe_info)
                ) {
                    if (state.rows.isEmpty()) {
                        Text(
                            stringResource(Res.string.nutrition_ingredients_empty),
                            color = if (state.showErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.rows.forEachIndexed { index, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.food?.displayName() ?: stringResource(Res.string.nutrition_unknown_food),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    kcalText(row.food?.nutrientsFor(row.grams ?: 0.0)?.kcal ?: 0.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = row.gramsText,
                                onValueChange = { onEvent(RecipeEditorIntent.ItemGramsChanged(index, it)) },
                                label = { Text(stringResource(Res.string.nutrition_amount_grams)) },
                                singleLine = true,
                                isError = row.grams == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(110.dp),
                            )
                            IconButton(onClick = { onEvent(RecipeEditorIntent.RemoveItem(index)) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(Res.string.nutrition_remove_ingredient)
                                )
                            }
                        }
                    }
                    TextButton(onClick = onAddIngredient) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.nutrition_add_ingredient))
                    }
                    Text(
                        stringResource(Res.string.nutrition_ingredients_weight, gramsText(state.itemsWeight)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.yieldText,
                        onValueChange = { onEvent(RecipeEditorIntent.YieldChanged(it)) },
                        label = { Text(stringResource(Res.string.nutrition_yield)) },
                        supportingText = {
                            Text(
                                if (state.yieldError) invalid else stringResource(Res.string.nutrition_yield_hint)
                            )
                        },
                        isError = state.yieldError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.servingName,
                        onValueChange = { onEvent(RecipeEditorIntent.ServingNameChanged(it)) },
                        label = { Text(stringResource(Res.string.nutrition_serving_name), maxLines = 1) },
                        singleLine = true,
                        isError = state.showErrors && state.servingError,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.servingGramsText,
                        onValueChange = { onEvent(RecipeEditorIntent.ServingGramsChanged(it)) },
                        label = { Text(stringResource(Res.string.nutrition_serving_grams), maxLines = 1) },
                        singleLine = true,
                        isError = state.showErrors && state.servingError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                SectionCard(stringResource(Res.string.nutrition_per100)) {
                    NutrientRows(state.per100)
                    Text(
                        stringResource(
                            Res.string.nutrition_recipe_total,
                            kcalText(state.totals.kcal),
                            Amounts.format(state.yieldGrams ?: state.itemsWeight, 0, language())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onEvent(RecipeEditorIntent.Save) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.nutrition_save)) }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { onEvent(RecipeEditorIntent.Delete) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(Res.string.nutrition_delete_recipe),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
