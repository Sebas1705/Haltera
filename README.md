# Haltera

Haltera was formerly called Ironlog (see [docs/NAMING.md](docs/NAMING.md)).

A free gym app for Android and iOS (Kotlin Multiplatform): flexible N-day routines, equipment-aware load logging, automatic rest timers, double progression, an exercise catalogue covering every kind of training and a catalogue of well-known routines. Body weight, body measurements (waist, arm, body fat or your own), progress photos kept only on your phone with a side-by-side compare, and nutrition logging are included. An optional cloud copy of your logbook (off until you turn it on, deletable any time) can be switched on in Settings → Account.

- Design: [docs/designs/2026-10-14-haltera-core-design.md](docs/designs/2026-10-14-haltera-core-design.md)
- Your data is yours: Settings → Data → Export my data writes CSV and JSON ([format](docs/EXPORT-FORMAT.md)).
- Switching apps? Settings → Data → Import workouts brings in your history from Hevy, Strong, FitNotes or a Haltera export, with a preview before anything is saved ([formats](docs/IMPORT-FORMATS.md)).
- Contributing rules: [AGENTS.md](AGENTS.md) — all documentation is in English.

## Development

Requirements: JDK 21 (`JAVA_HOME`), Android SDK (`ANDROID_HOME`), Node 22 for `scripts/`. iOS builds in CI (macOS); locally you need a Mac with XcodeGen.

    ./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug   # what CI runs
    ./gradlew :composeApp:installDebug                              # install on a device/emulator
    cd iosApp && xcodegen generate                                   # iOS project (Mac)

Plans: [docs/plans/README.md](docs/plans/README.md).

## License

[MIT](LICENSE). The license covers the code and our own catalogue data; the Haltera name and logo are not included, so a copy or derivative must use a different name. Third-party exercise data is credited in [catalog/ATTRIBUTION.md](catalog/ATTRIBUTION.md).
