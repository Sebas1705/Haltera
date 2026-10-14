## Summary

<!-- One-liner describing what this PR does -->

## Changes

- 

## Checklist

- [ ] Convention plugin used (no raw plugin blocks in module build.gradle.kts)
- [ ] No suspend inside `updateState {}` lambda
- [ ] All modules declare `:core:security` if they use it transitively
- [ ] Detekt passes (`./gradlew detekt`)
- [ ] Desktop target compiles (`./gradlew :composeApp:compileKotlinDesktop`)
- [ ] New feature module added to `settings.gradle.kts`

## Changelog label

<!-- Add one of: breaking / feature / fix / chore / docs / deps / ci / skip-changelog -->
