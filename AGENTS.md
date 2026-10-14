# Agent Operating Contract

This file defines rules for anyone (people or AI agents) contributing to this repository.

## Language

**Everything in this repository is written in English.** This includes:

- All documentation: `README.md`, `docs/` (designs, plans, ADRs, research, guides), this file
- Code: identifiers, comments, KDoc, log messages, test names
- Data files and scripts (`catalog/`, `routines/`, `scripts/`): keys, comments, docs
- Commit messages, branch names, pull request titles and descriptions, issues, review comments

The only exception is **user-facing translated content**: the app UI ships in English
(default) and Spanish, so Spanish text lives only in localisation resources
(`values-es/strings.xml`) and in the `es` fields of catalogue/routine data.
Conversations with the owner may be in Spanish; anything committed is in English.

## Design documents

- Specs: `docs/designs/YYYY-MM-DD-<topic>-design.md`
- Core spec: `docs/designs/2026-10-14-ironlog-core-design.md`

## Must-read before any change

1. `ARCHITECTURE.md` — layers, patterns, what depends on what
2. `MODULE_MAP.md` — which module holds what

## Non-negotiable rules

- **Never put suspend calls inside the `updateState { }` lambda** — it is a non-suspend `(S) -> S`
- **Never skip the `execute { }` wrapper** — all coroutine work in ViewModels goes through it
- **Never import across forbidden layer boundaries** — a feature cannot import another feature; domain cannot import data implementations
- **Always use convention plugins** — do not copy-paste `kotlin { }` / `android { }` blocks into module builds
- **Detekt must pass** — run `./gradlew detekt` before marking a task done (it autocorrects formatting locally; CI only reports)

## Changing the database schema

The schema lives in `.sq` files under `data/local/src/commonMain/sqldelight/`.
`data/local/src/commonMain/sqldelight/databases/1.db` is the schema snapshot that
`verifySqlDelightMigration` checks against.

- **Until the first release (F5):** edit the `.sq` files in place and regenerate `1.db`
  whenever a `.sq` file changes, with `./gradlew :data:local:generateCommonMainAppDatabaseSchema`
  run on its own (not combined with other tasks). Commit the new `1.db`.
- **From F5 on:** never edit a released schema; add `n.sqm` migrations, regenerate the snapshot
  with the same command and run `./gradlew verifySqlDelightMigration`.
- Android 7 ships SQLite 3.9: no `UPSERT` (use `INSERT OR REPLACE`), booleans are `INTEGER` 0/1.
- Foreign keys are enabled on both drivers (`DatabaseDriverFactory`).

## Environment

Toolchain, environment variables and pinned versions: [docs/plans/README.md](docs/plans/README.md)
(sections "Environment" and "Pinned versions"). Targets: Android (minSdk 24) and iOS
(`iosArm64`, `iosSimulatorArm64`); no desktop target. DI: Koin. DB: SQLDelight.

## Required checks before finishing

```sh templetry:checks
./gradlew detekt testDebugUnitTest :composeApp:assembleDebug
```

## This project came from a template

Four facts you cannot infer from the code in front of you:

- **Never hand-edit `.templetry-answers.yml`.** It records what generated this project. Editing it makes the next update merge against a state that never existed.
- **Before writing a capability by hand, run `templetry pieces`.** Auth, RBAC, audit trails, API keys and whole CRUD resources may already exist as pieces for this template. Adopting one is `templetry add <name>`, and it brings its own tests.
- **`templetry update` pulls improvements from the template** through a three-way merge that keeps your edits. Use it instead of copying files from the template by hand.
- **Directives like `tpl:if` belong to the template, not here.** If you find one in this project, it is a rendering bug worth reporting — do not try to interpret it.
