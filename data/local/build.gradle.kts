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
