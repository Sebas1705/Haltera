package es.sebas1705.ironlog.feature.session.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import es.sebas1705.ironlog.feature.session.R

/** Channels, ids and helpers shared by the session service and the rest alarm. */
internal object SessionNotifications {
    const val CHANNEL_LIVE = "ironlog.session.live"
    const val CHANNEL_ALERT = "ironlog.session.alert"
    const val ID_LIVE = 1705
    const val ID_ALERT = 1706

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIVE,
                context.getString(R.string.session_channel_live),
                NotificationManager.IMPORTANCE_LOW
            )
                .apply {
                    description = context.getString(R.string.session_channel_live_description)
                    setShowBadge(false)
                },
        )
        // Sound and vibration are played by the alarm receiver according to the user's settings.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.session_channel_alert),
                NotificationManager.IMPORTANCE_HIGH
            )
                .apply {
                    description = context.getString(R.string.session_channel_alert_description)
                    setSound(null, null)
                    enableVibration(false)
                },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun builder(context: Context, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

    /** Opens the app (the shell resumes the session from Today). */
    fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
}
