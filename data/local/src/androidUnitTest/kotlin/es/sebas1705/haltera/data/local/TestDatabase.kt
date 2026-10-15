package es.sebas1705.ironlog.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import java.util.Properties

/** In-memory driver with the schema created and foreign keys on, as on the device. */
fun inMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        .also { AppDatabase.Schema.create(it) }

fun inMemoryDatabase(): AppDatabase = AppDatabase(inMemoryDriver())
