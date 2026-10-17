package es.sebas1705.haltera.data.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthMetric
import es.sebas1705.haltera.data.repository.health.HealthPermissionException
import es.sebas1705.haltera.data.repository.health.HealthRecordKind
import es.sebas1705.haltera.data.repository.health.HealthTotals
import es.sebas1705.haltera.data.repository.health.HealthWeight
import es.sebas1705.haltera.data.repository.health.HealthWorkout
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthConnectGatewayTest {
    private class FakeHcClient : HcClient {
        var status = HealthConnectClient.SDK_AVAILABLE
        var granted = HealthConnectPermissions.of(HealthDataType.entries)
        var securityError = false
        var totals = HealthTotals(steps = 1_234)
        val aggregateCalls = mutableListOf<Triple<Instant, Instant, Set<HealthMetric>>>()
        val inserted = mutableListOf<Record>()
        val deleted = mutableListOf<Pair<KClass<out Record>, List<String>>>()
        var weights = emptyList<WeightRecord>()
        var revoked = false
        var statusCalls = 0

        private fun check() {
            if (securityError) throw SecurityException("Caller doesn't have android.permission.health.READ_STEPS")
        }

        override fun sdkStatus(): Int = status.also { statusCalls++ }

        override suspend fun grantedPermissions(): Set<String> = granted

        override suspend fun aggregate(start: Instant, end: Instant, metrics: Set<HealthMetric>): HealthTotals {
            check()
            aggregateCalls += Triple(start, end, metrics)
            return totals
        }

        override suspend fun readWeights(start: Instant, end: Instant): List<WeightRecord> {
            check()
            return weights
        }

        override suspend fun insert(records: List<Record>) {
            check()
            inserted += records
        }

        override suspend fun deleteByClientIds(type: KClass<out Record>, clientRecordIds: List<String>) {
            check()
            deleted += type to clientRecordIds
        }

        override suspend fun revokeAll() {
            revoked = true
        }
    }

    /** What Health Connect returns for a record another app wrote (the constructor is Kotlin-internal). */
    private fun readMetadata(id: String, origin: String, lastModified: Instant): Metadata =
        Metadata::class.java.getConstructor(
            Int::class.javaPrimitiveType,
            String::class.java,
            DataOrigin::class.java,
            Instant::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Device::class.java,
        ).newInstance(
            Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            id,
            DataOrigin(origin),
            lastModified,
            null,
            0L,
            null
        )

    private val client = FakeHcClient()
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun gateway(sdk: Int = 36) = HealthConnectGateway(client, sdk) { madrid }

    @Test
    fun `availability follows the Android version and the provider status`() = runTest {
        assertEquals(HealthAvailability.AVAILABLE, gateway(36).availability())
        assertEquals(HealthAvailability.AVAILABLE, gateway(30).availability())
        client.status = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        assertEquals(HealthAvailability.PROVIDER_MISSING, gateway(33).availability())
        assertEquals(HealthAvailability.PROVIDER_MISSING, gateway(36).availability())
        client.status = HealthConnectClient.SDK_UNAVAILABLE
        // Android 9-13 without the app: offer the install link.
        assertEquals(HealthAvailability.PROVIDER_MISSING, gateway(28).availability())
        assertEquals(HealthAvailability.UNSUPPORTED, gateway(34).availability())
    }

    @Test
    fun `Android 8 and older are unsupported without asking Health Connect`() = runTest {
        assertEquals(HealthAvailability.UNSUPPORTED, gateway(24).availability())
        assertEquals(HealthAvailability.UNSUPPORTED, gateway(27).availability())
        assertEquals(emptySet(), gateway(26).grantedTypes())
        assertEquals(0, client.statusCalls)
    }

    @Test
    fun `granted types need every permission of the type`() = runTest {
        client.granted = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED", // Total calories missing.
            "android.permission.health.WRITE_EXERCISE",
        )
        assertEquals(setOf(HealthDataType.STEPS, HealthDataType.WORKOUTS_EXPORT), gateway().grantedTypes())
        client.status = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        assertEquals(emptySet(), gateway().grantedTypes())
    }

    @Test
    fun `aggregate passes the exact range and nothing is asked for an empty request`() = runTest {
        val totals = gateway().aggregate(1_000, 2_000, setOf(HealthMetric.STEPS))
        assertEquals(1_234L, totals.steps)
        assertEquals(
            Triple(Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000), setOf(HealthMetric.STEPS)),
            client.aggregateCalls.single()
        )
        assertEquals(HealthTotals(), gateway().aggregate(1_000, 2_000, emptySet()))
        assertEquals(HealthTotals(), gateway().aggregate(2_000, 2_000, setOf(HealthMetric.STEPS)))
        assertEquals(1, client.aggregateCalls.size)
    }

    @Test
    fun `a revoked permission becomes a HealthPermissionException on every call`() = runTest {
        client.securityError = true
        val g = gateway()
        assertFailsWith<HealthPermissionException> { g.aggregate(0, 1, setOf(HealthMetric.STEPS)) }
        assertFailsWith<HealthPermissionException> { g.readWeights(0, 1) }
        assertFailsWith<HealthPermissionException> {
            g.writeWorkouts(listOf(HealthWorkout("haltera-workout-1", 1, 0, 60_000, "Haltera")))
        }
        val weight = HealthWeight("haltera-weight-1", 1, 0, 80_000)
        assertFailsWith<HealthPermissionException> { g.writeWeights(listOf(weight)) }
        assertFailsWith<HealthPermissionException> { g.delete(HealthRecordKind.WEIGHT, listOf("x")) }
    }

    @Test
    fun `workouts are strength training sessions with client id, version, title and local offsets`() = runTest {
        // 25 October 2026: Madrid goes from +02:00 to +01:00 at 03:00 local.
        val start = Instant.parse("2026-10-25T00:30:00Z")
        val end = Instant.parse("2026-10-25T02:30:00Z")
        gateway().writeWorkouts(
            listOf(HealthWorkout("haltera-workout-s1", 42, start.toEpochMilli(), end.toEpochMilli(), "Haltera · Push")),
        )
        val record = client.inserted.single() as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals("Haltera · Push", record.title)
        assertEquals(start to end, record.startTime to record.endTime)
        assertEquals(ZoneOffset.ofHours(2), record.startZoneOffset)
        assertEquals(ZoneOffset.ofHours(1), record.endZoneOffset)
        assertEquals("haltera-workout-s1", record.metadata.clientRecordId)
        assertEquals(42L, record.metadata.clientRecordVersion)
        assertEquals(Metadata.RECORDING_METHOD_MANUAL_ENTRY, record.metadata.recordingMethod)
    }

    @Test
    fun `weights are written in grams with their client id`() = runTest {
        gateway().writeWeights(listOf(HealthWeight("haltera-weight-a", 7, 1_760_000_000_000, 80_450)))
        val record = client.inserted.single() as WeightRecord
        assertEquals(80.45, record.weight.inKilograms, 1e-9)
        assertEquals("haltera-weight-a", record.metadata.clientRecordId)
        assertEquals(7L, record.metadata.clientRecordVersion)
    }

    @Test
    fun `empty writes and deletes do not call Health Connect`() = runTest {
        client.securityError = true // Would throw if called.
        gateway().writeWorkouts(emptyList())
        gateway().writeWeights(emptyList())
        gateway().delete(HealthRecordKind.WORKOUT, emptyList())
        assertTrue(client.inserted.isEmpty())
    }

    @Test
    fun `deletes go by client id to the right record type`() = runTest {
        gateway().delete(HealthRecordKind.WORKOUT, listOf("haltera-workout-1"))
        gateway().delete(HealthRecordKind.WEIGHT, listOf("haltera-weight-2"))
        assertEquals(
            listOf(
                ExerciseSessionRecord::class to listOf("haltera-workout-1"),
                WeightRecord::class to listOf("haltera-weight-2")
            ),
            client.deleted,
        )
    }

    @Test
    fun `weights read from Health Connect keep id, origin, client id and modification time`() = runTest {
        val time = Instant.parse("2026-10-10T06:00:00Z")
        client.weights = listOf(
            WeightRecord(
                time = time,
                zoneOffset = ZoneOffset.ofHours(2),
                weight = Mass.kilograms(80.4),
                metadata = readMetadata(
                    id = "abc",
                    origin = "com.withings.wiscale2",
                    lastModified = Instant.parse("2026-10-10T06:01:00Z"),
                ),
            ),
        )
        val weight = gateway().readWeights(0, Long.MAX_VALUE / 2).single()
        assertEquals("abc", weight.id)
        assertEquals(80_400L, weight.weightGrams)
        assertEquals(time.toEpochMilli(), weight.measuredAt)
        assertEquals("com.withings.wiscale2", weight.originPackage)
        assertEquals(null, weight.clientRecordId)
        assertEquals(Instant.parse("2026-10-10T06:01:00Z").toEpochMilli(), weight.lastModifiedAt)
    }

    @Test
    fun `revoke all only talks to an available Health Connect`() = runTest {
        client.status = HealthConnectClient.SDK_UNAVAILABLE
        gateway(36).revokeAll()
        assertEquals(false, client.revoked)
        client.status = HealthConnectClient.SDK_AVAILABLE
        gateway(36).revokeAll()
        assertEquals(true, client.revoked)
    }

    @Test
    fun `the manifest declares exactly the permissions the types request`() {
        val manifest = File("src/androidMain/AndroidManifest.xml").readText()
        val declared = Regex("""android:name="(android\.permission\.health\.[A-Z_]+)"""").findAll(manifest)
            .map { it.groupValues[1] }.toSet()
        assertEquals(HealthConnectPermissions.of(HealthDataType.entries), declared)
        // No background reads and no history beyond the default 30 days.
        assertTrue(declared.none { "BACKGROUND" in it || "HISTORY" in it })
    }

    @Test
    fun `each type maps to its own permissions`() {
        assertEquals(setOf("android.permission.health.READ_STEPS"), HealthConnectPermissions.of(HealthDataType.STEPS))
        assertEquals(
            setOf(
                "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
                "android.permission.health.READ_TOTAL_CALORIES_BURNED"
            ),
            HealthConnectPermissions.of(HealthDataType.CALORIES),
        )
        assertEquals(
            setOf("android.permission.health.WRITE_WEIGHT"),
            HealthConnectPermissions.of(HealthDataType.WEIGHT_EXPORT)
        )
        assertEquals(
            setOf("android.permission.health.READ_WEIGHT"),
            HealthConnectPermissions.of(HealthDataType.WEIGHT_IMPORT)
        )
        assertEquals(
            setOf("android.permission.health.WRITE_EXERCISE"),
            HealthConnectPermissions.of(HealthDataType.WORKOUTS_EXPORT)
        )
        assertEquals(setOf("android.permission.health.READ_SLEEP"), HealthConnectPermissions.of(HealthDataType.SLEEP))
        assertEquals(
            setOf("android.permission.health.READ_RESTING_HEART_RATE"),
            HealthConnectPermissions.of(HealthDataType.RESTING_HEART_RATE),
        )
    }
}
