# ADR 0008: Rename Ironlog to Haltera and keep the Firebase project

- **Status:** accepted
- **Date:** 2026-10-16 (`Rename Ironlog to Haltera ...`, #26)
- **Area:** `docs/NAMING.md`, `scripts/check-name.mjs`, `firebase/`, share decoders

## Context

The app was called Ironlog until 2026-10. The name changed because Ironlog clashed with existing apps or brands. Some
identifiers could not follow: Firebase project ids cannot be changed, and Auth users and Firestore data
live in the project; a registered Play upload key cannot be replaced; CI secrets and old installs depend on
the old names.

## Decision

- Renamed: display name, package `es.sebas1705.haltera`, iOS bundle id, Gradle plugin ids (`haltera.*`), database file
  `haltera.db`, share formats and documents.
- Kept on purpose (`docs/NAMING.md`): Firebase project `ironlog-sebas1705`, Firestore collections, fields and
  `schema` values and the rules, SQLite table names and migration snapshots, the Doppler project `ironlog` and its
  secret names, the release keystore and alias, the CI service account, and the legacy package
  `es.sebas1705.ironlog` with its three Firebase apps, so old installs and their data stay reachable.
- Share formats are written as Haltera and the Ironlog ones are still read (`haltera-routine`/`ironlog-routine`,
  `.haltera`/`.ironlog`, `HALTERA:`/`IRONLOG:` QR prefixes).
- `node scripts/check-name.mjs` runs in CI (job `catalog`) and fails when the token `ironlog` appears in a tracked
  file outside an allow-list kept in the script.

## Consequences

- Users of earlier Ironlog builds keep their account and data; the public privacy pages still mention the old name
  so they can find their data and the deletion route.
- Contributors will see `ironlog` identifiers in Firebase and Doppler; `docs/NAMING.md` is the list of what is
  deliberate.
