package es.sebas1705.haltera.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.BodyMeasurementEntity
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.data.repository.BodyMeasurementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlBodyMeasurementRepository(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : BodyMeasurementRepository {

    override fun observeAll(): Flow<List<BodyMeasurement>> =
        db.bodyMeasurementEntityQueries.selectAll().asFlow().mapToList(
            dispatcher
        ).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(
        type: MeasurementType,
        value: Long,
        measuredAt: Long?,
        customName: String,
        notes: String,
    ): String = withContext(dispatcher) {
        val id = newId()
        write(id, measuredAt ?: now(), type, value, customName, notes)
        id
    }

    override suspend fun update(measurement: BodyMeasurement) {
        withContext(dispatcher) {
            with(measurement) { write(id, measuredAt, type, value, customName, notes) }
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) { db.bodyMeasurementEntityQueries.delete(id) }
    }

    @Suppress("LongParameterList") // The columns of one row.
    private fun write(
        id: String,
        measuredAt: Long,
        type: MeasurementType,
        value: Long,
        customName: String,
        notes: String
    ) {
        val row = validated(id, measuredAt, type, value, customName, notes)
        db.bodyMeasurementEntityQueries.upsert(row.id, row.measuredAt, row.type, row.customName, row.value_, row.notes)
    }

    companion object {
        /**
         * The row stored for a measurement: custom names normalised (and dropped for the fixed types), notes trimmed.
         * Throws [IllegalArgumentException] for an out-of-range value or a custom measurement without a name.
         */
        @Suppress("LongParameterList")
        internal fun validated(
            id: String,
            measuredAt: Long,
            type: MeasurementType,
            value: Long,
            customName: String,
            notes: String,
        ): BodyMeasurementEntity {
            require(BodyMeasurements.isValidValue(type, value)) { "value $value out of range for $type" }
            val name = if (type == MeasurementType.CUSTOM) BodyMeasurements.normalizeName(customName) else ""
            require(type != MeasurementType.CUSTOM || name.isNotEmpty()) { "a custom measurement needs a name" }
            return BodyMeasurementEntity(id, measuredAt, type.name, name, value, notes.trim())
        }
    }
}

/**
 * A stored row as a model. A type written by a newer version shows as a custom measurement named after the stored
 * type, so it still has its own chart; the row is only rewritten if the user edits it.
 */
internal fun BodyMeasurementEntity.toModel(): BodyMeasurement {
    val parsed = MeasurementType.fromDb(type)
    val unknown = parsed == MeasurementType.CUSTOM && type != MeasurementType.CUSTOM.name
    return BodyMeasurement(
        id = id,
        measuredAt = measuredAt,
        type = parsed,
        value = value_,
        customName = if (unknown) customName.ifBlank { type.lowercase().replace('_', ' ') } else customName,
        notes = notes,
    )
}
