package es.sebas1705.ironlog.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.local.db.BodyWeightEntity
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlBodyWeightRepository(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : BodyWeightRepository {

    override fun observeAll(): Flow<List<BodyWeightEntry>> =
        db.bodyWeightEntityQueries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toModel() } }

    override fun observeLatest(): Flow<BodyWeightEntry?> =
        db.bodyWeightEntityQueries.selectLatest().asFlow().mapToOneOrNull(dispatcher).map { it?.toModel() }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<BodyWeightEntry>> =
        db.bodyWeightEntityQueries.selectBetween(fromMillis, toMillis).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(weightGrams: Long, measuredAt: Long?, notes: String): String = withContext(dispatcher) {
        val id = newId()
        db.bodyWeightEntityQueries.upsert(id, measuredAt ?: now(), weightGrams, notes)
        id
    }

    override suspend fun update(entry: BodyWeightEntry) {
        withContext(dispatcher) {
            db.bodyWeightEntityQueries.upsert(entry.id, entry.measuredAt, entry.weightGrams, entry.notes)
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) { db.bodyWeightEntityQueries.delete(id) }
    }

    private fun BodyWeightEntity.toModel() = BodyWeightEntry(id, measuredAt, weightGrams, notes)
}
