package es.sebas1705.ironlog.feature.session.timer

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent home of the [TimerSnapshot]: a few keys, so the timer survives process death. */
interface TimerStore {
    val state: StateFlow<TimerSnapshot>

    fun save(snapshot: TimerSnapshot)
}

/** [TimerStore] on multiplatform-settings (SharedPreferences / NSUserDefaults). */
class SettingsTimerStore(private val store: Settings) : TimerStore {
    private val mutable = MutableStateFlow(read())
    override val state: StateFlow<TimerSnapshot> = mutable.asStateFlow()

    override fun save(snapshot: TimerSnapshot) {
        mutable.value = snapshot
        putOrRemove(KEY_SESSION, snapshot.sessionId)
        store.putString(KEY_LABEL, snapshot.label)
        putOrRemove(KEY_SET, snapshot.setId)
        putOrRemove(KEY_COUNTDOWN, snapshot.countdownEndsAt)
        putOrRemove(KEY_WORK, snapshot.workEndsAt)
        putOrRemove(KEY_REST_START, snapshot.restStartsAt)
        putOrRemove(KEY_REST_END, snapshot.restEndsAt)
    }

    private fun read() = TimerSnapshot(
        sessionId = store.getStringOrNull(KEY_SESSION),
        label = store.getString(KEY_LABEL, ""),
        setId = store.getStringOrNull(KEY_SET),
        countdownEndsAt = store.getLongOrNull(KEY_COUNTDOWN),
        workEndsAt = store.getLongOrNull(KEY_WORK),
        restStartsAt = store.getLongOrNull(KEY_REST_START),
        restEndsAt = store.getLongOrNull(KEY_REST_END),
    )

    private fun putOrRemove(key: String, value: String?) =
        if (value == null) store.remove(key) else store.putString(key, value)

    private fun putOrRemove(key: String, value: Long?) =
        if (value == null) store.remove(key) else store.putLong(key, value)

    private companion object {
        const val KEY_SESSION = "session.timer.sessionId"
        const val KEY_LABEL = "session.timer.label"
        const val KEY_SET = "session.timer.setId"
        const val KEY_COUNTDOWN = "session.timer.countdownEndsAt"
        const val KEY_WORK = "session.timer.workEndsAt"
        const val KEY_REST_START = "session.timer.restStartsAt"
        const val KEY_REST_END = "session.timer.restEndsAt"
    }
}
