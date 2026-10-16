# Haltera — routine scoring and advice

- **Date:** 2026-10-15
- **Status:** requested by the owner ("an algorithm that scores routines according to informed statistics and advises changes").
- **Principle:** Haltera is a logbook (AGENTS.md). The score is **advice only**: it never changes, blocks or
  gates a routine, a session or a log. Every suggestion is phrased as something the user *may* do.
- **Code:** `domain:usecases`, package `routinescore` (`RoutineScorer`, pure and deterministic). UI in
  `feature:routines` (score badges, "Routine analysis" screen).

## 1. Interface

```kotlin
RoutineScorer.score(
    routine: Routine,                         // a saved routine, or a template via toScoringRoutine(language)
    catalogue: ScoringCatalogue,              // category + primary/secondary muscles of its exercises
    sessions: List<WorkoutSession> = empty,   // optional: recent sessions (only finished ones of this routine count)
    now: Long = 0,
): RoutineScore(total 0–100, verdict, focus, criteria: List<Criterion>, weeklySets, sessionsPerWeek)

Criterion(id, score 0–100, weight, finding, advice: List<Advice>, evidence: List<Reference>)
```

Findings and advice are data (sealed types with numbers, muscle groups, catalogue exercise ids and day names);
the UI turns them into English or Spanish text. References are author–year citations.

`total` is the weighted mean of the applicable criteria, rounded. Verdicts: **Excellent** ≥ 90, **Good** ≥ 75,
**Fair** ≥ 60, **Needs work** below. A routine whose resistance sets are under 40 % of its sets (cardio,
intervals) is **not scored** (focus `CONDITIONING`, badge "Cardio"); a routine without training slots is `EMPTY`.

## 2. Model

### Muscle groups

Catalogue muscles map to groups: chest; back (lats + middle back); shoulders; quads; hamstrings; glutes;
biceps; triceps; calves; abs (**major**, large = the first six) and traps, lower back, forearms, hips
(adductors/abductors), neck (**minor**, shown in the chart, not scored). `FULL_BODY` is ignored.

### Sets

Only working sets count (warm-ups never). Resistance work = categories strength, calisthenics and plyometrics,
except distance prescriptions; cardio does not add muscle volume. Per slot, each **primary** group gets the
slot's sets and each **secondary** group half of them (fractional counting, as in Pelland et al. 2024). A
group listed as both counts once, as primary.

### Schedule

- A cycle **with rest days** maps one cycle day to one calendar day: N days run 7/N times a week, so
  sessions/week = training days × 7 / N (a 7-day cycle is simply one week).
- A cycle **without rest days** (A/B programmes, GZCLP) says nothing about the calendar. The scorer assumes
  **3 sessions a week** for cycles of up to 4 days, and one cycle a week (at most 6 sessions) for longer ones.
- A routine with **flexible rest days** (docs/designs/2026-10-16-flexible-rest-days.md) knows its schedule:
  sessions/week = 7 − rest days a week (the number, or the chosen weekdays). Back-to-back days are neighbours in
  the training order once more than 3.5 sessions a week force them, as for cycles without rest days.
- Training days without slots count as rest.

Weekly sets of a group = its sets over the cycle × cycles per week. A group is **trained on a day** when it
gets at least 2 (fractional) sets there; frequency = such days × cycles per week.

Consecutive days (for recovery): with rest days, neighbours in the cycle (wrapping around); without them,
every pair of neighbours only when more than 3.5 sessions a week force back-to-back days.

### Focus

Each prescribed rep target falls in a zone by its midpoint: **heavy** ≤ 7 reps, **moderate** 8–12,
**light** ≥ 13. If heavy sets are at least half of the zoned resistance sets the focus is `STRENGTH`,
otherwise `HYPERTROPHY` (muscle-building and general training).

A slot is **compound** (multi-joint) when it involves at least two major groups (bench press, overhead press,
squat, row, deadlift…).

## 3. Criteria

Weights (plan criteria sum to 100; the session criteria add 20 when they apply, and the total is renormalised):

| Criterion | Weight | Evidence |
|---|---:|---|
| 3.1 Weekly volume | 25 | Schoenfeld, Ogborn & Krieger 2017; Pelland et al. 2024; Ralston et al. 2017 |
| 3.2 Frequency | 15 | Schoenfeld, Ogborn & Krieger 2016; Schoenfeld, Grgic & Krieger 2019; Pelland et al. 2024 |
| 3.3 Coverage | 15 | Ratamess et al. 2009 (ACSM position stand); Schoenfeld, Ogborn & Krieger 2017 |
| 3.4 Balance | 10 | Ratamess et al. 2009; Coombs & Garbutt 2002 |
| 3.5 Rep ranges and loading | 10 | Schoenfeld et al. 2017 (low vs high load); Schoenfeld et al. 2021 (repetition continuum) |
| 3.6 Recovery | 10 | MacDougall et al. 1995; Schoenfeld, Ogborn & Krieger 2016 |
| 3.7 Progression rules | 10 | Kraemer & Ratamess 2004; Ratamess et al. 2009 |
| 3.8 Session length | 5 | heuristic (no research threshold) |
| 3.9 Adherence (with sessions) | 10 | Schoenfeld, Ogborn & Krieger 2017; Ratamess et al. 2009 |
| 3.10 Load progress (with sessions) | 10 | Kraemer & Ratamess 2004; Ratamess et al. 2009 |

Group means weight large groups 1 and small groups 0.5 (0.25 for strength-focused routines, which rarely
program arm or calf work and need not).

### 3.1 Weekly volume

Target **10–20 weekly sets** per major group (the dose–response meta-analyses find growth rising with weekly
sets, ≥ 10 sets beating fewer, with diminishing returns higher up; strength needs less, so strength-focused
routines get full marks from 10 and 85 % at 6). Per group: below the target, score = sets / 10 (strength:
0.85 → 1 between 6 and 10, then proportional below 6); 10–20 → 1; 20–25 falls to 0.9; above 25 falls to
0.6 at 40 and stays there (more volume is not harmful per se, it just stops paying). Groups **under 6 or over
25** are flagged.

Advice: for the two largest weighted deficits, "*Hamstrings: 4 sets a week. Add 3 to 6 sets of Romanian
deadlift or leg curl on Lower A*": sets to reach the target per cycle (half to all of it), two suggested
catalogue exercises for the group, and a day (§4). Flagged high groups get "trim *a* to *b* sets".

### 3.2 Frequency

Checked for every major group with at least 6 weekly sets. Score per group: 1 from twice a week; 0.4 at once a
week, rising linearly to 1 at twice; 0.4 × frequency below once. Advice for the least frequent groups:
"*Chest: trained 1× a week. Move about half of its 12 weekly sets to Arms*" (a day of the same region that
does not train it yet and is not next to a day that does).

### 3.3 Coverage

A major group with under 1 weekly set is missing. Score = covered weight / total weight, large groups 2 and
small 1. Advice per missing group, large first: "*Calves: not trained. Add about 3 sets of standing calf raise
or seated calf raise on Legs*".

### 3.4 Balance

By movement, from primary and secondary muscles:

- **push** = chest primary, shoulders primary with triceps secondary (presses), or triceps primary with chest
  secondary (dips); **pull** = back primary, or shoulders primary with back secondary (face pulls, rear delts).
  Lateral raises and arm isolation count for neither.
- **quad-dominant** = quads primary; **hip-dominant** = hamstrings or glutes primary, or lower back primary
  with hamstrings or glutes secondary (deadlifts).

Bands: push:pull 2:3–3:2, quads:hip 1:2–2:1 → 1; outside, the score falls with the log distance to the band
and reaches 0 at three times outside it; a side at zero against a trained other side scores 0. The criterion
averages the ratios that apply. Advice names the weaker side: "*Pull work is low (push to pull 2:1). Add 3 to 4
sets of bent-over row or lat pulldown on Workout A*". The bands are a coaching heuristic built on the
agonist/antagonist balance guidance and the classic hamstring:quadriceps ratio; the ⓘ text says so.

### 3.5 Rep ranges and loading

Shares of zoned working sets per zone; a zone counts as used from 10 % of the sets. Variety: one zone 0.5, two
0.85, three 1. Heavy compound: any compound slot in the heavy zone.
Strength focus: 0.4 × variety + 0.6 × heavy compound (0 or 1). Otherwise: 0.6 × variety + 0.4 × (1, or 0.6 without
a heavy compound) — growth is similar across loads when sets are hard, heavy loads build more strength.
Advice: "add one multi-joint lift at 3–6 reps, such as squat or bench press" when none is heavy; when every set
is in one zone, a note suggesting the complementary zone.

### 3.6 Recovery

A conflict is a major group except abs and calves getting **at least 4 direct (primary) sets on two consecutive
training days** (rear-delt work does not load the pressing shoulder). Each conflict costs 20 points. Advice
per conflict: "*Quads: trained hard on Legs and Lower B back to back. Put a rest day or other muscles between
them, or trim the sets*".

### 3.7 Progression rules

Share of resistance slots with a rep target that have a progression rule (double or linear); 80 % or more
scores fully (score = share / 0.8). Advice: "*6 of 9 exercises have no progression rule…*".

### 3.8 Session length

Estimated per training day: every set, warm-ups included, takes 4 s a rep + 10 s set-up (at least 20 s), or
its prescribed time, or distance at 2.8 m/s; rest between working sets (warm-ups rest at most 60 s), plus the
rest after the slot; a superset rests once per round (its longest rest), and each slot adds 45 s to change
exercise. Per day: ≤ 75 min → 1, 75–90 → 1 to 0.8, above 90 → down to 0 at 150. The criterion is the mean over
days; days over 90 min are flagged: "*Legs takes about 105 min. Superset accessories, trim rest or move an
exercise to another day*".

### 3.9 Adherence (needs sessions)

Applies when the routine has a finished session in the last 4 weeks and its first session is at least a week
old. Window: the last 28 days, or since the first session. Planned sessions = sessions/week (§2) × weeks.
Sessions ratio = done / planned (capped at 1); sets ratio = mean over the sessions of logged working sets /
planned working sets of that routine day (capped at 1). Score = 0.6 × sessions + 0.4 × sets. Below 75 % of
the sessions: "*7 of 12 planned sessions done. Fewer training days or a shorter cycle may fit your week better*".

### 3.10 Load progress (needs sessions)

Window: the last 8 weeks. For every variant logged in at least 3 sessions spanning at least 2 weeks, the best
estimated 1RM per session (Epley, `Stats.bestOneRm`; the most reps for unloaded work) of the latest third of
the sessions is compared with the earliest third; above +1 % counts as progress. Score = progressing share /
0.7, capped at 1. Advice names stalled exercises: "*No recent progress on bench press. Consider a lighter week,
a different rep range or another variant*".

## 4. Where advice puts the work

`RoutinePlan.dayFor(group, newDay)`: among the training days with resistance work, prefer days of the same body
region (upper, lower, core) that already have at least 2 sets of some group in it. To raise frequency
(`newDay`, used when the group is trained less than twice a week) prefer a day that does not train the group and
whose calendar neighbours do not either; otherwise a day that already trains it. Ties go to the shortest day.
Suggested exercises come from `RoutineScorer.SUGGESTED_EXERCISES` (checked against the bundled catalogue by a
test).

## 5. UI

- **Score badge** ("Score 87", green from 75, amber from 60, red below; "Cardio" for conditioning) on each
  routine in My routines, on each famous routine in the catalogue and in its preview, and in the editor header.
- **Routine analysis** (from the editor header or the template preview): total score with verdict and focus,
  a short verdict sentence, the assumed sessions per week and a "suggestions only" note; the weekly sets per
  muscle chart (horizontal Compose Canvas bars, the 10–20 band shaded, 0–30+ axis); one card per criterion with
  a score bar, its weight, the finding, the advice and an ⓘ dialog explaining the calculation with its sources.
  Session criteria are grouped under "From your logged sessions". Strings in English and Spanish.

## 6. Famous routines (sanity check)

Scored in `FamousRoutineScoresTest` with the bundled catalogue (plan only):

| Routine | Total | Notes |
|---|---:|---|
| Upper/Lower 4 days | 97 | everything in range; abs low |
| r/bodyweightfitness Recommended Routine | 97 | strength focus (5–8 reps); no calves |
| PHUL | 96 | |
| Reddit PPL (6 days) | 96 | volume high on shoulders (presses count half), no abs |
| GZCLP | 89 | 3 sessions a week assumed; chest and hamstrings once and a half a week |
| Bro split (5 days) | 89 | frequency 56: each big group once a week |
| Arnold split | 88 | push:pull 2.6:1, no heavy sets, shoulders and triceps over 25 |
| Linear 5×5 | 88 | strength focus; squat 10:1 over hip work, no abs |
| Beginner full body (3 days) | 86 | light volume (chest 6, glutes 5.5), no heavy sets |
| Starting Strength-style | 86 | no rows (push:pull 9:0), no biceps or abs |
| Couch to 5K, Tabata | — | conditioning: not scored |

The famous programmes are good by design, so the scores are high; the criteria still single out their known
trade-offs (bro-split frequency, novice programmes' missing pull and accessory work, Arnold's push bias).

## 7. Limits

- The catalogue lists muscles, not movement patterns or heads (front, side and rear delts are one group), so
  balance and recovery are approximations.
- Volume counts prescribed sets, not effort; proximity to failure is not modelled.
- The schedule of cycles without rest days is assumed, not known.
