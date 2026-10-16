package es.sebas1705.haltera.data.repository.health

import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.data.models.HealthDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** Why the last read or write did not complete. */
enum class HealthSyncError {
    /** A permission is missing or was revoked; what is still granted was read. */
    PERMISSION_MISSING,

    /** The platform failed (busy, rate limited, unavailable); it is retried on the next foreground read. */
    FAILED,
}

/**
 * What Settings and the statistics show about the health integration.
 *
 * @property availability Null until checked.
 * @property granted Types whose permissions are granted right now.
 * @property lastError Outcome of the last read; null when it completed.
 */
data class HealthStatus(
    val availability: HealthAvailability? = null,
    val granted: Set<HealthDataType> = emptySet(),
    val syncing: Boolean = false,
    val lastError: HealthSyncError? = null,
)

/**
 * The health integration (Settings → Integrations → Health Connect): reads daily totals and weights from the
 * phone's health platform into a local cache and the body-weight log, and writes finished workouts and
 * body weight to it. Nothing is read or written until the user connects (`HealthSettings.connected`), and only
 * while the app is in the foreground ([refresh] is called on resume and from the screens).
 *
 * The cached daily totals are device-only and never synced (they can always be read again from the platform);
 * imported body-weight entries become ordinary log entries (id `hc-<platform id>`), synced like any other entry
 * when the cloud copy is on.
 */
interface HealthRepository {
    val status: StateFlow<HealthStatus>

    /** Re-reads availability and granted permissions (Settings opened, app resumed). */
    suspend fun checkStatus(): HealthStatus

    /**
     * After the permission screen closed: connects when at least one permission is granted and reads at once.
     * Nothing changes when everything was denied.
     */
    suspend fun onPermissionsResult()

    /** Switches a data type on or off. Turning a read type off deletes its cached values. */
    suspend fun setEnabled(type: HealthDataType, enabled: Boolean)

    /**
     * Reads the recent days, imports new weights and writes new or changed workouts and weights, for the types
     * that are both enabled and granted. Without [force] it does nothing within a few minutes of the last read.
     * Never throws for platform or permission problems: the outcome is in [status].
     */
    suspend fun refresh(force: Boolean = false)

    /**
     * Writes new, changed and deleted workouts and weights now, without reading (a workout just finished).
     * Does nothing unless connected; never throws for platform problems.
     */
    suspend fun writeChanges()

    /**
     * Until cancelled, writes workouts and body weight a few seconds after they change in the log. The app runs
     * it only while it is in the foreground (no background work).
     */
    suspend fun watchLocalChanges()

    /**
     * Revokes the permissions, deletes the cached totals and the export ledger and stops reading. Imported
     * body-weight entries stay in the log; records Haltera wrote stay on the platform (the user manages them
     * there).
     */
    suspend fun disconnect()

    /** Cached days in [from, to], oldest first; days never read are absent. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<HealthDay>>
}
