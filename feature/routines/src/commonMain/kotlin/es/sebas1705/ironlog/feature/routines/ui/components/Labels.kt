package es.sebas1705.ironlog.feature.routines.ui.components

import androidx.compose.runtime.Composable
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.model.ExerciseIndex
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_cycle_length
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_band
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_barbell
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_bodyweight
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_cable
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_cardio_machine
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_dumbbell
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_ez_bar
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_foam_roller
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_kettlebell
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_machine
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_medicine_ball
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_none
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_other
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_smith
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_swiss_ball
import es.sebas1705.ironlog.feature.routines.resources.routines_equipment_trap_bar
import es.sebas1705.ironlog.feature.routines.resources.routines_level_beginner
import es.sebas1705.ironlog.feature.routines.resources.routines_level_expert
import es.sebas1705.ironlog.feature.routines.resources.routines_level_intermediate
import es.sebas1705.ironlog.feature.routines.resources.routines_prescription_distance_value
import es.sebas1705.ironlog.feature.routines.resources.routines_prescription_duration_value
import es.sebas1705.ironlog.feature.routines.resources.routines_prescription_range_value
import es.sebas1705.ironlog.feature.routines.resources.routines_prescription_reps_value
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_any_variant
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_flexible_label
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_sets_value
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_superset
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_unknown_exercise
import es.sebas1705.ironlog.feature.routines.resources.routines_slot_warmup_value
import es.sebas1705.ironlog.feature.routines.resources.routines_training_days
import es.sebas1705.ironlog.feature.routines.resources.routines_variant_name
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun equipmentLabel(equipment: Equipment): String = stringResource(
    when (equipment) {
        Equipment.BARBELL -> Res.string.routines_equipment_barbell
        Equipment.EZ_BAR -> Res.string.routines_equipment_ez_bar
        Equipment.TRAP_BAR -> Res.string.routines_equipment_trap_bar
        Equipment.DUMBBELL -> Res.string.routines_equipment_dumbbell
        Equipment.KETTLEBELL -> Res.string.routines_equipment_kettlebell
        Equipment.SMITH -> Res.string.routines_equipment_smith
        Equipment.MACHINE -> Res.string.routines_equipment_machine
        Equipment.CABLE -> Res.string.routines_equipment_cable
        Equipment.BAND -> Res.string.routines_equipment_band
        Equipment.BODYWEIGHT -> Res.string.routines_equipment_bodyweight
        Equipment.MEDICINE_BALL -> Res.string.routines_equipment_medicine_ball
        Equipment.SWISS_BALL -> Res.string.routines_equipment_swiss_ball
        Equipment.FOAM_ROLLER -> Res.string.routines_equipment_foam_roller
        Equipment.CARDIO_MACHINE -> Res.string.routines_equipment_cardio_machine
        Equipment.OTHER -> Res.string.routines_equipment_other
        Equipment.NONE -> Res.string.routines_equipment_none
    },
)

@Composable
internal fun levelLabel(level: Level): String = stringResource(
    when (level) {
        Level.BEGINNER -> Res.string.routines_level_beginner
        Level.INTERMEDIATE -> Res.string.routines_level_intermediate
        Level.EXPERT -> Res.string.routines_level_expert
    },
)

/** "Bench press (Barbell)", "Lat Pulldown (Wide grip)": the exercise, then the variant label (ExerciseNaming). */
@Composable
internal fun variantLabel(variant: ExerciseVariant, exercise: Exercise?, language: String): String =
    stringResource(
        Res.string.routines_variant_name,
        exercise?.name?.get(language) ?: stringResource(Res.string.routines_slot_unknown_exercise),
        variantShortLabel(variant, language),
    )

/** Just the variant's distinguishing part: its own name or its equipment. */
@Composable
internal fun variantShortLabel(variant: ExerciseVariant, language: String): String =
    variant.name?.get(language) ?: equipmentLabel(variant.equipment)

/** What a slot trains: "Squat (Barbell)" or "Row · any variant" / "Row · Dumbbell, Cable". */
@Composable
internal fun targetLabel(target: SlotTarget, index: ExerciseIndex, language: String): String {
    val exercise = index.exerciseOf(target) ?: return stringResource(Res.string.routines_slot_unknown_exercise)
    return when (target) {
        is SlotTarget.Fixed -> index.variant(target.variantId)?.let { variantLabel(it, exercise, language) }
            ?: exercise.name.get(language)
        is SlotTarget.Flexible -> {
            val variants = target.allowedVariantIds.mapNotNull(index::variant)
            val which = if (variants.isEmpty()) {
                stringResource(Res.string.routines_slot_any_variant)
            } else {
                variants.map { variantShortLabel(it, language) }.joinToString(", ")
            }
            stringResource(Res.string.routines_slot_flexible_label, exercise.name.get(language), which)
        }
    }
}

/** "8–12 reps", "5 reps", "0:30", "400 m". */
@Composable
internal fun prescriptionLabel(prescription: Prescription): String = when (prescription) {
    is Prescription.RepRange -> if (prescription.min == prescription.max) {
        stringResource(Res.string.routines_prescription_reps_value, prescription.min)
    } else {
        stringResource(Res.string.routines_prescription_range_value, prescription.min, prescription.max)
    }
    is Prescription.Reps -> stringResource(Res.string.routines_prescription_reps_value, prescription.reps)
    is Prescription.Duration ->
        stringResource(Res.string.routines_prescription_duration_value, formatSeconds(prescription.seconds))
    is Prescription.Distance -> stringResource(Res.string.routines_prescription_distance_value, prescription.meters)
}

/** "3 × 8–12 reps · 1 warm-up · Superset A". */
@Composable
internal fun slotSummary(spec: SlotSpec): String = buildList {
    add(stringResource(Res.string.routines_slot_sets_value, spec.sets, prescriptionLabel(spec.prescription)))
    if (spec.warmupSets > 0) {
        add(pluralStringResource(Res.plurals.routines_slot_warmup_value, spec.warmupSets, spec.warmupSets))
    }
    spec.supersetGroup?.let { add(stringResource(Res.string.routines_slot_superset, supersetLetter(it))) }
}.joinToString(" · ")

@Composable
internal fun cycleLabel(days: Int, trainingDays: Int): String =
    pluralStringResource(Res.plurals.routines_cycle_length, days, days) + " · " +
        pluralStringResource(Res.plurals.routines_training_days, trainingDays, trainingDays)

internal fun trainingDays(kinds: List<DayKind>): Int = kinds.count { it == DayKind.TRAINING }

/** Superset group 1 is "A", 2 is "B"… */
internal fun supersetLetter(group: Int): String = ('A' + (group - 1).coerceIn(0, LETTERS - 1)).toString()

/** Seconds as "m:ss" (90 -> "1:30"). */
internal fun formatSeconds(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / SECONDS_PER_MINUTE}:${(s % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val SECONDS_PER_MINUTE = 60
private const val LETTERS = 26
