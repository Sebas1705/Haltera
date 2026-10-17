package es.sebas1705.haltera.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import es.sebas1705.haltera.core.datastore.HealthDataType

/**
 * Health Connect permissions of each data type. They must match the `<uses-permission>` list in this module's
 * manifest (checked by a test): Health Connect refuses to request a permission the app does not declare.
 */
object HealthConnectPermissions {
    fun of(type: HealthDataType): Set<String> = when (type) {
        HealthDataType.STEPS -> setOf(HealthPermission.getReadPermission(StepsRecord::class))
        HealthDataType.CALORIES -> setOf(
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
        HealthDataType.WEIGHT_IMPORT -> setOf(HealthPermission.getReadPermission(WeightRecord::class))
        HealthDataType.RESTING_HEART_RATE -> setOf(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
        HealthDataType.SLEEP -> setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
        HealthDataType.WORKOUTS_EXPORT -> setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
        HealthDataType.WEIGHT_EXPORT -> setOf(HealthPermission.getWritePermission(WeightRecord::class))
    }

    fun of(types: Collection<HealthDataType>): Set<String> = types.flatMap { of(it) }.toSet()

    /** Types whose permissions are all in [granted]. */
    fun grantedTypes(granted: Set<String>): Set<HealthDataType> =
        HealthDataType.entries.filter { granted.containsAll(of(it)) }.toSet()
}
