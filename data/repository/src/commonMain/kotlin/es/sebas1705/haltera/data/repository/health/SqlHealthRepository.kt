package es.sebas1705.haltera.data.repository.health

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.HealthDayEntity
import es.sebas1705.haltera.data.models.HealthDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [HealthRepository] on the local database and a [HealthGateway].
 *
 * Days are local calendar days in [timeZone] (a 23- or 25-hour day around a DST change is still one day); sleep
 * counts for the day the night ended (18:00 the day before to 18:00). The first read after connecting, or after a
 * read type is switched on, and every forced read ("Read now"), covers [HISTORY_DAYS] days (Health Connect gives
 * apps 30 days before the first grant); later reads re-read from [OVERLAP_DAYS] days before the last read,
 * because steps arrive late from watches.
 *
 * Loop prevention: weights Haltera wrote (client id `haltera-…` or written by a Haltera package) are never
 * imported, and imported weights (id `hc-…`) are never written back. Each written record carries a client id,
 * so writing again updates instead of duplicating; the export ledger remembers what was written to rewrite only
 * changes and to delete what was deleted in Haltera.
 */
@OptIn(ExperimentalTime::class)
@Suppress("TooManyFunctions") // One function per stage of a read.
internal class SqlHealthRepository(
    private val db: AppDatabase,
    private val gateway: HealthGateway,
    private val settings: HealthSettingsStore,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : HealthRepository {

    private val mutableStatus = MutableStateFlow(HealthStatus())
    override val status: StateFlow<HealthStatus> = mutableStatus.asStateFlow()
    private val mutex = Mutex()

    override suspend fun checkStatus(): HealthStatus {
        val availability = runCatchingNonCancel { gateway.availability() }.getOrDefault(HealthAvailability.UNSUPPORTED)
        val granted = if (availability == HealthAvailability.AVAILABLE) grantedOrEmpty() else emptySet()
        mutableStatus.update { it.copy(availability = availability, granted = granted) }
        return status.value
    }

    override suspend fun onPermissionsResult() {
        val status = checkStatus()
        if (status.granted.isEmpty()) return
        val time = now()
        settings.update {
            it.copy(
                connected = true,
                lastRefreshAt = 0,
                exportSince = it.exportSince.takeIf { since -> since > 0 } ?: (time - EXPORT_BACKFILL_MILLIS),
            )
        }
        refresh(force = true)
    }

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) {
        val time = now()
        settings.update { s ->
            if (enabled) {
                s.copy(
                    enabled = s.enabled + type,
                    // A newly read type gets its whole history on the next read.
                    lastRefreshAt = if (type.writes) s.lastRefreshAt else 0,
                    exportSince = if (type.writes && s.exportSince == 0L) time - EXPORT_BACKFILL_MILLIS else s.exportSince,
                )
            } else {
                s.copy(enabled = s.enabled - type)
            }
        }
        if (!enabled) withContext(dispatcher) { clearCached(type) }
    }

    private fun clearCached(type: HealthDataType) = with(db.healthDayEntityQueries) {
        when (type) {
            HealthDataType.STEPS -> clearSteps()
            HealthDataType.CALORIES -> clearCalories()
            HealthDataType.RESTING_HEART_RATE -> clearRestingHeartRate()
            HealthDataType.SLEEP -> clearSleep()
            HealthDataType.WEIGHT_IMPORT, HealthDataType.WORKOUTS_EXPORT, HealthDataType.WEIGHT_EXPORT -> Unit
        }
    }

    override suspend fun refresh(force: Boolean) {
        if (!settings.settings.value.connected) return
        if (force) {
            // "Read now" and connecting re-read the whole window: data can arrive late for any day (a new app,
            // a changed priority in Health Connect).
            mutex.withLock { if (settings.settings.value.connected) readAndWrite(fullHistory = true) }
            return
        }
        val last = settings.settings.value.lastRefreshAt
        if (last != 0L && now() - last in 0 until MIN_REFRESH_INTERVAL_MILLIS) return
        // A read already running covers this one.
        if (!mutex.tryLock()) return
        try {
            readAndWrite(fullHistory = false)
        } finally {
            mutex.unlock()
        }
    }

    /** Set for the duration of one read: re-read every day of the window instead of the recent ones. */
    private var fullHistory = false

    private suspend fun readAndWrite(fullHistory: Boolean) {
        this.fullHistory = fullHistory
        runStages(listOf(::readDays, ::importWeights, ::exportWorkouts, ::exportWeights), markRead = true)
    }

    override suspend fun writeChanges() {
        if (!settings.settings.value.connected) return
        mutex.withLock {
            val writes = listOf(::exportWorkouts, ::exportWeights)
            if (settings.settings.value.connected) runStages(writes, markRead = false)
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun watchLocalChanges() {
        val q = db.healthExportEntityQueries
        combine(
            q.selectFinishedSessionsSince(0).asFlow().mapToList(dispatcher),
            q.selectOwnWeightsSince(0).asFlow().mapToList(dispatcher),
        ) { sessions, weights -> sessions to weights }
            .drop(1) // The current state: nothing changed yet.
            .debounce(WRITE_DEBOUNCE_MILLIS)
            .collect { writeChanges() }
    }

    /** Runs [stages] for the enabled and granted types; a failing stage does not stop the next ones. */
    private suspend fun runStages(stages: List<suspend (Set<HealthDataType>) -> Unit>, markRead: Boolean) {
        mutableStatus.update { it.copy(syncing = true) }
        val availability = runCatchingNonCancel { gateway.availability() }.getOrDefault(HealthAvailability.UNSUPPORTED)
        if (availability != HealthAvailability.AVAILABLE) {
            mutableStatus.update { HealthStatus(availability = availability) }
            return
        }
        val started = now()
        val grants = runCatchingNonCancel { gateway.grantedTypes() }
        var granted = grants.getOrDefault(emptySet())
        // A read permission removed in Health Connect also removes what was copied with it (not when the grants
        // could not be read at all: that is a platform hiccup, not a revocation).
        if (markRead && grants.isSuccess) forgetRevoked(granted)
        var error: HealthSyncError? = null
        for (stage in stages) {
            val active = settings.settings.value.enabled intersect granted
            try {
                stage(active)
            } catch (e: CancellationException) {
                mutableStatus.update { it.copy(syncing = false) }
                throw e
            } catch (@Suppress("SwallowedException") e: HealthPermissionException) {
                // Revoked while in use: re-read what is still granted and carry on with that.
                error = HealthSyncError.PERMISSION_MISSING
                granted = grantedOrEmpty()
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // RemoteException, rate limits, IllegalState from the provider: retried on the next read.
                if (error == null) error = HealthSyncError.FAILED
            }
        }
        if (markRead && error != HealthSyncError.FAILED) settings.update { it.copy(lastRefreshAt = started) }
        mutableStatus.update { HealthStatus(availability, granted, syncing = false, lastError = error) }
    }

    private suspend fun forgetRevoked(granted: Set<HealthDataType>) {
        val revoked = HealthDataType.entries.filter { it.metrics.isNotEmpty() && it !in granted }
        if (revoked.isNotEmpty()) withContext(dispatcher) { db.transaction { revoked.forEach(::clearCached) } }
    }

    private suspend fun grantedOrEmpty(): Set<HealthDataType> =
        runCatchingNonCancel { gateway.grantedTypes() }.getOrDefault(emptySet())

    // --- Reading daily totals ---

    private suspend fun readDays(active: Set<HealthDataType>) {
        val metrics = active.flatMap { it.metrics }.toSet()
        if (metrics.isEmpty()) return
        val zone = timeZone()
        val s = settings.settings.value
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
        val earliest = today.minus(DatePeriod(days = HISTORY_DAYS - 1))
        val cacheEmpty = withContext(dispatcher) { db.healthDayEntityQueries.countAll().executeAsOne() == 0L }
        val from = if (fullHistory || s.lastRefreshAt == 0L || cacheEmpty) {
            earliest
        } else {
            maxOf(earliest, Instant.fromEpochMilliseconds(s.lastRefreshAt).toLocalDateTime(zone).date.minus(OVERLAP))
        }
        val dayMetrics = metrics - HealthMetric.SLEEP
        val readTime = now()
        val dates = generateSequence(from) { it.plus(ONE_DAY) }.takeWhile { it <= today }.toList()
        val rows = dates.map { date ->
            val window = HealthDayWindows.day(date, zone)
            val totals = if (dayMetrics.isEmpty()) {
                HealthTotals()
            } else {
                gateway.aggregate(window.first, window.second, dayMetrics)
            }
            val sleep = if (HealthMetric.SLEEP in metrics) {
                val night = HealthDayWindows.night(date, zone)
                gateway.aggregate(night.first, night.second, setOf(HealthMetric.SLEEP)).sleepMinutes
            } else {
                null
            }
            HealthDayEntity(
                date = date.toString(),
                steps = totals.steps.takeIf { HealthMetric.STEPS in metrics },
                activeKcal = totals.activeKcal.takeIf { HealthMetric.ACTIVE_CALORIES in metrics },
                totalKcal = totals.totalKcal.takeIf { HealthMetric.TOTAL_CALORIES in metrics },
                restingHeartRate = totals.restingHeartRate.takeIf { HealthMetric.RESTING_HEART_RATE in metrics },
                sleepMinutes = sleep,
                updatedAt = readTime,
            )
        }
        withContext(dispatcher) {
            db.transaction {
                rows.forEach {
                    db.healthDayEntityQueries.upsert(
                        it.date,
                        it.steps,
                        it.activeKcal,
                        it.totalKcal,
                        it.restingHeartRate,
                        it.sleepMinutes,
                        it.updatedAt,
                    )
                }
            }
        }
    }

    // --- Importing weights ---

    private suspend fun importWeights(active: Set<HealthDataType>) {
        if (HealthDataType.WEIGHT_IMPORT !in active) return
        val time = now()
        val records = gateway.readWeights(time - HISTORY_DAYS * DAY_MILLIS, time + FUTURE_SLACK_MILLIS)
            .filterNot { isOwn(it) }
        val watermark = settings.settings.value.weightWatermark
        val fresh = records.filter { it.lastModifiedAt > watermark && it.weightGrams in PLAUSIBLE_GRAMS }
            .sortedBy { it.measuredAt }
        withContext(dispatcher) {
            db.transaction {
                fresh.forEach { record ->
                    val id = IMPORTED_ID_PREFIX + record.id
                    val q = db.healthExportEntityQueries
                    if (q.bodyWeightExists(id).executeAsOne() > 0) return@forEach
                    val typedByHand = q.weightsAround(record.measuredAt - NEAR_MILLIS, record.measuredAt + NEAR_MILLIS)
                        .executeAsList().any { abs(it.weightGrams - record.weightGrams) <= NEAR_GRAMS }
                    if (!typedByHand) q.insertImportedWeight(id, record.measuredAt, record.weightGrams)
                }
            }
        }
        val newest = records.maxOfOrNull { it.lastModifiedAt } ?: return
        if (newest > watermark) settings.update { it.copy(weightWatermark = newest) }
    }

    private fun isOwn(record: ExternalWeight): Boolean =
        record.clientRecordId?.startsWith(HealthGateway.CLIENT_ID_PREFIX) == true ||
            record.originPackage.startsWith(OWN_PACKAGE_PREFIX)

    // --- Writing workouts and weights ---

    private suspend fun exportWorkouts(active: Set<HealthDataType>) {
        if (HealthDataType.WORKOUTS_EXPORT !in active) return
        val since = exportSince()
        val local = withContext(dispatcher) {
            db.healthExportEntityQueries.selectFinishedSessionsSince(since).executeAsList()
        }.mapNotNull { row ->
            val end = row.endedAt
            if (end <= row.startedAt) return@mapNotNull null
            val title = row.dayName?.takeIf { it.isNotBlank() }?.let { "$WORKOUT_TITLE · $it" } ?: WORKOUT_TITLE
            Exported(row.id, "${row.startedAt}|$end|$title") { version ->
                HealthWorkout(HealthGateway.workoutClientId(row.id), version, row.startedAt, end, title)
            }
        }
        export(
            KIND_WORKOUT,
            local,
            HealthRecordKind.WORKOUT,
            HealthGateway::workoutClientId
        ) { gateway.writeWorkouts(it) }
    }

    private suspend fun exportWeights(active: Set<HealthDataType>) {
        if (HealthDataType.WEIGHT_EXPORT !in active) return
        val since = exportSince()
        val local = withContext(dispatcher) {
            db.healthExportEntityQueries.selectOwnWeightsSince(since).executeAsList()
        }.map { row ->
            Exported(row.id, "${row.measuredAt}|${row.weightGrams}") { version ->
                HealthWeight(HealthGateway.weightClientId(row.id), version, row.measuredAt, row.weightGrams)
            }
        }
        export(KIND_WEIGHT, local, HealthRecordKind.WEIGHT, HealthGateway::weightClientId) { gateway.writeWeights(it) }
    }

    private fun exportSince(): Long {
        val since = settings.settings.value.exportSince
        if (since > 0) return since
        val start = now() - EXPORT_BACKFILL_MILLIS
        settings.update { it.copy(exportSince = start) }
        return start
    }

    /** A local item to write, its fingerprint and how to build the record for a version. */
    private class Exported<T>(val id: String, val fingerprint: String, val record: (version: Long) -> T)

    private suspend fun <T> export(
        kind: String,
        local: List<Exported<T>>,
        recordKind: HealthRecordKind,
        clientId: (String) -> String,
        write: suspend (List<T>) -> Unit,
    ) {
        val rows = withContext(dispatcher) { db.healthExportEntityQueries.selectByKind(kind).executeAsList() }
        val ledger = rows.associate { it.localId to it.fingerprint }
        val changed = local.filter { ledger[it.id] != it.fingerprint }
        val removed = ledger.keys - local.map { it.id }.toSet()
        // The platform keeps a record unless the new version is higher: never reuse one, even within a millisecond.
        var lastVersion = rows.maxOfOrNull { it.writtenAt } ?: 0L
        changed.chunked(BATCH).forEach { batch ->
            val version = maxOf(now(), lastVersion + 1).also { lastVersion = it }
            write(batch.map { it.record(version) })
            withContext(dispatcher) {
                db.transaction {
                    batch.forEach { db.healthExportEntityQueries.upsert(kind, it.id, it.fingerprint, version) }
                }
            }
        }
        removed.chunked(BATCH).forEach { batch ->
            gateway.delete(recordKind, batch.map(clientId))
            withContext(dispatcher) {
                db.transaction { batch.forEach { db.healthExportEntityQueries.delete(kind, it) } }
            }
        }
    }

    // --- Disconnecting and reading the cache ---

    override suspend fun disconnect() {
        mutex.withLock {
            runCatchingNonCancel { gateway.revokeAll() }
            withContext(dispatcher) {
                db.transaction {
                    db.healthDayEntityQueries.deleteAll()
                    db.healthExportEntityQueries.deleteAll()
                }
            }
            // The weight watermark stays: reconnecting does not bring back entries the user deleted.
            settings.update { it.copy(connected = false, lastRefreshAt = 0, exportSince = 0) }
            mutableStatus.update { it.copy(granted = emptySet(), syncing = false, lastError = null) }
        }
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<HealthDay>> =
        db.healthDayEntityQueries.selectBetween(from.toString(), to.toString()).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    private fun HealthDayEntity.toModel() = HealthDay(
        date = LocalDate.parse(date),
        steps = steps,
        activeKcal = activeKcal,
        totalKcal = totalKcal,
        restingHeartRate = restingHeartRate,
        sleepMinutes = sleepMinutes,
    )

    private inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(e)
    }

    companion object {
        /** Days read on the first read; Health Connect gives apps 30 days before the first permission. */
        const val HISTORY_DAYS = 30

        /** Days re-read before the last read (late watch syncs). */
        const val OVERLAP_DAYS = 2

        /** Foreground reads closer together than this are skipped (Health Connect has rate limits). */
        const val MIN_REFRESH_INTERVAL_MILLIS = 2 * 60_000L

        /** On connecting, the workouts and weights of the last 30 days are written too. */
        const val EXPORT_BACKFILL_MILLIS = 30 * 24 * 60 * 60_000L

        /** Id prefix of body-weight entries imported from the platform. */
        const val IMPORTED_ID_PREFIX = "hc-"

        /** Any Haltera build (release `es.sebas1705.haltera`, debug `.debug`). */
        const val OWN_PACKAGE_PREFIX = "es.sebas1705.haltera"

        /** Title of written workouts; the routine day name follows it ("Haltera · Push"). */
        const val WORKOUT_TITLE = "Haltera"

        /** An imported weight within 30 minutes and 100 g of an entry already in the log is the same reading. */
        const val NEAR_MILLIS = 30 * 60_000L
        const val NEAR_GRAMS = 100L

        /** A finished workout or a logged weight is written this long after the last change (edits come in bursts). */
        const val WRITE_DEBOUNCE_MILLIS = 3_000L

        internal const val KIND_WORKOUT = "WORKOUT"
        internal const val KIND_WEIGHT = "WEIGHT"

        private const val BATCH = 100
        private const val DAY_MILLIS = 24 * 60 * 60_000L
        private const val FUTURE_SLACK_MILLIS = 60 * 60_000L
        private val PLAUSIBLE_GRAMS = 1_000L..1_000_000L
        private val ONE_DAY = DatePeriod(days = 1)
        private val OVERLAP = DatePeriod(days = OVERLAP_DAYS)
    }
}

/** The time ranges a local date covers, as epoch millis [start, end). */
@OptIn(ExperimentalTime::class)
object HealthDayWindows {
    private const val NIGHT_SPLIT_HOUR = 18

    /** Local midnight to the next local midnight: 23 or 25 hours on DST change days. */
    fun day(date: LocalDate, zone: TimeZone): Pair<Long, Long> =
        date.atStartOfDayIn(zone).toEpochMilliseconds() to
            date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()

    /** 18:00 the day before to 18:00 on [date]: the night that ended on [date]. */
    fun night(date: LocalDate, zone: TimeZone): Pair<Long, Long> {
        val split = LocalTime(NIGHT_SPLIT_HOUR, 0)
        return LocalDateTime(date.minus(DatePeriod(days = 1)), split).toInstant(zone).toEpochMilliseconds() to
            LocalDateTime(date, split).toInstant(zone).toEpochMilliseconds()
    }
}
