# F1c · Catalogue data layer and importer — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Store the exercise catalogue in SQLDelight, import the bundled `catalog.json` on first launch and whenever its version increases (idempotent upsert + deprecate, never touching user data), and show the imported catalogue on a temporary startup screen.

**Architecture:** `data:local` gets the catalogue tables (`ExerciseEntity`, `ExerciseVariantEntity`), the `catalog.json` DTOs and `CatalogImporter` (one transaction; SQLite 3.9 has no `UPSERT`, so `INSERT OR IGNORE` + `UPDATE … WHERE source = 'CATALOG'`). `data:models` holds the small result types. `data:repository` exposes `CatalogRepository` (import + observable summary). `composeApp` reads the bundled file through Compose resources and a `StartupViewModel` drives the startup screen. Full exercise models and mapping arrive in F2 with the Exercises tab (YAGNI).

**Tech Stack:** SQLDelight 2.4.1 (+ coroutines extensions), kotlinx.serialization 1.11, Koin 4.2.2, Compose Multiplatform resources, Turbine.

**Platform:** KMP (Android + iOS)

---

## Context for the executor

- Requires F1a (modules, build) and F1b (`composeApp/src/commonMain/composeResources/files/catalog.json`) merged into `main`. Branch: `f1c-catalog-data`.
- Environment, identity and commit rules as in F1a. **Everything committed is in English**; Spanish only in `values-es/strings.xml`.
- The schema is unreleased: `.sq` files are edited in place and there are no migrations until F5, but the snapshot `data/local/src/commonMain/sqldelight/databases/1.db` must be regenerated after every `.sq` change by running `./gradlew :data:local:generateCommonMainAppDatabaseSchema` **on its own** (AGENTS.md); `verifySqlDelightMigration` runs in CI.
- Foreign keys are enabled on device (F1a): `ExerciseVariantEntity.exerciseId` references `ExerciseEntity`, so the importer writes each exercise before its variants (it does).
- Work in a worktree: `git worktree add ../.worktrees/Ironlog/f1c -b f1c-catalog-data main` (from `~/Documents/Repos/Ironlog`), and run every command there instead of switching branches in the main checkout.
- SQLite on Android 7 is 3.9: no `UPSERT`, booleans are `INTEGER` 0/1.
- Column encodings: muscles are comma-separated enum names (`CHEST,TRICEPS`); instructions and frames are JSON arrays of strings.
- Run Gradle with the F1a environment (`JAVA_HOME`, `ANDROID_HOME`).

## File map

| Action | Path |
|---|---|
| Modify | `settings.gradle.kts`, `data/local/build.gradle.kts`, `composeApp/build.gradle.kts`, `MODULE_MAP.md` |
| Create | `data/models/build.gradle.kts`, `data/models/src/commonMain/kotlin/es/sebas1705/ironlog/data/models/Catalog.kt` |
| Create | `data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/db/ExerciseEntity.sq`, `.../ExerciseVariantEntity.sq` |
| Create | `data/local/src/commonMain/kotlin/es/sebas1705/ironlog/data/local/catalog/CatalogDto.kt`, `.../catalog/CatalogImporter.kt` |
| Create | `data/local/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/local/catalog/CatalogImporterTest.kt`, `.../catalog/BundledCatalogTest.kt` |
| Create | `data/repository/build.gradle.kts`, `data/repository/src/commonMain/kotlin/es/sebas1705/ironlog/data/repository/CatalogRepository.kt`, `.../SqlCatalogRepository.kt` |
| Create | `data/repository/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/repository/SqlCatalogRepositoryTest.kt` |
| Create | `composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/startup/StartupViewModel.kt`, `.../startup/StartupScreen.kt`, `.../startup/BundledCatalog.kt` |
| Create | `composeApp/src/commonMain/composeResources/values/strings.xml`, `.../values-es/strings.xml` |
| Modify | `composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/App.kt`, `.../di/AppModule.kt` |

---

### Task 1: `data:models` and result types

**Files:**
- Modify: `settings.gradle.kts`
- Create: `data/models/build.gradle.kts`, `data/models/src/commonMain/kotlin/es/sebas1705/ironlog/data/models/Catalog.kt`

- [ ] **Step 1: Branch and register the new modules**

```bash
cd ~/Documents/Repos/Ironlog && git pull && git worktree add ../.worktrees/Ironlog/f1c -b f1c-catalog-data main && cd ../.worktrees/Ironlog/f1c
```
In `settings.gradle.kts`, replace `include(":data:local")` with:
```kotlin
include(":data:models", ":data:local", ":data:repository")
```

- [ ] **Step 2: `data/models/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.kmp.data) }
```

- [ ] **Step 3: `Catalog.kt`**

```kotlin
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
```

- [ ] **Step 4: Commit** (the module compiles with the next task's build)

```bash
git add settings.gradle.kts data/models
git commit -m "feat(data): models module with catalogue summary and import result"
```

### Task 2: Catalogue schema

**Files:**
- Create: `data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/db/ExerciseEntity.sq`, `ExerciseVariantEntity.sq`
- Modify: `data/local/build.gradle.kts`

- [ ] **Step 1: Make `data:local` depend on `data:models` and serialization**

In `data/local/build.gradle.kts`, `commonMain.dependencies` becomes:
```kotlin
        commonMain.dependencies {
            api(project(":data:models"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
```

- [ ] **Step 2: `ExerciseEntity.sq`**

```sql
-- Base exercises (spec §3.1). Catalogue rows are owned by the importer; USER rows by the user.
CREATE TABLE ExerciseEntity (
    id               TEXT NOT NULL PRIMARY KEY,
    source           TEXT NOT NULL,              -- CATALOG | USER
    nameEn           TEXT NOT NULL,
    nameEs           TEXT NOT NULL,
    category         TEXT NOT NULL,
    trackingType     TEXT NOT NULL,
    level            TEXT NOT NULL,
    primaryMuscles   TEXT NOT NULL,              -- comma-separated Muscle names
    secondaryMuscles TEXT NOT NULL,
    instructionsEn   TEXT NOT NULL,              -- JSON array of steps
    instructionsEs   TEXT NOT NULL,
    deprecated       INTEGER NOT NULL DEFAULT 0, -- 0/1
    updatedAt        INTEGER NOT NULL            -- epoch millis
);

insertCatalogIfMissing:
INSERT OR IGNORE INTO ExerciseEntity(
    id, source, nameEn, nameEs, category, trackingType, level,
    primaryMuscles, secondaryMuscles, instructionsEn, instructionsEs, deprecated, updatedAt
) VALUES (
    :id, 'CATALOG', :nameEn, :nameEs, :category, :trackingType, :level,
    :primaryMuscles, :secondaryMuscles, :instructionsEn, :instructionsEs, 0, :updatedAt
);

updateCatalog:
UPDATE ExerciseEntity
SET nameEn = :nameEn, nameEs = :nameEs, category = :category, trackingType = :trackingType, level = :level,
    primaryMuscles = :primaryMuscles, secondaryMuscles = :secondaryMuscles,
    instructionsEn = :instructionsEn, instructionsEs = :instructionsEs, deprecated = 0, updatedAt = :updatedAt
WHERE id = :id AND source = 'CATALOG';

deprecate:
UPDATE ExerciseEntity SET deprecated = 1, updatedAt = :updatedAt
WHERE id = :id AND source = 'CATALOG';

insertUser:
INSERT INTO ExerciseEntity(
    id, source, nameEn, nameEs, category, trackingType, level,
    primaryMuscles, secondaryMuscles, instructionsEn, instructionsEs, deprecated, updatedAt
) VALUES (
    :id, 'USER', :nameEn, :nameEs, :category, :trackingType, :level,
    :primaryMuscles, :secondaryMuscles, :instructionsEn, :instructionsEs, 0, :updatedAt
);

selectById:
SELECT * FROM ExerciseEntity WHERE id = ?;

countActive:
SELECT COUNT(*) FROM ExerciseEntity WHERE deprecated = 0;
```

- [ ] **Step 3: `ExerciseVariantEntity.sq`**

```sql
-- Equipment variants of an exercise (spec §3.1). History and progression are per variant.
CREATE TABLE ExerciseVariantEntity (
    id             TEXT NOT NULL PRIMARY KEY,
    exerciseId     TEXT NOT NULL REFERENCES ExerciseEntity(id),
    source         TEXT NOT NULL,              -- CATALOG | USER
    position       INTEGER NOT NULL,           -- order inside the exercise
    equipment      TEXT NOT NULL,
    loadType       TEXT NOT NULL,
    nameEn         TEXT,                       -- null: shown as "<exercise> (<equipment>)"
    nameEs         TEXT,
    instructionsEn TEXT,                       -- JSON array; null: use the exercise's
    instructionsEs TEXT,
    frames         TEXT NOT NULL,              -- JSON array of media paths
    deprecated     INTEGER NOT NULL DEFAULT 0, -- 0/1
    updatedAt      INTEGER NOT NULL            -- epoch millis
);

CREATE INDEX ExerciseVariantEntity_exerciseId ON ExerciseVariantEntity(exerciseId);

insertCatalogIfMissing:
INSERT OR IGNORE INTO ExerciseVariantEntity(
    id, exerciseId, source, position, equipment, loadType, nameEn, nameEs,
    instructionsEn, instructionsEs, frames, deprecated, updatedAt
) VALUES (
    :id, :exerciseId, 'CATALOG', :position, :equipment, :loadType, :nameEn, :nameEs,
    :instructionsEn, :instructionsEs, :frames, 0, :updatedAt
);

updateCatalog:
UPDATE ExerciseVariantEntity
SET exerciseId = :exerciseId, position = :position, equipment = :equipment, loadType = :loadType,
    nameEn = :nameEn, nameEs = :nameEs, instructionsEn = :instructionsEn, instructionsEs = :instructionsEs,
    frames = :frames, deprecated = 0, updatedAt = :updatedAt
WHERE id = :id AND source = 'CATALOG';

deprecate:
UPDATE ExerciseVariantEntity SET deprecated = 1, updatedAt = :updatedAt
WHERE id = :id AND source = 'CATALOG';

selectByExercise:
SELECT * FROM ExerciseVariantEntity WHERE exerciseId = ? ORDER BY position;

selectById:
SELECT * FROM ExerciseVariantEntity WHERE id = ?;

countActive:
SELECT COUNT(*) FROM ExerciseVariantEntity WHERE deprecated = 0;
```

- [ ] **Step 4: Generate and compile**

```bash
./gradlew :data:local:compileDebugKotlinAndroid 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL` (`AppDatabase` now exposes `exerciseEntityQueries` and `exerciseVariantEntityQueries`).

- [ ] **Step 5: Regenerate the schema snapshot**

```bash
./gradlew :data:local:generateCommonMainAppDatabaseSchema
./gradlew :data:local:verifySqlDelightMigration 2>&1 | tail -3
```
Expected: `databases/1.db` changes and the verification is `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add data/local
git commit -m "feat(data): exercise and variant tables for the catalogue"
```

### Task 3: Catalogue DTOs and importer

**Files:**
- Create: `data/local/src/commonMain/kotlin/es/sebas1705/ironlog/data/local/catalog/CatalogDto.kt`, `CatalogImporter.kt`
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/local/catalog/CatalogImporterTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package es.sebas1705.ironlog.data.local.catalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CatalogImporterTest {
    private val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })
    private var clock = 1_000L
    private val importer = CatalogImporter(db, now = { clock })

    private fun catalog(version: Int, benchEs: String = "Press de banca", deprecated: String = "", extra: String = "") = """
        {"catalogVersion":$version,"contentHash":"h$version","deprecatedIds":[$deprecated],"exercises":[
          {"id":"bench-press","name":{"en":"Bench Press","es":"$benchEs"},"category":"STRENGTH",
           "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["CHEST"],
           "secondaryMuscles":["SHOULDERS","TRICEPS"],"instructions":{"en":["Lie down."],"es":["Túmbate."]},
           "variants":[
             {"id":"bench-press.barbell","equipment":"BARBELL","loadType":"BARBELL","name":null,
              "instructions":{"en":["Lie down."],"es":["Túmbate."]},"frames":["B/0.jpg","B/1.jpg"]},
             {"id":"bench-press.dumbbell","equipment":"DUMBBELL","loadType":"DUMBBELL","name":null,
              "instructions":null,"frames":[]}]}
          $extra
        ]}
    """.trimIndent()

    @Test
    fun `first import inserts exercises and variants and stores the version`() {
        val result = importer.importIfNewer(catalog(1))

        assertEquals(CatalogImportResult.Imported(version = 1, exercises = 1, variants = 2), result)
        assertEquals(1, importer.installedVersion())
        val bench = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("CATALOG", bench.source)
        assertEquals("CHEST", bench.primaryMuscles)
        assertEquals("SHOULDERS,TRICEPS", bench.secondaryMuscles)
        assertEquals("[\"Túmbate.\"]", bench.instructionsEs)
        val variants = db.exerciseVariantEntityQueries.selectByExercise("bench-press").executeAsList()
        assertEquals(listOf("bench-press.barbell", "bench-press.dumbbell"), variants.map { it.id })
        assertEquals("[\"B/0.jpg\",\"B/1.jpg\"]", variants[0].frames)
        assertEquals(null, variants[1].instructionsEn)
    }

    @Test
    fun `same or older version is not imported again`() {
        importer.importIfNewer(catalog(2))
        clock = 2_000L

        assertIs<CatalogImportResult.UpToDate>(importer.importIfNewer(catalog(2, benchEs = "Cambiado")))
        assertIs<CatalogImportResult.UpToDate>(importer.importIfNewer(catalog(1)))
        assertEquals("Press de banca", db.exerciseEntityQueries.selectById("bench-press").executeAsOne().nameEs)
    }

    @Test
    fun `newer version updates rows, adds new ones and deprecates removed ids`() {
        importer.importIfNewer(catalog(1))
        clock = 2_000L
        val squat = """,{"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
            "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":[],
            "instructions":{"en":["Squat."],"es":["Agáchate."]},
            "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]}"""

        importer.importIfNewer(catalog(2, benchEs = "Press banca", deprecated = "\"bench-press.dumbbell\"", extra = squat))

        val bench = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("Press banca", bench.nameEs)
        assertEquals(2_000L, bench.updatedAt)
        assertEquals(1L, db.exerciseVariantEntityQueries.selectById("bench-press.dumbbell").executeAsOne().deprecated)
        assertEquals(2L, db.exerciseEntityQueries.countActive().executeAsOne())
        assertEquals(2, importer.installedVersion())
    }

    @Test
    fun `user exercises are never touched`() {
        db.exerciseEntityQueries.insertUser(
            id = "user-1", nameEn = "My lift", nameEs = "Mi ejercicio", category = "STRENGTH",
            trackingType = "WEIGHT_REPS", level = "BEGINNER", primaryMuscles = "CHEST", secondaryMuscles = "",
            instructionsEn = "[]", instructionsEs = "[]", updatedAt = 1L,
        )

        importer.importIfNewer(catalog(1, deprecated = "\"user-1\""))

        val mine = db.exerciseEntityQueries.selectById("user-1").executeAsOne()
        assertEquals(0L, mine.deprecated)
        assertEquals("USER", mine.source)
    }

    @Test
    fun `malformed catalogue leaves the installed one untouched`() {
        importer.importIfNewer(catalog(1))

        assertFailsWith<Exception> { importer.importIfNewer("{\"catalogVersion\":2,\"exercises\":[{\"id\":") }

        assertEquals(1, importer.installedVersion())
        assertEquals(1L, db.exerciseEntityQueries.countActive().executeAsOne())
    }
}
```

- [ ] **Step 2: Run to see them fail**

```bash
./gradlew :data:local:testDebugUnitTest --tests "*CatalogImporterTest" 2>&1 | tail -5
```
Expected: compilation FAIL — `Unresolved reference 'CatalogImporter'`.

- [ ] **Step 3: DTOs**

`CatalogDto.kt`:
```kotlin
package es.sebas1705.ironlog.data.local.catalog

import kotlinx.serialization.Serializable

// Shape of composeApp/.../files/catalog.json, produced by scripts/catalog (docs/plans/2026-10-14-f1b-catalog-pipeline.md).

@Serializable
internal data class CatalogDto(
    val catalogVersion: Int,
    val contentHash: String,
    val exercises: List<ExerciseDto>,
    val deprecatedIds: List<String> = emptyList(),
)

@Serializable
internal data class TextDto(val en: String, val es: String)

@Serializable
internal data class InstructionsDto(val en: List<String>, val es: List<String>)

@Serializable
internal data class ExerciseDto(
    val id: String,
    val name: TextDto,
    val category: String,
    val trackingType: String,
    val level: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: InstructionsDto,
    val variants: List<VariantDto>,
)

@Serializable
internal data class VariantDto(
    val id: String,
    val equipment: String,
    val loadType: String,
    val name: TextDto? = null,
    val instructions: InstructionsDto? = null,
    val frames: List<String> = emptyList(),
)
```

- [ ] **Step 4: Importer**

`CatalogImporter.kt`:
```kotlin
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
                exercise.variants.forEachIndexed { position, variant -> upsert(exercise.id, position.toLong(), variant, at) }
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
```
If SQLDelight orders the generated parameters differently, keep the named arguments as written (they are order-independent).

- [ ] **Step 5: Run the tests**

```bash
./gradlew :data:local:testDebugUnitTest --tests "*CatalogImporterTest" 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add data/local
git commit -m "feat(data): idempotent catalogue importer that never touches user rows"
```

### Task 4: The real catalogue imports cleanly

**Files:**
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/local/catalog/BundledCatalogTest.kt`

Guards against drift between the pipeline output (F1b) and the DTOs: the committed `catalog.json` must decode and import.

- [ ] **Step 1: Write the test**

```kotlin
package es.sebas1705.ironlog.data.local.catalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledCatalogTest {
    // Unit tests run with the module directory as working directory.
    private val bundled = File("../../composeApp/src/commonMain/composeResources/files/catalog.json")

    @Test
    fun `the bundled catalogue imports and counts match the file`() {
        val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })

        val result = CatalogImporter(db, now = { 0L }).importIfNewer(bundled.readText())

        val imported = assertIs<CatalogImportResult.Imported>(result)
        assertTrue(imported.exercises > 700, "expected the full catalogue, got ${imported.exercises}")
        assertEquals(imported.exercises.toLong(), db.exerciseEntityQueries.countActive().executeAsOne())
        assertEquals(imported.variants.toLong(), db.exerciseVariantEntityQueries.countActive().executeAsOne())
        assertEquals(
            listOf("bench-press.barbell", "bench-press.dumbbell", "bench-press.smith", "bench-press.machine"),
            db.exerciseVariantEntityQueries.selectByExercise("bench-press").executeAsList().map { it.id },
        )
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :data:local:testDebugUnitTest --tests "*BundledCatalogTest" 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Commit**

```bash
git add data/local/src/androidUnitTest
git commit -m "test(data): the bundled catalogue decodes and imports"
```

### Task 5: `CatalogRepository`

**Files:**
- Create: `data/repository/build.gradle.kts`, `.../CatalogRepository.kt`, `.../SqlCatalogRepository.kt`
- Test: `data/repository/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/repository/SqlCatalogRepositoryTest.kt`

- [ ] **Step 1: `data/repository/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package es.sebas1705.ironlog.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogSummary
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlCatalogRepositoryTest {
    private val catalog = """
        {"catalogVersion":3,"contentHash":"h","deprecatedIds":[],"exercises":[
          {"id":"plank","name":{"en":"Plank","es":"Plancha"},"category":"STRENGTH","trackingType":"TIME",
           "level":"BEGINNER","primaryMuscles":["ABDOMINALS"],"secondaryMuscles":[],
           "instructions":{"en":["Hold."],"es":["Aguanta."]},
           "variants":[{"id":"plank.bodyweight","equipment":"BODYWEIGHT","loadType":"BODYWEIGHT","frames":[]}]}]}
    """.trimIndent()

    @Test
    fun `summary reflects the import`() = runTest {
        val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })
        val repository = SqlCatalogRepository(db, CatalogImporter(db, now = { 0L }), UnconfinedTestDispatcher(testScheduler))

        repository.observeSummary().test {
            assertEquals(CatalogSummary(version = null, exercises = 0, variants = 0), awaitItem())
            repository.importBundled(catalog)
            assertEquals(CatalogSummary(version = 3, exercises = 1, variants = 1), expectMostRecentItem())
        }
    }
}
```

- [ ] **Step 3: Run to see it fail**

```bash
./gradlew :data:repository:testDebugUnitTest 2>&1 | tail -5
```
Expected: compilation FAIL — `Unresolved reference 'SqlCatalogRepository'`.

- [ ] **Step 4: Implement**

`CatalogRepository.kt`:
```kotlin
package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.data.models.CatalogImportResult
import es.sebas1705.ironlog.data.models.CatalogSummary
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    /** Imports [json] (the bundled catalogue) if it is newer than the installed one. */
    suspend fun importBundled(json: String): CatalogImportResult

    fun observeSummary(): Flow<CatalogSummary>
}
```

`SqlCatalogRepository.kt`:
```kotlin
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class SqlCatalogRepository(
    private val db: AppDatabase,
    private val importer: CatalogImporter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
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
```

- [ ] **Step 5: Run the test**

```bash
./gradlew :data:repository:testDebugUnitTest 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 1 test passed. If `expectMostRecentItem` sees an intermediate emission (one table updated before another), it still ends on the final summary because the import is one transaction and SQLDelight notifies after commit.

- [ ] **Step 6: Commit**

```bash
git add data/repository
git commit -m "feat(data): catalogue repository with import and observable summary"
```

### Task 6: Startup screen with the imported catalogue

**Files:**
- Create: `composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/startup/BundledCatalog.kt`, `StartupViewModel.kt`, `StartupScreen.kt`
- Create: `composeApp/src/commonMain/composeResources/values/strings.xml`, `values-es/strings.xml`
- Modify: `composeApp/build.gradle.kts`, `App.kt`, `di/AppModule.kt`

- [ ] **Step 1: Dependencies**

In `composeApp/build.gradle.kts` `commonMain.dependencies`, add next to `:data:local`:
```kotlin
            implementation(project(":data:repository"))
```

- [ ] **Step 2: Strings**

`composeApp/src/commonMain/composeResources/values/strings.xml`:
```xml
<resources>
    <string name="startup_title">Ironlog</string>
    <string name="startup_loading">Preparing the exercise catalogue…</string>
    <string name="startup_catalog_version">Exercise catalogue v%1$d</string>
    <string name="startup_catalog_counts">%1$d exercises · %2$d variants</string>
    <string name="startup_catalog_error">The exercise catalogue could not be loaded.</string>
</resources>
```

`composeApp/src/commonMain/composeResources/values-es/strings.xml`:
```xml
<resources>
    <string name="startup_title">Ironlog</string>
    <string name="startup_loading">Preparando el catálogo de ejercicios…</string>
    <string name="startup_catalog_version">Catálogo de ejercicios v%1$d</string>
    <string name="startup_catalog_counts">%1$d ejercicios · %2$d variantes</string>
    <string name="startup_catalog_error">No se ha podido cargar el catálogo de ejercicios.</string>
</resources>
```

- [ ] **Step 3: Bundled file reader**

`startup/BundledCatalog.kt`:
```kotlin
package es.sebas1705.ironlog.startup

import es.sebas1705.ironlog.resources.Res

/** The catalogue generated by scripts/catalog and bundled as a Compose resource. */
suspend fun readBundledCatalog(): String = Res.readBytes("files/catalog.json").decodeToString()
```

- [ ] **Step 4: ViewModel**

`startup/StartupViewModel.kt`:
```kotlin
package es.sebas1705.ironlog.startup

import co.touchlab.kermit.Logger
import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.CatalogSummary
import es.sebas1705.ironlog.data.repository.CatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest

data class StartupState(
    val summary: CatalogSummary? = null,
    val failed: Boolean = false,
) : MVIBaseState

sealed interface StartupIntent : MVIBaseIntent {
    data object Start : StartupIntent
}

/** Imports the bundled catalogue on launch and exposes what the database holds. Replaced by the tabs in F2. */
class StartupViewModel(
    private val catalog: CatalogRepository,
    private val readCatalog: suspend () -> String = ::readBundledCatalog,
) : MVIBaseViewModel<StartupState, StartupIntent>(StartupState()) {

    init {
        onEvent(StartupIntent.Start)
    }

    override fun intentHandler(intent: StartupIntent): Job = when (intent) {
        StartupIntent.Start -> execute {
            try {
                catalog.importBundled(readCatalog())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Catalogue import failed" }
                updateState { it.copy(failed = true) }
            }
            catalog.observeSummary().collectLatest { summary -> updateState { it.copy(summary = summary) } }
        }
    }
}
```
Add `import kotlin.coroutines.cancellation.CancellationException`. `MVIBaseViewModel` takes the initial state as a constructor argument (changed in F1a's review). `Logger` is Kermit and `core:logging` only has it as `implementation`: add `implementation(libs.kermit)` to `composeApp` `commonMain.dependencies`.

- [ ] **Step 5: Screen**

`startup/StartupScreen.kt`:
```kotlin
package es.sebas1705.ironlog.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.resources.Res
import es.sebas1705.ironlog.resources.startup_catalog_counts
import es.sebas1705.ironlog.resources.startup_catalog_error
import es.sebas1705.ironlog.resources.startup_catalog_version
import es.sebas1705.ironlog.resources.startup_loading
import es.sebas1705.ironlog.resources.startup_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun StartupScreen(state: StartupState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.startup_title), style = MaterialTheme.typography.headlineMedium)
        val summary = state.summary
        when {
            state.failed -> Text(stringResource(Res.string.startup_catalog_error), color = MaterialTheme.colorScheme.error)
            summary?.version == null -> {
                CircularProgressIndicator()
                Text(stringResource(Res.string.startup_loading))
            }
            else -> {
                Text(stringResource(Res.string.startup_catalog_version, summary.version))
                Text(stringResource(Res.string.startup_catalog_counts, summary.exercises.toInt(), summary.variants.toInt()))
            }
        }
    }
}
```

- [ ] **Step 6: Wire DI and the root composable**

`di/AppModule.kt`:
```kotlin
package es.sebas1705.ironlog.di

import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.createDatabase
import es.sebas1705.ironlog.data.repository.CatalogRepository
import es.sebas1705.ironlog.data.repository.SqlCatalogRepository
import es.sebas1705.ironlog.startup.StartupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
val appModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
    single { CatalogImporter(get(), now = { Clock.System.now().toEpochMilliseconds() }) }
    single<CatalogRepository> { SqlCatalogRepository(get(), get()) }
    viewModel { StartupViewModel(get()) }
}
```

`App.kt`:
```kotlin
package es.sebas1705.ironlog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.theme.AppTheme
import es.sebas1705.ironlog.startup.StartupScreen
import es.sebas1705.ironlog.startup.StartupViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Root composable. F2 replaces the startup screen with the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme {
        Surface(modifier.fillMaxSize()) {
            val viewModel: StartupViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StartupScreen(state)
        }
    }
}
```

- [ ] **Step 7: Build, run on the emulator, check**

```bash
./gradlew detekt testDebugUnitTest :composeApp:installDebug 2>&1 | tail -5
MSYS_NO_PATHCONV=1 "$ANDROID_HOME/platform-tools/adb" shell pm clear es.sebas1705.ironlog.debug
MSYS_NO_PATHCONV=1 "$ANDROID_HOME/platform-tools/adb" shell am start -n es.sebas1705.ironlog.debug/es.sebas1705.ironlog.MainActivity
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > "$TMP/ironlog-f1c.png"
```
Expected: `BUILD SUCCESSFUL`; the screenshot shows "Ironlog", "Exercise catalogue v1" and the exercise/variant counts (Spanish strings if the emulator is in Spanish). Restart the app: the counts appear immediately (`UpToDate`, no re-import). Check logcat has no `Catalogue import failed`:
```bash
"$ANDROID_HOME/platform-tools/adb" logcat -d | grep -i "catalogue import failed"; echo "exit=$?"
```
Expected: `exit=1`.

- [ ] **Step 8: Commit**

```bash
git add composeApp
git commit -m "feat(app): import the bundled catalogue on launch and show its summary"
```

### Task 7: Documentation, PR and plan status

- [ ] **Step 1: Update `MODULE_MAP.md`** rows:
  - `data:models`: `CatalogSummary`, `CatalogImportResult`; full exercise models in F2.
  - `data:local`: add `ExerciseEntity`, `ExerciseVariantEntity`, `CatalogImporter` (bundled `catalog.json` → upsert + deprecate, never touches `USER` rows).
  - `data:repository`: `CatalogRepository` (`importBundled`, `observeSummary`).
  - `composeApp`: temporary `startup` screen (replaced in F2).

- [ ] **Step 2: Mark F1c as done** in `docs/plans/README.md`, note deviations at the end of this plan, commit `docs: mark F1c as done`.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin f1c-catalog-data
gh pr create --base main --title "F1c: catalogue data layer and importer" --body "Implements docs/plans/2026-10-14-f1c-catalog-data.md."
```
Expected: all CI jobs green (Android, iOS simulator, catalogue). Merge with a merge commit and delete the branch.

## Final verification of F1c (and of F1)

- `./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug` → `BUILD SUCCESSFUL`.
- `node scripts/catalog/build.mjs --check` → up to date.
- Fresh install on the emulator shows catalogue v1 with ~800 exercises; a second launch does not re-import.
- CI green on `main` after the merge, including the iOS simulator build on the PR.

## Deviations during execution

- **Task 1:** `settings.gradle.kts` included only `:data:models` and `:data:local`. Gradle 9.5.1 refuses to configure a project whose directory does not exist, so `:data:repository` was added to the include in Task 5, when the module was created.
- **Task 3 (Detekt):** two long lines were wrapped (`forEachIndexed { … -> upsert(…) }` in `CatalogImporter` and one `importIfNewer(catalog(2, …))` call in the test) to satisfy `ArgumentListWrapping`/`Wrapping`. No change in logic.
- **Review fixes (commit `fix(data): cheap version check, rollback-safe import and user id guard`):**
  - `CatalogHeader(catalogVersion)` is decoded first; the full `CatalogDto` is decoded only when the bundled version is newer.
  - The version check and all writes run inside one `db.transactionWithResult { }`.
  - `updateCatalog` runs only when `insertCatalogIfMissing` wrote nothing (no double writes), for exercises and variants.
  - A catalogue exercise whose id belongs to a non-`CATALOG` row is skipped together with its variants. `Imported.exercises`/`variants` count rows actually written, not DTO entries. Invariant documented in the importer KDoc: user ids are `user-<uuid>` and catalogue ids never use that prefix.
  - Test drivers have foreign keys on, as on the device (`inMemoryDriver()`/`inMemoryDatabase()` in `data:local` tests; an equivalent private helper in `SqlCatalogRepositoryTest`). `MetaQueriesTest` uses it too.
  - New tests: user-id collision guard, and a real rollback (a `RAISE(ABORT)` trigger on `squat.barbell` makes the v2 import throw; version, `bench-press` and the absence of `squat` are checked). The malformed-JSON test now expects `SerializationException`.
  - Both `.sq` files note that `OR IGNORE` is meant only for primary-key conflicts and must be revisited before adding UNIQUE/CHECK constraints (comment outside the DDL; `1.db` unchanged).
- **Task 4:** besides the plan's assertions, the test checks `Imported` counts against the entries in `catalog.json` (currently 801 exercises, 878 variants), so silently skipped rows fail it.
- **Task 5:** the repository dispatcher defaults to `Dispatchers.IO` (blocking database I/O) instead of `Dispatchers.Default`. The test also asserts the `Imported` result of `importBundled` and opts in to `ExperimentalCoroutinesApi` for `UnconfinedTestDispatcher`.
- **Task 6:** not executed; the temporary startup screen is superseded by the MVP app shell. No `composeApp` changes and no `startup` strings in F1c.
