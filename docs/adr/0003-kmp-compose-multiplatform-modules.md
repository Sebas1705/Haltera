# ADR 0003: Kotlin Multiplatform with Compose Multiplatform and a layered module graph

- **Status:** accepted
- **Date:** 2026-10-14 (core design and F1a scaffold)
- **Area:** `settings.gradle.kts`, `build-logic/`, `ARCHITECTURE.md`, `MODULE_MAP.md`

## Context

Haltera targets Android and iOS from one codebase (core design §1-§2). The design says it uses "the same proven
stack as Owlsplit", started fresh from the Templetry `kmp/modular-features` template, and ports Owlsplit pieces
only when a phase needs them. Compose Multiplatform was chosen over native UIs for one shared UI: a single developer maintains the app, and one
interface costs far less to keep up than two.

## Decision

- Kotlin Multiplatform for Android and iOS, with Compose Multiplatform for the shared UI, Koin for dependency
  injection, navigation-compose with type-safe routes, SQLDelight for storage and Coil 3 for images.
- Clean Architecture as a layered module graph (`ARCHITECTURE.md`): `androidApp`/`iosApp` entry points over the
  shared `composeApp`; `feature:*` (MVI: state, intent, view model); `domain:usecases`; `data:*`; `core:*`.
  Dependencies only point down; features depend on `domain:usecases` (which re-exports `data:models` and the
  repository interfaces) and `core`, never on `data:local`.
- Build configuration lives in convention plugins in `build-logic/` (`haltera.kmp.library`, `.feature`, `.data`,
  `.domain`, `.core`, `haltera.android.app`, `haltera.detekt`), so module build files stay small.
- Repositories expose interfaces; the SQL implementations are `internal`. Reads are Flows, writes are
  transactional suspend functions.

## Consequences

- One UI and one set of business rules for both platforms; platform code is limited to `expect`/`actual` and the
  app modules.
- Cross-feature navigation goes through lambdas passed by the shell (`MODULE_MAP.md`).
- Reducers in view models must stay pure and suspend work goes through `execute` (`ARCHITECTURE.md`).
