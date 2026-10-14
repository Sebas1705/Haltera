package es.sebas1705.ironlog.data.local

import app.cash.sqldelight.db.SqlDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase

internal const val DATABASE_NAME = "ironlog.db"

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): AppDatabase =
    AppDatabase(factory.create())
