package es.sebas1705.haltera.feature.progress.fakes

import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Cached health days in memory; counts the reads the ViewModels ask for. */
class FakeHealthRepository(days: List<HealthDay> = emptyList()) : HealthRepository {
    val days = MutableStateFlow(days)
    override val status = MutableStateFlow(HealthStatus())
    val refreshes = mutableListOf<Boolean>()

    override suspend fun checkStatus() = status.value

    override suspend fun onPermissionsResult() = Unit

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) = Unit

    override suspend fun refresh(force: Boolean) {
        refreshes += force
    }

    override suspend fun writeChanges() = Unit

    override suspend fun watchLocalChanges() = Unit

    override suspend fun disconnect() {
        days.value = emptyList()
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<HealthDay>> =
        days.map { list -> list.filter { it.date in from..to }.sortedBy { it.date } }
}

class FakeHealthSettingsStore(initial: HealthSettings = HealthSettings(connected = true)) : HealthSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<HealthSettings> = state

    override fun update(change: (HealthSettings) -> HealthSettings) {
        state.value = change(state.value)
    }
}
