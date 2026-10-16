plugins { alias(libs.plugins.kmp.core) }

// Pure, dependency-free text search and ranking (docs/designs/2026-10-16-fuzzy-search-design.md).
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
