# Pattern Recipes

## Recipe: New feature module from scratch

```bash
# 1. Create directories
mkdir -p feature/newfeature/src/commonMain/kotlin/es/sebas1705/ironlog/feature/newfeature

# 2. build.gradle.kts
plugins { alias(libs.plugins.kmp.feature) }
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime); implementation(compose.material3)
            implementation(project(":core:common")); implementation(project(":core:ui"))
            implementation(project(":domain:usecases"))
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.bundles.koin.compose.bundle)
        }
    }
}

# 3. settings.gradle.kts — add: include(":feature:newfeature")
# 4. Create State, Intent, ViewModel, Screen
# 5. Wire viewModel in AppModule.kt
# 6. Add @Serializable route object + composable in App.kt
```

## Recipe: ViewModel with suspend calls (CORRECT)

```kotlin
private fun load() = execute {               // execute {} = suspend scope
    updateState { it.copy(isLoading = true) } // non-suspend lambda ✓
    val data = repository.fetchData()         // suspend call OUTSIDE updateState ✓
    updateState { it.copy(isLoading = false, data = data) }
}

// WRONG — will not compile:
private fun loadWrong() = execute {
    updateState { it.copy(data = repository.fetchData()) } // suspend inside non-suspend ✗
}
```

## Recipe: expect/actual for a new platform class

```kotlin
// commonMain
expect class PlatformClock {
    fun now(): Long
}

// androidMain
actual class PlatformClock {
    actual fun now() = System.currentTimeMillis()
}

// desktopMain
actual class PlatformClock {
    actual fun now() = System.currentTimeMillis()
}

// iosMain
import platform.Foundation.NSDate
actual class PlatformClock {
    actual fun now() = (NSDate().timeIntervalSince1970 * 1000).toLong()
}
```

## Recipe: Add a SQLDelight table

```sql
-- data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/ProductEntity.sq
CREATE TABLE IF NOT EXISTS ProductEntity (
    id    TEXT NOT NULL PRIMARY KEY,
    name  TEXT NOT NULL,
    price REAL NOT NULL
);

selectAll: SELECT * FROM ProductEntity;
upsert:    INSERT OR REPLACE INTO ProductEntity VALUES (?, ?, ?);
deleteById: DELETE FROM ProductEntity WHERE id = ?;
```
Then run `./gradlew generateCommonMainAppDatabaseInterface`.
