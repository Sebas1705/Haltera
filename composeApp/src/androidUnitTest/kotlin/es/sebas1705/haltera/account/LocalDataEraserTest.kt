package es.sebas1705.haltera.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.core.datastore.SettingsLoadStepStore
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Also erase data on this device": the database, the progress photos and the settings all go. */
class LocalDataEraserTest {
    private class FakePhotos : ProgressPhotoRepository {
        val photos = MutableStateFlow(listOf(ProgressPhoto("p1", 1), ProgressPhoto("p2", 2)))
        var deleteAllCalls = 0

        override fun observeAll(): Flow<List<ProgressPhoto>> = photos
        override suspend fun add(image: ByteArray, thumbnail: ByteArray, takenAt: Long, notes: String) = "p"
        override suspend fun update(photo: ProgressPhoto) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun image(id: String): ByteArray? = null
        override suspend fun thumbnail(id: String): ByteArray? = null
        override suspend fun deleteAll() {
            deleteAllCalls++
            photos.value = emptyList()
        }
    }

    @Test
    fun `erasing wipes measurements and body weight, deletes every photo and resets the settings`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        AppDatabase.Schema.create(driver)
        val db = AppDatabase(driver)
        db.bodyMeasurementEntityQueries.upsert("m1", 1, "WAIST", "", 800, "")
        db.bodyWeightEntityQueries.upsert("w1", 1, 80_000, "")
        val settings = KeyValueSettingsRepository(MapSettings())
        settings.setUnit(WeightUnit.LB, resetEquipment = false)
        val photos = FakePhotos()

        DatabaseLocalDataEraser(db, settings, SettingsLoadStepStore(MapSettings()), photos).erase()

        assertTrue(db.bodyMeasurementEntityQueries.selectAll().executeAsList().isEmpty())
        assertTrue(db.bodyWeightEntityQueries.selectAll().executeAsList().isEmpty())
        assertEquals(1, photos.deleteAllCalls)
        assertTrue(photos.photos.value.isEmpty())
        assertEquals(WeightUnit.KG, settings.settings.value.unit)
    }
}
