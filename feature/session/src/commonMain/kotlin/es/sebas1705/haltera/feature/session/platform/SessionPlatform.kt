package es.sebas1705.haltera.feature.session.platform

import androidx.compose.runtime.Composable
import org.koin.core.module.Module

/**
 * Alerts at absolute instants even when the app is in the background (spec §5): Android exact alarms,
 * iOS local notifications. Every call replaces what was scheduled before.
 */
interface RestAlert {
    /** Schedules an alert at each instant (epoch ms) of [instants]; [label] is the current exercise. */
    fun schedule(instants: List<Long>, label: String, sound: Boolean, vibrate: Boolean)

    fun cancel()
}

/** The persistent "session in progress" indicator: an Android foreground service; nothing on iOS (F9). */
interface LiveSessionIndicator {
    fun show(sessionId: String)

    fun hide()
}

/** Platform bindings of [RestAlert] and [LiveSessionIndicator]. */
expect val sessionPlatformModule: Module

/** Keeps the display on while in composition and [enabled]. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** Asks once, in context, for what the session needs to alert (notifications). */
@Composable
expect fun RequestSessionPermissions()
