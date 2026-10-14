# F1a · Project scaffold and CI — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Generate Ironlog from the Templetry `kmp/modular-features` template, align its build with the pins already validated in Owlsplit, remove the template sample and end up with an app that starts on Android and a green CI (Android + iOS simulator).

**Architecture:** Template structure kept (convention plugins in `build-logic`, `core`/`data`/`domain`/`feature` layers, Koin, SQLDelight). The template still pins Kotlin 2.1 / CMP 1.7, so the Gradle wrapper, version catalogue, `build-logic` and root build files are taken from Owlsplit (same author, same template origin, validated pins) and renamed. Only the modules F1 needs survive: `composeApp`, `core:common`, `core:ui`, `core:logging`, `core:testing`, `data:local`, `domain:usecases`. No desktop target, no environments feature (no backend until F8).

**Tech Stack:** Gradle 9.5.1, Kotlin 2.4.20, Compose Multiplatform 1.11.1, AGP 8.13.2, Koin 4.2.2, SQLDelight 2.4.1, Detekt 1.23.8, XcodeGen.

**Platform:** KMP (Android + iOS)

---

## Context for the executor

- Repo: `C:\Users\sebss\Documents\Repos\Ironlog` (Git Bash: `~/Documents/Repos/Ironlog`). It already contains `README.md`, `AGENTS.md`, `CLAUDE.md` and `docs/`. **Do not overwrite** them with the template's.
- Reference repo (read-only): `~/Documents/Repos/Owlsplit`. Never modify it.
- **Everything committed is in English** (AGENTS.md).
- Required environment in every terminal:
  ```bash
  export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
  export ANDROID_HOME="/c/Users/sebss/Android/sdk"
  ```
- Template CLI: `templetry` (installed in `~/go/bin`).
- Commits use the configured identity (`Sebas1705 <sebssgarcia502580@gmail.com>`), **without** `Co-Authored-By` lines or "Generated with" footers. Work on branch `f1a-scaffold`, open a PR to `main` at the end.
- Root package: `es.sebas1705.ironlog` (path `es/sebas1705/ironlog`). Convention plugin ids: `ironlog.kmp.*`, `ironlog.detekt`.

## File map

| Action | Path |
|---|---|
| Create (render) | template tree except `README.md`, `AGENTS.md`, `.gitignore` (merged) |
| Replace from Owlsplit | `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle.properties`, `gradle/libs.versions.toml`, `build-logic/**`, `build.gradle.kts`, `config/detekt/detekt.yml` |
| Rewrite | `settings.gradle.kts`, `composeApp/build.gradle.kts`, `core/common/build.gradle.kts`, `core/ui/build.gradle.kts`, `core/logging/build.gradle.kts`, `core/testing/build.gradle.kts`, `data/local/build.gradle.kts`, `domain/usecases/build.gradle.kts` |
| Rewrite | `composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/App.kt`, `.../di/AppModule.kt`, `composeApp/src/androidMain/kotlin/es/sebas1705/ironlog/IronlogApplication.kt` (replaces `KmpApplication.kt`), `AndroidManifest.xml`, `composeApp/src/iosMain/kotlin/es/sebas1705/ironlog/MainViewController.kt` |
| Create | `data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/db/Meta.sq`, `data/local/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/local/MetaQueriesTest.kt` |
| Create | `iosApp/project.yml`, `.github/workflows/ci.yml` |
| Delete | `feature/`, `data/network/`, `data/models/`, `data/repository/`, `core/security/`, `core/network/`, `core/datastore/`, `domain/usecases/.../user/`, `data/local/.../dao/`, `UserEntity.sq`, `AI_INDEX.md`, `CHANGELOG.md`, `docs/ai/`, template `.github/workflows/*` |
| Modify | `AGENTS.md` (merge template rules), `MODULE_MAP.md`, `README.md` (Development section), `.gitignore` |

---

### Task 1: Generate the project from the template

**Files:** whole template tree (except the files listed above as preserved)

- [ ] **Step 1: Branch and render into a scratch directory**

```bash
cd ~/Documents/Repos/Ironlog
git switch -c f1a-scaffold
SCR="$(mktemp -d)"
templetry init kmp/modular-features --out "$SCR/ironlog" \
  --set project_name=Ironlog \
  --set base_package=es.sebas1705.ironlog \
  --feature desktop=false \
  --feature environments=false
ls "$SCR/ironlog"
```
Expected: `build-logic composeApp config core data domain feature gradle iosApp settings.gradle.kts ...`.

- [ ] **Step 2: Check the identity rename**

```bash
grep -rIl "dev.template.kmpbase\|kmpbase\|KMPNativeBase" "$SCR/ironlog" | grep -v "/.git/" | head
ls "$SCR/ironlog/composeApp/src/commonMain/kotlin/es/sebas1705/ironlog"
```
Expected: the `grep` prints nothing; `ls` shows `App.kt di` (no `config`, environments are off).

- [ ] **Step 3: Copy into the repo without overwriting existing files**

```bash
cp "$SCR/ironlog/AGENTS.md" /tmp/template-AGENTS.md
cp -rn "$SCR/ironlog/." .
git status --short | head -40
```
Expected: `README.md`, `AGENTS.md`, `CLAUDE.md` do not appear as modified; the rest (including the template's `.gitignore`, the repo has none yet) shows as new (`??`).

- [ ] **Step 4: Clean `.gitignore`** (dedupe, and stop ignoring the Detekt config, which is repo configuration)

```bash
awk '!seen[$0]++ || /^$/' .gitignore > .gitignore.tmp && mv .gitignore.tmp .gitignore
sed -i '/^detekt.yml$/d' .gitignore
git check-ignore config/detekt/detekt.yml; echo "exit=$?"
```
Expected: `exit=1`.

- [ ] **Step 5: Commit the render as is (baseline)**

```bash
git add -A
git commit -m "chore: scaffold from Templetry kmp/modular-features"
```

### Task 2: Align the build with the validated pins

**Files:** replace `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle.properties`, `gradle/libs.versions.toml`, `build-logic/**`, `build.gradle.kts`, `config/detekt/detekt.yml`

- [ ] **Step 1: Copy the build infrastructure from Owlsplit**

```bash
O=~/Documents/Repos/Owlsplit
cp "$O/gradlew" "$O/gradlew.bat" .
cp "$O/gradle/wrapper/gradle-wrapper.jar" "$O/gradle/wrapper/gradle-wrapper.properties" gradle/wrapper/
cp "$O/gradle.properties" "$O/build.gradle.kts" .
cp "$O/gradle/libs.versions.toml" gradle/
cp "$O/config/detekt/detekt.yml" config/detekt/
rm -rf build-logic
mkdir build-logic
(cd "$O/build-logic" && git ls-files) | while read -r f; do mkdir -p "build-logic/$(dirname "$f")"; cp "$O/build-logic/$f" "build-logic/$f"; done
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip`. (`git ls-files` copies only tracked files, so no `build/` or `.gradle/` output comes along.)

- [ ] **Step 2: Rename the identity**

```bash
grep -rl -i "owlsplit" build-logic gradle/libs.versions.toml build.gradle.kts config/detekt/detekt.yml \
  | xargs sed -i 's/owlsplit/ironlog/g; s/Owlsplit/Ironlog/g'
grep -rn -i "owlsplit" build-logic gradle build.gradle.kts config; echo "exit=$?"
```
Expected: `exit=1` (no matches left).

- [ ] **Step 3: Remove the Firebase/Owlsplit-only entries from the catalogue and root build**

Ironlog has no Firebase, Google sign-in, QR or WorkManager until F8.
```bash
grep -v -E '^(gitlive-firebase|google-services|crashlytics-plugin|work|credentials|googleid|qrose|code-scanner) +=|gitlive|androidx-work|androidx-credentials|google-identity|qrose|play-services-code-scanner|^firebase-crashlytics|com\.google\.gms' \
  gradle/libs.versions.toml > /tmp/libs.toml && mv /tmp/libs.toml gradle/libs.versions.toml
sed -i '/google.services/d; /firebase.crashlytics/d' build.gradle.kts
grep -n -E "gitlive|firebase|qrose|credentials|google.services" gradle/libs.versions.toml build.gradle.kts; echo "exit=$?"
```
Expected: `exit=1`.

- [ ] **Step 4: Check the build-logic compiles**

```bash
./gradlew -p build-logic assemble --quiet && echo OK
```
Expected: `OK`. (The main build is not evaluated yet: the template module build files still use old accessors; Task 3 rewrites them.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: align wrapper, catalogue and conventions with the validated pins"
```

### Task 3: Remove the template sample and rewrite the module builds

**Files:** see the file map (Delete / Rewrite rows)

- [ ] **Step 1: Delete the sample and the modules F1 does not need**

```bash
git rm -r -q feature data/network data/models data/repository core/security core/network core/datastore \
  domain/usecases/src/commonMain/kotlin/es/sebas1705/ironlog/domain/usecases/user \
  data/local/src/commonMain/kotlin/es/sebas1705/ironlog/data/local/dao \
  data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/db/UserEntity.sq \
  composeApp/src/androidMain/kotlin/es/sebas1705/ironlog/KmpApplication.kt
git rm -r -q --ignore-unmatch AI_INDEX.md CHANGELOG.md docs/ai .github/workflows
git status --short | grep -v "^D" | head
```
Expected: only deletions are staged.

- [ ] **Step 2: Rewrite `settings.gradle.kts`**

```kotlin
rootProject.name = "Ironlog"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
    }
}

include(":composeApp")

include(
    ":core:common",
    ":core:ui",
    ":core:logging",
    ":core:testing",
)

include(":data:local")

include(":domain:usecases")
```

- [ ] **Step 3: Rewrite the core module builds**

`core/common/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kmp.core)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
            api(libs.lifecycle.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

`core/ui/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kmp.core)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    packageOfResClass = "es.sebas1705.ironlog.core.ui.resources"
    publicResClass = false
}
```

`core/logging/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies { implementation(libs.kermit) }
    }
}
```

`core/testing/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}
```

- [ ] **Step 4: Rewrite the data and domain builds**

`data/local/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kmp.data)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("es.sebas1705.ironlog.data.local.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Snapshots of released schemas (generateCommonMainAppDatabaseSchema, run on its own).
            // Until the first release (F5) the schema is edited in place and has no snapshot.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
```

`domain/usecases/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kmp.domain) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

- [ ] **Step 5: Key-value `Meta` table** (keeps `AppDatabase` non-empty; F1c stores the catalogue version in it)

`data/local/src/commonMain/sqldelight/es/sebas1705/ironlog/data/local/db/Meta.sq`:
```sql
CREATE TABLE Meta (
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

get:
SELECT value FROM Meta WHERE key = ?;

put:
INSERT OR REPLACE INTO Meta(key, value) VALUES (?, ?);
```

- [ ] **Step 6: Rewrite `composeApp/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kmp.app)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:logging"))
            implementation(project(":data:local"))
            implementation(project(":domain:usecases"))
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}

android {
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
    }
}

dependencies {
    debugImplementation(libs.leakcanary)
}

compose.resources {
    packageOfResClass = "es.sebas1705.ironlog.resources"
    publicResClass = false
}

// The Swift app imports the shared code as `ComposeApp` (iosApp/iosApp/*.swift).
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>().configureEach {
            baseName = "ComposeApp"
        }
    }
}
```

`build-logic/.../KmpAppConventionPlugin.kt` references `proguard-rules.pro` for release builds; create it if the template did not:
```bash
test -f composeApp/proguard-rules.pro || printf '# R8 rules for Ironlog. kotlinx.serialization ships its own rules.\n' > composeApp/proguard-rules.pro
```

- [ ] **Step 7: Rewrite the app entry points**

`composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/di/AppModule.kt`:
```kotlin
package es.sebas1705.ironlog.di

import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.data.local.createDatabase
import org.koin.dsl.module

val appModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
}
```

`composeApp/src/commonMain/kotlin/es/sebas1705/ironlog/App.kt`:
```kotlin
package es.sebas1705.ironlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.ironlog.core.ui.theme.AppTheme

/** Root composable. F1c replaces the placeholder with the catalogue status; F2 adds the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme(modifier) {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text("Ironlog", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
```
If `AppTheme` in `core/ui/.../theme/Theme.kt` has a different signature, open it and adapt the call (keep the template theme; the brand theme arrives in F2).

`composeApp/src/androidMain/kotlin/es/sebas1705/ironlog/IronlogApplication.kt`:
```kotlin
package es.sebas1705.ironlog

import android.app.Application
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class IronlogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@IronlogApplication)
            modules(
                appModule,
                module { single { DatabaseDriverFactory(this@IronlogApplication) } },
            )
        }
    }
}
```

In `composeApp/src/androidMain/AndroidManifest.xml`, set `android:name=".IronlogApplication"` on `<application>` (replacing `.KmpApplication`) and remove any permission the template declares (`INTERNET` is not needed until F2):
```bash
sed -i 's/\.KmpApplication/.IronlogApplication/' composeApp/src/androidMain/AndroidManifest.xml
sed -i '/<uses-permission/d' composeApp/src/androidMain/AndroidManifest.xml
grep -n "IronlogApplication\|uses-permission" composeApp/src/androidMain/AndroidManifest.xml
```
Expected: one line with `IronlogApplication`, none with `uses-permission`.

`composeApp/src/iosMain/kotlin/es/sebas1705/ironlog/MainViewController.kt`:
```kotlin
package es.sebas1705.ironlog

import androidx.compose.ui.window.ComposeUIViewController
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(appModule, module { single { DatabaseDriverFactory() } })
        }
    },
) { App() }
```
Check `iosApp/iosApp/ContentView.swift` calls `MainViewControllerKt.MainViewController()` and imports `ComposeApp`; fix the import name if the template uses another one.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: remove the template sample and keep the F1 modules"
```

### Task 4: Build, run on Android and fix

**Files:** whatever the compiler points at

- [ ] **Step 1: Build**

```bash
./gradlew :composeApp:assembleDebug --stacktrace 2>&1 | tail -40
```
Expected: `BUILD SUCCESSFUL`.

Known failures (from Owlsplit P1) and their fixes:

| Error | Fix |
|---|---|
| `Unresolved reference 'kotlinOptions'` | Replace with `compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }` |
| `Unresolved reference: compose` (`compose.runtime` accessors) | Use `implementation(libs.bundles.compose.core)` |
| Reference to `core.security`, `core.datastore`, `core.network` or `feature.*` in a kept file | Delete the import and its usage (those modules are gone) |
| `desktopMain` / `jvm("desktop")` | Delete the lines |
| `Plugin [id: 'ironlog.kmp.…'] was not found` | Check `build-logic/convention/build.gradle.kts` registers `ironlog.*` ids |
| `@Composable` naming flagged by Detekt | `config/detekt/detekt.yml` from Owlsplit already ignores `Composable` in `FunctionNaming`; check the copy |

Repeat until `BUILD SUCCESSFUL`. Note every unplanned fix in the commit message.

- [ ] **Step 2: Tests and Detekt**

```bash
./gradlew testDebugUnitTest detekt 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`. Fix Detekt findings in template code instead of disabling rules.

- [ ] **Step 3: Start it on the emulator**

Start the emulator detached from PowerShell (a Bash background emulator dies):
```powershell
& "$env:USERPROFILE\Android\sdk\emulator\emulator.exe" -list-avds
Start-Process -FilePath "$env:USERPROFILE\Android\sdk\emulator\emulator.exe" -ArgumentList "-avd","<first name printed above>"
```
If no AVD exists, create one with `sdkmanager "system-images;android-36;google_apis;x86_64"` and `avdmanager create avd -n ironlog_api36 -k "system-images;android-36;google_apis;x86_64"`.
Then:
```bash
"$ANDROID_HOME/platform-tools/adb" wait-for-device
./gradlew :composeApp:installDebug
MSYS_NO_PATHCONV=1 "$ANDROID_HOME/platform-tools/adb" shell am start -n es.sebas1705.ironlog.debug/es.sebas1705.ironlog.MainActivity
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > "$TMP/ironlog-f1a.png"
```
Expected: the screenshot shows "Ironlog" centered.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "build: make the scaffold compile and run on Android"
```

### Task 5: Local schema test

**Files:**
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/ironlog/data/local/MetaQueriesTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package es.sebas1705.ironlog.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaQueriesTest {
    private fun newDb(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    @Test
    fun `missing key returns null`() {
        assertNull(newDb().metaQueries.get("catalogVersion").executeAsOneOrNull())
    }

    @Test
    fun `put overwrites the previous value`() {
        val db = newDb()
        db.metaQueries.put("catalogVersion", "1")
        db.metaQueries.put("catalogVersion", "2")
        assertEquals("2", db.metaQueries.get("catalogVersion").executeAsOne())
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :data:local:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 3: Commit**

```bash
git add data/local/src/androidUnitTest
git commit -m "test: cover the meta key-value table"
```

### Task 6: iOS project (XcodeGen)

**Files:**
- Create: `iosApp/project.yml`

- [ ] **Step 1: Write `iosApp/project.yml`** (Owlsplit's, without Firebase, Google sign-in, entitlements, camera or app icon — the icon arrives with the brand in F2)

```yaml
# Xcode project of the iOS app, generated with XcodeGen (`cd iosApp && xcodegen generate`):
# the .xcodeproj is not committed. CI builds it for the simulator without signing
# (.github/workflows/ci.yml). Signing needs the Apple Developer Team ID (later phase).
name: Ironlog
options:
  bundleIdPrefix: es.sebas1705
  deploymentTarget:
    iOS: "16.0"
  createIntermediateGroups: true

settings:
  base:
    MARKETING_VERSION: "1.0.0"
    CURRENT_PROJECT_VERSION: "1"

targets:
  Ironlog:
    type: application
    platform: iOS
    sources:
      - path: iosApp
    info:
      path: iosApp/Info.plist
      properties:
        CFBundleDisplayName: Ironlog
        CFBundleShortVersionString: $(MARKETING_VERSION)
        CFBundleVersion: $(CURRENT_PROJECT_VERSION)
        CFBundleDevelopmentRegion: en
        CFBundleLocalizations: [en, es]
        UILaunchScreen: {}
        UISupportedInterfaceOrientations: [UIInterfaceOrientationPortrait]
        CADisableMinimumFrameDurationOnPhone: true
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: es.sebas1705.ironlog
        PRODUCT_NAME: Ironlog
        TARGETED_DEVICE_FAMILY: "1,2"
        ENABLE_USER_SCRIPT_SANDBOXING: NO
        # The shared code (Kotlin/Native static framework built by the phase below).
        FRAMEWORK_SEARCH_PATHS: $(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
        OTHER_LDFLAGS: [-framework, ComposeApp, -lsqlite3]
    preBuildScripts:
      - name: Compile Kotlin framework
        script: |
          cd "$SRCROOT/.."
          ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
        basedOnDependencyAnalysis: false
```

- [ ] **Step 2: Ignore generated Xcode files**

```bash
printf '\n# XcodeGen output\niosApp/*.xcodeproj/\niosApp/iosApp/Info.plist\n' >> .gitignore
```

- [ ] **Step 3: Commit**

```bash
git add iosApp/project.yml .gitignore
git commit -m "build: generate the iOS project with XcodeGen"
```

### Task 7: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

The repo is private: macOS minutes count ×10, so the iOS job runs only on PRs and manual runs (as in Owlsplit).

- [ ] **Step 1: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [main]
    paths-ignore: ["docs/**", "**/*.md"]
  pull_request:
    branches: [main]
    paths-ignore: ["docs/**", "**/*.md"]
  workflow_dispatch:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  android:
    name: Android (detekt, tests, assemble)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug --continue
      - uses: actions/upload-artifact@v6
        if: always()
        with:
          name: test-reports
          path: "**/build/reports/tests/"

  ios:
    name: iOS app (simulator)
    if: github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'
    runs-on: macos-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v6
      - run: brew install xcodegen
      - working-directory: iosApp
        run: xcodegen generate
      - working-directory: iosApp
        run: |
          xcodebuild build -project Ironlog.xcodeproj -scheme Ironlog -configuration Debug \
            -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 \
            CODE_SIGNING_ALLOWED=NO -quiet
```

- [ ] **Step 2: Run the Android job locally**

```bash
./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug --continue 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: Android checks on every push, iOS simulator build on PRs"
```

### Task 8: Repo documentation

**Files:**
- Modify: `AGENTS.md`, `MODULE_MAP.md`, `README.md`

- [ ] **Step 1: Merge the template's agent rules into `AGENTS.md`**

Keep the existing `# Agent Operating Contract`, `## Language` and `## Design documents` sections first. Append the template's sections from `/tmp/template-AGENTS.md` (everything after its title line), then edit the result so it is true for Ironlog:
- remove rules about modules that no longer exist (`core:security`, network, auth sample);
- replace the environment section with the pins table in `docs/plans/README.md` (link it, do not duplicate);
- keep the `templetry:checks` block, with `./gradlew detekt testDebugUnitTest :composeApp:assembleDebug`;
- add a "Changing the database schema" section: until the first release (F5) `.sq` files are edited in place; from F5 on, add `n.sqm` migrations, regenerate the snapshot with `./gradlew :data:local:generateCommonMainAppDatabaseSchema` run on its own, and run `./gradlew verifySqlDelightMigration`. SQLite on Android 7 is 3.9: no `UPSERT`, booleans are `INTEGER` 0/1.

- [ ] **Step 2: Rewrite `MODULE_MAP.md`**

```markdown
# Module map

| Module | Contents | Plan that extends it |
|---|---|---|
| `composeApp` | Android/iOS entry point (iOS: `iosApp/project.yml` with XcodeGen), root DI (`AppModule`), `App` root composable | F1c, F2 |
| `core:common` | MVI base (`MVIBaseViewModel`, intents, state), `Result`, flow extensions | — |
| `core:ui` | Template theme (`AppTheme`) and basic components; brand theme in F2 | F2 |
| `core:logging` | Kermit logger | — |
| `core:testing` | Coroutine test rule and shared fakes | F1c |
| `data:local` | SQLDelight `AppDatabase` (`Meta` key-value table), `DatabaseDriverFactory` per platform | F1c |
| `domain:usecases` | `UseCase` base | F3, F4 |

Features only see `domain`, `data:models` and `core` (ARCHITECTURE.md). Every module with UI strings has `composeResources/values/strings.xml` (English, default) and `values-es/strings.xml` (Spanish) with the same keys.
```

- [ ] **Step 3: Add a Development section to `README.md`**

Append:
```markdown
## Development

Requirements: JDK 21 (`JAVA_HOME`), Android SDK (`ANDROID_HOME`), Node 22 for `scripts/`. iOS builds in CI (macOS); locally you need a Mac with XcodeGen.

    ./gradlew detekt testDebugUnitTest :composeApp:assembleDebug   # what CI runs
    ./gradlew :composeApp:installDebug                              # install on a device/emulator
    cd iosApp && xcodegen generate                                   # iOS project (Mac)

Plans: [docs/plans/README.md](docs/plans/README.md).
```

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md MODULE_MAP.md README.md
git commit -m "docs: agent rules, module map and development setup"
```

### Task 9: PR and plan status

- [ ] **Step 1: Mark F1a as done** in `docs/plans/README.md` (Status column → `Done`) and add a `## Deviations during execution` section at the end of this plan if anything differed. Commit: `docs: mark F1a as done`.

- [ ] **Step 2: Push and open the PR**

```bash
git push -u origin f1a-scaffold
gh pr create --base main --title "F1a: project scaffold and CI" --body "Implements docs/plans/2026-10-14-f1a-scaffold.md."
```
Expected: CI green on both jobs (Android and iOS simulator). Merge with a merge commit when green and delete the branch.

## Final verification of F1a

- `./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug` → `BUILD SUCCESSFUL` locally.
- The debug app starts on the emulator and shows "Ironlog".
- CI green on the PR, including the iOS simulator build.
- `grep -rn -i "owlsplit\|kmpbase" --include=*.kt --include=*.kts --include=*.toml --include=*.yml .` prints nothing.

## Deviations during execution

Tasks 1–3 were executed as written. Later deviations:

- **Line endings (before Task 4).** Added `.gitattributes` (`* text=auto eol=lf`, CRLF for `.bat`, `jar`/`png`/`jpg` binary) and renormalised the index. Removed the dead `<uses-sdk tools:overrideLibrary="io.kamel.image" />`, the unused `tools` namespace and leftover blank lines from `AndroidManifest.xml`.
- **Task 4.** `:composeApp:assembleDebug` compiled without any of the known failures. Detekt needed fixes in template code (no rules disabled):
  - formatting autocorrected by detekt-formatting in `core:ui` (`Color.kt`, `Shape.kt`, `Theme.kt`, `ErrorScreen.kt`), `core:testing` (`CoroutineTestRule.kt`) and `core:common` (`FlowExtensions.kt`); unused import removed from `Theme.kt`;
  - `core:common` `utils/Result.kt` renamed to `AppResult.kt` (Filename, MatchingDeclarationName);
  - unused `safeApiCall` removed (TooGenericExceptionCaught; no network layer until F8);
  - unused `modifier` parameter removed from `AppTheme` (UnusedParameter); `App` applies its modifier to the `Surface`;
  - `FlowExtensions.kt` imports `AppResult` instead of fully qualified names and types `map` instead of an unchecked cast.
  - Emulator check done on an already running AVD (`youknow_api36`) instead of starting a new one.
- **Review fixes (after Task 5).**
  - `verifySqlDelightMigration` needs a database file: `data/local/src/commonMain/sqldelight/databases/1.db` is committed. Until F5 it is regenerated whenever a `.sq` file changes (replaces "no snapshot until F5").
  - iOS starts Koin once via `initKoin()` (`composeApp/src/iosMain/.../KoinIos.kt`) called from `iOSApp.init()`; `MainViewController()` is just `ComposeUIViewController { App() }`. Android binds `DatabaseDriverFactory(androidContext())`.
  - `MVIBaseViewModel` takes `initialState` as a constructor parameter (`initState()` removed) and `updateState` uses `MutableStateFlow.update`.
  - Detekt `autoCorrect` only when `CI` is unset (convention plugin and root build); `detekt-formatting` read from the version catalogue.
  - `domain:usecases` exposes `core:common` with `api`; `mapToResult` keeps the throwable and rethrows `CancellationException`.
  - Database file renamed `app.db` → `ironlog.db`; foreign keys enabled on both drivers (Android `Callback.onOpen`, iOS `extendedConfig.foreignKeyConstraints`).
  - Build cleanup: "Desktop" dropped from the library KDoc, unused imports removed from the feature/app conventions, `release { }` split over several lines, `roborazzi apply false` dropped from the root build, `kotlin.native.ignoreDisabledTargets=true` in `gradle.properties`.
- **Task 8.** Scope extended: `ARCHITECTURE.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `SECURITY.md` and `CONTRIBUTING.md` rewritten to match the current modules. `MODULE_MAP.md` says `AppResult` (the actual class) instead of `Result`. The README "what CI runs" command includes `verifySqlDelightMigration`, as CI does. AGENTS.md drops the "Adding a feature" recipe (features arrive in F2) and lists the real iOS targets (`iosArm64`, `iosSimulatorArm64`).
