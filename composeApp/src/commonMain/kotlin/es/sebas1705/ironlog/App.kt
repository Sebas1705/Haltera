package es.sebas1705.ironlog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.adaptive.ProvideWindowSize
import es.sebas1705.ironlog.core.ui.theme.AppTheme
import es.sebas1705.ironlog.navigation.MainShell
import es.sebas1705.ironlog.startup.StartupIntent
import es.sebas1705.ironlog.startup.StartupScreen
import es.sebas1705.ironlog.startup.StartupViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Root composable: imports the catalogue, then shows the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme {
        Surface(modifier.fillMaxSize()) {
            val startup: StartupViewModel = koinViewModel()
            val state by startup.uiState.collectAsStateWithLifecycle()
            if (state.ready) {
                ProvideWindowSize {
                    MainShell()
                }
            } else {
                StartupScreen(state, onRetry = { startup.onEvent(StartupIntent.Start) })
            }
        }
    }
}
