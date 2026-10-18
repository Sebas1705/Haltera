package es.sebas1705.haltera.feature.progress.fakes

import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.data.repository.BodyMeasurementRepository
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import es.sebas1705.haltera.feature.progress.photos.PickedPhoto
import es.sebas1705.haltera.feature.progress.photos.ProgressPhotoPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Same rules as the SQL repository: values validated, custom names normalised and required. */
class FakeBodyMeasurementRepository(
    initial: List<BodyMeasurement> = emptyList(),
    private val now: Long = 0,
) : BodyMeasurementRepository {
    val rows = MutableStateFlow(initial)
    private var nextId = 1

    override fun observeAll(): Flow<List<BodyMeasurement>> =
        rows.map { list -> list.sortedWith(compareByDescending<BodyMeasurement> { it.measuredAt }.thenBy { it.id }) }

    override suspend fun add(
        type: MeasurementType,
        value: Long,
        measuredAt: Long?,
        customName: String,
        notes: String,
    ): String {
        val id = "new-${nextId++}"
        val row = checked(BodyMeasurement(id, measuredAt ?: now, type, value, customName, notes))
        rows.update { it + row }
        return id
    }

    override suspend fun update(measurement: BodyMeasurement) {
        val row = checked(measurement)
        rows.update { list -> list.map { if (it.id == row.id) row else it } }
    }

    override suspend fun delete(id: String) = rows.update { list -> list.filterNot { it.id == id } }

    private fun checked(m: BodyMeasurement): BodyMeasurement {
        require(BodyMeasurements.isValidValue(m.type, m.value))
        val name = if (m.type == MeasurementType.CUSTOM) BodyMeasurements.normalizeName(m.customName) else ""
        require(m.type != MeasurementType.CUSTOM || name.isNotEmpty())
        return m.copy(customName = name, notes = m.notes.trim())
    }
}

class FakeProgressPhotoRepository(initial: List<ProgressPhoto> = emptyList()) : ProgressPhotoRepository {
    val photos = MutableStateFlow(initial)
    val images = HashMap<String, ByteArray>()
    var failWrites = false
    private var nextId = 1

    override fun observeAll(): Flow<List<ProgressPhoto>> =
        photos.map { list ->
            list.sortedWith(
                compareByDescending<ProgressPhoto> { it.takenAt }.thenByDescending { it.addedAt }
            )
        }

    override suspend fun add(image: ByteArray, thumbnail: ByteArray, takenAt: Long, notes: String): String {
        check(!failWrites) { "disk full" }
        val id = "photo-${nextId++}"
        images[id] = image
        photos.update { it + ProgressPhoto(id, takenAt, notes.trim(), addedAt = nextId.toLong()) }
        return id
    }

    override suspend fun update(photo: ProgressPhoto) {
        check(!failWrites) { "disk full" }
        photos.update { list ->
            list.map {
                if (it.id == photo.id) {
                    it.copy(
                        takenAt = photo.takenAt,
                        notes = photo.notes
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun delete(id: String) {
        check(!failWrites) { "disk full" }
        images.remove(id)
        photos.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun image(id: String): ByteArray? = images[id]

    override suspend fun thumbnail(id: String): ByteArray? = images[id]

    override suspend fun deleteAll() {
        images.clear()
        photos.value = emptyList()
    }
}

class FakePhotoPicker(var next: PickedPhoto? = null, var failure: Exception? = null) : ProgressPhotoPicker {
    override val isAvailable: Boolean = true
    var picks = 0

    override suspend fun pick(): PickedPhoto? {
        picks++
        failure?.let { throw it }
        return next
    }
}
