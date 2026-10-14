package es.sebas1705.ironlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.ironlog.data.local.catalog.CATALOG_VERSION_KEY
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import es.sebas1705.ironlog.data.models.CatalogSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** [CatalogRepository] over SQLDelight. Database work is blocking I/O, so it runs on [dispatcher]. */
class SqlCatalogRepository(
    private val db: AppDatabase,
    private val importer: CatalogImporter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CatalogRepository {

    override suspend fun importBundled(json: String): CatalogImportResult =
        withContext(dispatcher) { importer.importIfNewer(json) }

    override fun observeSummary(): Flow<CatalogSummary> = combine(
        db.metaQueries.get(CATALOG_VERSION_KEY).asFlow().mapToOneOrNull(dispatcher),
        db.exerciseEntityQueries.countActive().asFlow().mapToOne(dispatcher),
        db.exerciseVariantEntityQueries.countActive().asFlow().mapToOne(dispatcher),
    ) { version, exercises, variants ->
        CatalogSummary(version = version?.toIntOrNull(), exercises = exercises, variants = variants)
    }
}
