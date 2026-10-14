package es.sebas1705.ironlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.theme.AppTheme

/** Root composable. F1c replaces the placeholder with the catalogue status; F2 adds the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme(modifier) {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text("Ironlog", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
