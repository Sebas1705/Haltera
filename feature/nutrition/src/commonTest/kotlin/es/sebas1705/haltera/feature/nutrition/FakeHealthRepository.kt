package es.sebas1705.haltera.feature.nutrition

import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Cached health days in memory; nothing is read from a platform. */
class FakeHealthRepository(days: List<HealthDay> = emptyList()) : HealthRepository {
    val days = MutableStateFlow(days)
    override val status = MutableStateFlow(HealthStatus())

    override suspend fun checkStatus() = status.value

    override suspend fun onPermissionsResult() = Unit

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) = Unit

    override suspend fun refresh(force: Boolean) = Unit

    override suspend fun writeChanges() = Unit

    override suspend fun watchLocalChanges() = Unit

    override suspend fun disconnect() {
        days.value = emptyList()
    }

    override fun observeDays(from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate): Flow<List<HealthDay>> =
        days.map { list -> list.filter { it.date in from..to }.sortedBy { it.date } }
}
