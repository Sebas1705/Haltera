## Summary

<!-- One-liner describing what this PR does -->

## Changes

- 

## Checklist

- [ ] Convention plugin used (no raw plugin blocks in module build.gradle.kts)
- [ ] No suspend inside `updateState {}` lambda
- [ ] Detekt, tests and schema check pass (`./gradlew detekt testDebugUnitTest verifySqlDelightMigration`)
- [ ] Schema changed → `1.db` regenerated (AGENTS.md "Changing the database schema")
- [ ] New module added to `settings.gradle.kts` and `MODULE_MAP.md`
- [ ] UI strings added in English and Spanish
