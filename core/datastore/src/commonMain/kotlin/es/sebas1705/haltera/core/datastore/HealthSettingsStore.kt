package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One switch of Settings → Integrations → Health Connect. Read types copy data from the health platform into
 * Haltera; write types copy what was logged in Haltera to it. Each maps to one or more platform permissions
 * (data:health), requested only when the user turns the type on and taps Connect.
 */
enum class HealthDataType(val writes: Boolean) {
    /** Daily step totals (Activity statistics, Today card). */
    STEPS(writes = false),

    /** Active and total calories burned per day (Activity statistics, eaten vs burned in Nutrition). */
    CALORIES(writes = false),

    /** Body weight measured by other apps or scales, imported into the body-weight log. */
    WEIGHT_IMPORT(writes = false),

    /** Daily resting heart rate (trend only). */
    RESTING_HEART_RATE(writes = false),

    /** Sleep duration per night (trend only). */
    SLEEP(writes = false),

    /** Finished Haltera workouts written as strength-training exercise sessions. */
    WORKOUTS_EXPORT(writes = true),

    /** Body weight logged in Haltera written to the health platform. */
    WEIGHT_EXPORT(writes = true),
    ;

    companion object {
        /** Preselected before the first Connect: the data the integration exists for. */
        val DEFAULT_ENABLED: Set<HealthDataType> = setOf(STEPS, CALORIES, WEIGHT_IMPORT, WORKOUTS_EXPORT)
    }
}

/**
 * The health integration's choices and bookkeeping. Device-local on purpose (like [CloudBackupStore]): the data
 * comes from the phone's own health platform, so neither the switches nor the cached totals are synced.
 *
 * @property connected The user tapped Connect and granted at least one permission; nothing is read before.
 * @property enabled The switched-on data types (also the ones requested on Connect).
 * @property stepGoal Daily steps goal of the Activity statistics.
 * @property lastRefreshAt When the last read finished (epoch millis), 0 = never; throttles foreground reads.
 * @property weightWatermark Latest last-modified time (epoch millis) of the weights already considered for
 *   import: an imported entry the user deletes in Haltera is not imported again unless it changes.
 * @property exportSince Haltera data from this time on (epoch millis) is written to the platform, 0 = not set.
 */
data class HealthSettings(
    val connected: Boolean = false,
    val enabled: Set<HealthDataType> = HealthDataType.DEFAULT_ENABLED,
    val stepGoal: Int = DEFAULT_STEP_GOAL,
    val lastRefreshAt: Long = 0,
    val weightWatermark: Long = 0,
    val exportSince: Long = 0,
) {
    companion object {
        const val DEFAULT_STEP_GOAL = 8_000
        const val MIN_STEP_GOAL = 500
        const val MAX_STEP_GOAL = 100_000
    }
}

interface HealthSettingsStore {
    val settings: StateFlow<HealthSettings>

    fun update(change: (HealthSettings) -> HealthSettings)

    /** The goal clamped to [HealthSettings.MIN_STEP_GOAL]..[HealthSettings.MAX_STEP_GOAL]. */
    fun setStepGoal(goal: Int) = update {
        it.copy(stepGoal = goal.coerceIn(HealthSettings.MIN_STEP_GOAL, HealthSettings.MAX_STEP_GOAL))
    }
}

/** [HealthSettingsStore] on multiplatform-settings under `health.*`. Absent keys read as the defaults. */
class SettingsHealthSettingsStore(private val store: Settings) : HealthSettingsStore {
    private val mutable = MutableStateFlow(read())
    override val settings: StateFlow<HealthSettings> = mutable.asStateFlow()

    override fun update(change: (HealthSettings) -> HealthSettings) {
        val s = change(mutable.value)
        store.putBoolean(KEY_CONNECTED, s.connected)
        store.putString(KEY_ENABLED, s.enabled.joinToString(",") { it.name })
        store.putInt(KEY_STEP_GOAL, s.stepGoal)
        store.putLong(KEY_LAST_REFRESH, s.lastRefreshAt)
        store.putLong(KEY_WEIGHT_WATERMARK, s.weightWatermark)
        store.putLong(KEY_EXPORT_SINCE, s.exportSince)
        mutable.value = s
    }

    private fun read(): HealthSettings {
        val enabled = store.getStringOrNull(KEY_ENABLED)?.let { text ->
            text.split(',').mapNotNull { name -> HealthDataType.entries.firstOrNull { it.name == name } }.toSet()
        } ?: HealthDataType.DEFAULT_ENABLED
        return HealthSettings(
            connected = store.getBoolean(KEY_CONNECTED, false),
            enabled = enabled,
            stepGoal = store.getInt(KEY_STEP_GOAL, HealthSettings.DEFAULT_STEP_GOAL)
                .coerceIn(HealthSettings.MIN_STEP_GOAL, HealthSettings.MAX_STEP_GOAL),
            lastRefreshAt = store.getLong(KEY_LAST_REFRESH, 0),
            weightWatermark = store.getLong(KEY_WEIGHT_WATERMARK, 0),
            exportSince = store.getLong(KEY_EXPORT_SINCE, 0),
        )
    }

    private companion object {
        const val KEY_CONNECTED = "health.connected"
        const val KEY_ENABLED = "health.enabled"
        const val KEY_STEP_GOAL = "health.stepGoal"
        const val KEY_LAST_REFRESH = "health.lastRefreshAt"
        const val KEY_WEIGHT_WATERMARK = "health.weightWatermark"
        const val KEY_EXPORT_SINCE = "health.exportSince"
    }
}
