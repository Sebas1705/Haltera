package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.sync.SyncCollections.BODY_WEIGHT
import es.sebas1705.haltera.data.sync.SyncCollections.EXERCISES
import es.sebas1705.haltera.data.sync.SyncCollections.ROUTINES
import es.sebas1705.haltera.data.sync.SyncCollections.SESSIONS
import es.sebas1705.haltera.data.sync.SyncCollections.SETTINGS
import es.sebas1705.haltera.data.sync.SyncCollections.SETTINGS_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    private fun Device.everything() {
        userExercise()
        routine()
        session()
        bodyWeight()
        db.routineEntityQueries.markActive("r1")
        settings.setUnit(WeightUnit.LB)
        settings.setRestSound(false)
        bridge.onLocal(SyncJson.encodeToString(SettingsPrefs.serializer(), bridge.prefs()))
    }

    private val roots = listOf(
        EXERCISES to "user-1",
        ROUTINES to "r1",
        SESSIONS to "w1",
        BODY_WEIGHT to "bw1",
        SETTINGS to SETTINGS_ID
    )

    @Test
    fun `every collection round-trips between two devices`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        assertTrue(d1.dirty().isEmpty())
        assertEquals(5, cloud.all("uid-1").size)

        d2.engine.syncNow()
        roots.forEach { (collection, id) ->
            assertEquals(
                d1.read(collection, id),
                d2.read(collection, id),
                "$collection/$id"
            )
        }
        assertEquals("r1", d2.db.routineEntityQueries.selectActive().executeAsOne().id)
        assertEquals(WeightUnit.LB, d2.settings.settings.value.unit)
        assertEquals(false, d2.settings.settings.value.restSound)
        // Applied data is clean: no echo.
        assertTrue(d2.dirty().isEmpty(), d2.dirty().toString())
        val pushes = cloud.pushes
        d2.engine.syncNow()
        d1.engine.syncNow()
        assertEquals(pushes, cloud.pushes)
        assertIs<SyncStatus.Idle>(d2.engine.status.value)
    }

    @Test
    fun `an edit on the other device replaces children in place`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        d2.engine.syncNow()
        d2.db.routineSlotEntityQueries.delete("r1-s1")
        d2.db.routineDayEntityQueries.update("Push heavy", "TRAINING", "r1-d1")
        d2.db.setLogEntityQueries.delete("w1-t2")
        d2.engine.syncNow()
        d1.engine.syncNow()
        assertEquals(d2.read(ROUTINES, "r1"), d1.read(ROUTINES, "r1"))
        assertEquals(d2.read(SESSIONS, "w1"), d1.read(SESSIONS, "w1"))
        assertNull(d1.db.routineSlotEntityQueries.selectById("r1-s1").executeAsOneOrNull())
        assertNull(d1.db.sessionExerciseEntityQueries.selectById("w1-e1").executeAsOne().slotId)
    }

    @Test
    fun `last writer wins when both devices edit`() = runTest {
        d1.routine()
        d1.engine.syncNow()
        d2.engine.syncNow()

        d1.db.routineEntityQueries.update("Older", "", 2, "r1")
        cloud.tick()
        d2.db.routineEntityQueries.update("Newer", "", 3, "r1")
        d1.engine.syncNow() // uploads Older
        d2.engine.syncNow() // Older is older than its dirty Newer: kept, then uploaded
        d1.engine.syncNow() // receives Newer

        assertEquals("Newer", d1.db.routineEntityQueries.selectById("r1").executeAsOne().name)
        assertEquals("Newer", d2.db.routineEntityQueries.selectById("r1").executeAsOne().name)
        assertTrue(d1.dirty().isEmpty())
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `a tombstone propagates`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        d2.engine.syncNow()

        d1.db.workoutSessionEntityQueries.delete("w1")
        d1.db.bodyWeightEntityQueries.delete("bw1")
        d1.engine.syncNow()
        assertEquals(true, cloud.doc("uid-1", SESSIONS, "w1")?.deleted)
        assertEquals("", cloud.doc("uid-1", SESSIONS, "w1")?.payload)

        d2.engine.syncNow()
        assertNull(d2.db.workoutSessionEntityQueries.selectById("w1").executeAsOneOrNull())
        assertTrue(d2.db.sessionExerciseEntityQueries.selectBySession("w1").executeAsList().isEmpty())
        assertNull(d2.db.syncTrackingQueries.selectBodyWeight("bw1").executeAsOneOrNull())
        assertEquals(1L, d2.state(SESSIONS, "w1")?.deleted)
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `routine tombstone keeps the sessions that used it`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        d2.engine.syncNow()
        d1.db.routineEntityQueries.delete("r1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertNull(d2.db.routineEntityQueries.selectById("r1").executeAsOneOrNull())
        assertNull(d2.db.workoutSessionEntityQueries.selectById("w1").executeAsOne().routineId)
        assertEquals(d1.read(SESSIONS, "w1"), d2.read(SESSIONS, "w1"))
    }

    @Test
    fun `changes made offline are queued and flushed`() = runTest {
        d1.engine.syncNow()
        cloud.online.value = false
        d1.bodyWeight()
        d1.engine.syncNow()
        val offline = d1.engine.status.value
        assertIs<SyncStatus.Offline>(offline)
        assertTrue(offline.pending >= 1)
        assertNull(cloud.doc("uid-1", BODY_WEIGHT, "bw1"))

        cloud.online.value = true
        d1.engine.syncNow()
        assertNotNull(cloud.doc("uid-1", BODY_WEIGHT, "bw1"))
        assertTrue(d1.dirty().isEmpty())
        assertIs<SyncStatus.Idle>(d1.engine.status.value)
    }

    @Test
    fun `an edit during the upload stays dirty`() = runTest {
        d1.bodyWeight()
        val read = d1.state(BODY_WEIGHT, "bw1")!!
        cloud.tick()
        d1.bodyWeight(grams = 82_000)
        d1.db.syncTrackingQueries.clearDirty(BODY_WEIGHT, "bw1", read.updatedAt)
        assertEquals(1L, d1.state(BODY_WEIGHT, "bw1")!!.dirty)
    }

    @Test
    fun `backfill marks the data logged before sync`() = runTest {
        val pre = Device(
            cloud,
            Dispatchers.Unconfined,
            FakeAccount(AccountSession.SignedIn("uid-2", isAnonymous = true))
        )
        pre.db.metaQueries.put(META_SYNC_APPLYING, "1") // data written without tracking
        pre.userExercise()
        pre.routine()
        pre.session()
        pre.bodyWeight()
        pre.settings.setRestSound(false)
        pre.db.metaQueries.put(META_SYNC_APPLYING, "0")
        assertTrue(pre.dirty().isEmpty())

        pre.engine.syncNow()
        roots.forEach { (c, id) -> assertNotNull(cloud.doc("uid-2", c, id), "$c/$id") }
        assertTrue(pre.dirty().isEmpty())
    }

    @Test
    fun `signing in to another account merges local data into it`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        // The Google account already has a newer routine from another phone.
        val remoteRoutine = d1.read(ROUTINES, "r1")!!.replace("Push Pull Legs", "From Google")
        cloud.tick()
        cloud.put("google", SyncDocument(ROUTINES, "r1", cloud.clock.now() + 1_000, false, remoteRoutine))

        d1.account.set(AccountSession.SignedIn("google", isAnonymous = false, email = "a@b.c"))
        d1.engine.syncNow()

        roots.filter { it.first != ROUTINES }.forEach { (c, id) ->
            assertEquals(cloud.doc("uid-1", c, id)?.payload, cloud.doc("google", c, id)?.payload, "$c/$id")
        }
        assertEquals("From Google", d1.db.routineEntityQueries.selectById("r1").executeAsOne().name)
        assertTrue(d1.dirty().isEmpty())
    }

    @Test
    fun `a deleted account is forgotten and its data is not re-uploaded`() = runTest {
        d1.everything()
        d1.engine.syncNow()
        d1.bodyWeight(grams = 90_000)
        d1.engine.forgetAccount()
        cloud.deleteAll("uid-1")
        d1.account.set(AccountSession.SignedOut)
        assertEquals(SyncStatus.Disabled, d1.engine.status.value)
        assertTrue(d1.dirty().isEmpty())

        d1.account.set(AccountSession.SignedIn("uid-new", isAnonymous = true))
        d1.engine.syncNow()
        assertTrue(cloud.all("uid-1").isEmpty())
        assertTrue(cloud.all("uid-new").isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a signed-in engine with the cloud copy off never touches the cloud and resumes when it is turned on`() =
        runTest {
            val io = UnconfinedTestDispatcher(testScheduler)
            val a = Device(cloud, io, cloudOn = MutableStateFlow(false))
            a.bodyWeight()
            a.engine.start(backgroundScope)
            runCurrent()
            a.engine.syncNow()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(SyncStatus.Disabled, a.engine.status.value)
            assertEquals(0, cloud.pushes)
            assertTrue(cloud.all("uid-1").isEmpty())

            a.cloudOn.value = true
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertNotNull(cloud.doc("uid-1", BODY_WEIGHT, "bw1"))

            a.cloudOn.value = false
            runCurrent()
            assertEquals(SyncStatus.Disabled, a.engine.status.value)
            val pushes = cloud.pushes
            a.bodyWeight("bw2")
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(pushes, cloud.pushes)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `started engines sync live while signed in`() = runTest {
        val io = UnconfinedTestDispatcher(testScheduler)
        val a = Device(cloud, io)
        val b = Device(cloud, io)
        a.engine.start(backgroundScope)
        b.engine.start(backgroundScope)
        runCurrent()

        a.bodyWeight()
        advanceTimeBy(100)
        runCurrent()
        assertNotNull(cloud.doc("uid-1", BODY_WEIGHT, "bw1"))
        assertNotNull(b.db.syncTrackingQueries.selectBodyWeight("bw1").executeAsOneOrNull())

        b.settings.setKeepScreenOn(false)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(false, a.settings.settings.value.keepScreenOn)

        a.account.set(AccountSession.SignedOut)
        runCurrent()
        assertEquals(SyncStatus.Disabled, a.engine.status.value)
    }
}
