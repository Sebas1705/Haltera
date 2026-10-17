package es.sebas1705.haltera.health

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.HealthSyncError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

/**
 * Settings → Integrations → Health Connect.
 *
 * @property deniedAll The last Connect closed without any permission: nothing is read.
 * @property nothingGranted The last permission request granted none of the types it asked for. After two denials
 *   Health Connect stops showing its screen and answers "denied" at once, so the section then points to its settings.
 * @property goalInput Text of the open steps-goal dialog, null when closed.
 */
data class IntegrationsState(
    val availability: HealthAvailability? = null,
    val connected: Boolean = false,
    val enabled: Set<HealthDataType> = HealthDataType.DEFAULT_ENABLED,
    val granted: Set<HealthDataType> = emptySet(),
    val stepGoal: Int = HealthSettings.DEFAULT_STEP_GOAL,
    val syncing: Boolean = false,
    val requesting: Boolean = false,
    val lastError: HealthSyncError? = null,
    val lastReadAt: Long = 0,
    val deniedAll: Boolean = false,
    val nothingGranted: Boolean = false,
    val confirmDisconnect: Boolean = false,
    val goalInput: String? = null,
    val goalInvalid: Boolean = false,
) : MVIBaseState {
    /** Enabled types Health Connect does not allow (only meaningful once connected). */
    val missing: Set<HealthDataType> get() = if (connected) enabled - granted else emptySet()

    /** Connected, but every permission was revoked in Health Connect. */
    val noAccess: Boolean get() = connected && availability == HealthAvailability.AVAILABLE && granted.isEmpty()
}

sealed interface IntegrationsIntent : MVIBaseIntent {
    /** The section became visible: re-read availability and permissions (they change outside the app). */
    data object Refresh : IntegrationsIntent

    /** Requests the permissions of every enabled type, then connects if any was granted. */
    data object Connect : IntegrationsIntent

    data class Toggle(val type: HealthDataType, val on: Boolean) : IntegrationsIntent

    /** Asks again for a type Health Connect does not allow. */
    data class Allow(val type: HealthDataType) : IntegrationsIntent

    data object ReadNow : IntegrationsIntent

    data object Install : IntegrationsIntent

    data object ManagePermissions : IntegrationsIntent

    data object AskDisconnect : IntegrationsIntent

    data object DismissDisconnect : IntegrationsIntent

    data object ConfirmDisconnect : IntegrationsIntent

    data object OpenGoal : IntegrationsIntent

    data class GoalChanged(val text: String) : IntegrationsIntent

    data object SaveGoal : IntegrationsIntent

    data object DismissGoal : IntegrationsIntent
}

class IntegrationsViewModel(
    private val health: HealthRepository,
    private val store: HealthSettingsStore,
    private val bridge: HealthConnectBridge,
) : MVIBaseViewModel<IntegrationsState, IntegrationsIntent>(IntegrationsState()) {

    init {
        execute {
            combine(health.status, store.settings) { status, s -> status to s }.collect { (status, s) ->
                updateState {
                    it.copy(
                        availability = status.availability,
                        connected = s.connected,
                        enabled = s.enabled,
                        granted = status.granted,
                        stepGoal = s.stepGoal,
                        syncing = status.syncing,
                        lastError = status.lastError,
                        lastReadAt = s.lastRefreshAt,
                    )
                }
            }
        }
        execute { health.checkStatus() }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per intent.
    override fun intentHandler(intent: IntegrationsIntent): Job = execute {
        when (intent) {
            IntegrationsIntent.Refresh -> health.checkStatus()
            IntegrationsIntent.Connect -> request(store.settings.value.enabled)
            is IntegrationsIntent.Toggle -> toggle(intent.type, intent.on)
            is IntegrationsIntent.Allow -> request(setOf(intent.type))
            IntegrationsIntent.ReadNow -> health.refresh(force = true)
            IntegrationsIntent.Install -> bridge.openInstallPage()
            IntegrationsIntent.ManagePermissions -> bridge.openManagePermissions()
            IntegrationsIntent.AskDisconnect -> updateState { it.copy(confirmDisconnect = true) }
            IntegrationsIntent.DismissDisconnect -> updateState { it.copy(confirmDisconnect = false) }
            IntegrationsIntent.ConfirmDisconnect -> {
                updateState { it.copy(confirmDisconnect = false, deniedAll = false, nothingGranted = false) }
                health.disconnect()
            }
            IntegrationsIntent.OpenGoal -> openGoal()
            is IntegrationsIntent.GoalChanged -> updateState {
                it.copy(goalInput = intent.text.filter(Char::isDigit).take(GOAL_DIGITS), goalInvalid = false)
            }
            IntegrationsIntent.SaveGoal -> saveGoal()
            IntegrationsIntent.DismissGoal -> updateState { it.copy(goalInput = null, goalInvalid = false) }
        }
    }

    private suspend fun toggle(type: HealthDataType, on: Boolean) {
        health.setEnabled(type, on)
        // Once connected, switching a type on asks for its permission right away.
        if (on && store.settings.value.connected && type !in health.status.value.granted) request(setOf(type))
    }

    private suspend fun request(types: Set<HealthDataType>) {
        if (types.isEmpty() || currentState.requesting) return
        updateState { it.copy(requesting = true, deniedAll = false, nothingGranted = false) }
        try {
            bridge.requestPermissions(types)
            health.onPermissionsResult()
        } finally {
            val connected = store.settings.value.connected
            val nothingGranted = (types intersect health.status.value.granted).isEmpty()
            updateState { it.copy(requesting = false, deniedAll = !connected, nothingGranted = nothingGranted) }
        }
    }

    private fun openGoal() = updateState { it.copy(goalInput = it.stepGoal.toString(), goalInvalid = false) }

    private fun saveGoal() {
        val goal = currentState.goalInput?.toIntOrNull()
        if (goal == null || goal !in HealthSettings.MIN_STEP_GOAL..HealthSettings.MAX_STEP_GOAL) {
            updateState { it.copy(goalInvalid = true) }
            return
        }
        store.setStepGoal(goal)
        updateState { it.copy(goalInput = null, goalInvalid = false) }
    }

    private companion object {
        const val GOAL_DIGITS = 6
    }
}
