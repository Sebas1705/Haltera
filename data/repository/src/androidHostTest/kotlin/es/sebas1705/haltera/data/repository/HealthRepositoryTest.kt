package es.sebas1705.haltera.data.repository

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.SettingsHealthSettingsStore
import es.sebas1705.haltera.data.repository.health.ExternalWeight
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthDayWindows
import es.sebas1705.haltera.data.repository.health.HealthGateway
import es.sebas1705.haltera.data.repository.health.HealthMetric
import es.sebas1705.haltera.data.repository.health.HealthSyncError
import es.sebas1705.haltera.data.repository.health.SqlHealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class HealthRepositoryTest {
    private val madrid = TimeZone.of("Europe/Madrid")
    private var zone = madrid
    private val db = inMemoryDatabase()
    private val store = SettingsHealthSettingsStore(MapSettings())
    private val gateway = FakeHealthGateway()
    private val clock = TestClock(at(2026, 10, 16, 12, 0))
    private val repository = SqlHealthRepository(
        db,
        gateway,
        store,
        UnconfinedTestDispatcher(),
        clock,
        timeZone = { zone },
    )

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, z: TimeZone = madrid): Long =
        LocalDateTime(y, m, d, h, min).toInstant(z).toEpochMilliseconds()

    private val today = LocalDate(2026, 10, 16)

    private suspend fun days(from: LocalDate = LocalDate(2026, 9, 1), to: LocalDate = today) =
        repository.observeDays(from, to).first()

    private suspend fun connect(vararg types: HealthDataType) {
        store.update { it.copy(enabled = types.toSet()) }
        repository.onPermissionsResult()
    }

    private fun session(id: String, start: Long, end: Long?, day: String? = "Push") {
        db.workoutSessionEntityQueries.insert(id, start, null, null, day)
        if (end != null) db.workoutSessionEntityQueries.finish(end, id)
    }

    private fun weight(id: String, measuredAt: Long, grams: Long, modifiedAt: Long = measuredAt, origin: String = "com.scale") =
        ExternalWeight(id, measuredAt, grams, origin, null, modifiedAt)

    // --- Connecting ---

    @Test
    fun `nothing is read before the user connects`() = runTest {
        gateway.steps(at(2026, 10, 16, 9), 1_000)
        repository.refresh(force = true)
        repository.refresh()
        assertEquals(0, gateway.calls)
        assertEquals(emptyList(), days())
    }

    @Test
    fun `denying every permission leaves the integration disconnected`() = runTest {
        gateway.granted.clear()
        connect(HealthDataType.STEPS)
        assertFalse(store.settings.value.connected)
        assertEquals(0, gateway.calls)
        assertEquals(HealthAvailability.AVAILABLE, repository.status.value.availability)
    }

    @Test
    fun `connecting reads 30 days of steps grouped by local day`() = runTest {
        gateway.steps(at(2026, 10, 16, 8), 3_000)
        gateway.steps(at(2026, 10, 16, 11), 1_500)
        gateway.steps(at(2026, 10, 15, 23, 59), 700)
        gateway.steps(at(2026, 10, 15, 0, 1), 300)
        gateway.steps(at(2026, 9, 16, 12), 9_999) // 31 days ago: outside the first read.
        connect(HealthDataType.STEPS)

        assertTrue(store.settings.value.connected)
        val days = days()
        assertEquals(30, days.size)
        assertEquals(LocalDate(2026, 9, 17), days.first().date)
        assertEquals(4_500L, days.last().steps)
        assertEquals(1_000L, days.single { it.date == LocalDate(2026, 10, 15) }.steps)
        assertNull(days.single { it.date == LocalDate(2026, 10, 1) }.steps) // No data is not zero.
        assertNull(days.last().activeKcal) // Not enabled: not read.
        assertEquals(clock.millis, store.settings.value.lastRefreshAt)
        assertEquals(null, repository.status.value.lastError)
    }

    @Test
    fun `each day is local midnight to midnight, 25 hours on the autumn DST change`() {
        val (start, end) = HealthDayWindows.day(LocalDate(2026, 10, 25), madrid)
        assertEquals(25 * 3_600_000L, end - start)
        val (springStart, springEnd) = HealthDayWindows.day(LocalDate(2026, 3, 29), madrid)
        assertEquals(23 * 3_600_000L, springEnd - springStart)
        val (s, e) = HealthDayWindows.day(LocalDate(2026, 10, 16), TimeZone.UTC)
        assertEquals(24 * 3_600_000L, e - s)
    }

    @Test
    fun `steps on the DST change day stay in that day`() = runTest {
        clock.millis = at(2026, 10, 27, 12)
        // 02:30 happens twice on 25 October in Madrid; both belong to the 25th.
        gateway.steps(LocalDateTime(2026, 10, 25, 2, 30).toInstant(TimeZone.of("+02:00")).toEpochMilliseconds(), 100)
        gateway.steps(LocalDateTime(2026, 10, 25, 2, 30).toInstant(TimeZone.of("+01:00")).toEpochMilliseconds(), 200)
        gateway.steps(at(2026, 10, 25, 23, 30), 50)
        gateway.steps(at(2026, 10, 26, 0, 30), 7)
        connect(HealthDataType.STEPS)
        val days = days(LocalDate(2026, 10, 24), LocalDate(2026, 10, 27))
        assertEquals(350L, days.single { it.date == LocalDate(2026, 10, 25) }.steps)
        assertEquals(7L, days.single { it.date == LocalDate(2026, 10, 26) }.steps)
    }

    @Test
    fun `the day boundary follows the device time zone`() = runTest {
        val newYork = TimeZone.of("America/New_York")
        zone = newYork
        clock.millis = at(2026, 10, 16, 12, z = newYork)
        // 02:00 UTC on the 16th is still the evening of the 15th in New York.
        gateway.steps(LocalDateTime(2026, 10, 16, 2, 0).toInstant(TimeZone.UTC).toEpochMilliseconds(), 1_234)
        connect(HealthDataType.STEPS)
        assertEquals(1_234L, days().single { it.date == LocalDate(2026, 10, 15) }.steps)
        assertNull(days().single { it.date == LocalDate(2026, 10, 16) }.steps)
    }

    @Test
    fun `sleep counts for the morning the night ended`() = runTest {
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.SLEEP, at(2026, 10, 14, 23, 30), 450.0)
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.SLEEP, at(2026, 10, 15, 15, 0), 30.0) // Nap.
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.SLEEP, at(2026, 10, 15, 19, 0), 10.0) // Next night.
        connect(HealthDataType.SLEEP)
        val days = days()
        assertEquals(480L, days.single { it.date == LocalDate(2026, 10, 15) }.sleepMinutes)
        assertEquals(10L, days.single { it.date == LocalDate(2026, 10, 16) }.sleepMinutes)
        val night = HealthDayWindows.night(LocalDate(2026, 10, 15), madrid)
        assertEquals(at(2026, 10, 14, 18) to at(2026, 10, 15, 18), night)
    }

    @Test
    fun `calories and resting heart rate are read when enabled`() = runTest {
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.ACTIVE_CALORIES, at(2026, 10, 16, 9), 250.5)
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.TOTAL_CALORIES, at(2026, 10, 16, 9), 2_100.0)
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.RESTING_HEART_RATE, at(2026, 10, 16, 7), 56.0)
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.RESTING_HEART_RATE, at(2026, 10, 16, 8), 60.0)
        connect(HealthDataType.CALORIES, HealthDataType.RESTING_HEART_RATE)
        val day = days().last()
        assertEquals(250.5, day.activeKcal)
        assertEquals(2_100.0, day.totalKcal)
        assertEquals(58L, day.restingHeartRate)
        assertNull(day.steps)
        // One aggregate per day for the day metrics, none for sleep.
        assertEquals(30, gateway.aggregateCalls.size)
        assertTrue(gateway.aggregateCalls.all { HealthMetric.STEPS !in it.third })
    }

    @Test
    fun `a type that is enabled but not granted is not read`() = runTest {
        gateway.granted.remove(HealthDataType.SLEEP)
        gateway.steps(at(2026, 10, 16, 9), 10)
        connect(HealthDataType.STEPS, HealthDataType.SLEEP)
        assertEquals(10L, days().last().steps)
        assertTrue(gateway.aggregateCalls.none { HealthMetric.SLEEP in it.third })
        assertEquals(null, repository.status.value.lastError)
        assertFalse(HealthDataType.SLEEP in repository.status.value.granted)
    }

    @Test
    fun `empty Health Connect gives empty days and no error`() = runTest {
        connect(HealthDataType.STEPS, HealthDataType.CALORIES, HealthDataType.SLEEP, HealthDataType.WEIGHT_IMPORT)
        assertTrue(days().all { it.steps == null && it.totalKcal == null && it.sleepMinutes == null })
        assertEquals(emptyList(), db.bodyWeightEntityQueries.selectAll().executeAsList())
        assertNull(repository.status.value.lastError)
    }

    // --- Availability and permissions ---

    @Test
    fun `Health Connect not installed reads nothing and says so`() = runTest {
        store.update { it.copy(connected = true) }
        gateway.availability = HealthAvailability.PROVIDER_MISSING
        repository.refresh(force = true)
        assertEquals(HealthAvailability.PROVIDER_MISSING, repository.status.value.availability)
        assertEquals(emptySet(), repository.status.value.granted)
        assertEquals(0, gateway.calls)
        assertEquals(HealthAvailability.PROVIDER_MISSING, repository.checkStatus().availability)
    }

    @Test
    fun `an unsupported device cannot connect`() = runTest {
        gateway.availability = HealthAvailability.UNSUPPORTED
        connect(HealthDataType.STEPS)
        assertFalse(store.settings.value.connected)
        assertEquals(HealthAvailability.UNSUPPORTED, repository.status.value.availability)
    }

    @Test
    fun `a permission revoked during a read stops that type and the rest carries on`() = runTest {
        gateway.steps(at(2026, 10, 16, 9), 500)
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        gateway.revokeAfterAggregates = 3 to setOf(HealthDataType.STEPS)
        connect(HealthDataType.STEPS, HealthDataType.WORKOUTS_EXPORT)
        val status = repository.status.value
        assertEquals(HealthSyncError.PERMISSION_MISSING, status.lastError)
        assertFalse(HealthDataType.STEPS in status.granted)
        assertTrue(HealthDataType.WORKOUTS_EXPORT in status.granted)
        assertEquals(1, gateway.workouts.size) // The export still ran.
        assertFalse(status.syncing)

        // Next read: steps are skipped without errors.
        val callsBefore = gateway.aggregateCalls.size
        repository.refresh(force = true)
        assertEquals(callsBefore, gateway.aggregateCalls.size)
        assertNull(repository.status.value.lastError)
    }

    @Test
    fun `a read permission revoked in Health Connect deletes what was copied with it`() = runTest {
        gateway.steps(at(2026, 10, 16, 9), 500)
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.RESTING_HEART_RATE, at(2026, 10, 16, 7), 57.0)
        connect(HealthDataType.STEPS, HealthDataType.RESTING_HEART_RATE)
        assertEquals(500L, days().last().steps)
        gateway.granted.remove(HealthDataType.STEPS)
        clock.millis += 10 * 60_000
        repository.refresh()
        assertTrue(days().all { it.steps == null })
        assertEquals(57L, days().last().restingHeartRate) // Still granted: kept.
        assertNull(repository.status.value.lastError)
    }

    @Test
    fun `failing to read the grants keeps the cache`() = runTest {
        gateway.steps(at(2026, 10, 16, 9), 500)
        connect(HealthDataType.STEPS)
        gateway.grantsFail = true
        clock.millis += 10 * 60_000
        repository.refresh()
        assertEquals(500L, days().last().steps)
    }

    @Test
    fun `revoking the write permission stops writes without errors`() = runTest {
        connect(HealthDataType.WORKOUTS_EXPORT)
        gateway.granted.remove(HealthDataType.WORKOUTS_EXPORT)
        session("s1", at(2026, 10, 16, 9), at(2026, 10, 16, 10))
        repository.refresh(force = true)
        assertEquals(0, gateway.workoutWrites)
        assertNull(repository.status.value.lastError)
    }

    @Test
    fun `a platform failure is reported and retried on the next foreground read`() = runTest {
        connect(HealthDataType.STEPS)
        val readAt = store.settings.value.lastRefreshAt
        clock.millis += 10 * 60_000
        gateway.failNext = IllegalStateException("rate limited")
        repository.refresh()
        assertEquals(HealthSyncError.FAILED, repository.status.value.lastError)
        assertEquals(readAt, store.settings.value.lastRefreshAt)
        clock.millis += 1_000
        gateway.steps(clock.millis - 1, 42)
        repository.refresh() // Not throttled: the failed read did not count.
        assertNull(repository.status.value.lastError)
        assertEquals(42L, days().last().steps)
    }

    // --- Foreground reads ---

    @Test
    fun `foreground reads are throttled and later ones re-read only the recent days`() = runTest {
        connect(HealthDataType.STEPS)
        val first = gateway.aggregateCalls.size
        clock.millis += 60_000
        repository.refresh()
        assertEquals(first, gateway.aggregateCalls.size)
        clock.millis += 2 * 60_000
        repository.refresh()
        // Today and the two days before it.
        assertEquals(first + 3, gateway.aggregateCalls.size)
    }

    @Test
    fun `old days that get data later are picked up by Read now, not by a foreground read`() = runTest {
        connect(HealthDataType.STEPS)
        // Another app (or a changed priority in Health Connect) adds steps to a day 20 days ago.
        gateway.steps(at(2026, 9, 26, 12), 5_555)
        clock.millis += 10 * 60_000
        repository.refresh()
        assertNull(days().single { it.date == LocalDate(2026, 9, 26) }.steps)
        repository.refresh(force = true)
        assertEquals(5_555L, days().single { it.date == LocalDate(2026, 9, 26) }.steps)
    }

    @Test
    fun `a day later the new day appears and late steps of yesterday are picked up`() = runTest {
        connect(HealthDataType.STEPS)
        clock.millis = at(2026, 10, 17, 9)
        gateway.steps(at(2026, 10, 16, 20), 2_000) // Synced late by a watch.
        gateway.steps(at(2026, 10, 17, 8), 900)
        repository.refresh()
        assertEquals(2_000L, days(to = LocalDate(2026, 10, 17)).single { it.date == today }.steps)
        assertEquals(900L, days(to = LocalDate(2026, 10, 17)).last().steps)
    }

    @Test
    fun `turning a read type on reads its whole history, turning it off deletes it`() = runTest {
        gateway.samples += FakeHealthGateway.Sample(HealthMetric.RESTING_HEART_RATE, at(2026, 10, 1, 7), 61.0)
        connect(HealthDataType.STEPS)
        clock.millis += 10 * 60_000
        repository.setEnabled(HealthDataType.RESTING_HEART_RATE, true)
        assertEquals(0L, store.settings.value.lastRefreshAt)
        repository.refresh()
        assertEquals(61L, days().single { it.date == LocalDate(2026, 10, 1) }.restingHeartRate)

        repository.setEnabled(HealthDataType.RESTING_HEART_RATE, false)
        assertTrue(days().all { it.restingHeartRate == null })
        assertFalse(HealthDataType.RESTING_HEART_RATE in store.settings.value.enabled)
    }

    // --- Weight import ---

    @Test
    fun `weights from other apps are imported once`() = runTest {
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 80_400)
        gateway.weights += weight("w2", at(2026, 10, 12, 8), 80_100)
        connect(HealthDataType.WEIGHT_IMPORT)
        clock.millis += 10 * 60_000
        repository.refresh(force = true)
        val entries = db.bodyWeightEntityQueries.selectAll().executeAsList()
        assertEquals(listOf("hc-w2", "hc-w1"), entries.map { it.id })
        assertEquals(80_400L, entries.last().weightGrams)
    }

    @Test
    fun `an imported entry the user deleted does not come back unless it changes`() = runTest {
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 80_400, modifiedAt = at(2026, 10, 10, 8))
        connect(HealthDataType.WEIGHT_IMPORT)
        db.bodyWeightEntityQueries.delete("hc-w1")
        repository.refresh(force = true)
        assertEquals(emptyList(), db.bodyWeightEntityQueries.selectAll().executeAsList())

        gateway.weights[0] = gateway.weights[0].copy(weightGrams = 80_000, lastModifiedAt = at(2026, 10, 16, 11))
        repository.refresh(force = true)
        assertEquals(80_000L, db.bodyWeightEntityQueries.selectAll().executeAsOne().weightGrams)
    }

    @Test
    fun `a scale reading also typed in by hand is not duplicated`() = runTest {
        db.bodyWeightEntityQueries.upsert("mine", at(2026, 10, 10, 8, 5), 80_450, "")
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 80_400)
        gateway.weights += weight("w2", at(2026, 10, 10, 20), 81_000) // Same day, hours later: a new reading.
        connect(HealthDataType.WEIGHT_IMPORT)
        assertEquals(
            setOf("mine", "hc-w2"),
            db.bodyWeightEntityQueries.selectAll().executeAsList().map { it.id }.toSet()
        )
    }

    @Test
    fun `implausible weights are skipped`() = runTest {
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 0)
        gateway.weights += weight("w2", at(2026, 10, 11, 8), 5_000_000)
        connect(HealthDataType.WEIGHT_IMPORT)
        assertEquals(emptyList(), db.bodyWeightEntityQueries.selectAll().executeAsList())
    }

    @Test
    fun `weights Haltera wrote are never imported back and imported ones are never written back`() = runTest {
        db.bodyWeightEntityQueries.upsert("mine", at(2026, 10, 14, 8), 79_900, "")
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 80_400)
        gateway.weights += ExternalWeight("w9", at(2026, 10, 9, 8), 70_000, "es.sebas1705.haltera", null, 1)
        gateway.weights += ExternalWeight("w8", at(2026, 10, 8, 8), 70_000, "other", "haltera-weight-x", 1)
        connect(HealthDataType.WEIGHT_IMPORT, HealthDataType.WEIGHT_EXPORT)
        repeat(3) {
            clock.millis += 10 * 60_000
            repository.refresh()
        }
        val ids = db.bodyWeightEntityQueries.selectAll().executeAsList().map { it.id }.toSet()
        assertEquals(setOf("mine", "hc-w1"), ids)
        assertEquals(setOf(HealthGateway.weightClientId("mine")), gateway.writtenWeights.keys)
    }

    // --- Writing workouts and weights ---

    @Test
    fun `finished workouts are written once, as strength sessions with the day name`() = runTest {
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19, 10), day = "Push")
        session("s2", at(2026, 10, 16, 9), null) // In progress.
        session("s3", at(2026, 10, 14, 18), at(2026, 10, 14, 19), day = null)
        session("old", at(2026, 8, 1, 18), at(2026, 8, 1, 19)) // Before the 30-day backfill.
        connect(HealthDataType.WORKOUTS_EXPORT)
        assertEquals(
            setOf(HealthGateway.workoutClientId("s1"), HealthGateway.workoutClientId("s3")),
            gateway.workouts.keys,
        )
        val w = gateway.workouts.getValue(HealthGateway.workoutClientId("s1"))
        assertEquals("Haltera · Push", w.title)
        assertEquals(at(2026, 10, 15, 18) to at(2026, 10, 15, 19, 10), w.startMillis to w.endMillis)
        assertEquals("Haltera", gateway.workouts.getValue(HealthGateway.workoutClientId("s3")).title)

        val writes = gateway.workoutWrites
        clock.millis += 10 * 60_000
        repository.refresh()
        assertEquals(writes, gateway.workoutWrites) // Nothing changed: nothing written.
    }

    @Test
    fun `an edited workout is rewritten with a higher version and a deleted one is deleted`() = runTest {
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        session("s2", at(2026, 10, 14, 18), at(2026, 10, 14, 19))
        connect(HealthDataType.WORKOUTS_EXPORT)
        val v1 = gateway.workouts.getValue(HealthGateway.workoutClientId("s1")).version

        clock.millis += 10 * 60_000
        db.workoutSessionEntityQueries.updateTimes(at(2026, 10, 15, 17), at(2026, 10, 15, 19), "s1")
        db.workoutSessionEntityQueries.delete("s2")
        repository.refresh()
        val w = gateway.workouts.getValue(HealthGateway.workoutClientId("s1"))
        assertTrue(w.version > v1)
        assertEquals(at(2026, 10, 15, 17), w.startMillis)
        assertEquals(setOf(HealthGateway.workoutClientId("s1")), gateway.workouts.keys)
        assertEquals(
            listOf("s1"),
            db.healthExportEntityQueries.selectByKind("WORKOUT").executeAsList().map { it.localId }
        )
    }

    @Test
    fun `a workout reopened in progress is removed and a zero-length one is not written`() = runTest {
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        session("s2", at(2026, 10, 15, 20), at(2026, 10, 15, 20))
        connect(HealthDataType.WORKOUTS_EXPORT)
        assertEquals(setOf(HealthGateway.workoutClientId("s1")), gateway.workouts.keys)
        db.workoutSessionEntityQueries.updateTimes(at(2026, 10, 15, 18), null, "s1")
        repository.refresh(force = true)
        assertEquals(emptySet(), gateway.workouts.keys)
    }

    @Test
    fun `own body weight is written, rewritten when edited and deleted when deleted`() = runTest {
        db.bodyWeightEntityQueries.upsert("a", at(2026, 10, 14, 8), 80_000, "")
        db.bodyWeightEntityQueries.upsert("b", at(2026, 10, 15, 8), 79_800, "")
        connect(HealthDataType.WEIGHT_EXPORT)
        assertEquals(2, gateway.writtenWeights.size)
        db.bodyWeightEntityQueries.upsert("a", at(2026, 10, 14, 8), 80_200, "")
        db.bodyWeightEntityQueries.delete("b")
        repository.refresh(force = true)
        assertEquals(80_200L, gateway.writtenWeights.getValue(HealthGateway.weightClientId("a")).weightGrams)
        assertEquals(setOf(HealthGateway.weightClientId("a")), gateway.writtenWeights.keys)
    }

    @Test
    fun `nothing is written for types that are switched off`() = runTest {
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        db.bodyWeightEntityQueries.upsert("a", at(2026, 10, 14, 8), 80_000, "")
        connect(HealthDataType.STEPS)
        assertEquals(0, gateway.workoutWrites + gateway.weightWrites)
    }

    @Test
    fun `write changes writes a just finished workout without reading anything`() = runTest {
        connect(HealthDataType.STEPS, HealthDataType.WORKOUTS_EXPORT)
        val aggregates = gateway.aggregateCalls.size
        val readAt = store.settings.value.lastRefreshAt
        clock.millis += 30_000
        session("s1", at(2026, 10, 16, 11), at(2026, 10, 16, 12))
        repository.writeChanges()
        assertEquals(setOf(HealthGateway.workoutClientId("s1")), gateway.workouts.keys)
        assertEquals(aggregates, gateway.aggregateCalls.size)
        assertEquals(readAt, store.settings.value.lastRefreshAt) // The next foreground read is not skipped.
    }

    @Test
    fun `write changes does nothing before connecting`() = runTest {
        session("s1", at(2026, 10, 16, 11), at(2026, 10, 16, 12))
        repository.writeChanges()
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `while watched, a change in the log is written a few seconds later`() = runTest {
        val repo = SqlHealthRepository(db, gateway, store, UnconfinedTestDispatcher(testScheduler), clock) { zone }
        store.update { it.copy(enabled = setOf(HealthDataType.WORKOUTS_EXPORT, HealthDataType.WEIGHT_EXPORT)) }
        repo.onPermissionsResult()
        val watcher = backgroundScope.launch { repo.watchLocalChanges() }
        runCurrent()
        session("s1", at(2026, 10, 16, 11), at(2026, 10, 16, 12))
        db.bodyWeightEntityQueries.upsert("w", at(2026, 10, 16, 8), 80_000, "")
        advanceTimeBy(SqlHealthRepository.WRITE_DEBOUNCE_MILLIS - 1)
        assertTrue(gateway.workouts.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(setOf(HealthGateway.workoutClientId("s1")), gateway.workouts.keys)
        assertEquals(setOf(HealthGateway.weightClientId("w")), gateway.writtenWeights.keys)

        db.workoutSessionEntityQueries.delete("s1")
        advanceTimeBy(SqlHealthRepository.WRITE_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertTrue(gateway.workouts.isEmpty())
        watcher.cancel()
    }

    // --- Disconnecting ---

    @Test
    fun `disconnecting revokes, forgets the cache and the ledger and keeps the log`() = runTest {
        gateway.steps(at(2026, 10, 16, 9), 1_000)
        gateway.weights += weight("w1", at(2026, 10, 10, 8), 80_400)
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        connect(HealthDataType.STEPS, HealthDataType.WEIGHT_IMPORT, HealthDataType.WORKOUTS_EXPORT)

        repository.disconnect()
        assertTrue(gateway.revoked)
        assertFalse(store.settings.value.connected)
        assertEquals(emptyList(), days())
        assertEquals(emptyList(), db.healthExportEntityQueries.selectByKind("WORKOUT").executeAsList())
        assertEquals(listOf("hc-w1"), db.bodyWeightEntityQueries.selectAll().executeAsList().map { it.id })
        assertEquals(emptySet(), repository.status.value.granted)
        val calls = gateway.calls
        repository.refresh(force = true)
        assertEquals(calls, gateway.calls)
    }

    @Test
    fun `reconnecting rewrites workouts as updates, not duplicates`() = runTest {
        session("s1", at(2026, 10, 15, 18), at(2026, 10, 15, 19))
        connect(HealthDataType.WORKOUTS_EXPORT)
        repository.disconnect()
        gateway.granted.addAll(HealthDataType.entries)
        clock.millis += 60_000
        connect(HealthDataType.WORKOUTS_EXPORT)
        assertEquals(1, gateway.workouts.size)
    }

    @Test
    fun `check status reports what is granted`() = runTest {
        gateway.granted = mutableSetOf(HealthDataType.STEPS)
        val status = repository.checkStatus()
        assertEquals(HealthAvailability.AVAILABLE, status.availability)
        assertEquals(setOf(HealthDataType.STEPS), status.granted)
    }
}
