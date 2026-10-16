# Import formats

Settings → Data → **Import workouts** reads a history file from another app (or from Haltera itself), shows what it
holds, and writes it only after the user confirms. Sources: **Hevy**, **Strong**, **FitNotes** (CSV) and the
**Haltera export** ([EXPORT-FORMAT.md](EXPORT-FORMAT.md): the zip, or its `sessions.csv` / `body_weight.csv`).

Code: `data/repository/.../importer/` (`HistoryFileParser` = pure parsing, `ExerciseMatcher`, `SqlHistoryImporter` =
preview and write), UI in `composeApp/.../dataimport/`. Fixtures: `data/repository/src/androidUnitTest/resources/import/`
(small, synthetic, anonymised; never commit a real export).

## Flow

1. The user picks a file (Android: system document picker; iOS: document picker, behind `ImportFileBridge`).
2. The source is detected **from the header row** (below). An unrecognised file, an empty one, a known header
   without usable rows, a damaged zip or an export from a newer format version fails with a clear message and
   writes nothing.
3. The preview shows the source, sessions and sets, the date range, body weight entries, how many sessions are
   already in the logbook, the exercises matched automatically and the names that match nothing. Weight unit
   (when the file has none) and what to do with existing sessions are chosen here. **Nothing is written before
   the user taps Import.**
4. For each unmatched name the user chooses **Create as custom exercise** (default) or **Map to...** (search of the
   catalogue and the user's own exercises). The choice applies to this import only.
5. Import writes everything in one database transaction: if it fails, the logbook is unchanged. The data goes into
   the same tables as logged data, so the sync change tracking fires exactly as for a session logged by hand (the
   cloud copy stays opt-in; import does not touch sync).

## Detection

Column names are compared in lower case; the delimiter (comma, semicolon or tab) is taken from the header row.

| Source | Detected when the header has | Headers seen in the wild |
|---|---|---|
| Haltera sessions | `session_id`, `exercise_id`, `set_type` | the 25 columns of `sessions.csv` |
| Haltera body weight | `measured_at`, `weight_grams` | `body_weight.csv` |
| Hevy | `exercise_title`, `start_time` | `title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe` |
| Strong | `exercise name`, `set order`, `date` | iOS: `Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE`. Android (semicolons): adds `Workout #`, and `Duration (sec)`, `Weight (kg)`, `Distance (meters)`, optionally `Weight Unit` |
| FitNotes | `exercise`, `date`, `reps` and `category` or a `weight...` column | Android: `Date,Exercise,Category,Weight (kgs),Reps,Distance,Distance Unit,Time,Comment` (newer: `Weight,Weight Unit`). FitNotes 2: `Date,Exercise,Category,Weight (kg),Weight (lbs),Reps,Distance,Distance Unit,Time,Notes,Kind` |

How these were verified: there is no vendor documentation of the export columns. They were checked against open
source importers that state they were written against real exports (a multi-app importer listing the Hevy,
Strong and FitNotes headers "verified against real exports", the Strong importer of Ryot for the Android variant
with semicolons, `Duration (sec)`, `Weight (kg)`, `Distance (m)` and the `Rest Timer`/`Note` marker rows, the
LifTrace Strong parser for `Weight Unit`, and the FitNotes row schema of a FitNotes visualiser). Real exports
differ between app versions and languages; the reader finds columns by name, ignores unknown ones and accepts the
variants above, so extra or reordered columns are harmless. If your export fails, please open an issue with the
header row only (never the data).

## Mapping

A row is one set. Rows are grouped into sessions, sessions into exercises, keeping the order of the file.

| Concept | Hevy | Strong | FitNotes | Haltera |
|---|---|---|---|---|
| Session | `title` + `start_time` | `Date` + `Workout Name` | one per `Date` | `session_id` |
| Start | `start_time` | `Date` (the start of the workout) | the date at 12:00 local time (the file has no time of day) | `started_at` (UTC) |
| End | `end_time` | start + `Duration` | = start | `ended_at`, else the last `completed_at` |
| Session title (shown as the session name) | `title` | `Workout Name` | none | `day_name` |
| Session notes | `description` | `Workout Notes` | none | `session_notes` |
| Exercise notes | `exercise_notes` | `Notes` | `Comment` / `Notes` | `exercise_notes` |
| Load | `weight_kg` (or `weight_lbs`) | `Weight` in the unit chosen in the preview, or `Weight (kg)` / `Weight (lb)` / `Weight Unit` when the file says | `Weight (kgs)`, `Weight (kg)`, `Weight (lbs)` or `Weight` + `Weight Unit` | `load_grams` exactly |
| Reps / time / distance | `reps`, `duration_seconds`, `distance_km` | `Reps`, `Seconds`, `Distance` (metres) | `Reps`, `Time` (`h:mm:ss`), `Distance` + `Distance Unit` | same columns |
| RPE | `rpe` | `RPE` | none | `rpe` |
| Set type | `set_type`: `warmup`, `dropset`, `failure`, anything else is a working set | `Set Order`: `W` warm-up, `D` drop, `F` failure, a number is a working set | `Kind` (FitNotes 2) when present | `set_type` |

Rules that apply to every source:

- **Units.** Loads are stored in grams. Hevy is kilograms (or pounds when the column says so). A Strong export
  without a unit column does not say what the numbers are: the preview asks (default: the unit set in Haltera).
  Pounds convert with 1 lb = 0.45359237 kg. Distances are stored in metres (km and miles are converted).
- **Numbers** accept a decimal point or a decimal comma, with or without thousands separators.
- **Dates** accept `2025-04-29 18:05:00`, `2025-04-29T18:05`, `29 Apr 2025, 18:05` (month names in English,
  Spanish, French, German, Portuguese, Italian and Dutch), `Apr 29, 2025`, and `29/04/2025` (day first unless the
  second number cannot be a month). Times are read in the device's time zone; the date in the file is the date
  you get. Dates are kept as they are, in the past or the future: a logbook never refuses a day.
- **Sets with nothing measured** (no reps, load, time or distance) are skipped and counted in the preview. A
  zero load is "no load" (bodyweight); a negative load is kept (assisted bodyweight).
- **Bodyweight and assisted exercises** are plain sets without a load; a custom exercise created from them is
  `BODYWEIGHT_REPS`. **Timed** sets (`Plank`) and **distance** sets (`Running`) create `TIME` and `DISTANCE_TIME`
  exercises, with the FitNotes `Cardio` category read as cardio.
- **Supersets** (Hevy `superset_id`) are not kept: a logged session has no superset grouping (only routine slots
  do), and imports never create routines.
- **Never enforced**: no routine is created or matched, sessions are free sessions (`routineId` empty); the
  workout title becomes the session name.
- **Text encoding**: UTF-8 (with or without a byte order mark); a file that is not valid UTF-8 is read as Latin-1.
  Line ends CRLF, LF or CR; quoted fields may contain commas, quotes and line breaks.

### Haltera export

`sessions.csv` is read as written by [Export my data](EXPORT-FORMAT.md): session ids, set order, kinds, exact
`load_grams` (also for `plate` and `level` units, with their `load_detail`), RPE and completion times round-trip.
`body_weight.csv` entries keep their ids. When the zip has `manifest.json` it must say `haltera-export` and a
version this app knows. `exercises.json` lets a user exercise that does not exist on this device be recreated with
its category, tracking type, muscles and equipment. The zip reader handles the stored entries "Export my data"
writes; a zip that was recompressed by another tool is refused with a message to extract `sessions.csv`. Meals and
routines in the zip are not imported (routines have their own `.haltera` share file).

## Exercise matching

Each name is normalised (lower case, accents removed, punctuation and spacing ignored) and compared with the
English and Spanish names of the catalogue and the user's own exercises (the user's own win a tie).

- Equipment written next to the name picks the variant: `Bench Press (Barbell)` and `Barbell Bench Press` both
  become `bench-press.barbell`; `Dumbbell Bench Press` becomes `bench-press.dumbbell`. Without equipment the
  exercise's first variant is used.
- Equipment the exercise does not have is **not guessed** (`Squat (Dumbbell)` stays unmatched when there is no such
  variant): the user decides in the preview.
- A Haltera export names variants by id (`bench-press.barbell`); if the id exists on the device it is used as is,
  otherwise the name is matched like any other.
- Catalogue names are the only aliases: there is no alias table. Matching is exact after normalisation, never fuzzy.

A name that matches nothing is created once as a custom exercise (tracking type, category, muscles and equipment
inferred from the data and, for FitNotes, the category), or mapped by the user to an existing exercise.

## Idempotency

Every imported session has a deterministic id, so importing a file twice never duplicates:

- Hevy, Strong, FitNotes: a UUID-shaped hash of `source`, the start as written in the file (local date and
  time, independent of the device time zone) and, for sessions sharing a start, their order. Renaming a workout
  or editing its sets in the source app does not change its id. FitNotes has no time of day, so one FitNotes
  day is one session and its id is the source and the date: two FitNotes files with the same day are the same
  session.
- Haltera: the `session_id` of the file (and the `id` of body weight entries).

Sessions already in the logbook are **skipped** by default; the preview can switch to **replace**, which deletes
and rewrites them (edits made after the first import are lost). Entries inside a session get ids derived from the
session id. Body weight entries are skipped when their id exists. Two different sources describing the same real
session (for example Strong and Hevy after a migration) are different sessions: pick one source.

## Limits and not supported

- Files up to 64 MB; a 50 000 row CSV is parsed and written in a few seconds on a laptop (unit test).
- Hevy measurements (body weight, body fat) are a separate Hevy export and are not imported.
- Nutrition, routines and settings are not imported from any third-party source.
- Not verified on a device: the iOS document picker (compiles; needs a runtime check) and real-world exports of
  every app version and language.
