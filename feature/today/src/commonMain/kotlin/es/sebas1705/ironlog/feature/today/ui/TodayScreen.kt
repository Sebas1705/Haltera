package es.sebas1705.ironlog.feature.today.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.components.PlaceholderScreen
import es.sebas1705.ironlog.feature.today.resources.Res
import es.sebas1705.ironlog.feature.today.resources.today_placeholder
import es.sebas1705.ironlog.feature.today.resources.today_title
import org.jetbrains.compose.resources.stringResource

/** Placeholder until the today feature is built. */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.today_title),
        message = stringResource(Res.string.today_placeholder),
        modifier = modifier,
    )
}
