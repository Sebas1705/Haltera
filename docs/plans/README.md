# Implementation plans

The core spec ([2026-10-14-haltera-core-design.md](../designs/2026-10-14-haltera-core-design.md)) is implemented in **chained plans**. Each plan leaves software that compiles, passes its tests and can be tried out. Plans after F1 are written in detail right before they are executed, because each depends on how the previous one turned out.

## Plans

| # | Plan | Verifiable deliverable | Depends on | Status |
|---|---|---|---|---|
| F1a | [Project scaffold and CI](2026-10-14-f1a-scaffold.md) | KMP project from Templetry `kmp/modular-features` aligned with the build pins validated in Owlsplit, template sample removed, app starts on Android, green CI (Android + iOS simulator) | — | Done |
| F1b | [Exercise catalogue pipeline](2026-10-14-f1b-catalog-pipeline.md) | `scripts/catalog` (Node): pinned free-exercise-db + wger Spanish names + own curation → `catalog.json` + `ATTRIBUTION.md`, with validation in CI | — (parallel to F1a) | Done |
| F1c | [Catalogue data layer and importer](2026-10-14-f1c-catalog-data.md) | `data:models`, SQLDelight catalogue schema, idempotent importer (upsert + deprecate), `data:repository` counts (startup screen superseded by the MVP shell) | F1a, F1b | Done |
| F2 | Exercises tab + brand/theme | Brand tokens and theme, Exercises tab (search, filters, detail with media, custom exercises), media download | F1 | MVP (first cut, 2026-10-14) |
| F3 | Routines | Routine editor and famous routines catalogue | F2 | MVP (first cut, 2026-10-14) |
| F4 | Live session | Today tab, session, timers, foreground service, progression engine | F3 | MVP (first cut, 2026-10-14) |
| F5 | Progress + release | Calendar, history, PRs, charts, settings; Doppler, signing and Play Alpha | F4 | MVP (first cut, 2026-10-14) |
| P1–P11 | [Post-launch roadmap](2026-10-16-post-launch-roadmap.md) | Export, import (Hevy/Strong/FitNotes), opt-in cloud copy, Health Connect, widgets, measurements, iOS scanners, watch decision, routine links | F5 (launched) | Planned |

Changes against the spec's phase table (§10), agreed when planning F1:

- **Brand tokens move from F1 to F2.** F1 has no real UI; the visual identity is decided with the first real screen.
- **Doppler moves from F1 to F5.** F1–F4 have no secrets (no Firebase, no signing); Doppler arrives with release signing.
- **F5 release infrastructure done early (2026-10-14).** Doppler `ironlog/prd`, the release keystore, release signing and versioning in Gradle, and Firebase App Distribution for every merge to `main` are already in place ([RELEASE.md](../RELEASE.md)). F5 keeps the Play Console app, Play App Signing, store listing and the Play upload workflow.

## Dependencies and parallelism

```
F1a ──┐
      ├── F1c ── F2 ── F3 ── F4 ── F5
F1b ──┘
```

F1a and F1b share no files except `.github/workflows/ci.yml` (F1b adds a job; integrate with rebase).

## Environment (SEBAS-PC)

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
```

The system JDK 26 does not work for Gradle/Kotlin. iOS does not build on Windows: it is built in CI (macOS). Node 22 is used for `scripts/`.

## Pinned versions

Same pins as Owlsplit (validated there between 2026-10-07 and 2026-10-14; see `Owlsplit/docs/plans/README.md` for the reasons):

| Component | Version | Note |
|---|---|---|
| Gradle | 9.5.1 | AGP 8 breaks with Gradle ≥ 9.6 |
| Kotlin | 2.4.20 | |
| Compose Multiplatform | 1.11.1 (material3 1.9.0) | 1.12 requires compileSdk 37 |
| Android Gradle Plugin | 8.13.2 | compileSdk ≤ 36 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |
| SQLDelight | 2.4.1 | Android 7 ships SQLite 3.9: no `UPSERT` |
| Koin | 4.2.2 | |
| Lifecycle (JetBrains) / Navigation | 2.10.0 / 2.9.2 | 2.11 requires compileSdk 37 |
| Coil | 3.5.0 | 3.6 requires compileSdk 37 |
| kotlinx coroutines / serialization / datetime | 1.11.0 / 1.11.0 / 0.8.0 | |
| Detekt | 1.23.8 | Its Kotlin pinned to 2.0.21 |

## MVP first cut (2026-10-14)

F2–F5 were built in one push, without per-phase plans, to get a first build to testers through Firebase App Distribution: a shared foundation (models, full schema, repositories, progression/cycle/stats, famous routines, app shell and theme) followed by the five feature modules in parallel (`today`, `routines`, `exercises`, `session`, `progress`). Each feature reports its known limitations in its PR description; follow-up plans (F2b…F5b) pick those up together with the remaining spec items (media archive download, brand/icon, "save changes to routine", exact-alarm settings, Play release).
