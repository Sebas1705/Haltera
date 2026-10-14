# Ironlog — core design

- **Date:** 2026-10-14
- **Status:** approved in brainstorming, pending written review
- **Scope:** full product vision; detailed design for the MVP phases F1–F5. Phases F6–F9 are summarised here and get their own spec when they start.

## 1. Product

Ironlog is a free gym app (Android + iOS, Play Store / App Store, no monetisation) for logging and following training routines with as little friction as possible:

- Create, edit, share and import routines whose cycle can be any number of days (a classic week or an arbitrary N-day cycle).
- Log what was trained each day on a calendar: which routine day, duration, sets, reps and load, with load entry adapted to the equipment (barbell + plates, dumbbells, machines/cables, bodyweight ± load, bands).
- Automatic timers for rest periods and timed sets.
- Exercise catalogue covering every kind of training (strength, cardio, mobility, plyometrics, calisthenics, sports), each exercise documented with instructions and media.
- Double progression: when every working set reaches the top of the rep range for X sessions in a row at the same load, the app suggests the next load. X, sets, rep range and increment are configured per routine slot.
- An exercise can exist as a base exercise with equipment variants (bench press → barbell, dumbbell, machine…); a routine slot can pin a variant or stay flexible and let the user choose on the day depending on available equipment.
- A catalogue of well-known routines to copy and adapt.
- Later: body weight, meals, calories and macros with statistics over time; account, sync and sharing.

UI languages: Spanish and English. Code, docs, commits and PRs in English.

## 2. Platform and stack

Same proven stack as Owlsplit, started fresh from the Templetry `kmp/modular-features` template (Owlsplit pieces are ported only when a phase needs them: outbox sync, `brand.mjs`, release workflows, download page).

| Concern | Choice |
|---|---|
| Platforms | Kotlin Multiplatform, Android + iOS, Compose Multiplatform UI |
| Architecture | MVI per feature, Koin DI, features only depend on `domain`, `data:models` and `core` |
| Persistence | SQLDelight, local-first: the app is fully usable offline and without an account |
| Remote (F8) | Firebase via GitLive (anonymous auth + Google, Firestore), own outbox sync ported from Owlsplit; Spark plan (no Cloud Functions needed) |
| Images | Coil 3 |
| Secrets / CI | Doppler, GitHub Actions, Play internal/alpha track via CI as in Owlsplit |
| Package | `es.sebas1705.ironlog`, repo `Sebas1705/Ironlog` |

## 3. Domain model

### 3.1 Catalogue

**`Exercise`** — the base exercise ("Bench press").

| Field | Notes |
|---|---|
| `id` | Stable string id (`bench-press`), never reused |
| `name` | `{es, en}` |
| `category` | `STRENGTH`, `CARDIO`, `MOBILITY`, `PLYOMETRICS`, `CALISTHENICS`, `SPORT` |
| `level` | `BEGINNER`, `INTERMEDIATE`, `EXPERT` |
| `primaryMuscles`, `secondaryMuscles` | Fixed muscle enum: `ABDOMINALS`, `ABDUCTORS`, `ADDUCTORS`, `BICEPS`, `CALVES`, `CHEST`, `FOREARMS`, `GLUTES`, `HAMSTRINGS`, `LATS`, `LOWER_BACK`, `MIDDLE_BACK`, `NECK`, `QUADRICEPS`, `SHOULDERS`, `TRAPS`, `TRICEPS`, `FULL_BODY` (whole-body work: sports, swimming, burpees) |
| `instructions` | `{es, en}` list of steps |
| `trackingType` | What a set records — see below |
| `source` | `CATALOG` or `USER` |

`trackingType`:

| Value | A set records | Examples |
|---|---|---|
| `WEIGHT_REPS` | load + reps | Bench press, squat |
| `BODYWEIGHT_REPS` | reps + optional added (+) or assisting (−) load | Pull-ups, dips |
| `REPS` | reps | Burpees, jumping jacks |
| `TIME` | duration | Plank, stretching hold |
| `WEIGHT_TIME` | load + duration | Farmer's walk |
| `DISTANCE_TIME` | distance + duration | Running, rowing |

**`ExerciseVariant`** — the exercise performed with specific equipment. Every exercise has at least one variant (an equipment-free exercise has a single `NONE`/`BODYWEIGHT` variant).

| Field | Notes |
|---|---|
| `id` | Stable (`bench-press.barbell`) |
| `exerciseId` | Parent base exercise |
| `equipment` | `BARBELL`, `EZ_BAR`, `TRAP_BAR`, `DUMBBELL`, `KETTLEBELL`, `SMITH`, `MACHINE`, `CABLE`, `BAND`, `BODYWEIGHT`, `MEDICINE_BALL`, `SWISS_BALL`, `FOAM_ROLLER`, `CARDIO_MACHINE`, `OTHER`, `NONE` |
| `loadType` | How load is entered — see below |
| `media` | Media references (see 6.4) |
| `name` | Optional `{es, en}` override; default is "<exercise> (<equipment>)" |

`loadType`:

| Value | Entry | Stored total |
|---|---|---|
| `BARBELL` | Bar weight (configurable: 20/15/10 kg, EZ, custom) + plates per side, or total with plate breakdown shown | bar + 2 × plates |
| `DUMBBELL` | Weight of one implement (dumbbell, kettlebell, medicine ball); `bilateral` flag on the slot doubles volume | one implement |
| `MACHINE` | Either kg or stack position ("plate #7"), chosen per variant by the user; history is per variant so different machines never mix | kg, or plate index |
| `BODYWEIGHT` | Added (+) or assisting (−) load | signed load |
| `BAND` | Band level (user-defined labels/colours) | level id |
| `NONE` | Nothing | — |

Users can create their own exercises and variants (`source = USER`); they behave exactly like catalogue ones.

### 3.2 Routines

**`Routine`**: name, description, `cycle: List<RoutineDay>` (ordered, length N ≥ 1), origin (`USER` or copied from a catalogue routine id + version), `isActive`. Exactly one routine can be active; it drives the "Today" tab.

**`RoutineDay`**: position, name ("Push", "Legs"), `kind` (`TRAINING` or `REST`), ordered `slots`.

**`RoutineSlot`**:

| Field | Notes |
|---|---|
| `target` | `Fixed(variantId)` or `Flexible(exerciseId, allowedVariantIds?)` — flexible means the variant is chosen during the session |
| `sets` | Number of working sets; optional warm-up set count |
| `prescription` | `RepRange(min, max)`, `Reps(n)`, `Duration(s)`, `Distance(m)` — consistent with the exercise `trackingType` |
| `restBetweenSets`, `restAfter` | Seconds |
| `supersetGroup` | Optional; slots sharing a group alternate sets and rest only after the round |
| `bilateral` | For dumbbells: volume counts both hands |
| `notes` | Free text |
| `progression` | `ProgressionRule` |

**`ProgressionRule`** (sealed):

- `None`
- `DoubleProgression(sessionsAtTop: Int, increment: LoadIncrement)` — default `sessionsAtTop = 1`.
- `Linear(increment: LoadIncrement, everyNSessions: Int)` — for StrongLifts-type programmes.
- Reserved for F9: `PercentOfTrainingMax(...)` (5/3/1, nSuns, PHAT). The sealed hierarchy and the storage (rule type + JSON params) are designed so adding a rule needs no schema migration of existing rows.

`LoadIncrement` defaults by `loadType`: barbell +2.5 kg, dumbbell +2 kg, machine +1 plate (or +5 kg), bodyweight +2.5 kg added; always editable per slot.

### 3.3 Logging

**`WorkoutSession`**: id, `startedAt`, `endedAt?` (null while in progress), `routineId?` + `routineDayIndex?` (null for a free session), notes, `bodyWeight?` (filled in F6).

**`SessionExercise`**: order, `variantId` actually used, `slotId?` it came from (null if added ad hoc), notes.

**`SetLog`**: order, `kind` (`WARMUP`, `WORKING`, `DROP`, `FAILURE`), `reps?`, `load?`, `durationS?`, `distanceM?`, `rpe?`, `completed`, `completedAt?`.

**Load storage**: `load` is a normalised value — grams as `Long` for weight (no floating point), plus a `loadDetail` JSON with what was entered (bar id + plates per side, plate index, band level, sign for assistance) so it is shown back exactly as typed. Unit preference (kg/lb) only affects display and entry.

The calendar is derived from `WorkoutSession` rows; there is no calendar table.

### 3.4 Cycle position

Sequential, never date-anchored: the next suggested day is the day after the last *completed* training day of the active routine, skipping `REST` days (rest days are informational — "today the plan says rest"). The user can always pick a different day or start a free session. Completing day k sets the pointer to k + 1 (mod N). Changing a routine's structure clamps the pointer to a valid day.

### 3.5 Progression engine

Pure function in `domain:usecases/progression`, fully unit-tested:

```
suggest(slot, history: sessions of (same slot, same variant), latest first) -> Suggestion
Suggestion = Keep(load) | Increase(from, to, reason) | FirstTime
```

`DoubleProgression(X)`: take the last X sessions of that slot with that variant. If there are X of them, they all used the same load, and every `WORKING` set in each reached `prescription.max` (or the target duration/distance), suggest `load + increment`. Otherwise keep the last load. Warm-up and drop sets are ignored. Sessions done with a different variant (e.g. dumbbells instead of barbell in a flexible slot) do not count towards, nor reset, the other variant's streak.

`Linear(inc, n)`: after every n sessions in which all working sets hit the target, add `inc`.

Suggestions are never applied silently: the next session prefills the suggested load with a "↑" marker; the user accepts by simply logging it or edits it.

## 4. Modules

| Module | Contents |
|---|---|
| `composeApp` | Android/iOS entry, root DI, navigation with 4 tabs (Today · Routines · Exercises · Progress), live-session service bridge |
| `core:common` | MVI base, `AppResult`, utilities |
| `core:ui` | Ironlog theme from `brand/tokens.json`, load keypad, set row, components, Roborazzi screenshots |
| `core:units` | `Load` (grams `Long`), kg↔lb, rounding to available plates, plate calculator |
| `core:datastore` | Preferences (units, bar weights, available plates, keep-screen-on, sounds) |
| `core:logging`, `core:testing` | As in Owlsplit (fakes, in-memory repositories, `assertAccessible()`) |
| `data:models` | Entities in §3 |
| `data:local` | SQLDelight schema + migration snapshots, catalogue importer (`catalog.json`), routine catalogue importer |
| `data:repository` | Repositories exposing Flows; `sync` package added in F8 |
| `domain:usecases` | `progression`, `cycle`, `session` (start / log / finish / resume), `stats` (volume, estimated 1RM — Epley, PRs) |
| `feature:today` | Next cycle day, start/resume session, mini calendar |
| `feature:routines` | My routines, routine editor (days, slots, prescription, progression), famous routines catalogue |
| `feature:exercises` | Search + filters (muscle, equipment, category), exercise detail with media and history, custom exercise editor |
| `feature:session` | Live session |
| `feature:progress` | Full calendar, per-exercise history, PRs, charts |
| `scripts/catalog` | Node catalogue pipeline (§6) |

## 5. Live session

- **Persisted from the first set.** Every set is written to SQLDelight when marked; a session with `endedAt = null` is "in progress". On launch, if one exists, Today shows "Resume session". Process death, reboot or crash lose nothing.
- **Absolute-time timers.** Rest state is stored as `restEndsAt: Instant` (and timed sets as `workEndsAt`); the UI renders `endsAt − now`. Survives screen-off, background and process death.
- **Automatic flow.** Marking a set starts that slot's rest. Timed sets run 3 s countdown → work → rest → next round. Rest can be adjusted ±15 s or skipped. Supersets rest after the round.
- **Android:** a foreground service of type `specialUse` (`shortService` times out and `health` requires sensor/activity-recognition permissions the app does not need; the Play Console gets a "workout session timer" special-use declaration) with a persistent notification showing current exercise and remaining rest, with actions +15 s / Skip / Done. The rest end alarm uses `AlarmManager.setExactAndAllowWhileIdle` (permission `SCHEDULE_EXACT_ALARM`, requested in context; fallback to an inexact alarm plus the in-app timer if denied) to vibrate/sound.
- **iOS:** a local notification scheduled at `restEndsAt` (rescheduled on every change). Live Activity is F9 (needs the Apple Developer account).
- **Permissions** (notifications, exact alarms) are requested in context on the first session start.
- **Keep screen on** during the session, configurable.
- **Entry.** Each set is prefilled from the last session of that slot/variant or the progression suggestion; confirming is one tap. The load keypad adapts to `loadType` (barbell: total ↔ plates per side; machine: kg or plate #; bodyweight: +/− selector; band: level). Flexible slots show the variant picker when reached, with the last load for each variant.
- **Editing during a session** (add, remove, reorder, substitute an exercise, add sets) changes the session only. On finish, if it diverged from the routine, the app offers "Save changes to routine?".
- **Finish** shows a summary (duration, volume, PRs, progression suggestions earned) and advances the cycle pointer.

## 6. Exercise catalogue and media

### 6.1 Sources (research 2026-10-14)

| Source | Use | License |
|---|---|---|
| free-exercise-db (yuhonas) | Base data (~800 exercises) + 2 frames per exercise | Unlicense (public domain) |
| wger | Spanish names/instructions where available | CC-BY-SA 4.0, attribution required |
| Own curation | Variant grouping, tracking/load types, Spanish gaps, missing categories | Ours |

Rejected for a published app: ExerciseDB GIFs (no clear media licence; commercial dataset sold separately), API Ninjas (free tier forbids commercial use, no images), MuscleWiki (paid, streaming only, no offline). They remain possible later behind the media interface if a licence is obtained.

### 6.2 Pipeline (`scripts/catalog/`)

1. Fetch free-exercise-db at a pinned commit.
2. Match wger translations (language id 4) by name/alias table; keep per-item attribution.
3. Apply `catalog/overrides.yaml`: group source exercises into base exercises with variants, set `trackingType`/`loadType`/`equipment`, Spanish names and instructions, fix null fields.
4. Add own entries for categories the source covers poorly: cardio machines (treadmill, bike, rower, elliptical, stair climber), running, swimming, jump rope, calisthenics skills, mobility and stretching. These start without media.
5. Emit `catalog.json` (with `catalogVersion`) bundled in the app, and `ATTRIBUTION.md` shown in Settings → Credits.
6. CI test validates the catalogue: unique stable ids, es + en names for everything, `loadType` consistent with `equipment`, every exercise has ≥ 1 variant, every routine catalogue reference resolves.

The app imports `catalog.json` on first launch and when `catalogVersion` increases, by upsert on stable id. User exercises and all history are never modified; a catalogue entry is never deleted, only marked `deprecated` (hidden from search, still resolvable in history).

### 6.3 Famous routines (`routines/*.yaml`)

Routine structures are data, validated by the same CI test. Descriptive names reference the known name (trademarks), crediting authors with our own description text. Importing copies the routine into "My routines"; the copy is fully editable and keeps `origin` for reference.

MVP set:

| Routine | Cycle | Progression |
|---|---|---|
| Beginner full body, 3 days | 7 days (Mon/Wed/Fri) | Double |
| Linear 5×5 A/B (StrongLifts-style) | A/B alternating | Linear +2.5 kg |
| Starting Strength A/B | A/B alternating | Linear |
| GZCLP (simplified: linear on T1/T2) | 4 days | Linear |
| Push/Pull/Legs (Reddit PPL) | 6 + rest | Linear on main lifts, double on accessories |
| Upper/Lower 4 days | 7 days | Double |
| PHUL | 7 days | Double |
| Arnold split | 6 + rest | Double |
| Classic 5-day bro split | 7 days | Double |
| Recommended Routine (r/bodyweightfitness) | 3 days | Double |
| Couch to 5K | 9 weeks of interval days | None (timed sets) |
| HIIT / Tabata | 1 day | None |

Percentage-based programmes (5/3/1, nSuns, PHAT) arrive in F9.

### 6.4 Media

`ExerciseMediaProvider` interface; first implementation `BundledFramesProvider` shows the two free-exercise-db frames alternating every ~1 s via Coil. Exercises without media show a muscle-group illustration. To keep the APK small, images are not bundled: they are published as a versioned archive in the repo's GitHub Releases, downloaded on first launch over Wi-Fi (or on demand per exercise otherwise) and cached on disk. Further providers (own videos, licensed GIFs) plug in later without UI changes.

## 7. Screens (MVP)

- **Today**: active routine's next day (or "rest day" hint) with its exercises, Start/Resume, week strip of trained days, quick free session.
- **Routines**: My routines (active badge), New, Import from catalogue; editor with cycle length, day list (training/rest, reorder), slot editor (target fixed/flexible, sets, prescription, rest, superset, progression).
- **Exercises**: search, filter chips (muscle, equipment, category), detail (media, instructions, variants, history, PRs), create custom.
- **Session**: current exercise card, set rows, rest timer banner, exercise list drawer, finish summary.
- **Progress**: month calendar (tap a day → its sessions), per-exercise charts (top set, estimated 1RM, volume), PR list, weekly volume per muscle group.
- **Settings**: units, bar and plate inventory, rest sounds/vibration, keep screen on, language, credits.

## 8. Error handling

- All writes are local transactions; there is no network dependency in F1–F5 except the optional media download.
- Media download failures fall back to the illustration and retry later; never block the session.
- Catalogue import runs in one transaction and is idempotent; on failure the previous catalogue stays and the error is logged (Crashlytics in release).
- Schema changes use SQLDelight migrations with `.db` snapshots and `verifyMigrations` in CI.
- Invalid input in the load keypad is impossible by construction (stepper/keypad bounded per `loadType`).

## 9. Testing

- **Domain unit tests** (common): progression engine (streaks, variant isolation, warm-ups ignored, linear), cycle pointer (rest days, routine edits), plate calculator and unit conversion, 1RM/volume stats.
- **Repository tests** against in-memory SQLDelight drivers; catalogue importer idempotency and upsert/deprecate.
- **Catalogue CI test** (§6.2) on every PR.
- **ViewModel tests** with Turbine and fakes from `core:testing`.
- **Screenshot tests** (Roborazzi) for each screen in es/en, light/dark, with `assertAccessible()`.
- **Process-death test** for the live session (Android instrumented): kill during rest, relaunch, session and timer restored.
- iOS simulator build in CI on every PR (no Mac locally).

## 10. Phases

| Phase | Content | Milestone |
|---|---|---|
| **F1** Foundations | Repo from Templetry, CI, SQLDelight catalogue schema, catalogue pipeline + importer ([plans](../plans/README.md)) | — |
| **F2** Exercises | Brand tokens and theme, Exercises tab, search/filters, detail with media (download), custom exercises | — |
| **F3** Routines | Routine editor, famous routines catalogue | — |
| **F4** Live session | Today tab, session, timers, foreground service/notifications, progression engine | — |
| **F5** Progress | Calendar, per-exercise history, PRs, 1RM, volume charts, settings; Doppler, release signing | **MVP → Play Alpha** |
| F6 | Body weight and measurements with moving average/trend charts | — |
| F7 | Nutrition: meals, foods (Open Food Facts barcode lookup with local cache; bundled USDA FoodData Central generic subset, CC0), calories, macros, goals, statistics over time | — |
| F8 | Account + outbox sync (ported from Owlsplit), routine sharing, backup, CSV export | — |
| F9 | Percentage-based progression (5/3/1, nSuns, PHAT), Health Connect / HealthKit, iOS Live Activity | — |

### 10.1 Sharing format (built in F8, fixed now)

`ironlog-routine v1`: JSON referencing catalogue exercises by stable id, embedding any user exercises. Without an account: `.ironlog` file, QR code (deflate + base64url) or link with data in the fragment (`ironlog.web.app/r#<data>`). With an account: short link `ironlog.web.app/r/{code}` backed by Firestore. Import always shows a preview and creates a copy.

### 10.2 Nutrition sources (F7, decided now)

Open Food Facts (ODbL/DbCL, CC BY-SA images): barcode lookup `/api/v3/product/{barcode}`, 15 reads/min per IP, custom `User-Agent: Ironlog/<ver> (contact)`, results cached locally. USDA FoodData Central (CC0): a generic-foods subset bundled offline, so no API key ships in the app. Attribution in Credits.

## 11. Out of scope

Social feed, coaching/AI plans, paid tiers, wearables beyond Health Connect/HealthKit, web or desktop targets.
