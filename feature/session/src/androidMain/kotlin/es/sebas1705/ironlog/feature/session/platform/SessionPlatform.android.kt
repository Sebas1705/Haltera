package es.sebas1705.ironlog.feature.session.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val sessionPlatformModule: Module = module {
    single<RestAlert> { AndroidRestAlert(get<Context>()) }
    single<LiveSessionIndicator> { AndroidLiveSessionIndicator(get<Context>()) }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) view.keepScreenOn = true
        onDispose { if (enabled) view.keepScreenOn = false }
    }
}

/** Android 13+: asks for POST_NOTIFICATIONS the first time a session is open (never again after that). */
@Composable
actual fun RequestSessionPermissions() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return@LaunchedEffect
        }
        val activity = context.findActivity() ?: return@LaunchedEffect
        val firstTime = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val asked = prefs.getBoolean(KEY_ASKED, false)
            if (!asked) prefs.edit().putBoolean(KEY_ASKED, true).apply()
            !asked
        }
        if (firstTime) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PREFS = "ironlog_session"
private const val KEY_ASKED = "notificationsAsked"
private const val REQUEST_CODE = 1705
