@file:Suppress("MatchingDeclarationName") // The chooser composable and its step options live together.

package es.sebas1705.haltera.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.units.Numbers
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.core.units.Weights
import es.sebas1705.haltera.feature.session.resources.Res
import es.sebas1705.haltera.feature.session.resources.session_cancel
import es.sebas1705.haltera.feature.session.resources.session_load_step
import es.sebas1705.haltera.feature.session.resources.session_load_step_custom
import es.sebas1705.haltera.feature.session.resources.session_load_step_default
import es.sebas1705.haltera.feature.session.resources.session_load_step_plates
import es.sebas1705.haltera.feature.session.resources.session_load_step_text
import es.sebas1705.haltera.feature.session.resources.session_load_step_title
import es.sebas1705.haltera.feature.session.resources.session_load_step_use
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Step choices offered per unit (grams) and for machine stack positions. */
internal object LoadStepOptions {
    private val KG = listOf(1.0, 1.25, 2.0, 2.5, 5.0, 10.0)
    private val LB = listOf(1.0, 2.5, 5.0, 10.0, 25.0)
    private const val MAX_PLATES = 5L

    fun weights(unit: WeightUnit): List<Long> = (if (unit == WeightUnit.KG) KG else LB).map {
        Weights.toGrams(
            it,
            unit
        )
    }

    fun plates(): List<Long> = (1..MAX_PLATES).toList()

    /** The preset chips next to "Default (x)": every preset except the one equal to the default. */
    fun presets(plateMode: Boolean, unit: WeightUnit, default: Long): List<Long> =
        (if (plateMode) plates() else weights(unit)).filter { it != default }

    /** A typed custom step: a positive number of plates, or a positive weight in [unit] (comma or dot). */
    fun parse(text: String, plateMode: Boolean, unit: WeightUnit): Long? {
        return if (plateMode) {
            text.trim().toLongOrNull()?.takeIf { it > 0 }
        } else {
            Numbers.parse(text)?.takeIf { it > 0 }?.let { Weights.toGrams(it, unit) }?.takeIf { it > 0 }
        }
    }
}

/** "2.5 kg" or "2 plates". */
@Composable
private fun stepText(step: Long, plateMode: Boolean, unit: WeightUnit): String =
    if (plateMode) {
        pluralStringResource(
            Res.plurals.session_load_step_plates,
            step.toInt(),
            step.toInt()
        )
    } else {
        weightText(step, unit)
    }

/**
 * "Step: 2.5 kg ✎" under the load stepper. Tapping it offers the usual steps, a custom one and the default;
 * the choice is remembered for the variant ([onChoose] with null goes back to the default).
 */
@Composable
internal fun LoadStepChooser(
    plateMode: Boolean,
    chosen: Long?,
    default: Long,
    unit: WeightUnit,
    onChoose: (Long?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(stringResource(Res.string.session_load_step, stepText(chosen ?: default, plateMode, unit)))
    }
    if (open) {
        LoadStepDialog(plateMode, chosen, default, unit, onDismiss = { open = false }) {
            open = false
            onChoose(it)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadStepDialog(
    plateMode: Boolean,
    chosen: Long?,
    default: Long,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onChoose: (Long?) -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf("") }
    val parsed = LoadStepOptions.parse(custom, plateMode, unit)
    val options = LoadStepOptions.presets(plateMode, unit, default)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.session_load_step_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.session_load_step_text), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chosen == null || chosen == default,
                        onClick = { onChoose(null) },
                        label = {
                            Text(
                                stringResource(Res.string.session_load_step_default, stepText(default, plateMode, unit))
                            )
                        },
                    )
                    options.forEach { option ->
                        FilterChip(
                            selected = chosen == option,
                            onClick = { onChoose(option) },
                            label = { Text(stepText(option, plateMode, unit)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.session_load_step_custom)) },
                    suffix = if (plateMode) null else ({ Text(unitLabel(unit)) }),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (plateMode) KeyboardType.Number else KeyboardType.Decimal
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onChoose) }, enabled = parsed != null) {
                Text(stringResource(Res.string.session_load_step_use))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.session_cancel)) } },
    )
}
