package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings → Privacy → "Send crash reports": whether the platform crash reporter (Crashlytics in Android release
 * builds) may send a report when the app crashes. On by default; the user can turn it off. Device-local on purpose,
 * like [CloudBackupStore]: never synced, and "Also erase data on this device" keeps it.
 */
interface CrashReportsStore {
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
}

/** [CrashReportsStore] on multiplatform-settings under `privacy.crashReports`. An absent key reads as on. */
class SettingsCrashReportsStore(private val store: Settings) : CrashReportsStore {
    private val mutable = MutableStateFlow(store.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = mutable.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        store.putBoolean(KEY_ENABLED, enabled)
        mutable.value = enabled
    }

    private companion object {
        const val KEY_ENABLED = "privacy.crashReports"
    }
}
