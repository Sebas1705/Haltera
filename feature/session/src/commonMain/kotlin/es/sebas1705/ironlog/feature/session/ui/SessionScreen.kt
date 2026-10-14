package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_placeholder
import es.sebas1705.ironlog.feature.session.resources.session_title
import org.jetbrains.compose.resources.stringResource

/** Placeholder until the session feature is built. */
@Composable
@Suppress("UnusedParameter") // The feature implementation loads this session.
fun SessionScreen(sessionId: String, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.session_title),
        message = stringResource(Res.string.session_placeholder),
        modifier = modifier,
    )
}
