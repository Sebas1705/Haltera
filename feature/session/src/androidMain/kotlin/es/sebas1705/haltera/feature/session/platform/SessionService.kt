package es.sebas1705.haltera.feature.session.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.feature.session.R
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.timer.TimerMath
import es.sebas1705.haltera.feature.session.timer.TimerPhase
import es.sebas1705.haltera.feature.session.timer.TimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground service (type `specialUse`, spec §5) that keeps a persistent notification with the current
 * exercise and the remaining rest, with Skip and +15 s actions. It renders the persisted timer; the
 * countdown itself is drawn by the system chronometer, so nothing ticks here except at phase ends.
 */
class SessionService : Service(), KoinComponent {
    private val timer: RestTimerController by inject()
    private val sessions: SessionRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SessionNotifications.ensureChannels(this)
        startInForeground(build(timer.state.value, System.currentTimeMillis()))
        scope.launch { timer.state.collectLatest { render(it) } }
        // The notification never outlives its session, whoever ended it (finished, discarded or deleted).
        scope.launch {
            sessions.observeInProgress().first { it == null }
            shutDown()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SKIP -> timer.skip()
            ACTION_PLUS -> timer.adjustRest(PLUS_SECONDS)
        }
        // Not sticky: a killed service must not come back on its own without a session to show.
        return START_NOT_STICKY
    }

    /** The app was swiped away from recents: the timer notification goes with it (rest alarms stay armed). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        shutDown()
        super.onTaskRemoved(rootIntent)
    }

    private fun shutDown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Re-posts the notification now and at each phase end, until the timer changes (collectLatest). */
    private suspend fun render(snapshot: TimerSnapshot) {
        if (snapshot.sessionId == null) {
            shutDown()
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        while (true) {
            val now = System.currentTimeMillis()
            if (SessionNotifications.canPost(this)) manager.notify(SessionNotifications.ID_LIVE, build(snapshot, now))
            val end = TimerMath.phaseEndsAt(snapshot, now) ?: return
            delay(end - now + PHASE_SLACK_MS)
        }
    }

    private fun build(snapshot: TimerSnapshot, now: Long): Notification {
        val phase = TimerMath.phase(snapshot, now)
        val end = TimerMath.phaseEndsAt(snapshot, now)
        val text = getString(
            when (phase) {
                TimerPhase.REST -> R.string.session_notification_rest
                TimerPhase.WORK -> R.string.session_notification_work
                TimerPhase.COUNTDOWN -> R.string.session_notification_ready
                TimerPhase.IDLE -> R.string.session_notification_training
            },
        )
        val builder = SessionNotifications.builder(this, SessionNotifications.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_session_timer)
            .setContentTitle(snapshot.label.ifBlank { getString(R.string.session_notification_title) })
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(SessionNotifications.openApp(this))
        if (end != null) {
            builder.setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        if (phase == TimerPhase.REST) {
            builder.addAction(action(ACTION_SKIP, R.string.session_notification_skip))
            builder.addAction(action(ACTION_PLUS, R.string.session_notification_plus))
        }
        return builder.build()
    }

    private fun action(action: String, title: Int): Notification.Action {
        val intent = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, SessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_session_timer),
            getString(title),
            intent
        ).build()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SessionNotifications.ID_LIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(SessionNotifications.ID_LIVE, notification)
        }
    }

    internal companion object {
        const val ACTION_SKIP = "es.sebas1705.haltera.session.SKIP"
        const val ACTION_PLUS = "es.sebas1705.haltera.session.PLUS_15"
        private const val PLUS_SECONDS = 15
        private const val PHASE_SLACK_MS = 50L
    }
}

/** Starts and stops [SessionService]. A refused start (background limits) only loses the notification. */
internal class AndroidLiveSessionIndicator(private val context: Context) : LiveSessionIndicator {
    override fun show(sessionId: String) {
        val intent = Intent(context, SessionService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(
                    intent
                )
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Session service not started", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Session service not allowed", e)
        }
    }

    override fun hide() {
        context.stopService(Intent(context, SessionService::class.java))
    }

    private companion object {
        const val TAG = "SessionService"
    }
}
