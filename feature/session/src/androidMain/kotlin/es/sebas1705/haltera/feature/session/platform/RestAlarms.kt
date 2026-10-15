package es.sebas1705.ironlog.feature.session.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import es.sebas1705.ironlog.feature.session.R

/**
 * Rest alerts with [AlarmManager] at the absolute instants (spec §5): exact while idle when the app may
 * schedule exact alarms (`SCHEDULE_EXACT_ALARM`), otherwise an inexact alarm (the in-app timer and the
 * notification chronometer stay exact).
 */
internal class AndroidRestAlert(private val context: Context) : RestAlert {
    private val alarms: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(instants: List<Long>, label: String, sound: Boolean, vibrate: Boolean) {
        cancel()
        instants.take(MAX_ALARMS).forEachIndexed { index, at ->
            val intent = Intent(context, RestAlarmReceiver::class.java)
                .putExtra(RestAlarmReceiver.EXTRA_LABEL, label)
                .putExtra(RestAlarmReceiver.EXTRA_SOUND, sound)
                .putExtra(RestAlarmReceiver.EXTRA_VIBRATE, vibrate)
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            set(at, pending)
        }
    }

    override fun cancel() {
        val manager = alarms ?: return
        repeat(MAX_ALARMS) { index ->
            PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + index,
                Intent(context, RestAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun set(at: Long, pending: PendingIntent) {
        val manager = alarms ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using an inexact one", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private companion object {
        const val TAG = "RestAlert"
        const val REQUEST_BASE = 4_200
        const val MAX_ALARMS = 2
    }
}

/** Fires at the end of a rest or timed set: vibrates and/or sounds per settings and posts a short alert. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_VIBRATE, true)) vibrate(context)
        if (intent.getBooleanExtra(EXTRA_SOUND, true)) {
            RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )?.play()
        }
        post(context, intent.getStringExtra(EXTRA_LABEL).orEmpty())
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(PATTERN, -1)
        }
    }

    private fun post(context: Context, label: String) {
        if (!SessionNotifications.canPost(context)) return
        SessionNotifications.ensureChannels(context)
        val builder = SessionNotifications.builder(context, SessionNotifications.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_session_timer)
            .setContentTitle(context.getString(R.string.session_alert_title))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(SessionNotifications.openApp(context))
        if (label.isNotBlank()) builder.setContentText(context.getString(R.string.session_alert_text, label))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(ALERT_TIMEOUT_MS)
        } else {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_HIGH)
        }
        context.getSystemService(
            NotificationManager::class.java
        )?.notify(SessionNotifications.ID_ALERT, builder.build())
    }

    internal companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_VIBRATE = "vibrate"
        private const val ALERT_TIMEOUT_MS = 60_000L
        private val PATTERN = longArrayOf(0, 300, 150, 300, 150, 500)
    }
}
