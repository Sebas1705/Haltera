package es.sebas1705.haltera.settings

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.CrashReportsStore
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import kotlinx.coroutines.Job

/**
 * @property crashReportsAvailable The platform has a crash reporter (Android with Firebase configured): only then is
 * the "Send crash reports" switch shown.
 */
data class SettingsState(
    val settings: UserSettings = UserSettings(),
    val crashReportsAvailable: Boolean = false,
    val crashReports: Boolean = true,
) : MVIBaseState

/** Whether this platform build reports crashes (Crashlytics on Android with Firebase configured). */
data class CrashReportsAvailability(val available: Boolean)

sealed interface SettingsIntent : MVIBaseIntent {
    data class SetUnit(val unit: WeightUnit) : SettingsIntent
    data class SetBarWeight(val grams: Long) : SettingsIntent
    data class TogglePlate(val grams: Long) : SettingsIntent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsIntent
    data class SetRestSound(val enabled: Boolean) : SettingsIntent
    data class SetRestVibration(val enabled: Boolean) : SettingsIntent
    data class SetShareName(val name: String) : SettingsIntent
    data class SetCrashReports(val enabled: Boolean) : SettingsIntent
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val crashReports: CrashReportsStore,
    crashReportsAvailability: CrashReportsAvailability,
) : MVIBaseViewModel<SettingsState, SettingsIntent>(
    SettingsState(
        settings = repository.settings.value,
        crashReportsAvailable = crashReportsAvailability.available,
        crashReports = crashReports.enabled.value,
    ),
) {

    init {
        execute { repository.settings.collect { settings -> updateState { it.copy(settings = settings) } } }
        execute { crashReports.enabled.collect { enabled -> updateState { it.copy(crashReports = enabled) } } }
    }

    override fun intentHandler(intent: SettingsIntent): Job = execute {
        when (intent) {
            is SettingsIntent.SetUnit -> repository.setUnit(intent.unit)
            is SettingsIntent.SetBarWeight -> repository.setBarWeight(intent.grams)
            is SettingsIntent.TogglePlate -> {
                val plates = currentState.settings.availablePlatesGrams
                repository.setAvailablePlates(
                    if (intent.grams in plates) plates - intent.grams else plates + intent.grams
                )
            }
            is SettingsIntent.SetKeepScreenOn -> repository.setKeepScreenOn(intent.enabled)
            is SettingsIntent.SetRestSound -> repository.setRestSound(intent.enabled)
            is SettingsIntent.SetRestVibration -> repository.setRestVibration(intent.enabled)
            is SettingsIntent.SetShareName -> repository.setShareName(intent.name)
            is SettingsIntent.SetCrashReports -> crashReports.setEnabled(intent.enabled)
        }
    }
}
