package es.sebas1705.ironlog.core.datastore

import com.russhwolf.settings.Settings
import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** User preferences (spec §7 Settings). Weights are grams. */
data class UserSettings(
    val unit: WeightUnit = WeightUnit.KG,
    val barWeightGrams: Long = DEFAULT_BAR_KG,
    /** Plate sizes available per side, heaviest first. */
    val availablePlatesGrams: List<Long> = PlateCalculator.KG_PLATES,
    val keepScreenOn: Boolean = true,
    val restSound: Boolean = true,
    val restVibration: Boolean = true,
) {
    companion object {
        const val DEFAULT_BAR_KG = 20_000L

        /** 45 lb bar. */
        val DEFAULT_BAR_LB: Long = Weights.lbToGrams(45.0)

        fun defaultsFor(unit: WeightUnit) = UserSettings(
            unit = unit,
            barWeightGrams = if (unit == WeightUnit.KG) DEFAULT_BAR_KG else DEFAULT_BAR_LB,
            availablePlatesGrams = PlateCalculator.defaultPlates(unit),
        )
    }
}

interface SettingsRepository {
    val settings: StateFlow<UserSettings>

    /** Changes the unit; with [resetEquipment] the bar and plates switch to that unit's defaults. */
    fun setUnit(unit: WeightUnit, resetEquipment: Boolean = true)

    fun setBarWeight(grams: Long)

    fun setAvailablePlates(grams: List<Long>)

    fun setKeepScreenOn(enabled: Boolean)

    fun setRestSound(enabled: Boolean)

    fun setRestVibration(enabled: Boolean)
}

/** [SettingsRepository] on multiplatform-settings (SharedPreferences / NSUserDefaults). */
class KeyValueSettingsRepository(private val store: Settings) : SettingsRepository {
    private val state = MutableStateFlow(read())
    override val settings: StateFlow<UserSettings> = state.asStateFlow()

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) = write {
        if (resetEquipment) {
            UserSettings.defaultsFor(unit).copy(
                keepScreenOn = it.keepScreenOn,
                restSound = it.restSound,
                restVibration = it.restVibration,
            )
        } else {
            it.copy(unit = unit)
        }
    }

    override fun setBarWeight(grams: Long) = write { it.copy(barWeightGrams = grams.coerceAtLeast(0)) }

    override fun setAvailablePlates(grams: List<Long>) =
        write { it.copy(availablePlatesGrams = grams.filter { g -> g > 0 }.distinct().sortedDescending()) }

    override fun setKeepScreenOn(enabled: Boolean) = write { it.copy(keepScreenOn = enabled) }

    override fun setRestSound(enabled: Boolean) = write { it.copy(restSound = enabled) }

    override fun setRestVibration(enabled: Boolean) = write { it.copy(restVibration = enabled) }

    private fun write(change: (UserSettings) -> UserSettings) {
        state.update(change)
        val s = state.value
        store.putString(KEY_UNIT, s.unit.name)
        store.putLong(KEY_BAR, s.barWeightGrams)
        store.putString(KEY_PLATES, s.availablePlatesGrams.joinToString(","))
        store.putBoolean(KEY_SCREEN_ON, s.keepScreenOn)
        store.putBoolean(KEY_SOUND, s.restSound)
        store.putBoolean(KEY_VIBRATION, s.restVibration)
    }

    private fun read(): UserSettings {
        val unit = WeightUnit.fromName(store.getStringOrNull(KEY_UNIT))
        val defaults = UserSettings.defaultsFor(unit)
        val plates = store.getStringOrNull(KEY_PLATES)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it > 0 }
            ?.takeIf { it.isNotEmpty() }
        return UserSettings(
            unit = unit,
            barWeightGrams = store.getLongOrNull(KEY_BAR) ?: defaults.barWeightGrams,
            availablePlatesGrams = plates ?: defaults.availablePlatesGrams,
            keepScreenOn = store.getBoolean(KEY_SCREEN_ON, defaults.keepScreenOn),
            restSound = store.getBoolean(KEY_SOUND, defaults.restSound),
            restVibration = store.getBoolean(KEY_VIBRATION, defaults.restVibration),
        )
    }

    private companion object {
        const val KEY_UNIT = "settings.unit"
        const val KEY_BAR = "settings.barWeightGrams"
        const val KEY_PLATES = "settings.platesGrams"
        const val KEY_SCREEN_ON = "settings.keepScreenOn"
        const val KEY_SOUND = "settings.restSound"
        const val KEY_VIBRATION = "settings.restVibration"
    }
}
