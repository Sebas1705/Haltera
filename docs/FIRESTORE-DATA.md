# Firestore data: layout, backup and migration

Firebase project: `ironlog-sebas1705`. Cloud sync stores one document per entity under the signed-in user.
The cloud copy is **optional**: an install writes nothing here until its user turns on Settings → Account → "Back up
to the cloud" (installs that were already syncing before that option keep syncing). A user who turns it off keeps
their documents here until they choose "Also delete my cloud data" or Delete account.

## Layout

```
users/{uid}/{collection}/{docId}
```

Collections: `routines`, `sessions`, `bodyWeight`, `bodyMeasurements` (schema 9), `exercises`, `settings`, `foods`,
`mealDays`. Progress photos are never in Firestore: they stay on the device.
Every document has the fields `payload`, `updatedAt`, `deleted` (tombstone) and `schema`.
Security rules only let a signed-in user read and write `users/{their uid}`.

The `users/{uid}` parent documents usually do not exist (only their subcollections do); the tools below handle that.

## Why the uid matters

A fresh install that turns the cloud copy on signs in anonymously and gets a new uid, so it cannot see data written under the previous uid. The
data is only reachable again by signing in with the Google account that was linked to that uid (linking keeps the
uid). Nothing is lost as long as the old users and the Firestore data are left alone.

## Tools

All need `npx firebase-tools login` as the project owner, and Node 22. They never print document payloads.

| Script | Purpose |
| --- | --- |
| `node scripts/firestore-backup.mjs --out <dir>` | Read-only dump of every document under `users/*`: one JSON file per user plus `index.json` with counts. Refuses an `--out` inside the git repo. |
| `node scripts/firestore-migrate-user.mjs --list` | For each uid: documents per collection and the max `updatedAt`. Use it to find the right uid. |
| `node scripts/firestore-migrate-user.mjs --from <uid> --to <uid>` | Dry run: reports would copy / skip newer / identical / conflicts. |
| `... --from <uid> --to <uid> --apply` | Same, and writes. Never deletes; never overwrites a newer destination document. |

Merge rule per document: last writer wins on `updatedAt`. A newer destination is kept, a newer source is copied,
tombstones (`deleted`) are ordinary documents and follow the same rule. Equal `updatedAt` with different content is
reported as a conflict and the destination is kept.

The backup holds personal data: store it outside the repo and never commit it. To see which accounts are
Google-linked: `npx firebase-tools auth:export <backup dir>/auth-users.json --format=json --project ironlog-sebas1705`
(accounts with a `google.com` entry in `providerUserInfo`; the rest are anonymous).

## Safe order for the rename (Ironlog to Haltera)

1. In the OLD app: Settings > Account > link Google. Wait for the sync to finish.
2. Back up: `node scripts/firestore-backup.mjs --out <dir outside the repo>`.
3. Install the new app and sign in with the same Google account. That signs in to the existing uid and downloads
   the data.
4. Only if data is missing: run `--list`, identify the uid that holds it (counts and max `updatedAt`), find the new
   app's uid (Firebase console > Authentication, or the `--list` entry that appeared after the sign-in), run the
   migration as a dry run, check the numbers, then repeat with `--apply`.

## Do not

- Delete the old Firebase apps or users: the uids and their data must stay reachable.
- Change collection names, document fields or the security rules.
- Run `--apply` without reading the dry-run output first, or against a backup you have not taken.
