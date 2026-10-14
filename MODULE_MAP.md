# Module map

| Module | Contents | Plan that extends it |
|---|---|---|
| `composeApp` | Android/iOS entry point (iOS: `iosApp/project.yml` with XcodeGen), root DI (`AppModule`), `App` root composable | F1c, F2 |
| `core:common` | MVI base (`MVIBaseViewModel`, intents, state), `AppResult`, flow extensions | — |
| `core:ui` | Template theme (`AppTheme`) and basic components; brand theme in F2 | F2 |
| `core:logging` | Kermit logger | — |
| `core:testing` | Coroutine test rule; shared fakes (from F1c) | F1c |
| `data:models` | Plain data types shared with features: `CatalogSummary`, `CatalogImportResult`; full exercise models in F2 | F2 |
| `data:local` | SQLDelight `AppDatabase` (`Meta` key-value table, `ExerciseEntity`, `ExerciseVariantEntity`), `DatabaseDriverFactory` per platform, `CatalogImporter` (bundled `catalog.json` → upsert + deprecate in one transaction, never touches `USER` rows) | F2 |
| `data:repository` | `CatalogRepository` (`importBundled`, `observeSummary`) over SQLDelight | F2 |
| `domain:usecases` | `UseCase` base | F3, F4 |

Features only see `domain`, `data:models` (from F1c) and `core` (ARCHITECTURE.md). Every module with UI strings has `composeResources/values/strings.xml` (English, default) and `values-es/strings.xml` (Spanish) with the same keys.
