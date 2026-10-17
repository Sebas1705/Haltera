package es.sebas1705.haltera.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.data.health.HealthConnectPermissions
import es.sebas1705.haltera.share.CurrentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Android: Health Connect's own permission screen (the contract of the client library), Play page and settings. */
class AndroidHealthConnectBridge(private val context: Context) : HealthConnectBridge {

    override suspend fun requestPermissions(types: Set<HealthDataType>) {
        val permissions = HealthConnectPermissions.of(types)
        if (permissions.isEmpty() || Build.VERSION.SDK_INT < MIN_SDK) return
        val activity = CurrentActivity.activity ?: return
        suspendCancellableCoroutine { cont ->
            var launcher: ActivityResultLauncher<Set<String>>? = null
            launcher = activity.activityResultRegistry.register(
                REQUEST_KEY,
                PermissionController.createRequestPermissionResultContract(),
            ) {
                launcher?.unregister()
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(permissions)
        }
    }

    override fun openInstallPage() {
        val market = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER")
        if (!start(Intent(Intent.ACTION_VIEW, market).setPackage(PLAY_STORE))) start(Intent(Intent.ACTION_VIEW, web))
    }

    override fun openManagePermissions() {
        if (Build.VERSION.SDK_INT < MIN_SDK) return
        // Health Connect's home screen, whose "App permissions" entry leads to Haltera's permissions. "Data and access"
        // (the manage-data intent) has no way to them, so it is only the fallback. The per-app page
        // (MANAGE_HEALTH_PERMISSIONS) is reserved to the system and would throw a SecurityException.
        if (!start(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))) {
            start(HealthConnectClient.getHealthConnectManageDataIntent(context))
        }
    }

    private fun start(intent: Intent): Boolean = try {
        val activity = CurrentActivity.activity
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (@Suppress("SwallowedException") e: ActivityNotFoundException) {
        false
    } catch (@Suppress("SwallowedException") e: SecurityException) {
        // A Health Connect screen the app may not open on this version: try the next one.
        false
    }

    private companion object {
        const val REQUEST_KEY = "haltera-health-permissions"
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val PLAY_STORE = "com.android.vending"
        const val MIN_SDK = 28
    }
}
