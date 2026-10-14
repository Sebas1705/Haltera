package es.sebas1705.ironlog.data.models

/** What the database holds of the bundled exercise catalogue. `version` is null before the first import. */
data class CatalogSummary(
    val version: Int?,
    val exercises: Long,
    val variants: Long,
)

/** Outcome of importing the bundled catalogue. */
sealed interface CatalogImportResult {
    val version: Int

    data class Imported(override val version: Int, val exercises: Int, val variants: Int) : CatalogImportResult

    data class UpToDate(override val version: Int) : CatalogImportResult
}
