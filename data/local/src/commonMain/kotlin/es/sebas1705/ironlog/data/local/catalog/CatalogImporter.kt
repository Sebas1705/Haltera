package es.sebas1705.ironlog.data.local.catalog

import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** `Meta` key holding the installed catalogue version. */
const val CATALOG_VERSION_KEY = "catalogVersion"

/**
 * Imports the bundled catalogue (spec §6.2): only when its version is newer than the installed one,
 * in a single transaction, upserting catalogue rows and deprecating removed ids. Rows with
 * `source = 'USER'` are never modified. A malformed catalogue throws and leaves the database as it was.
 */
class CatalogImporter(
    private val db: AppDatabase,
    private val now: () -> Long,
) {
    fun installedVersion(): Int? =
        db.metaQueries.get(CATALOG_VERSION_KEY).executeAsOneOrNull()?.toIntOrNull()

    fun importIfNewer(json: String): CatalogImportResult {
        val catalog = CatalogJson.decodeFromString(CatalogDto.serializer(), json)
        val installed = installedVersion()
        if (installed != null && installed >= catalog.catalogVersion) return CatalogImportResult.UpToDate(installed)

        val at = now()
        db.transaction {
            catalog.exercises.forEach { exercise ->
                upsert(exercise, at)
                exercise.variants.forEachIndexed { position, variant ->
                    upsert(exercise.id, position.toLong(), variant, at)
                }
            }
            catalog.deprecatedIds.forEach { id ->
                db.exerciseEntityQueries.deprecate(updatedAt = at, id = id)
                db.exerciseVariantEntityQueries.deprecate(updatedAt = at, id = id)
            }
            db.metaQueries.put(CATALOG_VERSION_KEY, catalog.catalogVersion.toString())
        }
        return CatalogImportResult.Imported(
            version = catalog.catalogVersion,
            exercises = catalog.exercises.size,
            variants = catalog.exercises.sumOf { it.variants.size },
        )
    }

    private fun upsert(e: ExerciseDto, at: Long) {
        val primary = e.primaryMuscles.joinToString(",")
        val secondary = e.secondaryMuscles.joinToString(",")
        val stepsEn = e.instructions.en.toJsonColumn()
        val stepsEs = e.instructions.es.toJsonColumn()
        with(db.exerciseEntityQueries) {
            insertCatalogIfMissing(
                id = e.id, nameEn = e.name.en, nameEs = e.name.es, category = e.category,
                trackingType = e.trackingType, level = e.level, primaryMuscles = primary,
                secondaryMuscles = secondary, instructionsEn = stepsEn, instructionsEs = stepsEs, updatedAt = at,
            )
            updateCatalog(
                nameEn = e.name.en, nameEs = e.name.es, category = e.category, trackingType = e.trackingType,
                level = e.level, primaryMuscles = primary, secondaryMuscles = secondary,
                instructionsEn = stepsEn, instructionsEs = stepsEs, updatedAt = at, id = e.id,
            )
        }
    }

    private fun upsert(exerciseId: String, position: Long, v: VariantDto, at: Long) {
        val stepsEn = v.instructions?.en?.toJsonColumn()
        val stepsEs = v.instructions?.es?.toJsonColumn()
        val frames = v.frames.toJsonColumn()
        with(db.exerciseVariantEntityQueries) {
            insertCatalogIfMissing(
                id = v.id, exerciseId = exerciseId, position = position, equipment = v.equipment,
                loadType = v.loadType, nameEn = v.name?.en, nameEs = v.name?.es,
                instructionsEn = stepsEn, instructionsEs = stepsEs, frames = frames, updatedAt = at,
            )
            updateCatalog(
                exerciseId = exerciseId, position = position, equipment = v.equipment, loadType = v.loadType,
                nameEn = v.name?.en, nameEs = v.name?.es, instructionsEn = stepsEn, instructionsEs = stepsEs,
                frames = frames, updatedAt = at, id = v.id,
            )
        }
    }
}

internal val CatalogJson = Json { ignoreUnknownKeys = true }

private val StringList = ListSerializer(String.serializer())

internal fun List<String>.toJsonColumn(): String = CatalogJson.encodeToString(StringList, this)
