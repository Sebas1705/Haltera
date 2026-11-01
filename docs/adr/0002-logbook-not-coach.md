# ADR 0002: Haltera is a logbook, not a coach

- **Status:** accepted
- **Date:** 2026-10-14 (core design; the log-mode wording landed the same day in "docs: describe the derived cycle, past sessions and log mode")
- **Area:** `docs/designs/2026-10-14-haltera-core-design.md` §3.4, `domain:usecases` (`cycle`, `progression`), README "A logbook, not a coach"

## Context

The product is a free gym app for logging and following routines "with as little friction as possible" (core
design §1). The design records the owner's principle that a logbook never loses history and does not push a
schedule on the user. Later requests repeat it: flexible rest days were added because some routines have a fixed
order of training days but no fixed rest between them (`docs/designs/2026-10-16-flexible-rest-days.md`).

## Decision

- No routine is enforced. Any session can be logged after the fact, on any date, as a routine day or a free
  session, and the user can always start another day than the suggested one.
- Everything stays editable afterwards: date and time, duration, exercises, sets and loads. The only confirmation
  is before a delete.
- The cycle position is derived, not stored: the next day is the day after the routine's most recent session by
  `startedAt`, so logging out of order, editing a date or deleting a session keeps it right. A manual "next day"
  override wins until a newer session of the routine starts. Rest days are hints only.
- Progression suggestions (double progression, linear) are computed from the history ordered by `startedAt`, a
  session only sees sessions that started before it, and a suggestion is never applied silently.
- Anything that interprets logged data shows the shared health disclaimer (AGENTS.md).

## Consequences

- Past volume, PRs and progression stay consistent after edits, because they are read from history, not cached.
- Derived reads must order by `startedAt`; a session logged after the fact is kept only with at least one
  completed set.
- Routine analysis advises but never blocks or rewrites a routine (README).
