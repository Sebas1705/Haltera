package es.sebas1705.ironlog.feature.progress.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_placeholder
import es.sebas1705.ironlog.feature.progress.resources.progress_title
import org.jetbrains.compose.resources.stringResource

/** Placeholder until the progress feature is built. */
@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.progress_title),
        message = stringResource(Res.string.progress_placeholder),
        modifier = modifier,
    )
}
