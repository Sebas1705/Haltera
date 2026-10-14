package es.sebas1705.ironlog.feature.routines.ui.slot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.model.PrescriptionKind
import es.sebas1705.ironlog.feature.routines.model.PrescriptionRules
import es.sebas1705.ironlog.feature.routines.model.ProgressionKind
import es.sebas1705.ironlog.feature.routines.model.SlotLimits
import es.sebas1705.ironlog.feature.routines.model.kind
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_bilateral
import es.sebas1705.ironlog.feature.routines.resources.routines_bilateral_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_distance
import es.sebas1705.ironlog.feature.routines.resources.routines_distance_value
import es.sebas1705.ironlog.feature.routines.resources.routines_duration
import es.sebas1705.ironlog.feature.routines.resources.routines_every_n_sessions
import es.sebas1705.ironlog.feature.routines.resources.routines_flexible_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_increment
import es.sebas1705.ironlog.feature.routines.resources.routines_increment_steps
import es.sebas1705.ironlog.feature.routines.resources.routines_kind_distance
import es.sebas1705.ironlog.feature.routines.resources.routines_kind_duration
import es.sebas1705.ironlog.feature.routines.resources.routines_kind_rep_range
import es.sebas1705.ironlog.feature.routines.resources.routines_kind_reps
import es.sebas1705.ironlog.feature.routines.resources.routines_max_reps
import es.sebas1705.ironlog.feature.routines.resources.routines_min_reps
import es.sebas1705.ironlog.feature.routines.resources.routines_prescription_mismatch
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_double
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_double_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_linear
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_linear_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_none
import es.sebas1705.ironlog.feature.routines.resources.routines_progression_none_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_reps
import es.sebas1705.ironlog.feature.routines.resources.routines_rest_after
import es.sebas1705.ironlog.feature.routines.resources.routines_rest_between
import es.sebas1705.ironlog.feature.routines.resources.routines_section_extras
import es.sebas1705.ironlog.feature.routines.resources.routines_section_progression
import es.sebas1705.ironlog.feature.routines.resources.routines_section_rest
import es.sebas1705.ironlog.feature.routines.resources.routines_section_target
import es.sebas1705.ironlog.feature.routines.resources.routines_section_variant
import es.sebas1705.ironlog.feature.routines.resources.routines_sessions_at_top
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_any_variant
import es.sebas1705.ironlog.feature.routines.resources.routines_superset
import es.sebas1705.ironlog.feature.routines.resources.routines_superset_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_superset_none
import es.sebas1705.ironlog.feature.routines.resources.routines_target_fixed
import es.sebas1705.ironlog.feature.routines.resources.routines_target_flexible
import es.sebas1705.ironlog.feature.routines.resources.routines_unit_kg_value
import es.sebas1705.ironlog.feature.routines.resources.routines_unit_lb_value
import es.sebas1705.ironlog.feature.routines.ui.components.SectionTitle
import es.sebas1705.ironlog.feature.routines.ui.components.Stepper
import es.sebas1705.ironlog.feature.routines.ui.components.formatSeconds
import es.sebas1705.ironlog.feature.routines.ui.components.supersetLetter
import es.sebas1705.ironlog.feature.routines.ui.components.variantShortLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** A row of mutually exclusive chips. */
@Composable
private fun <T> ChoiceChips(options: List<Pair<T, StringResource>>, selected: T?, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

/** Fixed variant or flexible (any variant, or a set of allowed ones). */
@Composable
internal fun TargetSection(
    state: SlotEditorState,
    exercise: Exercise,
    spec: SlotSpec,
    send: (SlotEditorIntent) -> Unit
) {
    val flexible = spec.target is SlotTarget.Flexible
    val variants = exercise.variants.filter { !it.deprecated || state.isAllowed(it.id) }
    SectionTitle(stringResource(Res.string.routines_section_variant))
    if (variants.size > 1 || flexible) {
        ChoiceChips(
            options = listOf(false to Res.string.routines_target_fixed, true to Res.string.routines_target_flexible),
            selected = flexible,
            onSelect = { toFlexible ->
                when {
                    toFlexible -> send(SlotEditorIntent.SetFlexible)
                    flexible -> variants.firstOrNull { state.isAllowed(it.id) }
                        ?.let { send(SlotEditorIntent.SetFixedVariant(it.id)) }
                }
            },
        )
    }
    if (flexible) {
        Hint(stringResource(Res.string.routines_flexible_hint))
        FilterChip(
            selected = (spec.target as SlotTarget.Flexible).allowedVariantIds.isEmpty(),
            onClick = { send(SlotEditorIntent.SetAnyVariant) },
            label = { Text(stringResource(Res.string.routines_slot_any_variant)) },
        )
    }
    variants.forEach { variant ->
        OptionRow(
            label = variantShortLabel(variant, state.language),
            selected = state.isAllowed(variant.id),
            multiple = flexible,
            onClick = {
                send(
                    if (flexible) {
                        SlotEditorIntent.ToggleAllowedVariant(variant.id)
                    } else {
                        SlotEditorIntent.SetFixedVariant(variant.id)
                    },
                )
            },
        )
    }
}

/** A checkbox row when [multiple], else a radio button row; the whole row is the touch target. */
@Composable
private fun OptionRow(label: String, selected: Boolean, multiple: Boolean, onClick: () -> Unit) {
    val modifier = if (multiple) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier.fillMaxWidth().then(modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiple) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Text(label, Modifier.padding(start = 12.dp))
    }
}

/** What each working set aims for, shaped by the exercise's tracking type. */
@Composable
internal fun PrescriptionSection(state: SlotEditorState, spec: SlotSpec, send: (SlotEditorIntent) -> Unit) {
    SectionTitle(stringResource(Res.string.routines_section_target))
    val kinds = state.allowedKinds
    val prescription = spec.prescription
    if (kinds.size > 1 || state.prescriptionMismatch) {
        ChoiceChips(
            options = kinds.map { it to kindLabel(it) },
            selected = kinds.firstOrNull { it == PrescriptionRules.kindOf(prescription) },
            onSelect = { send(SlotEditorIntent.SetPrescriptionKind(it)) },
        )
    }
    if (state.prescriptionMismatch) {
        Text(
            stringResource(Res.string.routines_prescription_mismatch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    when (prescription) {
        is Prescription.RepRange -> {
            Stepper(
                label = stringResource(Res.string.routines_min_reps),
                value = prescription.min,
                onValueChange = { send(SlotEditorIntent.SetRepMin(it)) },
                range = SlotLimits.REPS,
            )
            Stepper(
                label = stringResource(Res.string.routines_max_reps),
                value = prescription.max,
                onValueChange = { send(SlotEditorIntent.SetRepMax(it)) },
                range = SlotLimits.REPS,
            )
        }
        is Prescription.Reps -> Stepper(
            label = stringResource(Res.string.routines_reps),
            value = prescription.reps,
            onValueChange = { send(SlotEditorIntent.SetReps(it)) },
            range = SlotLimits.REPS,
        )
        is Prescription.Duration -> Stepper(
            label = stringResource(Res.string.routines_duration),
            value = prescription.seconds,
            onValueChange = { send(SlotEditorIntent.SetDuration(it)) },
            range = SlotLimits.DURATION_S,
            step = SlotLimits.DURATION_STEP_S,
            valueText = formatSeconds(prescription.seconds),
        )
        is Prescription.Distance -> Stepper(
            label = stringResource(Res.string.routines_distance),
            value = prescription.meters,
            onValueChange = { send(SlotEditorIntent.SetDistance(it)) },
            range = SlotLimits.DISTANCE_M,
            step = SlotLimits.DISTANCE_STEP_M,
            valueText = stringResource(Res.string.routines_distance_value, prescription.meters),
        )
    }
}

private fun kindLabel(kind: PrescriptionKind): StringResource = when (kind) {
    PrescriptionKind.REP_RANGE -> Res.string.routines_kind_rep_range
    PrescriptionKind.REPS -> Res.string.routines_kind_reps
    PrescriptionKind.DURATION -> Res.string.routines_kind_duration
    PrescriptionKind.DISTANCE -> Res.string.routines_kind_distance
}

@Composable
internal fun RestSection(spec: SlotSpec, send: (SlotEditorIntent) -> Unit) {
    SectionTitle(stringResource(Res.string.routines_section_rest))
    Stepper(
        label = stringResource(Res.string.routines_rest_between),
        value = spec.restBetweenSetsS,
        onValueChange = { send(SlotEditorIntent.SetRestBetweenSets(it)) },
        range = SlotLimits.REST_S,
        step = SlotLimits.REST_STEP_S,
        valueText = formatSeconds(spec.restBetweenSetsS),
    )
    Stepper(
        label = stringResource(Res.string.routines_rest_after),
        value = spec.restAfterS,
        onValueChange = { send(SlotEditorIntent.SetRestAfter(it)) },
        range = SlotLimits.REST_S,
        step = SlotLimits.REST_STEP_S,
        valueText = formatSeconds(spec.restAfterS),
    )
}

/** Superset group and the bilateral toggle. */
@Composable
internal fun ExtrasSection(state: SlotEditorState, spec: SlotSpec, send: (SlotEditorIntent) -> Unit) {
    SectionTitle(stringResource(Res.string.routines_section_extras))
    val group = spec.supersetGroup ?: 0
    Stepper(
        label = stringResource(Res.string.routines_superset),
        value = group,
        onValueChange = { send(SlotEditorIntent.SetSupersetGroup(it)) },
        range = SlotLimits.SUPERSET_GROUP,
        valueText = if (group == 0) stringResource(Res.string.routines_superset_none) else supersetLetter(group),
    )
    Hint(stringResource(Res.string.routines_superset_hint))
    if (state.bilateralApplies || spec.bilateral) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = spec.bilateral,
                    role = Role.Switch,
                    onValueChange = { send(SlotEditorIntent.SetBilateral(it)) }
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.routines_bilateral), style = MaterialTheme.typography.bodyLarge)
                Hint(stringResource(Res.string.routines_bilateral_hint))
            }
            Switch(checked = spec.bilateral, onCheckedChange = null)
        }
    }
}

/** None, double progression (sessions at the top + increment) or linear (increment every N sessions). */
@Composable
internal fun ProgressionSection(state: SlotEditorState, spec: SlotSpec, send: (SlotEditorIntent) -> Unit) {
    if (!state.progressionApplies && spec.progression == ProgressionRule.None) return
    SectionTitle(stringResource(Res.string.routines_section_progression))
    val rule = spec.progression
    ChoiceChips(
        options = listOf(
            ProgressionKind.NONE to Res.string.routines_progression_none,
            ProgressionKind.DOUBLE to Res.string.routines_progression_double,
            ProgressionKind.LINEAR to Res.string.routines_progression_linear,
        ),
        selected = rule.kind(),
        onSelect = { send(SlotEditorIntent.SetProgressionKind(it)) },
    )
    when (rule) {
        ProgressionRule.None -> Hint(stringResource(Res.string.routines_progression_none_hint))
        is ProgressionRule.DoubleProgression -> {
            Hint(stringResource(Res.string.routines_progression_double_hint))
            Stepper(
                label = stringResource(Res.string.routines_sessions_at_top),
                value = rule.sessionsAtTop,
                onValueChange = { send(SlotEditorIntent.SetSessionsAtTop(it)) },
                range = SlotLimits.SESSIONS,
            )
            IncrementStepper(rule.increment, state.unit) { send(SlotEditorIntent.SetIncrement(it)) }
        }
        is ProgressionRule.Linear -> {
            Hint(stringResource(Res.string.routines_progression_linear_hint))
            IncrementStepper(rule.increment, state.unit) { send(SlotEditorIntent.SetIncrement(it)) }
            Stepper(
                label = stringResource(Res.string.routines_every_n_sessions),
                value = rule.everyNSessions,
                onValueChange = { send(SlotEditorIntent.SetEveryNSessions(it)) },
                range = SlotLimits.SESSIONS,
            )
        }
    }
}

/** Load increment in the user's unit (0.25 kg or 0.5 lb steps), or in machine/band steps. */
@Composable
private fun IncrementStepper(increment: LoadIncrement, unit: WeightUnit, onChange: (LoadIncrement) -> Unit) {
    val label = stringResource(Res.string.routines_increment)
    when (increment) {
        is LoadIncrement.Weight -> {
            val stepSize = if (unit == WeightUnit.KG) KG_STEP else LB_STEP
            val steps = (Weights.fromGrams(increment.grams, unit) / stepSize).roundToInt()
            val maxSteps = (Weights.fromGrams(SlotLimits.MAX_INCREMENT_GRAMS, unit) / stepSize).roundToInt()
            val text = Weights.format(increment.grams, unit)
            Stepper(
                label = label,
                value = steps,
                onValueChange = { onChange(LoadIncrement.Weight(Weights.toGrams(it * stepSize, unit))) },
                range = 0..maxSteps,
                valueText = stringResource(
                    if (unit == WeightUnit.KG) Res.string.routines_unit_kg_value else Res.string.routines_unit_lb_value,
                    text,
                ),
            )
        }
        is LoadIncrement.Steps -> Stepper(
            label = label,
            value = increment.steps,
            onValueChange = { onChange(LoadIncrement.Steps(it)) },
            range = SlotLimits.INCREMENT_STEPS,
            valueText = stringResource(Res.string.routines_increment_steps, increment.steps),
        )
    }
}

private const val KG_STEP = 0.25
private const val LB_STEP = 0.5
