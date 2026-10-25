package es.sebas1705.haltera.health

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import es.sebas1705.haltera.settings.LegalLinks
import java.util.Locale

/**
 * Required by Health Connect: opened from its permission screen ("Read privacy policy", Android 13 and older:
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) and from the system's app-permission usage page (Android 14+:
 * `VIEW_PERMISSION_USAGE` with `HEALTH_PERMISSIONS`). It shows the privacy policy, whose Health Connect section
 * says what is read, why, and that nothing is shared or used for ads, then closes.
 */
class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = LegalLinks.privacyPolicy(Locale.getDefault().language)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (@Suppress("SwallowedException") e: ActivityNotFoundException) {
            // No browser: nothing else to show.
        }
        finish()
    }
}
