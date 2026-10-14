# Architecture

## Overview

Ironlog is a **Kotlin Multiplatform** app targeting **Android** and **iOS**.
It follows **Clean Architecture** with a layered module graph. Network-backed data arrives in F8.

```
┌──────────────────────────────────────────────────────────────────┐
│                composeApp / iosApp                               │  ← Entry points, shell, DI
├──────────────────────────────────────────────────────────────────┤
│  feature:today │ routines │ exercises │ session │ progress        │  ← UI + MVI
├──────────────────────────────────────────────────────────────────┤
│                domain:usecases                                   │  ← Business logic
├──────────────────────────────────────────────────────────────────┤
│  data:repository │ data:local │ data:routines │ data:models       │  ← Data layer
├──────────────────────────────────────────────────────────────────┤
│  core:common │ ui │ units │ datastore │ logging │ testing          │  ← Shared foundations
└──────────────────────────────────────────────────────────────────┘
```

## Layers (dependency direction: up → down)

| Layer | Modules | Can depend on |
|-------|---------|---------------|
| App   | `composeApp`, `iosApp` | feature, domain, data, core |
| Feature | `feature:*` | `domain:usecases` (re-exports `data:models` and the `data:repository` interfaces), `core:*` |
| Domain | `domain:usecases` | `data:models`, `data:repository` (interfaces), `core:common` |
| Data | `data:repository`, `data:local`, `data:routines`, `data:models` | `data:*` below them, `core` |
| Core | `core:*` | other `core` modules only (e.g. `core:datastore` → `core:units`) |

## Key patterns

### MVI
Every feature module has:
- `*State : MVIBaseState` — immutable data class
- `*Intent : MVIBaseIntent` — sealed interface
- `*ViewModel : MVIBaseViewModel<S, I>(initialState)` — handles intents, emits state

State mutations happen via `updateState { reducer }`. Reducers must be pure: no suspend calls and no side effects (they may run more than once).
Coroutine work happens inside `execute { suspend block }`.
**Never** call suspend functions inside the `updateState {}` lambda.

### Expect / Actual
Platform-specific implementations live under `androidMain` (Android SDK) and
`iosMain` (Kotlin/Native + Darwin). Current `expect` class: `DatabaseDriverFactory`.

### Dependency Injection
[Koin 4.x](https://insert-koin.io/) — DSL-based, KMP-native, no annotation processing.
Every shared module is listed in `appModules` (`composeApp/src/commonMain/.../di/AppModule.kt`): `appModule`, `datastoreModule`, `repositoryModule`, `useCaseModule` and one module per feature. The platform `DatabaseDriverFactory` is bound in `IronlogApplication` (Android) and `startIronlogKoin()` (iOS, called once from `iOSApp.init()`).

### Repositories
`data:repository` exposes interfaces only; the SQL implementations are `internal` and bound in `repositoryModule`. Reads are `Flow`s that re-emit after any write to their tables; writes are `suspend` and run on `Dispatchers.IO` in one transaction. Ids of user rows are UUID strings (user exercises: `user-<uuid>`); loads are grams (`Long`); times are epoch milliseconds.

### Navigation
navigation-compose with type-safe `@Serializable` routes. Each feature exposes its route objects and a `NavGraphBuilder.<name>Graph(...)` entry point; the shell (`MainShell`) owns the `NavHost`, the bottom bar and the top bar. Cross-feature navigation goes through lambdas passed by the shell (see MODULE_MAP.md).

### Local database
[SQLDelight 2.x](https://sqldelight.github.io/) generates type-safe Kotlin from `.sq` files in
`data/local/src/commonMain/sqldelight/`. The database file is `ironlog.db`, with foreign keys
enabled. See AGENTS.md "Changing the database schema".

## Convention plugins

All build config lives in `build-logic/` so module `build.gradle.kts` files stay small:

| Plugin ID | Use for |
|-----------|---------|
| `ironlog.kmp.library` | Any library module |
| `ironlog.kmp.feature` | Feature modules (+ Compose) |
| `ironlog.kmp.data` | Data modules (SQLDelight applied per module) |
| `ironlog.kmp.domain` | Domain/use-case modules |
| `ironlog.kmp.core` | Core modules |
| `ironlog.kmp.app` | composeApp |
| `ironlog.detekt` | Detekt (applied by the conventions above) |
