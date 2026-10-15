package es.sebas1705.ironlog.data.local.catalog

import es.sebas1705.ironlog.data.local.SearchText
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** `Meta` key holding the installed catalogue version. */
const val CATALOG_VERSION_KEY = "catalogVersion"

private const val CATALOG_SOURCE = "CATALOG"

/**
 * Imports the bundled catalogue (spec §6.2): only when its version is newer than the installed one,
 * in a single transaction, upserting catalogue rows and deprecating removed ids. Rows with
 * `source = 'USER'` are never modified. A malformed catalogue, or any failure while writing, throws
 * and leaves the database as it was.
 *
 * Invariant: user ids are namespaced `user-<uuid>` and catalogue ids never use that prefix. If a
 * catalogue exercise id still collides with a non-catalogue row, that exercise and its variants are
 * skipped. [CatalogImportResult.Imported] counts the rows actually written, not the entries in the file.
 */
class CatalogImporter(
    private val db: AppDatabase,
    private val now: () -> Long,
) {
    fun installedVersion(): Int? =
        db.metaQueries.get(CATALOG_VERSION_KEY).executeAsOneOrNull()?.toIntOrNull()

    fun importIfNewer(json: String): CatalogImportResult {
        // Cheap check first: only the header is decoded unless the bundled catalogue is newer.
        val bundledVersion = CatalogJson.decodeFromString(CatalogHeader.serializer(), json).catalogVersion
        return db.transactionWithResult {
            val installed = installedVersion()
            if (installed != null && installed >= bundledVersion) {
                return@transactionWithResult CatalogImportResult.UpToDate(installed)
            }
            val catalog = CatalogJson.decodeFromString(CatalogDto.serializer(), json)
            val at = now()
            var exercises = 0
            var variants = 0
            catalog.exercises.forEach { exercise ->
                if (upsert(exercise, at)) {
                    exercises++
                    exercise.variants.forEachIndexed { position, variant ->
                        if (upsert(exercise.id, position.toLong(), variant, at)) variants++
                    }
                }
            }
            catalog.deprecatedIds.forEach { id ->
                db.exerciseEntityQueries.deprecate(updatedAt = at, id = id)
                db.exerciseVariantEntityQueries.deprecate(updatedAt = at, id = id)
            }
            db.metaQueries.put(CATALOG_VERSION_KEY, catalog.catalogVersion.toString())
            CatalogImportResult.Imported(version = catalog.catalogVersion, exercises = exercises, variants = variants)
        }
    }

    /** Writes a catalogue exercise; returns false when its id belongs to a non-catalogue row. */
    private fun upsert(e: ExerciseDto, at: Long): Boolean {
        val existing = db.exerciseEntityQueries.selectById(e.id).executeAsOneOrNull()
        if (existing != null && existing.source != CATALOG_SOURCE) return false
        val primary = e.primaryMuscles.joinToString(",")
        val secondary = e.secondaryMuscles.joinToString(",")
        val stepsEn = e.instructions.en.toJsonColumn()
        val stepsEs = e.instructions.es.toJsonColumn()
        val searchEn = SearchText.normalize(e.name.en)
        val searchEs = SearchText.normalize(e.name.es)
        with(db.exerciseEntityQueries) {
            val inserted = insertCatalogIfMissing(
                id = e.id, nameEn = e.name.en, nameEs = e.name.es, nameEnSearch = searchEn, nameEsSearch = searchEs,
                category = e.category,
                trackingType = e.trackingType, level = e.level, primaryMuscles = primary,
                secondaryMuscles = secondary, instructionsEn = stepsEn, instructionsEs = stepsEs, updatedAt = at,
            ).value
            if (inserted == 0L) {
                updateCatalog(
                    nameEn = e.name.en, nameEs = e.name.es, nameEnSearch = searchEn, nameEsSearch = searchEs,
                    category = e.category, trackingType = e.trackingType,
                    level = e.level, primaryMuscles = primary, secondaryMuscles = secondary,
                    instructionsEn = stepsEn, instructionsEs = stepsEs, updatedAt = at, id = e.id,
                )
            }
        }
        return true
    }

    /** Writes a catalogue variant; returns whether a row was inserted or updated. */
    private fun upsert(exerciseId: String, position: Long, v: VariantDto, at: Long): Boolean {
        val stepsEn = v.instructions?.en?.toJsonColumn()
        val stepsEs = v.instructions?.es?.toJsonColumn()
        val frames = v.frames.toJsonColumn()
        with(db.exerciseVariantEntityQueries) {
            val inserted = insertCatalogIfMissing(
                id = v.id, exerciseId = exerciseId, position = position, equipment = v.equipment,
                loadType = v.loadType, nameEn = v.name?.en, nameEs = v.name?.es,
                instructionsEn = stepsEn, instructionsEs = stepsEs, frames = frames, updatedAt = at,
            ).value
            if (inserted > 0L) return true
            return updateCatalog(
                exerciseId = exerciseId, position = position, equipment = v.equipment, loadType = v.loadType,
                nameEn = v.name?.en, nameEs = v.name?.es, instructionsEn = stepsEn, instructionsEs = stepsEs,
                frames = frames, updatedAt = at, id = v.id,
            ).value > 0L
        }
    }
}

internal val CatalogJson = Json { ignoreUnknownKeys = true }

private val StringList = ListSerializer(String.serializer())

internal fun List<String>.toJsonColumn(): String = CatalogJson.encodeToString(StringList, this)
