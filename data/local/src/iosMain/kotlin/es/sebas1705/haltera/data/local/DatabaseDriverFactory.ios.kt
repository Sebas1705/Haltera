package es.sebas1705.haltera.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import es.sebas1705.haltera.data.local.db.AppDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(
        schema = AppDatabase.Schema,
        name = DATABASE_NAME,
        onConfiguration = { config ->
            config.copy(
                extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true),
            )
        },
    )
}
