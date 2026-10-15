package es.sebas1705.haltera.account

import es.sebas1705.haltera.core.datastore.LoadStepStore
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.eraseUserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wipes what the user logged on this device, for "Also erase data on this device". Never the catalogue. */
interface LocalDataEraser {
    suspend fun erase()
}

/** Database tables of user data, then the settings and per-exercise load steps. */
class DatabaseLocalDataEraser(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val loadSteps: LoadStepStore,
) : LocalDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.Default) { db.eraseUserData() }
        settings.reset()
        loadSteps.clear()
    }
}
