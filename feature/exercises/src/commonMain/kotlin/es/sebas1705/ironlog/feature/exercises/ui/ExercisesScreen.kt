package es.sebas1705.ironlog.feature.exercises.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.feature.exercises.resources.Res
import es.sebas1705.ironlog.feature.exercises.resources.exercises_placeholder
import es.sebas1705.ironlog.feature.exercises.resources.exercises_title
import org.jetbrains.compose.resources.stringResource

/** Placeholder until the exercises feature is built. */
@Composable
fun ExercisesScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.exercises_title),
        message = stringResource(Res.string.exercises_placeholder),
        modifier = modifier,
    )
}
