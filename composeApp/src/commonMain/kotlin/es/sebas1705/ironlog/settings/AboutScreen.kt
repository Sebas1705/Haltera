package es.sebas1705.ironlog.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import es.sebas1705.ironlog.core.ui.components.HealthDisclaimer
import es.sebas1705.ironlog.core.ui.components.HealthDisclaimerKind
import es.sebas1705.ironlog.core.ui.components.IronlogLogo
import es.sebas1705.ironlog.core.units.appLanguage
import es.sebas1705.ironlog.resources.Res
import es.sebas1705.ironlog.resources.about_delete_account
import es.sebas1705.ironlog.resources.about_health
import es.sebas1705.ironlog.resources.about_legal
import es.sebas1705.ironlog.resources.about_license
import es.sebas1705.ironlog.resources.about_privacy
import es.sebas1705.ironlog.resources.about_title
import es.sebas1705.ironlog.resources.about_version
import es.sebas1705.ironlog.resources.app_name
import es.sebas1705.ironlog.resources.settings_credits
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * About: name, version, licence, the health disclaimer, credits and the two web pages Google Play asks
 * for (privacy policy and account deletion). Links open in the system browser.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onCredits: () -> Unit, info: AppInfo = koinInject()) {
    val uriHandler = LocalUriHandler.current
    val language = appLanguage()

    // No browser installed (or a refused intent) must not crash the app.
    fun open(url: String) = runCatching { uriHandler.openUri(url) }
    BackScaffold(Res.string.about_title, onBack) { modifier ->
        Column(
            modifier.fillMaxSize().centeredContent().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IronlogLogo(size = 72.dp)
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(Res.string.about_version, info.versionName, info.versionCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(Res.string.about_license), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.about_health), style = MaterialTheme.typography.titleSmall)
                HealthDisclaimer(HealthDisclaimerKind.GENERAL)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(Res.string.about_legal), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { open(LegalLinks.privacyPolicy(language)) }) {
                    Text(stringResource(Res.string.about_privacy))
                }
                TextButton(onClick = { open(LegalLinks.deleteAccount(language)) }) {
                    Text(stringResource(Res.string.about_delete_account))
                }
                TextButton(onClick = onCredits) { Text(stringResource(Res.string.settings_credits)) }
            }
        }
    }
}
