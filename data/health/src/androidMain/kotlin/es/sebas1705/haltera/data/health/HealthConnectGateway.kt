package es.sebas1705.haltera.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
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
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.reflect.KClass

/**
 * [HealthGateway] on Health Connect. All calls go through [HcClient] so the logic here (availability by Android
 * version, permission mapping, record building, revoked permissions) is tested with a fake client.
 *
 * Android 8 and older are unsupported without touching the library (it needs API 26 to load and 28 to run).
 * A `SecurityException` from Health Connect (permission missing or revoked in system settings) becomes a
 * [HealthPermissionException].
 */
class HealthConnectGateway internal constructor(
    private val client: HcClient,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : HealthGateway {

    constructor(context: Context) : this(RealHcClient(context.applicationContext))

    override suspend fun availability(): HealthAvailability {
        if (sdkInt < MIN_SDK) return HealthAvailability.UNSUPPORTED
        return when (client.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.PROVIDER_MISSING
            // Android 9-13 without the Health Connect app: it can be installed from Google Play.
            else -> if (sdkInt < BUILT_IN_SDK) HealthAvailability.PROVIDER_MISSING else HealthAvailability.UNSUPPORTED
        }
    }

    override suspend fun grantedTypes(): Set<HealthDataType> {
        if (availability() != HealthAvailability.AVAILABLE) return emptySet()
        return HealthConnectPermissions.grantedTypes(guarded { client.grantedPermissions() })
    }

    override suspend fun aggregate(startMillis: Long, endMillis: Long, metrics: Set<HealthMetric>): HealthTotals {
        if (metrics.isEmpty() || endMillis <= startMillis) return HealthTotals()
        return guarded { client.aggregate(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis), metrics) }
    }

    override suspend fun readWeights(startMillis: Long, endMillis: Long): List<ExternalWeight> {
        val records = guarded { client.readWeights(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis)) }
        return records.map { r ->
            ExternalWeight(
                id = r.metadata.id,
                measuredAt = r.time.toEpochMilli(),
                weightGrams = r.weight.inGrams.roundToLong(),
                originPackage = r.metadata.dataOrigin.packageName,
                clientRecordId = r.metadata.clientRecordId,
                lastModifiedAt = r.metadata.lastModifiedTime.toEpochMilli(),
            )
        }
    }

    override suspend fun writeWorkouts(workouts: List<HealthWorkout>) {
        if (workouts.isEmpty()) return
        guarded { client.insert(workouts.map(::workoutRecord)) }
    }

    override suspend fun writeWeights(weights: List<HealthWeight>) {
        if (weights.isEmpty()) return
        guarded { client.insert(weights.map(::weightRecord)) }
    }

    override suspend fun delete(kind: HealthRecordKind, clientRecordIds: List<String>) {
        if (clientRecordIds.isEmpty()) return
        val type: KClass<out Record> = when (kind) {
            HealthRecordKind.WORKOUT -> ExerciseSessionRecord::class
            HealthRecordKind.WEIGHT -> WeightRecord::class
        }
        guarded { client.deleteByClientIds(type, clientRecordIds) }
    }

    override suspend fun revokeAll() {
        if (availability() != HealthAvailability.AVAILABLE) return
        guarded { client.revokeAll() }
    }

    internal fun workoutRecord(w: HealthWorkout): ExerciseSessionRecord {
        val start = Instant.ofEpochMilli(w.startMillis)
        val end = Instant.ofEpochMilli(w.endMillis)
        val rules = zone().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            metadata = Metadata.manualEntry(clientRecordId = w.clientRecordId, clientRecordVersion = w.version),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = w.title,
        )
    }

    internal fun weightRecord(w: HealthWeight): WeightRecord {
        val time = Instant.ofEpochMilli(w.measuredAt)
        return WeightRecord(
            time = time,
            zoneOffset = zone().rules.getOffset(time),
            weight = Mass.grams(w.weightGrams.toDouble()),
            metadata = Metadata.manualEntry(clientRecordId = w.clientRecordId, clientRecordVersion = w.version),
        )
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw HealthPermissionException(e.message ?: "Health Connect permission missing", e)
    }

    private companion object {
        /** Health Connect runs on Android 9 (API 28) and later. */
        const val MIN_SDK = 28

        /** Built into the system from Android 14 (API 34). */
        const val BUILT_IN_SDK = 34
    }
}
