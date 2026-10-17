# Haltera — flexible rest days

- **Date:** 2026-10-16
- **Status:** requested by the owner ("some routines are not fixed in their rest days: the order of the training
  days is fixed, but not every week there is a rest between the same workouts").
- **Principle:** Haltera is a logbook (AGENTS.md). Nothing here blocks a session: a rest day is a hint, the user
  can always start the next workout, another day or a free session, and log any date.
- **Code:** model `RestPolicy` (`data:models`), `Cycle` and `FlexibleRest` (`domain:usecases`, package `cycle`),
  `RoutineRepository.setRestPolicy`, editor (`feature:routines`), Today card (`feature:today`).

## 1. Two kinds of routine

| | Fixed (default) | Flexible |
|---|---|---|
| Days | An N-day cycle of training **and rest** positions (core spec §3.2) | The ordered list of **training** days only |
| Rest | A rest position of the cycle | A policy: *N rest days a week* (0–6) **or** fixed rest weekdays (Mon…Sun) |
| Next day | Core spec §3.4, unchanged | The training day after the last one logged from the routine |
| Today on a rest day | "The plan says rest" while the pointer is on a rest position | Count mode: never; weekday mode: on the chosen weekdays |

Every existing routine, every famous routine of the catalogue and every imported code without the new field is
**fixed** and behaves exactly as before.

### Next day (both kinds)

Both kinds use the same derived pointer (`Cycle.pointer`): the day after the routine's most recent session by
`startedAt` (finished or not), unless the manual override (`nextDayIndex` + `nextDayIndexSetAt`) is newer.
Logging a past session, logging days out of order, editing a date or deleting a session keeps it right because
the most recent session **by date** wins, whatever order sessions were logged in. Free sessions have no routine,
so they never move it. A flexible routine has no rest positions, so the pointer always lands on a training day
however many calendar days passed ("Push, Pull, Legs" stays Push → Pull → Legs whether the user trains on
Monday–Tuesday–Wednesday or Monday–Thursday–Saturday). Should a flexible routine still contain a REST day (data
written by an older app version), `Cycle` skips it silently.

### Rest policy of a flexible routine

- **A number** (`restDaysPerWeek`, 0–6; "I don't know which days"): the app never decides which days are rest.
  Today shows the next training day every day and, as information only, *"Rest days from “PPL” this week: x of N"*.
- **Fixed weekdays** (`restWeekdays`, a Monday-first bitmask, at most six days): on those weekdays Today says
  the plan is rest (with the next workout still one tap away); on the other days it shows the next training day.
  The weekly number is derived from the weekdays (`restDaysPerWeek` = number of weekdays chosen).

**"x" (rest days used)** = days of the current week (Monday first, device time zone) **before today** without a
session of this routine (by the local date of `startedAt`). Today is not counted yet: it can still be trained.
A day with only a free session or only sessions of another routine counts as rest **for this routine** (by
design: the count is about this routine's rotation, not about training in general). Those days are also counted
apart (`RestWeek.usedWithOtherTraining`) so Today can say so. The count is shown even when it exceeds N, but
never as "x of N" then (see Wording).

### Wording (revised after the alpha.4 QA, and again after the pre-alpha.5 QA)

The training days of a flexible routine are a rotation, not days of the week, so they are never put next to the
weekly rest days as if they added up to a week ("3 training days · 5 rest days a week" read as 8 days):

| Where | Text |
|---|---|
| Routine list, editor header, import preview | *"3 workouts in rotation · 5 rest days a week"* (fixed routines keep *"4-day cycle · 3 training days"*) |
| Today, x ≤ N | *"Rest days from “PPL” this week: 1 of 3"* |
| Today, x > N | *"4 rest days from “PPL” this week (target 3)"*, never "4 of 3" |
| Today, N = 0 and x > 0 | *"2 rest days from “PPL” this week (none planned)"* |
| Today, N = 0 and x = 0 | *"No rest days from “PPL” planned this week"* |
| Today, some of the x days had other training | an extra line: *"You trained outside this routine on 2 of those days."* |
| Today, always under the line | *"A rest day here is a day without a session of this routine: free sessions and other routines do not count as its training days. Information only: the next day is always the one after your last session of this routine."* |

Today's lines name the routine (“PPL” stands for its name; Spanish uses «…», e.g. *"Días de descanso de «PPL»
esta semana: 1 de 3"*). Before the pre-alpha.5 QA they said only "5 rest days this week", which read as a
contradiction next to the week strip, since the strip counts every session of the week (free sessions and other
routines included) and could show 3 trained days. The rule did not change, only the words: the rest days belong
to the routine, and the days that had other training are said apart.

Counts use plurals in both languages ("1 rest day", "1 workout"). The choice is `RestWeekUi.wording`
(`feature:today`, strings `today_rest_week*`) and `routineSummary` (`feature:routines`).

Examples (Push · Pull · Legs · Upper, 3 rest days a week, today Thursday):

| Logged this week | Today shows | Rest days this week |
|---|---|---|
| Mon Push, Tue Pull | Day 3 of 4 · Legs | 1 of 3 (Wed) |
| Mon Push, Wed Pull (logged on Thu as a past session) | Day 3 of 4 · Legs | 1 of 3 (Tue) |
| Nothing, last session was Upper last Saturday | Day 1 of 4 · Push | 3 of 3 |
| Mon Push, Tue a free session, Wed another routine | Day 2 of 4 · Pull | 2 of 3 (Tue, Wed) + "You trained outside this routine on 2 of those days." (the week strip shows 3 trained days) |

With rest weekdays Wed + Sun: on Wednesday Today shows "The plan says rest today" above the next day (Start
still works); on Thursday it shows the next day without the hint.

## 2. Converting

- **Fixed → flexible:** REST days without exercises are removed; a REST day that holds exercises becomes a
  training day (nothing the user wrote is lost). Training days keep their order. The rest policy starts as
  *N rest days a week* with N = the cycle's rest positions scaled to a week (rest × 7 / cycle length, rounded,
  0–6): a 7-day cycle with one rest day gives 1, "Upper · Lower · Rest" gives 2.
- **Flexible → fixed:** the editor asks whether to add the N rest days at the end of the cycle (they can then be
  moved with the day arrows) or to add none. No training day is removed.
- In both directions, in one transaction: the `routineDayIndex` of the routine's past sessions and the manual
  override are remapped to the new positions of the same days, so history keeps pointing at the right day and
  the next day does not jump. Sessions remapped this way are synced again (a real change).

## 3. Data

`RoutineEntity` gets three columns (migration `4.sqm`, schema 4 → 5; existing rows get the defaults):

| Column | Type | Default |
|---|---|---|
| `restMode` | `TEXT` `FIXED` \| `FLEXIBLE`, read leniently (unknown → FIXED) | `'FIXED'` |
| `restDaysPerWeek` | `INTEGER` 0–6 | `0` |
| `restWeekdays` | `INTEGER` bitmask, bit 0 = Monday … bit 6 = Sunday; 0 = weekdays not known | `0` |

- **Sync** (`routines` collection): `RoutinePayload` carries `restMode`, `restDaysPerWeek`, `restWeekdays` with
  the defaults above, so documents written by older versions decode as fixed. The existing `RoutineEntity`
  update trigger already tracks the new columns. Firestore rules are unchanged (the payload is opaque).
  Known limit: an older app version that edits the routine re-uploads it without the fields, which makes it
  fixed again on the other devices (the training days are kept).
- **Share format** (`haltera-routine` v1, file, QR and links): `routine.rest` is an optional object
  `{"mode":"FLEXIBLE","daysPerWeek":3,"weekdays":[3,7]}` (ISO weekdays, 1 = Monday) written **only** for flexible
  routines, so fixed routines produce exactly the same JSON and codes as before. Older apps ignore the key and
  import the training days as a fixed routine; codes without it import as fixed. The format version is not
  bumped (the field is optional and additive).
- **Export my data**: `routines.json` embeds the share document, so it carries the field too.

## 4. Routine score

For a flexible routine the scorer's schedule (routine-scoring §2) is known instead of assumed:
**sessions a week = 7 − rest days a week** (from the number, or the weekdays). Weekly sets and frequency per muscle
follow from it (cycles a week = sessions a week ÷ training days). Back-to-back days for recovery are neighbours
in the training order (wrapping around) once more than 3.5 sessions a week force them, as for cycles without rest
days. Adherence plans the same sessions a week. The advice texts are unchanged; the analysis says the weekly
numbers come from the flexible rest days.

## 5. Editor and Today

- Editor header: a segmented control **Fixed / Flexible** with a one-line explanation of the selected kind and an
  ⓘ dialog with the longer explanation and an example. Flexible shows a switch **"I know my rest days"**: on →
  weekday chips (Mon…Sun, at most six); off → a 0–6 stepper. Flexible routines hide the "Rest day" button and
  the training/rest switch of each day.
- Today: the day label counts training days only ("Day 2 of 4 · Pull"); weekday rest shows the existing rest hint;
  both flexible modes show the weekly rest line under it ("Rest days from “PPL” this week: x of N", or the forms of §1
  "Wording" when x exceeds N or N is 0).
- Famous routines stay fixed (none of the bundled templates is clearly flexible).
