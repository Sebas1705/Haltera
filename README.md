# Ironlog

A free gym app for Android and iOS (Kotlin Multiplatform): flexible N-day routines, equipment-aware load logging, automatic rest timers, double progression, an exercise catalogue covering every kind of training and a catalogue of well-known routines. Body weight, nutrition and sync come later.

- Design: [docs/designs/2026-10-14-ironlog-core-design.md](docs/designs/2026-10-14-ironlog-core-design.md)
- Contributing rules: [AGENTS.md](AGENTS.md) — all documentation is in English.

## Development

Requirements: JDK 21 (`JAVA_HOME`), Android SDK (`ANDROID_HOME`), Node 22 for `scripts/`. iOS builds in CI (macOS); locally you need a Mac with XcodeGen.

    ./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug   # what CI runs
    ./gradlew :composeApp:installDebug                              # install on a device/emulator
    cd iosApp && xcodegen generate                                   # iOS project (Mac)

Plans: [docs/plans/README.md](docs/plans/README.md).

## License

[MIT](LICENSE). The license covers the code and our own catalogue data; the Ironlog name and logo are not included, so a copy or derivative must use a different name. Third-party exercise data is credited in [catalog/ATTRIBUTION.md](catalog/ATTRIBUTION.md).
