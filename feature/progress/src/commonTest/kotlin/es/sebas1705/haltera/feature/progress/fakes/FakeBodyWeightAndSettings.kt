package es.sebas1705.haltera.feature.progress.fakes

import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBodyWeightRepository(initial: List<BodyWeightEntry> = emptyList(), private val now: Long = 0) : BodyWeightRepository {
    val entries = MutableStateFlow(initial)
    private var nextId = 1

    override fun observeAll(): Flow<List<BodyWeightEntry>> =
        entries.map { list -> list.sortedByDescending { it.measuredAt } }

    override fun observeLatest(): Flow<BodyWeightEntry?> = entries.map { list -> list.maxByOrNull { it.measuredAt } }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<BodyWeightEntry>> =
        entries.map { list -> list.filter { it.measuredAt in fromMillis until toMillis }.sortedBy { it.measuredAt } }

    override suspend fun add(weightGrams: Long, measuredAt: Long?, notes: String): String {
        val id = "new-${nextId++}"
        entries.update { it + BodyWeightEntry(id, measuredAt ?: now, weightGrams, notes) }
        return id
    }

    override suspend fun update(entry: BodyWeightEntry) =
        entries.update { list -> list.map { if (it.id == entry.id) entry else it } }

    override suspend fun delete(id: String) = entries.update { list -> list.filterNot { it.id == id } }
}

class FakeSettingsRepository(unit: WeightUnit = WeightUnit.KG) : SettingsRepository {
    private val state = MutableStateFlow(UserSettings.defaultsFor(unit))
    override val settings: StateFlow<UserSettings> = state

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) = state.update { it.copy(unit = unit) }

    override fun setBarWeight(grams: Long) = state.update { it.copy(barWeightGrams = grams) }

    override fun setAvailablePlates(grams: List<Long>) = state.update { it.copy(availablePlatesGrams = grams) }

    override fun setKeepScreenOn(enabled: Boolean) = state.update { it.copy(keepScreenOn = enabled) }

    override fun setRestSound(enabled: Boolean) = state.update { it.copy(restSound = enabled) }

    override fun setRestVibration(enabled: Boolean) = state.update { it.copy(restVibration = enabled) }
}
