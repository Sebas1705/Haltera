package es.sebas1705.haltera.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import es.sebas1705.haltera.data.repository.health.HealthMetric
import es.sebas1705.haltera.data.repository.health.HealthTotals
import java.time.Instant
import kotlin.reflect.KClass

/** The part of Health Connect Haltera uses: a seam so [HealthConnectGateway] is tested with a fake. */
internal interface HcClient {
    fun sdkStatus(): Int

    suspend fun grantedPermissions(): Set<String>

    /** [metrics] over [start, end); Health Connect removes overlaps between apps by the user's priority. */
    suspend fun aggregate(start: Instant, end: Instant, metrics: Set<HealthMetric>): HealthTotals

    /** Every weight record measured in [start, end), all pages. */
    suspend fun readWeights(start: Instant, end: Instant): List<WeightRecord>

    /** Inserts; a record whose client id exists is updated when its version is higher. */
    suspend fun insert(records: List<Record>)

    suspend fun deleteByClientIds(type: KClass<out Record>, clientRecordIds: List<String>)

    suspend fun revokeAll()
}

/** [HcClient] on the real [HealthConnectClient], created only once Health Connect is available. */
internal class RealHcClient(private val context: Context) : HcClient {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    override suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    override suspend fun aggregate(start: Instant, end: Instant, metrics: Set<HealthMetric>): HealthTotals {
        val wanted: Set<AggregateMetric<*>> = metrics.flatMap { it.aggregateMetrics() }.toSet()
        val result = client.aggregate(AggregateRequest(wanted, TimeRangeFilter.between(start, end)))
        return HealthTotals(
            steps = result[StepsRecord.COUNT_TOTAL].takeIf { HealthMetric.STEPS in metrics },
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                .takeIf { HealthMetric.ACTIVE_CALORIES in metrics },
            totalKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                .takeIf { HealthMetric.TOTAL_CALORIES in metrics },
            restingHeartRate = result[RestingHeartRateRecord.BPM_AVG].takeIf { HealthMetric.RESTING_HEART_RATE in metrics },
            sleepMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()
                .takeIf { HealthMetric.SLEEP in metrics },
        )
    }

    private fun HealthMetric.aggregateMetrics(): List<AggregateMetric<*>> = when (this) {
        HealthMetric.STEPS -> listOf(StepsRecord.COUNT_TOTAL)
        HealthMetric.ACTIVE_CALORIES -> listOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        HealthMetric.TOTAL_CALORIES -> listOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        HealthMetric.RESTING_HEART_RATE -> listOf(RestingHeartRateRecord.BPM_AVG)
        HealthMetric.SLEEP -> listOf(SleepSessionRecord.SLEEP_DURATION_TOTAL)
    }

    override suspend fun readWeights(start: Instant, end: Instant): List<WeightRecord> {
        val all = mutableListOf<WeightRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = token,
                ),
            )
            all += response.records
            token = response.pageToken
        } while (token != null)
        return all
    }

    override suspend fun insert(records: List<Record>) {
        client.insertRecords(records)
    }

    override suspend fun deleteByClientIds(type: KClass<out Record>, clientRecordIds: List<String>) {
        client.deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = clientRecordIds)
    }

    override suspend fun revokeAll() {
        client.permissionController.revokeAllPermissions()
    }

    private companion object {
        const val PAGE_SIZE = 1_000
    }
}
