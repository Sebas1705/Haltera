package es.sebas1705.haltera.data.repository.export

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.share.RoutineSharing
import es.sebas1705.haltera.data.repository.share.SharedExercise
import es.sebas1705.haltera.data.repository.share.SharedVariant
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.toModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What an export contained; also written to `manifest.json`. */
@Serializable
data class ExportCounts(
    val sessions: Int = 0,
    val sets: Int = 0,
    val bodyWeightEntries: Int = 0,
    val mealEntries: Int = 0,
    val routines: Int = 0,
    val customExercises: Int = 0,
)

/**
 * Writes everything the user logged as a zip (docs/EXPORT-FORMAT.md): `sessions.csv`, `body_weight.csv`,
 * `meal_entries.csv`, `routines.json`, `exercises.json` (the user's own exercises) and `manifest.json`.
 * The bundled catalogue and food database are not exported; exercises are referenced by their stable ids.
 */
interface DataExporter {
    /**
     * Streams the archive to [sink] and returns what it contained. Database rows are read in pages, so the
     * history is never held in memory as a whole. [exportedAt] is epoch millis; local dates use [timeZone].
     */
    suspend fun export(
        sink: ByteSink,
        appVersion: String,
        exportedAt: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): ExportCounts
}

@Serializable
internal data class ExportedExercises(
    val format: String = EXERCISES_FORMAT,
    val version: Int = 1,
    val exercises: List<SharedExercise> = emptyList(),
) {
    companion object {
        const val EXERCISES_FORMAT = "haltera-exercises"
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ExportManifest(
    @EncodeDefault val format: String = ExportFormat.FORMAT,
    @EncodeDefault val version: Int = ExportFormat.VERSION,
    val appVersion: String,
    val schemaVersion: Long,
    val exportedAt: String,
    val timeZone: String,
    val counts: ExportCounts,
    val files: List<String>,
)

@OptIn(ExperimentalTime::class)
@Suppress("LongParameterList") // One collaborator per kind of data exported.
internal class SqlDataExporter(
    private val db: AppDatabase,
    private val history: HistoryLoader,
    private val exercises: ExerciseRepository,
    private val nutrition: NutritionRepository,
    private val bodyWeight: BodyWeightRepository,
    private val routines: RoutineRepository,
    private val sharing: RoutineSharing,
    private val dispatcher: CoroutineDispatcher,
    private val pageSize: Int = PAGE_SIZE,
) : DataExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun export(
        sink: ByteSink,
        appVersion: String,
        exportedAt: Long,
        timeZone: TimeZone,
    ): ExportCounts = withContext(dispatcher) {
        val zip = ZipWriter(sink, dosTime(exportedAt))
        val sessions = ByteArrayOutput(ENTRY_CAPACITY)
        val sessionsCsv = CsvWriter(sessions, ExportFormat.SESSION_COLUMNS)
        val routineList = routines.observeRoutines().first()
        val sessionCount = writeSessions(sessionsCsv, timeZone, routineList.associate { it.id to it.name })
        zip.entry(ExportFormat.SESSIONS_FILE, sessions)

        val weights = ByteArrayOutput()
        val weightsCsv = CsvWriter(weights, ExportFormat.BODY_WEIGHT_COLUMNS)
        bodyWeight.observeAll().first().sortedBy { it.measuredAt }.forEach {
            weightsCsv.row(ExportFormat.bodyWeightRow(it, timeZone))
        }
        zip.entry(ExportFormat.BODY_WEIGHT_FILE, weights)

        val meals = ByteArrayOutput()
        val mealsCsv = CsvWriter(meals, ExportFormat.MEAL_COLUMNS)
        writeMeals(mealsCsv)
        zip.entry(ExportFormat.MEAL_ENTRIES_FILE, meals)

        val routineFiles = routineList.mapNotNull { sharing.export(it.id) }
        zip.entry(ExportFormat.ROUTINES_FILE, text("[" + routineFiles.joinToString(",\n") + "]\n"))

        val custom = customExercises()
        zip.entry(
            ExportFormat.EXERCISES_FILE,
            text(json.encodeToString(ExportedExercises.serializer(), ExportedExercises(exercises = custom)) + "\n"),
        )

        val counts = ExportCounts(
            sessions = sessionCount,
            sets = sessionsCsv.rows,
            bodyWeightEntries = weightsCsv.rows,
            mealEntries = mealsCsv.rows,
            routines = routineFiles.size,
            customExercises = custom.size,
        )
        val manifest = ExportManifest(
            appVersion = appVersion,
            schemaVersion = AppDatabase.Schema.version,
            exportedAt = ExportFormat.instant(exportedAt),
            timeZone = timeZone.id,
            counts = counts,
            files = listOf(
                ExportFormat.SESSIONS_FILE,
                ExportFormat.BODY_WEIGHT_FILE,
                ExportFormat.MEAL_ENTRIES_FILE,
                ExportFormat.ROUTINES_FILE,
                ExportFormat.EXERCISES_FILE,
            ),
        )
        zip.entry(
            ExportFormat.MANIFEST_FILE,
            text(json.encodeToString(ExportManifest.serializer(), manifest) + "\n"),
        )
        zip.finish()
        counts
    }

    /** Writes the sets page by page (oldest session first); returns the number of sessions with rows. */
    private suspend fun writeSessions(csv: CsvWriter, timeZone: TimeZone, routineNames: Map<String, String>): Int {
        val variants = HashMap<String, ExportFormat.VariantInfo>()
        var sessionCount = 0
        var afterStartedAt = Long.MIN_VALUE
        var afterId = ""
        while (true) {
            val page = db.workoutSessionEntityQueries.selectExportPage(afterStartedAt, afterId, pageSize.toLong())
                .executeAsList()
            if (page.isEmpty()) break
            val sessions = history.sessions(page)
            resolve(sessions.flatMap { s -> s.exercises.map { it.variantId } }, variants)
            sessions.forEach { session ->
                val routineName = session.routineId?.let(routineNames::get)
                val rows = ExportFormat.sessionRows(session, { variants.getValue(it) }, timeZone, routineName)
                rows.forEach(csv::row)
                if (rows.isNotEmpty()) sessionCount++
            }
            afterStartedAt = page.last().startedAt
            afterId = page.last().id
        }
        return sessionCount
    }

    private suspend fun resolve(variantIds: List<String>, cache: MutableMap<String, ExportFormat.VariantInfo>) {
        val missing = variantIds.filter { it !in cache }.distinct()
        if (missing.isEmpty()) return
        val found = exercises.getVariants(missing).associateBy { it.id }
        val names = HashMap<String, Exercise?>()
        missing.forEach { id ->
            val variant = found[id]
            val exercise = variant?.let { v -> names.getOrPut(v.exerciseId) { exercises.getExercise(v.exerciseId) } }
            cache[id] = ExportFormat.VariantInfo(
                variantId = id,
                exerciseId = variant?.exerciseId.orEmpty(),
                exercise = exercise?.name?.en.orEmpty(),
                variant = variant?.let(::variantLabel).orEmpty(),
                loadType = variant?.loadType,
            )
        }
    }

    private fun variantLabel(variant: ExerciseVariant): String =
        variant.name?.en ?: variant.equipment.name.lowercase().replace('_', ' ')

    /** Meal entries by date pages; inside a date the app's order (meal, then position). */
    private suspend fun writeMeals(csv: CsvWriter) {
        var after = ""
        while (true) {
            val dates = db.mealEntryEntityQueries.selectDatesPage(after, pageSize.toLong()).executeAsList()
            if (dates.isEmpty()) break
            val days = nutrition.observeDays(LocalDate.parse(dates.first()), LocalDate.parse(dates.last())).first()
            days.forEach { day ->
                day.entries.sortedWith(compareBy({ it.meal.ordinal }, { it.position }, { it.createdAt }, { it.id }))
                    .forEach { csv.row(ExportFormat.mealRow(it)) }
            }
            after = dates.last()
        }
    }

    private fun customExercises(): List<SharedExercise> {
        val rows = db.exerciseEntityQueries.selectUser().executeAsList()
        if (rows.isEmpty()) return emptyList()
        val variants = rows.map { it.id }.chunked(MAX_IN_ARGS).flatMap {
            db.exerciseVariantEntityQueries.selectByExercises(it).executeAsList()
        }.map { it.toModel() }.groupBy { it.exerciseId }
        return rows.map { it.toModel(variants[it.id].orEmpty().sortedBy { v -> v.position }) }
            .filter { it.source == ExerciseSource.USER }
            .map { e ->
                SharedExercise(
                    id = e.id,
                    name = e.name,
                    category = e.category.name,
                    level = e.level.name,
                    primaryMuscles = e.primaryMuscles.map { it.name },
                    secondaryMuscles = e.secondaryMuscles.map { it.name },
                    trackingType = e.trackingType.name,
                    variants = e.variants.map { v -> SharedVariant(v.id, v.equipment.name, v.loadType.name, v.name) },
                )
            }
    }

    private fun text(content: String) = ByteArrayOutput().also { it.writeText(content) }

    private fun dosTime(epochMillis: Long): DosTime {
        val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
        return DosTime(t.year, t.month.ordinal + 1, t.day, t.hour, t.minute, t.second)
    }

    companion object {
        const val PAGE_SIZE = 200
        private const val ENTRY_CAPACITY = 65_536
        private const val MAX_IN_ARGS = 500
    }
}
