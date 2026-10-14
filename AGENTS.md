# Agent Operating Contract

This file defines rules for anyone (people or AI agents) contributing to this repository.

## Language

**Everything in this repository is written in English.** This includes:

- All documentation: `README.md`, `docs/` (designs, plans, ADRs, research, guides), this file
- Code: identifiers, comments, KDoc, log messages, test names
- Data files and scripts (`catalog/` and `scripts/` from F1b, `routines/` from F3): keys, comments, docs
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
2. `MODULE_MAP.md` — which module holds what, and the feature module contract (entry points, routes, Koin modules)

## Non-negotiable rules

- **Never put suspend calls inside the `updateState { }` lambda** — reducers must be pure: no suspend calls and no side effects (they may run more than once)
- **Never skip the `execute { }` wrapper** — all coroutine work in ViewModels goes through it
- **Never import across forbidden layer boundaries** — a feature cannot import another feature; domain cannot import data implementations
- **Always use convention plugins** — do not copy-paste `kotlin { }` / `android { }` blocks into module builds
- **Feature agents edit only inside their `feature:<name>` module** — the shell calls each feature through its fixed entry points (MODULE_MAP.md); changing a signature means changing `composeApp` in the same PR
- **Every UI string is a resource** — English in `composeResources/values/strings.xml`, Spanish in `values-es/strings.xml`, same keys; no hard-coded user-facing text
- **Loads are grams (`Long`), times epoch millis** — convert only for display (`core:units`)
- **Secrets live only in Doppler** (project `ironlog`, config `prd`), never in the repository: no keystores, passwords, service account keys or tokens in code, docs, scripts or commits. GitHub only holds `DOPPLER_TOKEN` (read-only). See [docs/RELEASE.md](docs/RELEASE.md)
- **Detekt must pass** — run `./gradlew detekt` before marking a task done (it autocorrects formatting locally; CI only reports)
- **The brand has one source, `brand/tokens.json`** — never hand-edit generated icons, the splash theme or `brand/*.svg`; change the tokens and run `node scripts/brand.mjs --png` (see [docs/BRAND.md](docs/BRAND.md)). Features use `IronlogTheme.colors` (accent, PR gold) and `PrBadge` from `core:ui` instead of new colours

## Changing the database schema

The schema lives in `.sq` files under `data/local/src/commonMain/sqldelight/`.
`data/local/src/commonMain/sqldelight/databases/n.db` are the snapshots of each shipped schema version and
`migrations/n.sqm` upgrade version n to n + 1; `verifySqlDelightMigration` checks that they agree.

- **The schema has shipped** (testers have real data), so **every schema change needs a migration**:
  add `migrations/n.sqm` (n = the current version, e.g. `1.sqm` upgrades 1 → 2), update the `.sq` files to
  match (columns added by `ALTER TABLE` go last in the `CREATE TABLE`), regenerate the snapshot with
  `./gradlew :data:local:generateCommonMainAppDatabaseSchema` run on its own (not combined with other
  tasks), which writes `n+1.db`, and make `./gradlew verifySqlDelightMigration` pass. Add a migration test
  (see `MigrationTest`: open a copy of `n.db`, insert data, migrate, check it survived).
- **Never edit or regenerate a shipped snapshot** (`1.db`, `2.db`, …): commit only the new one.
  Query-only changes (new or edited queries without table changes) need no migration.
- Android 7 ships SQLite 3.9: no `UPSERT` (use `INSERT OR REPLACE`), booleans are `INTEGER` 0/1.
- Foreign keys are enabled on both drivers (`DatabaseDriverFactory`); repository tests use an in-memory JDBC driver with `foreign_keys=true`.
- Enum columns are read leniently (`fromDb`): unknown values fall back to a default. JSON columns (slot target, prescription, progression) go through `ModelJson`.

## Environment

Toolchain, environment variables and pinned versions: [docs/plans/README.md](docs/plans/README.md)
(sections "Environment" and "Pinned versions"). Targets: Android (minSdk 24) and iOS
(`iosArm64`, `iosSimulatorArm64`); no desktop target. DI: Koin. DB: SQLDelight.

## Required checks before finishing

```sh templetry:checks
./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug
```

After changing `brand/` or `scripts/brand.mjs`, also run `node scripts/brand.mjs --check`.

## This project came from a template

Four facts you cannot infer from the code in front of you:

- **Never hand-edit `.templetry-answers.yml`.** It records what generated this project. Editing it makes the next update merge against a state that never existed.
- **Before writing a capability by hand, run `templetry pieces`.** Auth, RBAC, audit trails, API keys and whole CRUD resources may already exist as pieces for this template. Adopting one is `templetry add <name>`, and it brings its own tests.
- **`templetry update` pulls improvements from the template** through a three-way merge that keeps your edits. Use it instead of copying files from the template by hand.
- **Directives like `tpl:if` belong to the template, not here.** If you find one in this project, it is a rendering bug worth reporting — do not try to interpret it.
