package es.sebas1705.ironlog.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import java.util.Properties

/** In-memory database with the schema created and foreign keys on, as on the device. */
fun inMemoryDatabase(): AppDatabase = AppDatabase(
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        .also { AppDatabase.Schema.create(it) },
)

/** Mutable test clock in epoch millis. */
class TestClock(var millis: Long = 1_000L) : () -> Long {
    override fun invoke(): Long = millis
}

/** A tiny catalogue: bench press (barbell, dumbbell), squat (barbell), plank (bodyweight). */
val TEST_CATALOG = """
    {"catalogVersion":1,"contentHash":"h","deprecatedIds":[],"exercises":[
      {"id":"bench-press","name":{"en":"Bench Press","es":"Press de banca"},"category":"STRENGTH",
       "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["CHEST"],"secondaryMuscles":["TRICEPS"],
       "instructions":{"en":["Lie down."],"es":["Túmbate."]},
       "variants":[{"id":"bench-press.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":["b/0.jpg"]},
                   {"id":"bench-press.dumbbell","equipment":"DUMBBELL","loadType":"DUMBBELL","frames":[]}]},
      {"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
       "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":["GLUTES"],
       "instructions":{"en":[],"es":[]},
       "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]},
      {"id":"plank","name":{"en":"Plank","es":"Plancha"},"category":"STRENGTH",
       "trackingType":"TIME","level":"BEGINNER","primaryMuscles":["ABDOMINALS"],"secondaryMuscles":[],
       "instructions":{"en":[],"es":[]},
       "variants":[{"id":"plank.bodyweight","equipment":"BODYWEIGHT","loadType":"BODYWEIGHT","frames":[]}]}
    ]}
""".trimIndent()

fun AppDatabase.withTestCatalog(): AppDatabase = also { CatalogImporter(it, now = { 0L }).importIfNewer(TEST_CATALOG) }
