package es.sebas1705.ironlog.data.remote

import es.sebas1705.ironlog.data.sync.AccountSession
import es.sebas1705.ironlog.data.sync.GoogleIdToken
import es.sebas1705.ironlog.data.sync.LinkResult
import es.sebas1705.ironlog.data.sync.SyncDocument
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteDocumentTest {

    @Test
    fun documentRoundTripsThroughTheRemoteShape() {
        val doc = SyncDocument("routines", "r1", updatedAt = 42, deleted = false, payload = "{\"a\":1}")
        val remote = doc.toRemote()
        assertEquals(RemoteDocument(payload = "{\"a\":1}", updatedAt = 42, deleted = false, schema = 1), remote)
        assertEquals(doc, remote.toSync("routines", "r1"))
    }

    @Test
    fun tombstonesCarryNoPayload() {
        val doc = SyncDocument("sessions", "s1", updatedAt = 7, deleted = true, payload = "stale")
        assertEquals("", doc.toRemote().payload)
        assertEquals("", RemoteDocument(payload = "x", updatedAt = 7, deleted = true).toSync("sessions", "s1").payload)
    }

    @Test
    fun syncedCollectionsMatchTheDesign() {
        assertEquals(listOf("routines", "sessions", "bodyWeight", "exercises", "settings"), SYNCED_COLLECTIONS)
    }

    @Test
    fun notConfiguredIsInertAndNeverSignedIn() = runTest {
        NotConfiguredAccountService.ensureSignedIn()
        assertEquals(AccountSession.NotConfigured, NotConfiguredAccountService.session.value)
        assertIs<LinkResult.Failed>(NotConfiguredAccountService.linkGoogle(GoogleIdToken("token")))
        assertTrue(NotConfiguredRemoteStore.changes("uid", "routines", 0).toList().isEmpty())
    }
}
