package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.datastore.UserSettings
import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.feature.session.load.LoadDetail
import es.sebas1705.ironlog.feature.session.load.LoadSteps
import es.sebas1705.ironlog.feature.session.load.Prefill
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_band_level
import es.sebas1705.ironlog.feature.session.resources.session_bar_only
import es.sebas1705.ironlog.feature.session.resources.session_decrease
import es.sebas1705.ironlog.feature.session.resources.session_delete_set
import es.sebas1705.ironlog.feature.session.resources.session_distance
import es.sebas1705.ironlog.feature.session.resources.session_duration
import es.sebas1705.ironlog.feature.session.resources.session_edit_set
import es.sebas1705.ironlog.feature.session.resources.session_increase_value
import es.sebas1705.ironlog.feature.session.resources.session_kind_drop
import es.sebas1705.ironlog.feature.session.resources.session_kind_failure
import es.sebas1705.ironlog.feature.session.resources.session_kind_warmup
import es.sebas1705.ironlog.feature.session.resources.session_kind_working
import es.sebas1705.ironlog.feature.session.resources.session_load_added
import es.sebas1705.ironlog.feature.session.resources.session_load_assisted
import es.sebas1705.ironlog.feature.session.resources.session_load_band
import es.sebas1705.ironlog.feature.session.resources.session_load_machine_plate
import es.sebas1705.ironlog.feature.session.resources.session_load_machine_weight
import es.sebas1705.ironlog.feature.session.resources.session_load_per_dumbbell
import es.sebas1705.ironlog.feature.session.resources.session_load_total
import es.sebas1705.ironlog.feature.session.resources.session_meters
import es.sebas1705.ironlog.feature.session.resources.session_per_side
import es.sebas1705.ironlog.feature.session.resources.session_plate_number
import es.sebas1705.ironlog.feature.session.resources.session_plates_missing
import es.sebas1705.ironlog.feature.session.resources.session_reps
import es.sebas1705.ironlog.feature.session.resources.session_save
import es.sebas1705.ironlog.feature.session.resources.session_seconds
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val DURATION_STEP_S = 5
private const val DISTANCE_STEP_M = 100

/** Bottom sheet to edit one set: kind, load (adapted to the load type) and reps/time/distance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetEditorSheet(
    card: ExerciseCard,
    draft: SetInput,
    settings: UserSettings,
    steps: VariantSteps,
    onChange: (SetInput) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded: half-expanded hides Save on short (landscape) screens.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.session_edit_set), style = MaterialTheme.typography.titleLarge)
            Text(card.name.fullTitle(), style = MaterialTheme.typography.bodyMedium)
            KindSelector(draft.kind) { onChange(draft.copy(kind = it)) }
            if (Prefill.tracksLoad(
                    card.trackingType,
                    card.loadType
                )
            ) {
                LoadEditor(card.loadType, draft, settings, steps, onChange)
            }
            if (card.trackingType.tracksReps) {
                Stepper(
                    stringResource(Res.string.session_reps),
                    (draft.reps ?: 0).toString(),
                    onMinus = { onChange(draft.copy(reps = ((draft.reps ?: 0) - 1).coerceAtLeast(0))) },
                    onPlus = { onChange(draft.copy(reps = (draft.reps ?: 0) + 1)) }
                )
            }
            if (card.trackingType.tracksDistance) {
                val m = draft.distanceM ?: 0
                Stepper(
                    stringResource(Res.string.session_distance),
                    stringResource(Res.string.session_meters, m),
                    onMinus = { onChange(draft.copy(distanceM = (m - DISTANCE_STEP_M).coerceAtLeast(0))) },
                    onPlus = { onChange(draft.copy(distanceM = m + DISTANCE_STEP_M)) }
                )
            }
            if (card.trackingType.tracksDuration) {
                val s = draft.durationS ?: 0
                Stepper(
                    stringResource(Res.string.session_duration),
                    stringResource(Res.string.session_seconds, s),
                    onMinus = {
                        onChange(
                            draft.copy(durationS = (s - DURATION_STEP_S).coerceAtLeast(DURATION_STEP_S))
                        )
                    },
                    onPlus = { onChange(draft.copy(durationS = s + DURATION_STEP_S)) }
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDelete) { Text(stringResource(Res.string.session_delete_set)) }
                Button(onClick = onSave) { Text(stringResource(Res.string.session_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(kind: SetKind, onSelect: (SetKind) -> Unit) {
    val kinds = listOf(
        SetKind.WARMUP to Res.string.session_kind_warmup,
        SetKind.WORKING to Res.string.session_kind_working,
        SetKind.DROP to Res.string.session_kind_drop,
        SetKind.FAILURE to Res.string.session_kind_failure,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = kind == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
            ) { Text(stringResource(label), maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** The load steps remembered for the edited variant (null: default) and how to change them. */
internal class VariantSteps(val weight: Long?, val plates: Long?, val onChoose: (step: Long?, plates: Boolean) -> Unit)

/** Load entry per load type (spec §5): barbell with plates per side, machine kg or plate #, ± bodyweight, band. */
@Composable
private fun LoadEditor(
    loadType: LoadType,
    draft: SetInput,
    settings: UserSettings,
    steps: VariantSteps,
    onChange: (SetInput) -> Unit,
) {
    val unit = settings.unit
    val plates = settings.availablePlatesGrams
    val bar = settings.barWeightGrams
    val detail = LoadDetail.decode(draft.loadDetail)
    val plateMode = loadType == LoadType.MACHINE && detail is LoadDetail.MachinePlate
    fun step(taps: Int, machinePlate: Boolean = false) = LoadSteps.step(
        draft.loadGrams,
        taps,
        loadType,
        unit,
        plates,
        bar,
        machinePlate,
        override = if (machinePlate) steps.plates else steps.weight,
    )
    LoadInputs(loadType, draft, detail, settings, steps.weight, onChange, ::step)
    if (LoadSteps.customizable(loadType)) {
        LoadStepChooser(
            plateMode = plateMode,
            chosen = if (plateMode) steps.plates else steps.weight,
            default = LoadSteps.defaultStep(loadType, unit, plates, plateMode),
            unit = unit,
        ) { steps.onChoose(it, plateMode) }
    }
}

@Suppress("LongParameterList") // The load inputs share the editor's state and the stepper.
@Composable
private fun LoadInputs(
    loadType: LoadType,
    draft: SetInput,
    detail: LoadDetail?,
    settings: UserSettings,
    weightStep: Long?,
    onChange: (SetInput) -> Unit,
    stepper: (taps: Int, machinePlate: Boolean) -> Long,
) {
    val unit = settings.unit
    val bar = settings.barWeightGrams
    fun step(taps: Int) = stepper(taps, false)
    when (loadType) {
        LoadType.BARBELL -> {
            val total = draft.loadGrams ?: bar
            val barDetail = LoadDetail.encode(LoadDetail.Barbell(bar))
            Stepper(
                stringResource(Res.string.session_load_total),
                weightText(total, unit),
                onMinus = { onChange(draft.copy(loadGrams = step(-1), loadDetail = barDetail)) },
                onPlus = { onChange(draft.copy(loadGrams = step(1), loadDetail = barDetail)) }
            )
            PlateBreakdown(total, settings)
        }
        LoadType.DUMBBELL -> Stepper(
            stringResource(Res.string.session_load_per_dumbbell),
            weightText(draft.loadGrams ?: 0, unit),
            onMinus = { onChange(draft.copy(loadGrams = step(-1))) },
            onPlus = { onChange(draft.copy(loadGrams = step(1))) }
        )
        LoadType.MACHINE -> MachineEditor(
            draft,
            detail is LoadDetail.MachinePlate,
            settings,
            onChange
        ) { taps, plate -> stepper(taps, plate) }
        LoadType.BODYWEIGHT -> BodyweightEditor(draft, settings, weightStep, onChange)
        LoadType.BAND -> {
            val level = draft.loadGrams ?: 1
            val bandDetail = LoadDetail.encode(LoadDetail.Band)
            Stepper(
                stringResource(Res.string.session_load_band),
                stringResource(Res.string.session_band_level, level.toInt()),
                onMinus = { onChange(draft.copy(loadGrams = step(-1), loadDetail = bandDetail)) },
                onPlus = { onChange(draft.copy(loadGrams = step(1), loadDetail = bandDetail)) }
            )
        }
        LoadType.NONE -> Unit
    }
}

@Composable
private fun PlateBreakdown(total: Long, settings: UserSettings) {
    val loadout = PlateCalculator.platesPerSide(total, settings.barWeightGrams, settings.availablePlatesGrams)
    val unit = settings.unit
    val text = if (loadout.perSide.isEmpty()) {
        stringResource(Res.string.session_bar_only, weightText(settings.barWeightGrams, unit))
    } else {
        stringResource(
            Res.string.session_per_side,
            weightText(settings.barWeightGrams, unit),
            loadout.perSide.map { weightText(it, unit) }.joinToString(" + "),
        )
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
    if (!loadout.isExact) {
        Text(
            stringResource(Res.string.session_plates_missing, weightText(loadout.achievedGrams, unit)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun MachineEditor(
    draft: SetInput,
    plateMode: Boolean,
    settings: UserSettings,
    onChange: (SetInput) -> Unit,
    step: (Int, Boolean) -> Long,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !plateMode,
            onClick = {
                if (plateMode) {
                    onChange(
                        draft.copy(loadGrams = null, loadDetail = LoadDetail.encode(LoadDetail.MachineWeight))
                    )
                }
            },
            label = { Text(stringResource(Res.string.session_load_machine_weight)) },
        )
        FilterChip(
            selected = plateMode,
            onClick = {
                if (!plateMode) {
                    onChange(
                        draft.copy(loadGrams = 1, loadDetail = LoadDetail.encode(LoadDetail.MachinePlate))
                    )
                }
            },
            label = { Text(stringResource(Res.string.session_load_machine_plate)) },
        )
    }
    if (plateMode) {
        Stepper(
            stringResource(Res.string.session_load_machine_plate),
            stringResource(Res.string.session_plate_number, (draft.loadGrams ?: 1).toInt()),
            onMinus = { onChange(draft.copy(loadGrams = step(-1, true))) },
            onPlus = { onChange(draft.copy(loadGrams = step(1, true))) }
        )
    } else {
        Stepper(
            stringResource(Res.string.session_load_machine_weight),
            weightText(draft.loadGrams ?: 0, settings.unit),
            onMinus = { onChange(draft.copy(loadGrams = step(-1, false))) },
            onPlus = { onChange(draft.copy(loadGrams = step(1, false))) }
        )
    }
}

/** Added (+) or assisting (−) load: a sign selector and a magnitude stepper. */
@Composable
private fun BodyweightEditor(
    draft: SetInput,
    settings: UserSettings,
    stepOverride: Long?,
    onChange: (SetInput) -> Unit
) {
    val load = draft.loadGrams ?: 0
    val assisted = load < 0 || (load == 0L && (LoadDetail.decode(draft.loadDetail) as? LoadDetail.Bodyweight)?.assisted == true)
    fun signed(assist: Boolean, magnitude: Long) = draft.copy(
        loadGrams = if (assist) -magnitude else magnitude,
        loadDetail = LoadDetail.encode(LoadDetail.Bodyweight(assist)),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !assisted,
            onClick = { onChange(signed(false, abs(load))) },
            label = { Text(stringResource(Res.string.session_load_added)) }
        )
        FilterChip(
            selected = assisted,
            onClick = { onChange(signed(true, abs(load))) },
            label = { Text(stringResource(Res.string.session_load_assisted)) }
        )
    }
    // The stepper moves the magnitude; the selector above sets the sign.
    val magnitude = abs(load)
    val stepGrams = LoadSteps.stepGrams(
        LoadType.BODYWEIGHT,
        settings.unit,
        settings.availablePlatesGrams,
        override = stepOverride,
    )
    Stepper(
        stringResource(if (assisted) Res.string.session_load_assisted else Res.string.session_load_added),
        weightText(magnitude, settings.unit),
        onMinus = { onChange(signed(assisted, (magnitude - stepGrams).coerceAtLeast(0))) },
        onPlus = { onChange(signed(assisted, magnitude + stepGrams)) },
    )
}

/** A labelled value with large − / + buttons for one-handed use. */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minus = stringResource(Res.string.session_decrease)
    val plus = stringResource(Res.string.session_increase_value)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = onMinus,
            modifier = Modifier.size(56.dp).semantics { contentDescription = minus }
        ) {
            Text("−", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        FilledTonalIconButton(
            onClick = onPlus,
            modifier = Modifier.size(56.dp).semantics { contentDescription = plus }
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
