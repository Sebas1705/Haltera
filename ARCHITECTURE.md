# Architecture

## Overview

Ironlog is a **Kotlin Multiplatform** app targeting **Android** and **iOS**.
It follows **Clean Architecture** with a layered module graph. F1 ships only the foundations;
feature modules (`feature:*`) arrive from F2 and network-backed data from F8.

```
┌──────────────────────────────────────────────────────┐
│                composeApp / iosApp                   │  ← Entry points
├──────────────────────────────────────────────────────┤
│        feature:*  (from F2)                          │  ← UI + MVI
├──────────────────────────────────────────────────────┤
│                domain:usecases                       │  ← Business logic
├──────────────────────────────────────────────────────┤
│                data:local                            │  ← Data layer
├──────────────────────────────────────────────────────┤
│  core:common │ core:ui │ core:logging │ core:testing │  ← Shared foundations
└──────────────────────────────────────────────────────┘
```

## Layers (dependency direction: up → down)

| Layer | Modules | Can depend on |
|-------|---------|---------------|
| App   | `composeApp`, `iosApp` | feature, domain, data, core |
| Feature (from F2) | `feature:*` | domain, data:models (from F1c), core |
| Domain | `domain:usecases` | data interfaces and models, core:common |
| Data | `data:local` | core |
| Core | `core:*` | nothing project-local |

## Key patterns

### MVI
Every feature module will have:
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
Shared modules are wired in `composeApp/src/commonMain/.../di/AppModule.kt`; the platform
`DatabaseDriverFactory` is bound in `IronlogApplication` (Android) and `startIronlogKoin()` (iOS,
called once from `iOSApp.init()`).

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
