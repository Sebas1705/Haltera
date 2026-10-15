package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.core.datastore.NutritionSettings
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.local.db.AppDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Tracks `settings/user` (design §5): multiplatform-settings is not SQLite, so instead of a trigger this
 * observes [SettingsRepository.settings] and marks the document dirty when the settings differ from the last
 * version seen (kept in Meta, so changes made while sync was off are caught on the next start).
 * Applying remote settings records them first, so writing them back is not taken as a local change.
 */
class SettingsSyncBridge(
    private val repository: SettingsRepository,
    private val db: AppDatabase,
) {
    internal fun prefs(): SettingsPrefs = repository.settings.value.toPrefs()

    /** Untouched settings: a fresh install, which should take the settings already in the cloud. */
    internal fun isDefault(): Boolean = repository.settings.value.let { it == UserSettings.defaultsFor(it.unit) }

    /** Runs until cancelled. Settings are debounced: writing remote ones back goes through several setters. */
    @OptIn(FlowPreview::class)
    suspend fun observe() {
        onLocal(
            encode(repository.settings.value)
        ) // the snapshot right away, so no early change is taken as first sight
        repository.settings
            .map(::encode)
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MILLIS)
            .collect(::onLocal)
    }

    internal fun onLocal(json: String) {
        db.transaction {
            val known = db.metaQueries.get(META_SETTINGS).executeAsOneOrNull()
            if (known != json) {
                db.metaQueries.put(META_SETTINGS, json)
                // First sight (no snapshot yet): the engine's backfill decides (customised settings only).
                if (known != null) {
                    db.syncTrackingQueries.markDirty(
                        SyncCollections.SETTINGS,
                        SyncCollections.SETTINGS_ID
                    )
                }
            }
        }
    }

    internal fun writeBack(prefs: SettingsPrefs) {
        db.metaQueries.put(META_SETTINGS, SyncJson.encodeToString(SettingsPrefs.serializer(), prefs))
        repository.setUnit(WeightUnit.fromName(prefs.unit), resetEquipment = false)
        repository.setBarWeight(prefs.barWeightGrams)
        repository.setAvailablePlates(prefs.availablePlatesGrams)
        repository.setKeepScreenOn(prefs.keepScreenOn)
        repository.setRestSound(prefs.restSound)
        repository.setRestVibration(prefs.restVibration)
        // Documents from app versions before nutrition carry no goals: keep the local ones.
        prefs.nutrition?.let { repository.setNutrition(it.toSettings()) }
    }

    private fun encode(settings: UserSettings) = SyncJson.encodeToString(SettingsPrefs.serializer(), settings.toPrefs())

    private fun UserSettings.toPrefs() = SettingsPrefs(
        unit = unit.name,
        barWeightGrams = barWeightGrams,
        availablePlatesGrams = availablePlatesGrams,
        keepScreenOn = keepScreenOn,
        restSound = restSound,
        restVibration = restVibration,
        nutrition = nutrition.toPrefs(),
    )

    private fun NutritionSettings.toPrefs() = NutritionPrefs(
        kcal, proteinG, carbsG, fatG, macroMode, sex, birthYear, heightCm, activity, objective,
    )

    private fun NutritionPrefs.toSettings() = NutritionSettings(
        kcal, proteinG, carbsG, fatG, macroMode, sex, birthYear, heightCm, activity, objective,
    )

    private companion object {
        const val META_SETTINGS = "syncSettings"
        const val DEBOUNCE_MILLIS = 500L
    }
}
