# Contributing

Read [AGENTS.md](AGENTS.md) first: everything committed is in English.

## Branch naming

- `feature/<name>` — new features
- `fix/<name>` — bug fixes
- `chore/<name>` — tooling, deps, refactor
- `docs/<name>` — documentation only

Plan branches use the plan id (for example `f1a-scaffold`).

## Commit style

```
<type>(<scope>): <short description>

feat(routines): add the routine editor
fix(logging): keep the last set when the timer ends
chore(deps): upgrade SQLDelight to 2.4.1
```

## PR checklist

Use the PR template — all boxes must be checked before merge.

## Code style

Enforced by Detekt. Run `./gradlew detekt` before pushing.
Max line length: 140.

## Adding a module

Use a convention plugin from `build-logic/` (see ARCHITECTURE.md), add the module to
`settings.gradle.kts` and describe it in `MODULE_MAP.md`.

## Semantic versioning

- `MAJOR` — breaking change
- `MINOR` — new feature, backward-compatible
- `PATCH` — bug fix

Tag releases with `v<MAJOR>.<MINOR>.<PATCH>`.
