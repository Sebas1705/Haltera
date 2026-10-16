package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's choice about the optional cloud copy.
 *
 * @property enabled The cloud copy is on: only then does the app sign in and talk to Firebase.
 * @property promptHandled The first-launch question was answered (Yes or Not now) or replaced by the notice.
 * @property noticePending An install that was already syncing before the choice existed: show the one-time notice.
 */
data class CloudBackupState(
    val enabled: Boolean = false,
    val promptHandled: Boolean = false,
    val noticePending: Boolean = false,
)

/**
 * Whether the logbook is copied to the user's private cloud space. Device-local on purpose: like [LoadStepStore]
 * it never goes through [SettingsRepository] or sync, and "Also erase data on this device" keeps it.
 */
interface CloudBackupStore {
    val state: StateFlow<CloudBackupState>

    /** Same as `state.value.enabled`, for the sync engine. */
    val enabled: StateFlow<Boolean>

    /** Turns the cloud copy on or off; either way the first-launch question counts as answered. */
    fun setEnabled(enabled: Boolean)

    /** "Not now": the first-launch question is not shown again; the cloud copy stays off. */
    fun dismissPrompt()

    /**
     * An install that was signed in before this choice existed keeps syncing: on, with a one-time notice.
     * Does nothing once the user (or an earlier call) made the choice.
     */
    fun adoptExistingAccount()

    fun dismissNotice()
}

/** [CloudBackupStore] on multiplatform-settings under `cloud.*`. Absent keys read as "never decided, off". */
class SettingsCloudBackupStore(private val store: Settings) : CloudBackupStore {
    private val mutable = MutableStateFlow(read())
    private val enabledFlow = MutableStateFlow(mutable.value.enabled)
    override val state: StateFlow<CloudBackupState> = mutable.asStateFlow()
    override val enabled: StateFlow<Boolean> = enabledFlow.asStateFlow()

    override fun setEnabled(enabled: Boolean) = write { it.copy(enabled = enabled, promptHandled = true) }

    override fun dismissPrompt() = write { it.copy(promptHandled = true) }

    override fun adoptExistingAccount() {
        if (store.hasKey(KEY_ENABLED) || store.hasKey(KEY_PROMPT_HANDLED)) return
        write { CloudBackupState(enabled = true, promptHandled = true, noticePending = true) }
    }

    override fun dismissNotice() = write { it.copy(noticePending = false) }

    private fun write(change: (CloudBackupState) -> CloudBackupState) {
        val s = change(mutable.value)
        store.putBoolean(KEY_ENABLED, s.enabled)
        store.putBoolean(KEY_PROMPT_HANDLED, s.promptHandled)
        store.putBoolean(KEY_NOTICE_PENDING, s.noticePending)
        mutable.value = s
        enabledFlow.value = s.enabled
    }

    private fun read() = CloudBackupState(
        enabled = store.getBoolean(KEY_ENABLED, false),
        promptHandled = store.getBoolean(KEY_PROMPT_HANDLED, false),
        noticePending = store.getBoolean(KEY_NOTICE_PENDING, false),
    )

    private companion object {
        const val KEY_ENABLED = "cloud.enabled"
        const val KEY_PROMPT_HANDLED = "cloud.promptHandled"
        const val KEY_NOTICE_PENDING = "cloud.noticePending"
    }
}
