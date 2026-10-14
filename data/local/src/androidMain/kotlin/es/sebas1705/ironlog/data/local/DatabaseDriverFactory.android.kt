package es.sebas1705.ironlog.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(AppDatabase.Schema, context, "app.db")
}
