package es.sebas1705.ironlog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    isLoading    : Boolean,
    error        : String?,
    onLogin      : (String) -> Unit,
    onAnonymous  : () -> Unit,
    onClearError : () -> Unit,
) {
    var token by remember { mutableStateOf("") }

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ironlog", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(8.dp))
        Text("Template de proyecto", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(40.dp))

        AnimatedVisibility(error != null) {
            Card(
                colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearError) { Text("✕") }
                }
            }
        }

        OutlinedTextField(
            value         = token,
            onValueChange = { token = it },
            label         = { Text("Token de acceso") },
            placeholder   = { Text("Pega tu API token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            shape                = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick  = { onLogin(token) },
            enabled  = token.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = MaterialTheme.shapes.medium,
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Iniciar sesión")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick  = onAnonymous,
            enabled  = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = MaterialTheme.shapes.medium,
        ) { Text("Continuar sin cuenta") }
    }
}
