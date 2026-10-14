package es.sebas1705.ironlog.core.datastore

import com.russhwolf.settings.Settings
import org.koin.dsl.module

/** Preferences backed by the platform default store (multiplatform-settings no-arg). */
val datastoreModule = module {
    single<SettingsRepository> { KeyValueSettingsRepository(Settings()) }
}
