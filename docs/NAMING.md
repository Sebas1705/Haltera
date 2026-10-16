# Naming

The app is called **Haltera** (Spanish for barbell or dumbbell). Until 2026-10 it was called Ironlog. The rename covers
the display name, the package `es.sebas1705.haltera`, the iOS bundle id, the Gradle plugin ids (`haltera.*`), the
database file `haltera.db`, the share formats and every document.

## Share formats

| | Written now | Still read |
|---|---|---|
| JSON format key | `haltera-routine` | `ironlog-routine` |
| File extension | `.haltera` | `.ironlog` |
| QR code, routine | `HALTERA:R2:` | `IRONLOG:R2:`, `ironlog:r1:` |
| QR code, template | `HALTERA:T1:` | `IRONLOG:T1:` |

## Identifiers that keep the old name on purpose

Renaming these would break data, signing or access, and nothing user-visible depends on them.

| Identifier | Why it stays |
|---|---|
| Firebase project id `ironlog-sebas1705` (`firebase/.firebaserc`, scripts, docs) | Project ids cannot be changed. Auth users and Firestore data live there. |
| Firestore collections, document fields, `schema` values, rules | Existing data and old app versions depend on them. |
| SQLite table names, `Meta` keys, SQLDelight migrations and snapshots (`1.db` to `4.db`) | The schema history must stay verifiable. |
| Doppler project `ironlog`, config `prd`, and every secret name | Renaming would break CI. |
| Release keystore `ironlog-release.jks`, key alias `ironlog` | The upload key cannot be replaced once registered. |
| Service account `ironlog-ci@ironlog-sebas1705.iam.gserviceaccount.com` | Used by CI and App Distribution. |
| Legacy package `es.sebas1705.ironlog` and its three Firebase apps (Android, Android `.debug`, iOS) | Kept registered so the old installs, their uids and data stay reachable. |

## The allow-list

`node scripts/check-name.mjs` (run in CI, job `catalog`) fails when the case-insensitive token `ironlog` appears in a
tracked file outside this list. The list lives in the script.

- Anywhere, only as part of the identifiers above: `ironlog-sebas1705`, `ironlog-release.jks`, `ironlog-ci`,
  `ironlog/prd`, the Doppler project `ironlog` and the keystore alias.
- Whole files that describe the old name on purpose: this file, `docs/RELEASE.md` (legacy package and apps),
  `docs/FIRESTORE-DATA.md` (rename runbook), `README.md` (the "formerly" line),
  `docs/designs/2026-10-14-haltera-core-design.md` (share format),
  `composeApp/src/androidMain/AndroidManifest.xml` (`.ironlog` intent filter), and the share decoders with their test:
  `RoutineExport.kt`, `RoutineShareCode.kt`, `RoutineSharingTest.kt`.
- The four public pages under `firebase/hosting` (privacy and account deletion, English and Spanish) and
  `scripts/hosting-pages.test.mjs`, which caps how often they mention it: users of the earlier Ironlog test builds
  must be able to find their data and the deletion route.
- `docs/play/PLAY-CONSOLE-GUIDE.md` and `docs/play/DATA-INVENTORY.md` (migration of the Ironlog testers and cloud data,
  legacy package and Firebase apps).
