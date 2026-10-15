package es.sebas1705.haltera.feature.routines.ui.components

import androidx.compose.runtime.Composable
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_category_calisthenics
import es.sebas1705.haltera.feature.routines.resources.routines_category_cardio
import es.sebas1705.haltera.feature.routines.resources.routines_category_mobility
import es.sebas1705.haltera.feature.routines.resources.routines_category_plyometrics
import es.sebas1705.haltera.feature.routines.resources.routines_category_sport
import es.sebas1705.haltera.feature.routines.resources.routines_category_strength
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_abdominals
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_abductors
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_adductors
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_biceps
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_calves
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_chest
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_forearms
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_full_body
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_glutes
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_hamstrings
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_lats
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_lower_back
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_middle_back
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_neck
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_quadriceps
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_shoulders
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_traps
import es.sebas1705.haltera.feature.routines.resources.routines_muscle_triceps
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun categoryLabel(category: ExerciseCategory): String = stringResource(
    when (category) {
        ExerciseCategory.STRENGTH -> Res.string.routines_category_strength
        ExerciseCategory.CARDIO -> Res.string.routines_category_cardio
        ExerciseCategory.MOBILITY -> Res.string.routines_category_mobility
        ExerciseCategory.PLYOMETRICS -> Res.string.routines_category_plyometrics
        ExerciseCategory.CALISTHENICS -> Res.string.routines_category_calisthenics
        ExerciseCategory.SPORT -> Res.string.routines_category_sport
    },
)

@Composable
internal fun muscleLabel(muscle: Muscle): String = stringResource(
    when (muscle) {
        Muscle.ABDOMINALS -> Res.string.routines_muscle_abdominals
        Muscle.ABDUCTORS -> Res.string.routines_muscle_abductors
        Muscle.ADDUCTORS -> Res.string.routines_muscle_adductors
        Muscle.BICEPS -> Res.string.routines_muscle_biceps
        Muscle.CALVES -> Res.string.routines_muscle_calves
        Muscle.CHEST -> Res.string.routines_muscle_chest
        Muscle.FOREARMS -> Res.string.routines_muscle_forearms
        Muscle.GLUTES -> Res.string.routines_muscle_glutes
        Muscle.HAMSTRINGS -> Res.string.routines_muscle_hamstrings
        Muscle.LATS -> Res.string.routines_muscle_lats
        Muscle.LOWER_BACK -> Res.string.routines_muscle_lower_back
        Muscle.MIDDLE_BACK -> Res.string.routines_muscle_middle_back
        Muscle.NECK -> Res.string.routines_muscle_neck
        Muscle.QUADRICEPS -> Res.string.routines_muscle_quadriceps
        Muscle.SHOULDERS -> Res.string.routines_muscle_shoulders
        Muscle.TRAPS -> Res.string.routines_muscle_traps
        Muscle.TRICEPS -> Res.string.routines_muscle_triceps
        Muscle.FULL_BODY -> Res.string.routines_muscle_full_body
    },
)
