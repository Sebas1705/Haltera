package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.BODY_WEIGHT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression: downloads followed the `updatedAt` of the documents, which is the uploader's clock at the moment the user
 * changed something, minus a 7 day look-back. A phone that was offline longer than that uploaded documents dated
 * before the other phones' cursor minus 7 days, and those phones never saw them (docs/adr/0009). Downloads now follow
 * `pushedAt`, the moment of the upload.
 */
class LateUploadSyncTest {
    private val cloud = FakeCloud()
    private val offline = Device(cloud, Dispatchers.Unconfined)
    private val online = Device(cloud, Dispatchers.Unconfined)
    private val other = Device(cloud, Dispatchers.Unconfined)

    private fun daysLater(days: Long) = cloud.clock.tick(days * DAY_MILLIS)

    @Test
    fun `a document uploaded more than a week late reaches phones that already synced`() = runTest {
        offline.bodyWeight("old", grams = 80_000) // logged now, on a phone that stays offline
        val loggedAt = cloud.clock.now()

        daysLater(10)
        other.bodyWeight("recent", grams = 81_000)
        other.engine.syncNow()
        online.engine.syncNow() // downloads `recent`: its cursor is now 10 days past `old`

        offline.engine.syncNow() // back online: uploads `old`, dated 10 days ago
        assertEquals(loggedAt, cloud.doc("uid-1", BODY_WEIGHT, "old")!!.updatedAt)

        online.engine.syncNow()
        assertNotNull(online.db.syncTrackingQueries.selectBodyWeight("old").executeAsOneOrNull(), "never reached")
        assertEquals(offline.read(BODY_WEIGHT, "old"), online.read(BODY_WEIGHT, "old"))
        assertTrue(online.dirty().isEmpty())
    }

    @Test
    fun `an upload stamps the documents with the time it happened`() = runTest {
        offline.bodyWeight("old")
        daysLater(3)
        val uploadedAt = cloud.clock.now()

        offline.engine.syncNow()

        assertEquals(uploadedAt, cloud.doc("uid-1", BODY_WEIGHT, "old")!!.pushedAt)
        assertTrue(cloud.doc("uid-1", BODY_WEIGHT, "old")!!.updatedAt < uploadedAt)
    }

    @Test
    fun `the cursor follows pushedAt, not updatedAt`() = runTest {
        offline.bodyWeight("old")
        val doc = SyncDocument(
            BODY_WEIGHT,
            "old",
            offline.state(BODY_WEIGHT, "old")!!.updatedAt,
            deleted = false,
            payload = offline.read(BODY_WEIGHT, "old")!!,
            pushedAt = 5_000_000_000_000,
        )

        online.engine.receive(BODY_WEIGHT, listOf(doc))

        assertEquals(5_000_000_000_000, online.db.syncTrackingQueries.selectCursor(BODY_WEIGHT).executeAsOne())
    }

    @Test
    fun `documents written before pushedAt existed are still downloaded by a new install`() = runTest {
        offline.bodyWeight("legacy")
        val doc = SyncDocument(
            BODY_WEIGHT,
            "legacy",
            offline.state(BODY_WEIGHT, "legacy")!!.updatedAt,
            deleted = false,
            payload = offline.read(BODY_WEIGHT, "legacy")!!,
        )
        cloud.put("uid-1", doc, legacy = true)
        assertEquals(0, cloud.doc("uid-1", BODY_WEIGHT, "legacy")!!.pushedAt)

        online.engine.syncNow()

        assertEquals(offline.read(BODY_WEIGHT, "legacy"), online.read(BODY_WEIGHT, "legacy"))
    }

    @Test
    fun `a cursor kept by an older version is discarded so nothing it missed stays missed`() = runTest {
        offline.bodyWeight("missed") // an earlier late upload that an older version never downloaded
        cloud.put(
            "uid-1",
            SyncDocument(
                BODY_WEIGHT,
                "missed",
                offline.state(BODY_WEIGHT, "missed")!!.updatedAt,
                deleted = false,
                payload = offline.read(BODY_WEIGHT, "missed")!!,
            ),
            legacy = true,
        )
        daysLater(30)
        // What an older version left behind: a cursor on updatedAt, 30 days past the document, and no marker.
        online.db.syncTrackingQueries.putCursor(BODY_WEIGHT, cloud.clock.now())
        assertNull(online.db.metaQueries.get("syncCursorKind").executeAsOneOrNull())

        online.engine.syncNow()

        assertNotNull(online.db.syncTrackingQueries.selectBodyWeight("missed").executeAsOneOrNull())
        assertEquals("pushedAt", online.db.metaQueries.get("syncCursorKind").executeAsOneOrNull())
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1_000
    }
}
