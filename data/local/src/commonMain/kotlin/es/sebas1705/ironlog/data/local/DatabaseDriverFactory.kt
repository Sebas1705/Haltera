package es.sebas1705.ironlog.data.local

import app.cash.sqldelight.db.SqlDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): AppDatabase =
    AppDatabase(factory.create())
