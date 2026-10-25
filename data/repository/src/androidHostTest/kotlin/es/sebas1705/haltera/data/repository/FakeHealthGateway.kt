package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.data.repository.health.ExternalWeight
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthGateway
import es.sebas1705.haltera.data.repository.health.HealthMetric
import es.sebas1705.haltera.data.repository.health.HealthPermissionException
import es.sebas1705.haltera.data.repository.health.HealthRecordKind
import es.sebas1705.haltera.data.repository.health.HealthTotals
import es.sebas1705.haltera.data.repository.health.HealthWeight
import es.sebas1705.haltera.data.repository.health.HealthWorkout
import es.sebas1705.haltera.data.repository.health.metrics
import kotlin.math.roundToLong

/**
 * An in-memory health platform that behaves like Health Connect for what Haltera uses: per-type permissions
 * (a call without them throws), aggregation over a time range, client-id upserts with versions, deletes by
 * client id and revocation.
 */
class FakeHealthGateway : HealthGateway {
    data class Sample(val metric: HealthMetric, val atMillis: Long, val value: Double)

    var availability = HealthAvailability.AVAILABLE
    var granted: MutableSet<HealthDataType> = HealthDataType.entries.toMutableSet()
    val samples = mutableListOf<Sample>()
    val weights = mutableListOf<ExternalWeight>()
    val workouts = linkedMapOf<String, HealthWorkout>()
    val writtenWeights = linkedMapOf<String, HealthWeight>()
    val aggregateCalls = mutableListOf<Triple<Long, Long, Set<HealthMetric>>>()
    var workoutWrites = 0
    var weightWrites = 0
    var revoked = false

    /** grantedTypes() throws (the provider is busy). */
    var grantsFail = false

    /** Thrown (once) by the next platform call other than availability and grants. */
    var failNext: Exception? = null

    /** Revokes these types after this many aggregate calls (a permission removed in system settings mid-read). */
    var revokeAfterAggregates: Pair<Int, Set<HealthDataType>>? = null

    val calls: Int get() = aggregateCalls.size + workoutWrites + weightWrites + weightReads
    private var weightReads = 0

    fun steps(atMillis: Long, count: Long) = samples.add(Sample(HealthMetric.STEPS, atMillis, count.toDouble()))

    private fun maybeFail() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    private fun require(type: HealthDataType) {
        if (type !in granted) throw HealthPermissionException("missing $type")
    }

    override suspend fun availability() = availability

    override suspend fun grantedTypes(): Set<HealthDataType> {
        check(!grantsFail) { "busy" }
        return if (availability == HealthAvailability.AVAILABLE) granted.toSet() else emptySet()
    }

    override suspend fun aggregate(startMillis: Long, endMillis: Long, metrics: Set<HealthMetric>): HealthTotals {
        maybeFail()
        metrics.forEach { m -> require(HealthDataType.entries.first { m in it.metrics }) }
        aggregateCalls += Triple(startMillis, endMillis, metrics)
        revokeAfterAggregates?.let { (after, types) -> if (aggregateCalls.size >= after) granted.removeAll(types) }
        fun values(m: HealthMetric) = samples.filter { it.metric == m && it.atMillis in startMillis until endMillis }
            .map { it.value }.takeIf { m in metrics && it.isNotEmpty() }
        return HealthTotals(
            steps = values(HealthMetric.STEPS)?.sum()?.roundToLong(),
            activeKcal = values(HealthMetric.ACTIVE_CALORIES)?.sum(),
            totalKcal = values(HealthMetric.TOTAL_CALORIES)?.sum(),
            restingHeartRate = values(HealthMetric.RESTING_HEART_RATE)?.average()?.roundToLong(),
            sleepMinutes = values(HealthMetric.SLEEP)?.sum()?.roundToLong(),
        )
    }

    override suspend fun readWeights(startMillis: Long, endMillis: Long): List<ExternalWeight> {
        maybeFail()
        require(HealthDataType.WEIGHT_IMPORT)
        weightReads++
        val own = writtenWeights.values.map {
            ExternalWeight(
                "own-${it.clientRecordId}",
                it.measuredAt,
                it.weightGrams,
                "es.sebas1705.haltera.debug",
                it.clientRecordId,
                it.version
            )
        }
        return (weights + own).filter { it.measuredAt in startMillis until endMillis }
    }

    override suspend fun writeWorkouts(workouts: List<HealthWorkout>) {
        maybeFail()
        require(HealthDataType.WORKOUTS_EXPORT)
        workoutWrites++
        workouts.forEach { w ->
            val existing = this.workouts[w.clientRecordId]
            if (existing == null || w.version > existing.version) this.workouts[w.clientRecordId] = w
        }
    }

    override suspend fun writeWeights(weights: List<HealthWeight>) {
        maybeFail()
        require(HealthDataType.WEIGHT_EXPORT)
        weightWrites++
        weights.forEach { w ->
            val existing = writtenWeights[w.clientRecordId]
            if (existing == null || w.version > existing.version) writtenWeights[w.clientRecordId] = w
        }
    }

    override suspend fun delete(kind: HealthRecordKind, clientRecordIds: List<String>) {
        maybeFail()
        when (kind) {
            HealthRecordKind.WORKOUT -> {
                require(HealthDataType.WORKOUTS_EXPORT)
                clientRecordIds.forEach { workouts.remove(it) }
            }
            HealthRecordKind.WEIGHT -> {
                require(HealthDataType.WEIGHT_EXPORT)
                clientRecordIds.forEach { writtenWeights.remove(it) }
            }
        }
    }

    override suspend fun revokeAll() {
        revoked = true
        granted.clear()
    }
}
