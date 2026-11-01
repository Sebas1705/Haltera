package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.BODY_WEIGHT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: the upload loop followed the dirty count with `collectLatest`, and every batch it acknowledged
 * cleared dirty flags, which changed the count and cancelled the pass while the next batch awaited its ack.
 * Like Firestore, the server keeps a write whose ack nobody awaits any more, so that batch was pushed again;
 * and while the user kept logging faster than an ack came back, no pass ever finished (docs/adr/0001).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadLoopTest {
    private val cloud = FakeCloud()
    private val remote = SlowAckRemote(cloud)

    private fun TestScope.device(batchSize: Int = SyncEngineImpl.BATCH_SIZE) =
        Device(cloud, UnconfinedTestDispatcher(testScheduler), remote = remote, batchSize = batchSize)

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    @Test
    fun `the triggers stamp changes with the test clock`() = runTest {
        val d = device()
        d.bodyWeight()
        assertEquals(cloud.clock.now(), d.state(BODY_WEIGHT, "bw1")!!.updatedAt)
        cloud.tick()
        d.bodyWeight("bw2")
        assertEquals(cloud.clock.now(), d.state(BODY_WEIGHT, "bw2")!!.updatedAt)
    }

    @Test
    fun `a pass of several batches pushes each document once`() = runTest {
        val d = device(batchSize = 1)
        d.engine.start(backgroundScope)
        settle()

        d.logBodyWeights(BATCHES)
        settle()

        assertEquals((1..BATCHES).map { "bw$it" }.toSet(), remote.pushed.map { it.id }.toSet())
        assertEquals(emptyMap(), remote.duplicates())
        assertTrue(d.dirty().isEmpty())
    }

    @Test
    fun `steady logging during a workout neither starves nor repeats uploads`() = runTest {
        val d = device()
        d.engine.start(backgroundScope)
        settle()

        // One set logged every half ack: a new change always arrives while an upload awaits its ack.
        for (set in 1..SETS) {
            cloud.tick()
            d.bodyWeight("bw$set", grams = 80_000L + set)
            advanceTimeBy(ACK_MILLIS / 2)
            runCurrent()
        }
        // Still logging, but the first sets are already acknowledged.
        assertEquals(0L, d.state(BODY_WEIGHT, "bw1")!!.dirty)

        settle()
        assertEquals(SETS, remote.pushed.map { it.id }.toSet().size)
        assertEquals(emptyMap(), remote.duplicates())
        assertTrue(d.dirty().isEmpty())
    }

    @Test
    fun `signing out stops a pass after the batch in flight`() = runTest {
        val d = device(batchSize = 1)
        d.engine.start(backgroundScope)
        settle()

        d.logBodyWeights(BATCHES)
        runCurrent() // the first batch is accepted and awaits its ack
        d.account.set(AccountSession.SignedOut)
        settle()

        assertEquals(listOf("bw1"), remote.pushed.map { it.id })
        assertEquals(SyncStatus.Disabled, d.engine.status.value)
        assertEquals(BATCHES - 1, d.dirty().size)
    }

    /** Logs body weights bw1..bw[count] in one transaction: one change of the dirty count. */
    private fun Device.logBodyWeights(count: Int) = db.transaction {
        for (i in 1..count) bodyWeight("bw$i", grams = 80_000L + i)
    }

    private companion object {
        const val ACK_MILLIS = 200L
        const val SETTLE_MILLIS = 10_000L
        const val BATCHES = 4
        const val SETS = 12
    }

    /**
     * Firestore-like latency: a pushed batch is written at once, and the ack comes back [ACK_MILLIS] later.
     * When the caller stops waiting, the write stays: it was already accepted.
     */
    private class SlowAckRemote(private val cloud: FakeCloud) : RemoteStore by cloud {
        val pushed = mutableListOf<SyncDocument>()

        override suspend fun push(uid: String, documents: List<SyncDocument>) {
            cloud.push(uid, documents)
            pushed += documents
            delay(ACK_MILLIS)
        }

        /** Every version (document and `updatedAt`) pushed more than once, with its count. */
        fun duplicates(): Map<Pair<String, Long>, Int> =
            pushed.groupingBy { it.id to it.updatedAt }.eachCount().filterValues { it > 1 }
    }
}
