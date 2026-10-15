package es.sebas1705.ironlog.feature.session.platform

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
    single { ExactAlarmPrompt(AndroidExactAlarmAccess(get<Context>()), PrefsExactAlarmPromptStore(get<Context>())) }
}

/** Android 12+: SCHEDULE_EXACT_ALARM is a special access the user grants in system settings. */
internal class AndroidExactAlarmAccess(private val context: Context) : ExactAlarmAccess {
    override fun isMissing(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return !alarms.canScheduleExactAlarms()
    }

    override fun openSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No settings screen to open: the prompt was still the one time.
        }
    }
}

/** The "answered" flag lives next to the notification one in the session preferences. */
internal class PrefsExactAlarmPromptStore(context: Context) : ExactAlarmPromptStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var handled: Boolean
        get() = prefs.getBoolean(KEY_EXACT_HANDLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXACT_HANDLED, value).apply()
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
private const val KEY_EXACT_HANDLED = "exactAlarmPromptHandled"
private const val REQUEST_CODE = 1705
