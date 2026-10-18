package es.sebas1705.haltera.account

import es.sebas1705.haltera.core.datastore.LoadStepStore
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.eraseUserData
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wipes what the user logged on this device, for "Also erase data on this device". Never the catalogue. */
interface LocalDataEraser {
    suspend fun erase()
}

/** Database tables of user data, the progress photos (files on the device), then the settings and load steps. */
class DatabaseLocalDataEraser(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val loadSteps: LoadStepStore,
    private val photos: ProgressPhotoRepository,
) : LocalDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.Default) { db.eraseUserData() }
        photos.deleteAll()
        settings.reset()
        loadSteps.clear()
    }
}
