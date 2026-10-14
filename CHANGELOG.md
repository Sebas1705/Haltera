# Changelog

## [Unreleased]

### Added
- Initial Ironlog template
- Android + Desktop + iOS targets
- Clean architecture with 14 modules
- Koin 4.x DI
- Ktor 3.x networking (expect/actual engine)
- SQLDelight 2.x local database
- DataStore preferences (expect/actual)
- SecureStorage expect/actual (Android EncryptedSharedPrefs / Desktop Preferences / iOS NSUserDefaults)
- MVI base classes (`MVIBaseViewModel`, `MVIBaseState`, `MVIBaseIntent`)
- Type-safe navigation (navigation-compose 2.9.1)
- Compose Multiplatform 1.7.3 theme
- Coil3 image loading
- Kermit logging
- Convention Gradle plugins (7 plugins in build-logic)
- Detekt static analysis with KMP-aware config
- GitHub Actions: validate, release (Android + Desktop 3-OS matrix), security (CodeQL), changelog gate
- AI context docs (AGENTS.md, ARCHITECTURE.md, MODULE_MAP.md, AI_INDEX.md)
