package es.sebas1705.ironlog.feature.nutrition.ui.editor

import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_barcode
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_brand
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_carbs_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_delete_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_editor_edit
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_editor_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_editor_new
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fat_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fiber_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_field_invalid
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_food_name
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_optional
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100_values
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100ml_values
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_protein_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_salt_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_save
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_serving_name
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_sugar_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_unit_g
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_unit_ml
import es.sebas1705.ironlog.feature.nutrition.ui.components.SectionCard
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Suppress("LongMethod") // One form; splitting it would only pass the state around.
@Composable
internal fun FoodEditorScreen(
    viewModel: FoodEditorViewModel,
    onBack: () -> Unit,
    onFinished: (FoodEditorState.Finished) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (FoodEditorIntent) -> Unit = { viewModel.onEvent(it) }
    LaunchedEffect(state.finished) { state.finished?.let(onFinished) }
    NutritionScaffold(
        title = stringResource(if (state.isNew) Res.string.nutrition_editor_new else Res.string.nutrition_editor_edit),
        onBack = onBack,
    ) { modifier ->
        if (state.loading) {
            LoadingScreen(modifier)
            return@NutritionScaffold
        }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).centeredContent().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(state, FoodField.NAME, Res.string.nutrition_food_name, onEvent, number = false)
                OutlinedTextField(
                    value = state.brand,
                    onValueChange = { onEvent(FoodEditorIntent.BrandChanged(it)) },
                    label = {
                        Text(
                            stringResource(Res.string.nutrition_brand) + " " + stringResource(Res.string.nutrition_optional)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.barcode,
                    onValueChange = { onEvent(FoodEditorIntent.BarcodeChanged(it)) },
                    label = {
                        Text(
                            stringResource(Res.string.nutrition_barcode) + " " + stringResource(Res.string.nutrition_optional)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionCard(
                    stringResource(
                        if (state.unit == FoodUnit.ML) Res.string.nutrition_per100ml_values else Res.string.nutrition_per100_values
                    ),
                    info = stringResource(Res.string.nutrition_editor_info),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FoodUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = state.unit == unit,
                                onClick = { onEvent(FoodEditorIntent.UnitChanged(unit)) },
                                shape = SegmentedButtonDefaults.itemShape(index, FoodUnit.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        if (unit == FoodUnit.ML) Res.string.nutrition_unit_ml else Res.string.nutrition_unit_g
                                    )
                                )
                            }
                        }
                    }
                    Field(state, FoodField.KCAL, Res.string.nutrition_kcal, onEvent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(state, FoodField.PROTEIN, Res.string.nutrition_protein_g, onEvent, Modifier.weight(1f))
                        Field(state, FoodField.CARBS, Res.string.nutrition_carbs_g, onEvent, Modifier.weight(1f))
                        Field(state, FoodField.FAT, Res.string.nutrition_fat_g, onEvent, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(state, FoodField.SUGAR, Res.string.nutrition_sugar_g, onEvent, Modifier.weight(1f))
                        Field(state, FoodField.FIBER, Res.string.nutrition_fiber_g, onEvent, Modifier.weight(1f))
                        Field(state, FoodField.SALT, Res.string.nutrition_salt_g, onEvent, Modifier.weight(1f))
                    }
                }
                SectionCard(
                    stringResource(Res.string.nutrition_serving) + " " + stringResource(Res.string.nutrition_optional)
                ) {
                    Field(state, FoodField.SERVING_NAME, Res.string.nutrition_serving_name, onEvent, number = false)
                    Field(state, FoodField.SERVING_GRAMS, Res.string.nutrition_serving_grams, onEvent)
                }
                Button(
                    onClick = { onEvent(FoodEditorIntent.Save) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.nutrition_save)) }
                if (!state.isNew) {
                    OutlinedButton(onClick = { onEvent(FoodEditorIntent.Delete) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.nutrition_delete_food), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    state: FoodEditorState,
    field: FoodField,
    label: StringResource,
    onEvent: (FoodEditorIntent) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = true,
) {
    val error = state.showErrors && field in state.errors
    OutlinedTextField(
        value = state.value(field),
        onValueChange = { onEvent(FoodEditorIntent.Changed(field, it)) },
        label = { Text(stringResource(label), maxLines = 1) },
        singleLine = true,
        isError = error,
        supportingText = if (error) ({ Text(stringResource(Res.string.nutrition_field_invalid)) }) else null,
        keyboardOptions = if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        },
        modifier = modifier,
    )
}
