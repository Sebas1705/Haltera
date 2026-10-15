package es.sebas1705.haltera.feature.today.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.feature.today.resources.Res
import es.sebas1705.haltera.feature.today.resources.today_equipment_band
import es.sebas1705.haltera.feature.today.resources.today_equipment_barbell
import es.sebas1705.haltera.feature.today.resources.today_equipment_bodyweight
import es.sebas1705.haltera.feature.today.resources.today_equipment_cable
import es.sebas1705.haltera.feature.today.resources.today_equipment_cardio_machine
import es.sebas1705.haltera.feature.today.resources.today_equipment_dumbbell
import es.sebas1705.haltera.feature.today.resources.today_equipment_ez_bar
import es.sebas1705.haltera.feature.today.resources.today_equipment_foam_roller
import es.sebas1705.haltera.feature.today.resources.today_equipment_kettlebell
import es.sebas1705.haltera.feature.today.resources.today_equipment_machine
import es.sebas1705.haltera.feature.today.resources.today_equipment_medicine_ball
import es.sebas1705.haltera.feature.today.resources.today_equipment_other
import es.sebas1705.haltera.feature.today.resources.today_equipment_smith
import es.sebas1705.haltera.feature.today.resources.today_equipment_swiss_ball
import es.sebas1705.haltera.feature.today.resources.today_equipment_trap_bar
import es.sebas1705.haltera.feature.today.resources.today_prescription_distance
import es.sebas1705.haltera.feature.today.resources.today_prescription_duration
import es.sebas1705.haltera.feature.today.resources.today_prescription_rep_range
import es.sebas1705.haltera.feature.today.resources.today_prescription_reps
import es.sebas1705.haltera.feature.today.resources.today_unit_kg
import es.sebas1705.haltera.feature.today.resources.today_unit_lb
import es.sebas1705.haltera.feature.today.resources.today_weekday_1
import es.sebas1705.haltera.feature.today.resources.today_weekday_2
import es.sebas1705.haltera.feature.today.resources.today_weekday_3
import es.sebas1705.haltera.feature.today.resources.today_weekday_4
import es.sebas1705.haltera.feature.today.resources.today_weekday_5
import es.sebas1705.haltera.feature.today.resources.today_weekday_6
import es.sebas1705.haltera.feature.today.resources.today_weekday_7
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The UI language code ("en", "es"…), used to pick catalogue names. */
@Composable
internal fun currentLanguage(): String = Locale.current.language

@Composable
internal fun unitLabel(unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.KG -> Res.string.today_unit_kg
        WeightUnit.LB -> Res.string.today_unit_lb
    }
)

@Composable
internal fun prescriptionText(sets: Int, prescription: Prescription): String = when (prescription) {
    is Prescription.RepRange -> stringResource(
        Res.string.today_prescription_rep_range,
        sets,
        prescription.min,
        prescription.max
    )
    is Prescription.Reps -> stringResource(Res.string.today_prescription_reps, sets, prescription.reps)
    is Prescription.Duration -> stringResource(Res.string.today_prescription_duration, sets, prescription.seconds)
    is Prescription.Distance -> stringResource(Res.string.today_prescription_distance, sets, prescription.meters)
}

private val WEEKDAYS = listOf(
    Res.string.today_weekday_1,
    Res.string.today_weekday_2,
    Res.string.today_weekday_3,
    Res.string.today_weekday_4,
    Res.string.today_weekday_5,
    Res.string.today_weekday_6,
    Res.string.today_weekday_7,
)

/** Short label of an ISO day of week (1 = Monday … 7 = Sunday). */
internal fun weekdayLabel(isoDayOfWeek: Int): StringResource = WEEKDAYS.getOrElse(isoDayOfWeek - 1) { WEEKDAYS.last() }

/** Label of an equipment, or null for [Equipment.NONE]. */
internal fun equipmentLabel(equipment: Equipment): StringResource? = when (equipment) {
    Equipment.BARBELL -> Res.string.today_equipment_barbell
    Equipment.EZ_BAR -> Res.string.today_equipment_ez_bar
    Equipment.TRAP_BAR -> Res.string.today_equipment_trap_bar
    Equipment.DUMBBELL -> Res.string.today_equipment_dumbbell
    Equipment.KETTLEBELL -> Res.string.today_equipment_kettlebell
    Equipment.SMITH -> Res.string.today_equipment_smith
    Equipment.MACHINE -> Res.string.today_equipment_machine
    Equipment.CABLE -> Res.string.today_equipment_cable
    Equipment.BAND -> Res.string.today_equipment_band
    Equipment.BODYWEIGHT -> Res.string.today_equipment_bodyweight
    Equipment.MEDICINE_BALL -> Res.string.today_equipment_medicine_ball
    Equipment.SWISS_BALL -> Res.string.today_equipment_swiss_ball
    Equipment.FOAM_ROLLER -> Res.string.today_equipment_foam_roller
    Equipment.CARDIO_MACHINE -> Res.string.today_equipment_cardio_machine
    Equipment.OTHER -> Res.string.today_equipment_other
    Equipment.NONE -> null
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** Elapsed time as "m:ss" or "h:mm:ss"; negative durations count as zero. */
internal fun formatElapsed(millis: Long): String {
    val total = (millis / 1_000).coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}
