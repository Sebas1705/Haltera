package es.sebas1705.haltera.data.local

import app.cash.sqldelight.db.SqlDriver
import es.sebas1705.haltera.data.local.db.AppDatabase

internal const val DATABASE_NAME = "haltera.db"

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): AppDatabase =
    AppDatabase(factory.create())
