package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.intl.Locale
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.core.units.formatVolume
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.domain.usecases.naming.VariantLabel
import es.sebas1705.ironlog.feature.session.load.Prefill
import es.sebas1705.ironlog.feature.session.load.isMachinePlate
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_band_level
import es.sebas1705.ironlog.feature.session.resources.session_bodyweight
import es.sebas1705.ironlog.feature.session.resources.session_equipment_band
import es.sebas1705.ironlog.feature.session.resources.session_equipment_barbell
import es.sebas1705.ironlog.feature.session.resources.session_equipment_bodyweight
import es.sebas1705.ironlog.feature.session.resources.session_equipment_cable
import es.sebas1705.ironlog.feature.session.resources.session_equipment_cardio_machine
import es.sebas1705.ironlog.feature.session.resources.session_equipment_dumbbell
import es.sebas1705.ironlog.feature.session.resources.session_equipment_ez_bar
import es.sebas1705.ironlog.feature.session.resources.session_equipment_foam_roller
import es.sebas1705.ironlog.feature.session.resources.session_equipment_kettlebell
import es.sebas1705.ironlog.feature.session.resources.session_equipment_machine
import es.sebas1705.ironlog.feature.session.resources.session_equipment_medicine_ball
import es.sebas1705.ironlog.feature.session.resources.session_equipment_none
import es.sebas1705.ironlog.feature.session.resources.session_equipment_other
import es.sebas1705.ironlog.feature.session.resources.session_equipment_smith
import es.sebas1705.ironlog.feature.session.resources.session_equipment_swiss_ball
import es.sebas1705.ironlog.feature.session.resources.session_equipment_trap_bar
import es.sebas1705.ironlog.feature.session.resources.session_kind_drop_short
import es.sebas1705.ironlog.feature.session.resources.session_kind_failure_short
import es.sebas1705.ironlog.feature.session.resources.session_kind_warmup_short
import es.sebas1705.ironlog.feature.session.resources.session_meters
import es.sebas1705.ironlog.feature.session.resources.session_no_load
import es.sebas1705.ironlog.feature.session.resources.session_plate_number
import es.sebas1705.ironlog.feature.session.resources.session_seconds
import es.sebas1705.ironlog.feature.session.resources.session_unit_kg
import es.sebas1705.ironlog.feature.session.resources.session_unit_lb
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The UI language code ("en", "es"…). */
@Composable
internal fun uiLanguage(): String = Locale.current.language

@OptIn(ExperimentalTime::class)
internal fun systemNow(): Long = Clock.System.now().toEpochMilliseconds()

/** Current time, re-read on every second boundary while in composition (cancelled when it leaves). */
@Composable
internal fun rememberNow(): State<Long> = produceState(systemNow()) {
    while (true) {
        val now = systemNow()
        value = now
        delay(MILLIS_PER_TICK - now % MILLIS_PER_TICK)
    }
}

private const val MILLIS_PER_TICK = 1_000L

@Composable
internal fun ExerciseName.title(): String = plain(uiLanguage())

/** The variant label shown under the title (and on variant chips): the variant's own name, else its equipment. */
@Composable
internal fun ExerciseName.subtitle(): String? = when (val label = label) {
    is VariantLabel.Named -> label.name.get(uiLanguage())
    is VariantLabel.OfEquipment -> stringResource(equipmentLabel(label.equipment))
    null -> null
}

/** Title and variant label on one line, where there is no room for a subtitle: "Lat Pulldown · Wide grip". */
@Composable
internal fun ExerciseName.fullTitle(): String = listOfNotNull(title(), subtitle()).joinToString(" · ")

internal fun equipmentLabel(equipment: Equipment): StringResource = when (equipment) {
    Equipment.BARBELL -> Res.string.session_equipment_barbell
    Equipment.EZ_BAR -> Res.string.session_equipment_ez_bar
    Equipment.TRAP_BAR -> Res.string.session_equipment_trap_bar
    Equipment.DUMBBELL -> Res.string.session_equipment_dumbbell
    Equipment.KETTLEBELL -> Res.string.session_equipment_kettlebell
    Equipment.SMITH -> Res.string.session_equipment_smith
    Equipment.MACHINE -> Res.string.session_equipment_machine
    Equipment.CABLE -> Res.string.session_equipment_cable
    Equipment.BAND -> Res.string.session_equipment_band
    Equipment.BODYWEIGHT -> Res.string.session_equipment_bodyweight
    Equipment.MEDICINE_BALL -> Res.string.session_equipment_medicine_ball
    Equipment.SWISS_BALL -> Res.string.session_equipment_swiss_ball
    Equipment.FOAM_ROLLER -> Res.string.session_equipment_foam_roller
    Equipment.CARDIO_MACHINE -> Res.string.session_equipment_cardio_machine
    Equipment.OTHER -> Res.string.session_equipment_other
    Equipment.NONE -> Res.string.session_equipment_none
}

@Composable
internal fun unitLabel(unit: WeightUnit): String =
    stringResource(if (unit == WeightUnit.KG) Res.string.session_unit_kg else Res.string.session_unit_lb)

/** "62.5 kg" in the user's unit. */
@Composable
internal fun weightText(grams: Long, unit: WeightUnit): String = "${Weights.format(grams, unit)} ${unitLabel(unit)}"

/** A volume (gram-reps) rounded like everywhere else ([formatVolume]): "908 kg". */
@Composable
internal fun volumeText(
    gramReps: Long,
    unit: WeightUnit
): String = "${Weights.formatVolume(gramReps, unit)} ${unitLabel(unit)}"

/** A load as entered: weight, signed bodyweight load, plate number or band level. */
@Composable
internal fun loadText(loadGrams: Long?, loadDetail: String?, loadType: LoadType, unit: WeightUnit): String = when {
    loadType == LoadType.BODYWEIGHT && (loadGrams == null || loadGrams == 0L) -> stringResource(
        Res.string.session_bodyweight
    )
    loadGrams == null -> stringResource(Res.string.session_no_load)
    loadType == LoadType.BAND -> stringResource(Res.string.session_band_level, loadGrams.toInt())
    loadType == LoadType.MACHINE && isMachinePlate(loadDetail) -> stringResource(
        Res.string.session_plate_number,
        loadGrams.toInt()
    )
    loadType == LoadType.BODYWEIGHT && loadGrams > 0 -> "+${weightText(loadGrams, unit)}"
    loadType == LoadType.BODYWEIGHT -> "−${weightText(-loadGrams, unit)}"
    else -> weightText(loadGrams, unit)
}

/** The values of a set row: "60 kg × 8", "45 s", "+10 kg × 6", "1000 m · 5:00". */
@Composable
internal fun setValuesText(set: SetLog, trackingType: TrackingType, loadType: LoadType, unit: WeightUnit): String {
    val parts = buildList {
        if (Prefill.tracksLoad(trackingType, loadType)) add(loadText(set.loadGrams, set.loadDetail, loadType, unit))
        if (trackingType.tracksDistance) add(stringResource(Res.string.session_meters, set.distanceM ?: 0))
        if (trackingType.tracksDuration) add(stringResource(Res.string.session_seconds, set.durationS ?: 0))
    }
    val reps = if (trackingType.tracksReps) (set.reps ?: 0).toString() else null
    val main = parts.joinToString(" · ")
    return when {
        reps == null -> main
        main.isEmpty() -> "× $reps"
        else -> "$main × $reps"
    }
}

/** Short kind marker for non-working sets ("W", "D", "F"), or null for working sets. */
@Composable
internal fun kindShort(kind: SetKind): String? = when (kind) {
    SetKind.WARMUP -> stringResource(Res.string.session_kind_warmup_short)
    SetKind.DROP -> stringResource(Res.string.session_kind_drop_short)
    SetKind.FAILURE -> stringResource(Res.string.session_kind_failure_short)
    SetKind.WORKING -> null
}
