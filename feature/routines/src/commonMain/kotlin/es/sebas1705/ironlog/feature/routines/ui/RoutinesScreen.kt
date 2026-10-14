package es.sebas1705.ironlog.feature.routines.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_placeholder
import es.sebas1705.ironlog.feature.routines.resources.routines_title
import org.jetbrains.compose.resources.stringResource

/** Placeholder until the routines feature is built. */
@Composable
fun RoutinesScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.routines_title),
        message = stringResource(Res.string.routines_placeholder),
        modifier = modifier,
    )
}
