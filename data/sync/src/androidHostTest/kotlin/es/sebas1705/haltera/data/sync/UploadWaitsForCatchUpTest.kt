package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.BODY_WEIGHT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: when the first download of a session failed, the pass skipped its upload but the upload loop, started
 * right after it, pushed the dirty roots anyway. A root edited before another device pushed a newer version then
 * overwrote it in the cloud (the push is a plain set), losing that edit on every device (docs/adr/0010).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadWaitsForCatchUpTest {
    private val cloud = FakeCloud()
    private val remote = FlakyDownload(cloud)

    /** Reads fail while [failing]; writes always work. */
    private class FlakyDownload(private val cloud: FakeCloud) : RemoteStore by cloud {
        var failing = true
        var pushes = 0

        override suspend fun push(uid: String, documents: List<SyncDocument>) {
            pushes++
            cloud.push(uid, documents)
        }

        override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> =
            if (failing) {
                flow { error("download failed") }
            } else {
                cloud.changes(uid, collection, sinceUpdatedAt)
            }
    }

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    /** This phone has an old edit of bw1 that was never uploaded; another phone already pushed a newer one. */
    private fun TestScope.staleDeviceAndNewerRemote(): Device {
        val stale = Device(cloud, UnconfinedTestDispatcher(testScheduler), remote = remote)
        stale.bodyWeight("bw1", grams = 80_000)
        val other = Device(cloud, UnconfinedTestDispatcher(testScheduler))
        cloud.tick()
        other.bodyWeight("bw1", grams = 90_000)
        cloud.put(
            "uid-1",
            SyncDocument(
                BODY_WEIGHT,
                "bw1",
                other.state(BODY_WEIGHT, "bw1")!!.updatedAt,
                deleted = false,
                payload = other.read(BODY_WEIGHT, "bw1")!!,
            ),
        )
        return stale
    }

    @Test
    fun `a failed first download does not let an older edit overwrite the newer remote document`() = runTest {
        val stale = staleDeviceAndNewerRemote()
        val newer = cloud.doc("uid-1", BODY_WEIGHT, "bw1")!!
        stale.engine.start(backgroundScope)
        settle()

        assertEquals(0, remote.pushes, "pushed before the download worked")
        assertEquals(newer, cloud.doc("uid-1", BODY_WEIGHT, "bw1"))
        assertEquals(1, stale.dirty().size) // still waiting to be uploaded
    }

    @Test
    fun `once the download works the newer document wins and nothing older is pushed`() = runTest {
        val stale = staleDeviceAndNewerRemote()
        val newer = cloud.doc("uid-1", BODY_WEIGHT, "bw1")!!
        stale.engine.start(backgroundScope)
        settle()
        assertEquals(0, remote.pushes)

        remote.failing = false
        settle() // the listeners retry with backoff

        assertEquals(0, remote.pushes)
        assertEquals(newer, cloud.doc("uid-1", BODY_WEIGHT, "bw1"))
        assertEquals(newer.payload, stale.read(BODY_WEIGHT, "bw1"))
        assertTrue(stale.dirty().isEmpty())
    }

    @Test
    fun `an edit newer than the remote document is uploaded once the download works`() = runTest {
        val stale = staleDeviceAndNewerRemote()
        cloud.tick()
        stale.bodyWeight("bw1", grams = 95_000) // edited after the other phone's version
        stale.engine.start(backgroundScope)
        settle()
        assertEquals(0, remote.pushes)

        remote.failing = false
        settle()

        assertEquals(1, remote.pushes)
        assertEquals(stale.read(BODY_WEIGHT, "bw1"), cloud.doc("uid-1", BODY_WEIGHT, "bw1")?.payload)
        assertTrue(stale.dirty().isEmpty())
    }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
    }
}
