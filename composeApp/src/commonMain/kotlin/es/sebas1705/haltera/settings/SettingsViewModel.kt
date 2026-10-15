package es.sebas1705.haltera.settings

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import kotlinx.coroutines.Job

data class SettingsState(val settings: UserSettings = UserSettings()) : MVIBaseState

sealed interface SettingsIntent : MVIBaseIntent {
    data class SetUnit(val unit: WeightUnit) : SettingsIntent
    data class SetBarWeight(val grams: Long) : SettingsIntent
    data class TogglePlate(val grams: Long) : SettingsIntent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsIntent
    data class SetRestSound(val enabled: Boolean) : SettingsIntent
    data class SetRestVibration(val enabled: Boolean) : SettingsIntent
}

class SettingsViewModel(
    private val repository: SettingsRepository,
) : MVIBaseViewModel<SettingsState, SettingsIntent>(SettingsState(repository.settings.value)) {

    init {
        execute { repository.settings.collect { settings -> updateState { it.copy(settings = settings) } } }
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
        }
    }
}
