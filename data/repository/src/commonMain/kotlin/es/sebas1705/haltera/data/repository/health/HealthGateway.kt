package es.sebas1705.haltera.data.repository.health

import es.sebas1705.haltera.core.datastore.HealthDataType

/** Whether the phone's health platform can be used. */
enum class HealthAvailability {
    /** Health Connect is installed and up to date (built in from Android 14). */
    AVAILABLE,

    /** Android 9–13: the Health Connect app must be installed or updated from Google Play first. */
    PROVIDER_MISSING,

    /** Not available on this device or platform (Android 8 and older; iOS until HealthKit is implemented). */
    UNSUPPORTED,
}

/** One aggregated value the platform computes for a time range (it removes overlaps between apps itself). */
enum class HealthMetric { STEPS, ACTIVE_CALORIES, TOTAL_CALORIES, RESTING_HEART_RATE, SLEEP }

/** The metrics read for a data type; write types read nothing. */
val HealthDataType.metrics: Set<HealthMetric>
    get() = when (this) {
        HealthDataType.STEPS -> setOf(HealthMetric.STEPS)
        HealthDataType.CALORIES -> setOf(HealthMetric.ACTIVE_CALORIES, HealthMetric.TOTAL_CALORIES)
        HealthDataType.RESTING_HEART_RATE -> setOf(HealthMetric.RESTING_HEART_RATE)
        HealthDataType.SLEEP -> setOf(HealthMetric.SLEEP)
        HealthDataType.WEIGHT_IMPORT, HealthDataType.WORKOUTS_EXPORT, HealthDataType.WEIGHT_EXPORT -> emptySet()
    }

/** Aggregates of one time range; null = no data in it (or not asked). */
data class HealthTotals(
    val steps: Long? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val restingHeartRate: Long? = null,
    val sleepMinutes: Long? = null,
)

/**
 * A body-weight record found on the platform.
 *
 * @property id The platform's record id (stable).
 * @property originPackage The app that wrote it.
 * @property clientRecordId The writer's own id, if it set one (Haltera writes `haltera-weight-<id>`).
 * @property lastModifiedAt When the record last changed on the platform, epoch millis.
 */
data class ExternalWeight(
    val id: String,
    val measuredAt: Long,
    val weightGrams: Long,
    val originPackage: String,
    val clientRecordId: String?,
    val lastModifiedAt: Long,
)

/** A finished Haltera workout as a strength-training exercise session. [clientRecordId] makes rewrites updates. */
data class HealthWorkout(
    val clientRecordId: String,
    val version: Long,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
)

/** A Haltera body-weight entry as a platform weight record. */
data class HealthWeight(
    val clientRecordId: String,
    val version: Long,
    val measuredAt: Long,
    val weightGrams: Long,
)

enum class HealthRecordKind { WORKOUT, WEIGHT }

/** A permission the call needed is not (or no longer) granted, e.g. revoked in system settings. */
class HealthPermissionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The phone's health platform: Health Connect on Android (data:health), nothing yet on iOS (HealthKit is a
 * follow-up). Reads and writes happen only while the app is in the foreground; no background permission is
 * requested. Permission requests are UI and live in the app shell. Times are epoch millis.
 *
 * Every call except [availability] may throw [HealthPermissionException] when a permission it needs is missing.
 */
interface HealthGateway {
    suspend fun availability(): HealthAvailability

    /** Types whose permissions are all granted. Empty when the platform is not available. */
    suspend fun grantedTypes(): Set<HealthDataType>

    /** [metrics] aggregated over [startMillis, endMillis). */
    suspend fun aggregate(startMillis: Long, endMillis: Long, metrics: Set<HealthMetric>): HealthTotals

    /** Weight records measured in [startMillis, endMillis), any app. */
    suspend fun readWeights(startMillis: Long, endMillis: Long): List<ExternalWeight>

    /** Inserts or updates (same client record id, higher version) the workouts. */
    suspend fun writeWorkouts(workouts: List<HealthWorkout>)

    suspend fun writeWeights(weights: List<HealthWeight>)

    /** Deletes records Haltera wrote, by client record id. Unknown ids are ignored. */
    suspend fun delete(kind: HealthRecordKind, clientRecordIds: List<String>)

    /** Revokes every permission Haltera holds (Disconnect). */
    suspend fun revokeAll()

    companion object {
        /** Prefix of the client record ids Haltera writes; records with it are never imported back. */
        const val CLIENT_ID_PREFIX = "haltera-"

        fun workoutClientId(sessionId: String) = "${CLIENT_ID_PREFIX}workout-$sessionId"

        fun weightClientId(entryId: String) = "${CLIENT_ID_PREFIX}weight-$entryId"
    }
}

/** No health platform (iOS for now, previews, tests): everything is unsupported and empty. */
object UnavailableHealthGateway : HealthGateway {
    override suspend fun availability() = HealthAvailability.UNSUPPORTED

    override suspend fun grantedTypes(): Set<HealthDataType> = emptySet()

    override suspend fun aggregate(startMillis: Long, endMillis: Long, metrics: Set<HealthMetric>) = HealthTotals()

    override suspend fun readWeights(startMillis: Long, endMillis: Long): List<ExternalWeight> = emptyList()

    override suspend fun writeWorkouts(workouts: List<HealthWorkout>) = Unit

    override suspend fun writeWeights(weights: List<HealthWeight>) = Unit

    override suspend fun delete(kind: HealthRecordKind, clientRecordIds: List<String>) = Unit

    override suspend fun revokeAll() = Unit
}
