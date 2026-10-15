package es.sebas1705.ironlog.data.local

import es.sebas1705.ironlog.data.local.db.AppDatabase

private const val META_SYNC_APPLYING = "syncApplying"
private const val META_SYNC_UID = "syncUid"

/**
 * Wipes the data the user created or logged (routines, sessions, sets, body weight, custom exercises, foods
 * and meals) and the sync bookkeeping, keeping the bundled catalogue. One transaction; the change-tracking
 * triggers are muted so no tombstones are queued for an account that is being deleted.
 */
fun AppDatabase.eraseUserData() {
    transaction {
        metaQueries.put(META_SYNC_APPLYING, "1")
        with(eraseUserDataQueries) {
            eraseSetLogs()
            eraseSessionExercises()
            eraseSessions()
            eraseRoutineSlots()
            eraseRoutineDays()
            eraseRoutines()
            eraseUserVariants()
            eraseUserExercises()
            eraseBodyWeight()
            eraseMealEntries()
            eraseRecipeItems()
            eraseNonCatalogFoods()
            eraseSyncState()
            eraseSyncCursors()
        }
        metaQueries.put(META_SYNC_UID, "")
        metaQueries.put(META_SYNC_APPLYING, "0")
    }
}
