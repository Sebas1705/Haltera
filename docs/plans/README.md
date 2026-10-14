# Implementation plans

The core spec ([2026-10-14-ironlog-core-design.md](../designs/2026-10-14-ironlog-core-design.md)) is implemented in **chained plans**. Each plan leaves software that compiles, passes its tests and can be tried out. Plans after F1 are written in detail right before they are executed, because each depends on how the previous one turned out.

## Plans

| # | Plan | Verifiable deliverable | Depends on | Status |
|---|---|---|---|---|
| F1a | [Project scaffold and CI](2026-10-14-f1a-scaffold.md) | KMP project from Templetry `kmp/modular-features` aligned with the build pins validated in Owlsplit, template sample removed, app starts on Android, green CI (Android + iOS simulator) | — | Done |
| F1b | [Exercise catalogue pipeline](2026-10-14-f1b-catalog-pipeline.md) | `scripts/catalog` (Node): pinned free-exercise-db + wger Spanish names + own curation → `catalog.json` + `ATTRIBUTION.md`, with validation in CI | — (parallel to F1a) | Planned |
| F1c | [Catalogue data layer and importer](2026-10-14-f1c-catalog-data.md) | `data:models`, SQLDelight catalogue schema, idempotent importer (upsert + deprecate), `data:repository` counts, startup screen showing the imported catalogue | F1a, F1b | Planned |
| F2 | Exercises tab + brand/theme | Brand tokens and theme, Exercises tab (search, filters, detail with media, custom exercises), media download | F1 | — |
| F3 | Routines | Routine editor and famous routines catalogue | F2 | — |
| F4 | Live session | Today tab, session, timers, foreground service, progression engine | F3 | — |
| F5 | Progress + release | Calendar, history, PRs, charts, settings; Doppler, signing and Play Alpha | F4 | — |

Changes against the spec's phase table (§10), agreed when planning F1:

- **Brand tokens move from F1 to F2.** F1 has no real UI; the visual identity is decided with the first real screen.
- **Doppler moves from F1 to F5.** F1–F4 have no secrets (no Firebase, no signing); Doppler arrives with release signing.

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
