package es.sebas1705.haltera.data.local

import es.sebas1705.haltera.data.local.db.AppDatabase

private const val META_SYNC_APPLYING = "syncApplying"
private const val META_SYNC_UID = "syncUid"

/**
 * Wipes the data the user created or logged (routines, sessions, sets, body weight, body measurements, custom
 * exercises, foods and meals, the cached health totals and the health export ledger) and the sync bookkeeping, keeping the
 * bundled catalogue. One transaction; the change-tracking triggers are muted so no tombstones are queued for an
 * account that is being deleted.
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
            eraseBodyMeasurements()
            eraseMealEntries()
            eraseRecipeItems()
            eraseNonCatalogFoods()
            eraseSyncState()
            eraseSyncCursors()
            eraseHealthDays()
            eraseHealthExports()
        }
        metaQueries.put(META_SYNC_UID, "")
        metaQueries.put(META_SYNC_APPLYING, "0")
    }
}
