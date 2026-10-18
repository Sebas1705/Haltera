# Post-launch roadmap (P1–P11)

- **Date:** 2026-10-16
- **Source:** market study of 2026-10-16 (private deliverable, not committed). Conclusion: Haltera does not need more logging depth, it needs the layer that makes switching to it painless (import, export, health integrations, watch) and consistency between what it says (private, local-first) and what it does (automatic cloud copy).
- **Positioning this plan serves:** free, open source, no ads, a logbook (never a coach) with nutrition included, in Spanish and English; "your data is yours and exportable".
- **Constraints that apply to every item:** the product principle in AGENTS.md (logbook, never enforce a routine); English-only repo; every shipped schema change needs `n.sqm` + snapshot + migration test; every new data type updates `docs/play/DATA-INVENTORY.md`, the Data safety form and the privacy pages; UI strings in `values/strings.xml` and `values-es/strings.xml`; CI green before merge; each item ships behind its own PR.

Effort sizes are estimates (S ≤ 1 day, M 2–4 days, L 1–2 weeks) from the architecture, not from sources.

## Milestones

| Milestone | Items | Goal | When |
|---|---|---|---|
| **M1 – Trust and portability** | P1 export, P2 import, P3 opt-in cloud | A user can leave and arrive freely; the privacy claim is true | Before leaving the closed test (12 testers × 14 days) |
| **M2 – Ecosystem** | P4 Health Connect → HealthKit, P5 widgets, P6 body measurements and photos, P7 iOS scanners | Parity with what the category treats as expected | The quarter after M1 |
| **M3 – Growth and decisions** | P8 watch (decision gate), P9 routine links | Switching and word of mouth | After measuring tester demand |
| **Later** | P10 optional explained AI, P11 support/donations | Only if evidence appears | Not scheduled |

```
P1 ──┬── P2
     └── P3 ── P4 ── P6
P5, P7 independent        P8 after P4      P9 after P1
```

P1 and P3 are independent and can run in parallel. P2 reuses the format and tests of P1.

---

## M1 – Trust and portability

### P1. Export everything (CSV and JSON) — impact high, effort S–M

**Why.** Table stakes in the category; the proof behind "your data is yours"; precondition for P2's round-trip tests.

**Scope.**
- Settings → Data → **Export my data**: one file (`haltera-export-YYYY-MM-DD.zip`) with `sessions.csv` (one row per set: date, session, exercise, variant, set number, reps, load grams, unit shown, RPE if present, notes), `body_weight.csv`, `meal_entries.csv`, `routines.json` (existing `.haltera` routine format, one per routine) and a `manifest.json` (app version, schema version, counts). CSV columns are stable and documented in `docs/EXPORT-FORMAT.md`.
- Always in kg/grams with a `unit` column so the file does not depend on the display unit.
- Android: system share / save-to-file (`ACTION_CREATE_DOCUMENT`); iOS: share sheet. Behind an `expect/actual` `FileExporter` in `core:common` or a data module; the formatting lives in common code and is unit-tested.
- Does not export the bundled catalogue; exports user exercises and the variant ids that reference the catalogue by stable id.

**Design points.** One reader per repository (`SessionRepository`, `BodyWeightRepository`, `NutritionRepository`, `RoutineRepository`), streaming rows so a large history does not load into memory. No schema change.

**Tests.** Golden-file test of every CSV; round-trip test against P2's parser; large-history test (10 000 sets); Spanish/English locale does not change the file (decimal point, ISO dates).

**Docs.** `docs/EXPORT-FORMAT.md`; privacy pages mention the export; README feature list.

**Done when.** Exporting on both platforms produces a file that opens in a spreadsheet with correct columns, and the golden tests pass.

### P2. Import from Hevy, Strong, FitNotes (and Haltera) — impact high, effort M

**Why.** Removes the main barrier to switching; competitors (Liftin', openGym) already offer it; growth in this market is switching, not new downloads.

**Scope.**
- Settings → Data → **Import**: pick a CSV or a Haltera export; the app detects the source by header; shows a preview (sessions, sets, date range, unmatched exercises) before writing anything.
- Parsers (common code, pure functions): Hevy CSV, Strong CSV, FitNotes CSV, Haltera export. Units converted to grams on import; dates kept as in the file (logbook principle: any date).
- **Exercise matching.** Normalised name (accent-insensitive, `nameSearch`) against catalogue names and aliases; unmatched names are listed once and the user chooses *map to…* (search) or *create as custom exercise*. The mapping is remembered during the import only.
- Idempotent: each imported session gets a deterministic id from the source, date and start time, so importing the same file twice does not duplicate. Re-import offers "skip existing".
- Imported sessions are marked `source = IMPORT` only in the document payload if the model already allows it; otherwise no schema change. The sync triggers fire normally, so imported data reaches the cloud like any other data (only if P3's cloud copy is on).

**Sample files.** Anonymised fixtures per source under `data/repository/src/commonTest/resources/import/`, created from the apps' documented formats; real exports from the owner's own accounts are never committed.

**Tests.** Parser tests per source (including locale commas, supersets, warm-up sets, bodyweight and assisted exercises); matcher tests; idempotency test; round-trip with P1; performance with 50 000 rows.

**Risks.** Third-party CSV formats change without notice → parsers fail loudly with "unrecognised format" and the preview, never partial writes. Competitor terms of use are not affected (the user imports their own data).

**Done when.** Importing a fixture of each source gives the expected sessions, sets and body-weight entries, twice in a row without duplicates.

### P3. Cloud copy opt-in — impact high, effort S–M

**Why.** `docs/play/DATA-INVENTORY.md` (finding 1): the anonymous sign-in and the full upload happen automatically on first launch, including sex, birth year and height. That contradicts a privacy-first message, sits badly with GDPR article 9 for health data, and the Play listing and Data safety already had to say "automatic".

**Decision to make before building (owner):**
1. **Opt-in at first launch** (recommended): a short onboarding card "Back up your logbook to a private cloud space? Yes / Not now" with a plain-language line about what is stored. Default "Not now".
2. Keep automatic but explain it on first launch (cheaper, weaker).

**Scope for option 1.**
- `SyncStarter` no longer calls `ensureSignedIn()` until the user opts in (Settings → Account → **Back up to the cloud**, also reachable from the onboarding card). Local data created before opting in is uploaded when they opt in (`SyncEngine.markAllDirty()` already exists).
- Existing installs: users who already have a signed-in account keep syncing (a one-time notice explains the change and the switch). Do not silently stop syncing for anyone.
- Turning it off: stop sync, keep local data, offer "also delete my cloud data" (the existing delete path).
- Sensitive profile fields (sex, birth year, height) stay local unless the cloud copy is on; the privacy page and the Data safety form are updated.

**Tests.** `SyncStarter` unit tests (no network before opt-in; upload after opt-in; existing-user path); migration of the `UserSettings` flag; UI test of the onboarding card in both languages.

**Docs.** `DATA-INVENTORY.md`, `docs/FIRESTORE-DATA.md`, store descriptions (the line added in PR #28 changes again), the four hosting pages, Data safety answers (collection becomes optional), this must ship together.

**Done when.** A fresh install makes zero Firebase calls until the user opts in, and all texts say the same thing.

---

## M2 – Ecosystem

### P4. Health Connect, then HealthKit — impact high, effort M

**Scope.** Android first: write completed workouts (exercise sessions with duration; sets as segments when the API allows) and read/write body weight. User picks the direction per data type in Settings → Integrations; nothing is requested until the user taps *Connect*. HealthKit afterwards, behind the same `HealthGateway` interface (`expect/actual`).

**Needs.** Play *health apps* declaration update (Health Connect permission rationale and declaration form), privacy-policy section on health data, Data safety review, permission rationale activity, tests with the Health Connect test harness. The app's `HealthDisclaimer` wording stays.

**Why after P3.** Health Connect reads count as sensitive data; the privacy story must be consistent first.

**Done when.** A session finished in Haltera appears in Health Connect and body weight round-trips without duplicates; revoking the permission in system settings stops all writes without errors.

### P5. Widgets and session shortcuts — impact medium, effort M

Android Glance widget: "Start today's workout" and "Resume session" with the running rest countdown; app shortcuts (long-press launcher icon). iOS later: Live Activity for the rest timer (already planned in the core spec) and a small widget. No new data; reads the active session and routine day through existing repositories.

**Done when.** The widget starts or resumes a session from the home screen on a physical device and an emulator, and respects the time-zone and cycle logic of `Cycle`.

### P6. Body measurements and progress photos — impact medium, effort S–M

- **Measurements:** new `BodyMeasurement` entity (date, type from a fixed list: waist, chest, hips, arm, thigh, neck, body fat %, custom; value in mm or tenths of percent), charts reusing the body-weight chart component. Schema change → `n.sqm` + snapshot + migration test + sync collection `bodyMeasurements` (rules already cover `users/{uid}/**`).
- **Photos:** stored on device only (app-private storage), never synced in this phase; one-tap compare of two dates. No camera permission is required if the system photo picker is used. Data safety does not change because nothing leaves the device; document it in `DATA-INVENTORY.md`.

**Done when.** A measurement round-trips through sync (when on) and a photo pair can be compared offline.

**Status (2026-10-18).** Built: schema 9 (`BodyMeasurementEntity`, `migrations/8.sqm`), sync collection
`bodyMeasurements`, export format 3 (`body_measurements.csv`), Progress → Body (Weight / Measurements / Photos).
Photos are files in app-private storage, outside the database, the backups, the export and the cloud. iOS picks
photos with PHPickerViewController (compiled in CI, not run on a device yet).

### P7. Finish iOS: QR and barcode scanners — impact medium, effort M

Replace the iOS stubs with AVFoundation (or VisionKit `DataScannerViewController`) scanners behind the existing interfaces so routine QR import and Open Food Facts barcode lookup work on iOS. Nutrition by barcode on iOS is otherwise half a product.

**Done when.** The same QR and barcode flows pass on a physical iPhone; CI keeps compiling the iOS target.

---

## M3 – Growth and decisions

### P8. Wear OS and Apple Watch — impact high, effort L (decision gate)

Do not start until the closed test shows demand. **Gate:** ask the testers (feedback channel) and count requests; start only if a clear share asks for a watch. A mediocre watch app does more damage than none (watch sync failures are the most-cited complaint against Strong). If it goes ahead: a minimal companion first (see current exercise, log a set, rest countdown), reading the active session through a small sync channel, with offline-safe behaviour.

### P9. Share routines by link — impact medium, effort M

Public, read-only routine links (e.g. `haltera.web.app/r/<code>`) that open the app (App Links / Universal Links) or show a web preview with an "Open in Haltera" and a download button. The existing share code (base45 v2) is the payload; no feed and no accounts. Needs a static page on Hosting, `assetlinks.json` using the Play-signed certificate (SHA-256 `45:53:E7:BB…F2:13:C9:7B`), and a privacy review (a routine contains no personal data).

---

## Later

### P10. Optional, explained AI — only on evidence

Off by default, bring-your-own-key, never changes a routine automatically, always shows why. Conflicts with "logbook, not coach" and with user distrust of opaque AI, so it needs real demand first.

### P11. Support / donations — only if costs require it

GitHub Sponsors or Open Collective link in About, never blocking anything. Revisit if Firebase cost leaves the Spark plan.

---

## Also pending from the launch (not roadmap items)

- 12 testers for 14 consecutive days in the closed test, then apply for production.
- Native debug symbols upload warning in Play Console.
- Back up `ironlog-release.jks` off the PC.
- Dependabot PRs held until compileSdk 37.
- Decide whether to keep `SCHEDULE_EXACT_ALARM` if Play questions it.

## Suggested order of work

1. **Now, in parallel:** P1 (export) and P3 (cloud opt-in decision, then build). Both independent.
2. **Then:** P2 (import), reusing P1's format.
3. **Release as `1.0.0-alpha.3`** (or `beta.1`) with M1 and measure tester feedback.
4. **M2** in the order P4 → P6, with P5 and P7 slotted in parallel by whoever is free.
5. **Decide P8** from tester feedback; P9 once P1 is stable.
