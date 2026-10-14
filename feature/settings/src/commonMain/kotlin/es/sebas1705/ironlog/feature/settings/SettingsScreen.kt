package es.sebas1705.ironlog.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state         : SettingsState,
    onDarkMode    : (Boolean) -> Unit,
    onSaveUrl     : (String) -> Unit,
    onSaveToken   : (String) -> Unit,
    onLogout      : () -> Unit,
    onClearSuccess: () -> Unit,
    modifier      : Modifier = Modifier,
) {
    var urlDraft   by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var tokenDraft by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        // Dark mode
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Modo oscuro", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.isDarkMode, onCheckedChange = onDarkMode)
            }
        }
        Spacer(Modifier.height(16.dp))

        // API URL
        Text("API", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(urlDraft, { urlDraft = it }, label = { Text("URL base") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(tokenDraft, { tokenDraft = it }, label = { Text("Token (${state.tokenMasked.ifBlank { "no configurado" }})") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            onSaveUrl(urlDraft)
            if (tokenDraft.isNotBlank()) { onSaveToken(tokenDraft); tokenDraft = "" }
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
            Text("Guardar")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout,
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
            Text("Cerrar sesión")
        }
    }
}
