package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.data.models.CatalogImportResult
import es.sebas1705.ironlog.data.models.CatalogSummary
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    /** Imports [json] (the bundled catalogue) if it is newer than the installed one. */
    suspend fun importBundled(json: String): CatalogImportResult

    fun observeSummary(): Flow<CatalogSummary>
}
