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
    /** Nutrition goals and calculator profile (nutrition design §2); defaults: no goals. */
    val nutrition: NutritionSettings = NutritionSettings(),
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

/**
 * Nutrition goals as stored (enum values by name; null = not set). Macros are grams. The typed model and the
 * calculator live in data:models / domain:usecases; this is only persistence.
 */
data class NutritionSettings(
    val kcal: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
    val macroMode: String = "GRAMS",
    val sex: String? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val activity: String = "MODERATE",
    val objective: String = "MAINTAIN",
)

interface SettingsRepository {
    val settings: StateFlow<UserSettings>

    /** Changes the unit; with [resetEquipment] the bar and plates switch to that unit's defaults. */
    fun setUnit(unit: WeightUnit, resetEquipment: Boolean = true)

    fun setBarWeight(grams: Long)

    fun setAvailablePlates(grams: List<Long>)

    fun setKeepScreenOn(enabled: Boolean)

    fun setRestSound(enabled: Boolean)

    fun setRestVibration(enabled: Boolean)

    /** Replaces the nutrition goals. The default does nothing, for test fakes that do not need goals. */
    fun setNutrition(nutrition: NutritionSettings) = Unit

    /** Back to the defaults, nutrition goals included ("Also erase data on this device"). */
    fun reset() = Unit
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
                nutrition = it.nutrition,
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

    override fun setNutrition(nutrition: NutritionSettings) = write { it.copy(nutrition = nutrition) }

    override fun reset() = write { UserSettings() }

    private fun write(change: (UserSettings) -> UserSettings) {
        state.update(change)
        val s = state.value
        store.putString(KEY_UNIT, s.unit.name)
        store.putLong(KEY_BAR, s.barWeightGrams)
        store.putString(KEY_PLATES, s.availablePlatesGrams.joinToString(","))
        store.putBoolean(KEY_SCREEN_ON, s.keepScreenOn)
        store.putBoolean(KEY_SOUND, s.restSound)
        store.putBoolean(KEY_VIBRATION, s.restVibration)
        writeNutrition(s.nutrition)
    }

    private fun writeNutrition(n: NutritionSettings) {
        putOrRemove(KEY_N_KCAL, n.kcal)
        putOrRemove(KEY_N_PROTEIN, n.proteinG)
        putOrRemove(KEY_N_CARBS, n.carbsG)
        putOrRemove(KEY_N_FAT, n.fatG)
        store.putString(KEY_N_MODE, n.macroMode)
        if (n.sex == null) store.remove(KEY_N_SEX) else store.putString(KEY_N_SEX, n.sex)
        putOrRemove(KEY_N_BIRTH_YEAR, n.birthYear)
        putOrRemove(KEY_N_HEIGHT, n.heightCm)
        store.putString(KEY_N_ACTIVITY, n.activity)
        store.putString(KEY_N_OBJECTIVE, n.objective)
    }

    private fun putOrRemove(key: String, value: Int?) {
        if (value == null) store.remove(key) else store.putInt(key, value)
    }

    private fun readNutrition() = NutritionSettings(
        kcal = store.getIntOrNull(KEY_N_KCAL),
        proteinG = store.getIntOrNull(KEY_N_PROTEIN),
        carbsG = store.getIntOrNull(KEY_N_CARBS),
        fatG = store.getIntOrNull(KEY_N_FAT),
        macroMode = store.getStringOrNull(KEY_N_MODE) ?: NutritionSettings().macroMode,
        sex = store.getStringOrNull(KEY_N_SEX),
        birthYear = store.getIntOrNull(KEY_N_BIRTH_YEAR),
        heightCm = store.getIntOrNull(KEY_N_HEIGHT),
        activity = store.getStringOrNull(KEY_N_ACTIVITY) ?: NutritionSettings().activity,
        objective = store.getStringOrNull(KEY_N_OBJECTIVE) ?: NutritionSettings().objective,
    )

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
            nutrition = readNutrition(),
        )
    }

    private companion object {
        const val KEY_UNIT = "settings.unit"
        const val KEY_BAR = "settings.barWeightGrams"
        const val KEY_PLATES = "settings.platesGrams"
        const val KEY_SCREEN_ON = "settings.keepScreenOn"
        const val KEY_SOUND = "settings.restSound"
        const val KEY_VIBRATION = "settings.restVibration"
        const val KEY_N_KCAL = "nutrition.kcal"
        const val KEY_N_PROTEIN = "nutrition.proteinG"
        const val KEY_N_CARBS = "nutrition.carbsG"
        const val KEY_N_FAT = "nutrition.fatG"
        const val KEY_N_MODE = "nutrition.macroMode"
        const val KEY_N_SEX = "nutrition.sex"
        const val KEY_N_BIRTH_YEAR = "nutrition.birthYear"
        const val KEY_N_HEIGHT = "nutrition.heightCm"
        const val KEY_N_ACTIVITY = "nutrition.activity"
        const val KEY_N_OBJECTIVE = "nutrition.objective"
    }
}
