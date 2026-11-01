package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.Settings
import org.koin.dsl.module

/** Preferences backed by the platform default store (multiplatform-settings no-arg). */
val datastoreModule = module {
    single<SettingsRepository> { KeyValueSettingsRepository(Settings()) }
    // Device-local, not synced: separate from SettingsRepository (and so from SettingsSyncBridge).
    single<LoadStepStore> { SettingsLoadStepStore(Settings()) }
    // The optional cloud copy choice: device-local, never synced, kept by "Also erase data on this device".
    single<CloudBackupStore> { SettingsCloudBackupStore(Settings()) }
    // Health Connect / HealthKit switches and bookkeeping: device-local, never synced.
    single<HealthSettingsStore> { SettingsHealthSettingsStore(Settings()) }
    // Settings → Privacy → "Send crash reports": device-local, never synced, kept by "Also erase data on this device".
    single<CrashReportsStore> { SettingsCrashReportsStore(Settings()) }
}
