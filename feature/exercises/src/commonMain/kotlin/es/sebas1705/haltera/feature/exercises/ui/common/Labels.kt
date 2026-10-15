package es.sebas1705.haltera.feature.exercises.ui.common

import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.feature.exercises.resources.Res
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_calisthenics
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_cardio
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_mobility
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_plyometrics
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_sport
import es.sebas1705.haltera.feature.exercises.resources.exercises_category_strength
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_band
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_barbell
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_bodyweight
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_cable
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_cardio_machine
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_dumbbell
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_ez_bar
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_foam_roller
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_kettlebell
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_machine
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_medicine_ball
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_none
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_other
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_smith
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_swiss_ball
import es.sebas1705.haltera.feature.exercises.resources.exercises_equipment_trap_bar
import es.sebas1705.haltera.feature.exercises.resources.exercises_level_beginner
import es.sebas1705.haltera.feature.exercises.resources.exercises_level_expert
import es.sebas1705.haltera.feature.exercises.resources.exercises_level_intermediate
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_abdominals
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_abductors
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_adductors
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_biceps
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_calves
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_chest
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_forearms
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_full_body
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_glutes
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_hamstrings
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_lats
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_lower_back
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_middle_back
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_neck
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_quadriceps
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_shoulders
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_traps
import es.sebas1705.haltera.feature.exercises.resources.exercises_muscle_triceps
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_bodyweight_reps
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_distance_time
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_reps
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_time
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_weight_reps
import es.sebas1705.haltera.feature.exercises.resources.exercises_tracking_weight_time
import es.sebas1705.haltera.feature.exercises.resources.exercises_unit_kg
import es.sebas1705.haltera.feature.exercises.resources.exercises_unit_lb
import org.jetbrains.compose.resources.StringResource

// Display names of the catalogue enums.

internal val ExerciseCategory.label: StringResource
    get() = when (this) {
        ExerciseCategory.STRENGTH -> Res.string.exercises_category_strength
        ExerciseCategory.CARDIO -> Res.string.exercises_category_cardio
        ExerciseCategory.MOBILITY -> Res.string.exercises_category_mobility
        ExerciseCategory.PLYOMETRICS -> Res.string.exercises_category_plyometrics
        ExerciseCategory.CALISTHENICS -> Res.string.exercises_category_calisthenics
        ExerciseCategory.SPORT -> Res.string.exercises_category_sport
    }

internal val Level.label: StringResource
    get() = when (this) {
        Level.BEGINNER -> Res.string.exercises_level_beginner
        Level.INTERMEDIATE -> Res.string.exercises_level_intermediate
        Level.EXPERT -> Res.string.exercises_level_expert
    }

internal val TrackingType.label: StringResource
    get() = when (this) {
        TrackingType.WEIGHT_REPS -> Res.string.exercises_tracking_weight_reps
        TrackingType.BODYWEIGHT_REPS -> Res.string.exercises_tracking_bodyweight_reps
        TrackingType.REPS -> Res.string.exercises_tracking_reps
        TrackingType.TIME -> Res.string.exercises_tracking_time
        TrackingType.WEIGHT_TIME -> Res.string.exercises_tracking_weight_time
        TrackingType.DISTANCE_TIME -> Res.string.exercises_tracking_distance_time
    }

internal val Muscle.label: StringResource
    get() = when (this) {
        Muscle.ABDOMINALS -> Res.string.exercises_muscle_abdominals
        Muscle.ABDUCTORS -> Res.string.exercises_muscle_abductors
        Muscle.ADDUCTORS -> Res.string.exercises_muscle_adductors
        Muscle.BICEPS -> Res.string.exercises_muscle_biceps
        Muscle.CALVES -> Res.string.exercises_muscle_calves
        Muscle.CHEST -> Res.string.exercises_muscle_chest
        Muscle.FOREARMS -> Res.string.exercises_muscle_forearms
        Muscle.GLUTES -> Res.string.exercises_muscle_glutes
        Muscle.HAMSTRINGS -> Res.string.exercises_muscle_hamstrings
        Muscle.LATS -> Res.string.exercises_muscle_lats
        Muscle.LOWER_BACK -> Res.string.exercises_muscle_lower_back
        Muscle.MIDDLE_BACK -> Res.string.exercises_muscle_middle_back
        Muscle.NECK -> Res.string.exercises_muscle_neck
        Muscle.QUADRICEPS -> Res.string.exercises_muscle_quadriceps
        Muscle.SHOULDERS -> Res.string.exercises_muscle_shoulders
        Muscle.TRAPS -> Res.string.exercises_muscle_traps
        Muscle.TRICEPS -> Res.string.exercises_muscle_triceps
        Muscle.FULL_BODY -> Res.string.exercises_muscle_full_body
    }

internal val Equipment.label: StringResource
    get() = when (this) {
        Equipment.BARBELL -> Res.string.exercises_equipment_barbell
        Equipment.EZ_BAR -> Res.string.exercises_equipment_ez_bar
        Equipment.TRAP_BAR -> Res.string.exercises_equipment_trap_bar
        Equipment.DUMBBELL -> Res.string.exercises_equipment_dumbbell
        Equipment.KETTLEBELL -> Res.string.exercises_equipment_kettlebell
        Equipment.SMITH -> Res.string.exercises_equipment_smith
        Equipment.MACHINE -> Res.string.exercises_equipment_machine
        Equipment.CABLE -> Res.string.exercises_equipment_cable
        Equipment.BAND -> Res.string.exercises_equipment_band
        Equipment.BODYWEIGHT -> Res.string.exercises_equipment_bodyweight
        Equipment.MEDICINE_BALL -> Res.string.exercises_equipment_medicine_ball
        Equipment.SWISS_BALL -> Res.string.exercises_equipment_swiss_ball
        Equipment.FOAM_ROLLER -> Res.string.exercises_equipment_foam_roller
        Equipment.CARDIO_MACHINE -> Res.string.exercises_equipment_cardio_machine
        Equipment.OTHER -> Res.string.exercises_equipment_other
        Equipment.NONE -> Res.string.exercises_equipment_none
    }

internal val WeightUnit.label: StringResource
    get() = when (this) {
        WeightUnit.KG -> Res.string.exercises_unit_kg
        WeightUnit.LB -> Res.string.exercises_unit_lb
    }
