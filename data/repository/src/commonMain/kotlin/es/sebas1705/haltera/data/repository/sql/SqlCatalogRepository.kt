package es.sebas1705.haltera.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.haltera.data.local.catalog.CATALOG_VERSION_KEY
import es.sebas1705.haltera.data.local.catalog.CatalogImporter
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.CatalogImportResult
import es.sebas1705.haltera.data.models.CatalogSummary
import es.sebas1705.haltera.data.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

internal class SqlCatalogRepository(
    private val db: AppDatabase,
    private val importer: CatalogImporter,
    private val dispatcher: CoroutineDispatcher,
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
