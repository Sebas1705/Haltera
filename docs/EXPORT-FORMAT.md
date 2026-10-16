# Export format (`haltera-export` v1)

Settings → Data → **Export my data** writes one zip, `haltera-export-YYYY-MM-DD.zip` (the date is the local date of
the device). It holds everything the user logged and nothing that ships with the app: the bundled exercise
catalogue and the bundled food database are not exported; they are referenced by their stable ids. Import (P2)
reads the same files.

Code: `data/repository/.../export/` (`ExportFormat` = columns and row formatting, `DataExporter`, `ZipWriter`).
Golden files for every file below live in `data/repository/src/androidUnitTest/resources/export/`; regenerate
them deliberately with `UPDATE_GOLDEN=1 ./gradlew :data:repository:testDebugUnitTest --tests '*DataExporterTest*'`.

## Rules that hold for every file

- UTF-8. CSV files start with a byte order mark (so spreadsheets read accents) that readers must strip, use
  RFC 4180 quoting (a field is quoted only when it contains a comma, a double quote or a line break; quotes are
  doubled) and CRLF line ends. The first line is the header; the column list is stable, new columns are only
  ever appended.
- Decimal point, never a comma, whatever the device language. No exponents. Empty = not recorded.
- Dates are ISO 8601. `date` columns are the **local** calendar date on the device when the export was made
  (the time zone is in `manifest.json`); `*_at` columns are UTC instants (`2026-10-16T09:30:00Z`, fractional
  seconds only when the milliseconds are not zero).
- Loads are always kilograms and grams, whatever unit the app shows. A `unit` column says what a load is (see below).
- Exercises are identified by their stable ids: `exercise_id` (e.g. `bench-press`) and `variant_id`
  (e.g. `bench-press.barbell`) for catalogue exercises, `user-<uuid>` ids for the user's own exercises
  (described in `exercises.json`). Names are informative (English); match on ids.
- Only completed sets are exported (a set that was added but never ticked is not a logged set).

## `sessions.csv`: one row per completed set

| Column | Meaning |
|---|---|
| `date` | Local date the session started |
| `session_id` | Session id (same on every row of a session) |
| `started_at`, `ended_at` | UTC instants; `ended_at` empty while the session is in progress |
| `day_name` | Routine day the session was started from; empty for a free session |
| `session_notes` | Notes of the session |
| `session_body_weight_kg` | Body weight stored with the session, kg |
| `exercise_id`, `variant_id` | Stable ids (see above) |
| `exercise`, `variant` | English exercise name and variant label (informative) |
| `exercise_position` | 1-based order of the exercise in the session |
| `exercise_notes` | Notes of the exercise in this session |
| `set_number` | 1-based order of the set within the exercise (completed sets only) |
| `set_type` | `WARMUP`, `WORKING`, `DROP` or `FAILURE` |
| `reps` | Repetitions, empty for timed/distance sets |
| `load_grams` | The stored load. Grams for a weight (negative = assisted bodyweight); a plate number when `unit` is `plate`; a level when `unit` is `level` |
| `load_kg` | `load_grams` in kilograms (up to 3 decimals), only when `unit` is `kg` |
| `unit` | `kg` (weight), `plate` (machine stack position) or `level` (resistance band level); empty when no load |
| `load_detail` | What was typed (bar, plate or band information) as JSON, informative |
| `duration_s`, `distance_m` | Time and distance for timed/distance exercises |
| `rpe` | Rate of perceived exertion, up to 2 decimals, if entered |
| `completed_at` | UTC instant the set was ticked |
| `set_id` | Set id |

## `body_weight.csv`

`date`, `measured_at`, `weight_kg`, `weight_grams`, `unit` (always `kg`), `notes`, `id`. Oldest first.

## `meal_entries.csv`

One row per logged food. `date`, `meal` (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`), `position`, `entry_id`,
`food_id`, `food` (English name), `brand`, `barcode`, `food_source` (`GENERIC`, `OFF`, `USER`, `RECIPE`),
`amount` (grams, or millilitres when `amount_unit` is `ml`), `amount_unit` (`g`/`ml`), `servings` (as typed, if
it was), then the nutrients **for that amount**: `kcal`, `protein_g`, `carbs_g`, `fat_g`, `fiber_g`, `sugar_g`,
`salt_g` (empty when unknown), and `created_at`. Food fields are empty if the food is no longer on the device.

## `routines.json`

A JSON array with one element per routine, each exactly the `haltera-routine` v1 document of the routine share
file (`.haltera`, see `RoutineExport`): `{"format":"haltera-routine","version":1,"exportedAt":…,"routine":{…},
"customExercises":[…]}`. Each element can be read on its own by the routine importer.

## `exercises.json`

`{"format":"haltera-exercises","version":1,"exercises":[…]}`: the exercises the user created (id, names in both
languages, category, level, muscles, tracking type and variants), so history that points to a `user-…` id can be
resolved. Catalogue exercises never appear here.

## `manifest.json`

`format` (`haltera-export`), `version` (1), `appVersion`, `schemaVersion` (database schema), `exportedAt`
(UTC instant), `timeZone`, `counts` (`sessions` with at least one row, `sets`, `bodyWeightEntries`, `mealEntries`,
`routines`, `customExercises`) and `files`. It is the last entry of the zip.

## Notes for readers

- Excel in some languages opens a double-clicked `.csv` using `;` as separator; use *Data → From text/CSV* and
  choose UTF-8 and comma. Google Sheets and LibreOffice detect it.
- Text is exported as typed: a note that starts with `=` is not escaped, so open untrusted files as text, not
  as formulas.
- Compatibility: version 1 will only gain columns and keys; a breaking change bumps `version`.
