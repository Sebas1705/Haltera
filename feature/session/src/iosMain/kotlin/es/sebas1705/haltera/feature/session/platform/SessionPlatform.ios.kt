package es.sebas1705.haltera.feature.session.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import es.sebas1705.haltera.feature.session.resources.Res
import es.sebas1705.haltera.feature.session.resources.session_alert_rest_over
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual val sessionPlatformModule: Module = module {
    single<RestAlert> { IosRestAlert() }
    single<LiveSessionIndicator> { NoLiveSessionIndicator }
    single { ExactAlarmPrompt(NoExactAlarmAccess, InMemoryExactAlarmPromptStore()) }
}

/** Local notifications at the absolute instants, rescheduled on every change (spec §5). */
internal class IosRestAlert : RestAlert {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private val scope = MainScope()
    private var job: Job? = null

    override fun schedule(instants: List<Long>, label: String, sound: Boolean, vibrate: Boolean) {
        cancel()
        job = scope.launch {
            val body = getString(Res.string.session_alert_rest_over)
            val nowMs = (NSDate().timeIntervalSince1970 * MILLIS).toLong()
            instants.take(MAX_ALERTS).forEachIndexed { index, at ->
                val seconds = (at - nowMs) / MILLIS
                if (seconds <= 0) return@forEachIndexed
                val content = UNMutableNotificationContent().apply {
                    setTitle(label)
                    setBody(body)
                    if (sound) setSound(UNNotificationSound.defaultSound)
                }
                val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
                center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id(index), content, trigger), null)
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        center.removePendingNotificationRequestsWithIdentifiers(List(MAX_ALERTS) { id(it) })
    }

    private fun id(index: Int) = "haltera.session.rest.$index"

    private companion object {
        const val MILLIS = 1_000.0
        const val MAX_ALERTS = 2
    }
}

/** Live Activities arrive in F9; nothing persistent on iOS for now. */
internal object NoLiveSessionIndicator : LiveSessionIndicator {
    override fun show(sessionId: String) = Unit

    override fun hide() = Unit
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { if (enabled) UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun RequestSessionPermissions() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }
}
