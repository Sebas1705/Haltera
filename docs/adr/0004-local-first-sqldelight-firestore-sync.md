# ADR 0004: Local-first SQLDelight storage with a dirty-flag Firestore sync

- **Status:** accepted
- **Date:** 2026-10-14 (`docs: account and sync design (F8)`, `feat(sync): data:sync ...`)
- **Area:** `data:local` (`SyncTracking.sq`), `data:sync`, `data:remote`, `docs/designs/2026-10-14-sync-design.md`, `docs/FIRESTORE-DATA.md`

## Context

The app must be fully usable offline and without an account; the cloud is "a copy of the user's data so nothing is
lost on uninstall or a new phone" (sync design, principle). The design ports the outbox-style sync of Owlsplit.
The backend is Firebase on the Spark plan with no Cloud Functions, through GitLive Firebase KMP.

## Decision

- SQLite through SQLDelight is the source of truth. Repositories never know about sync.
- Sync works on **aggregate roots** (routine, session, body weight, custom exercise, settings, and later the
  nutrition data). Each is one Firestore document under `users/{uid}/{collection}/{docId}`, stored as
  `{ payload, updatedAt, deleted, schema }`. Deletes are tombstones. The bundled exercise catalogue is not synced.
- SQLite triggers (`SyncTracking.sq`) mark the owning root dirty in `SyncState` on every insert, update or delete.
  They are skipped while remote changes are applied (`Meta syncApplying = '1'`).
- The engine uploads dirty roots in batches with exponential backoff, keeps `dirty` until the upload is acknowledged
  and unchanged since, and downloads per collection from a cursor. Conflicts are **last writer wins per document by
  `updatedAt`**.
- Security rules limit a user to `users/{auth.uid}/**` and are tested against the Firestore emulator in CI.
- An upload pass is not cancelled by its own dirty-count changes: see [ADR 0001](0001-sync-upload-pass-runs-to-completion.md).

## Consequences

- Coarse granularity: a whole session or routine is re-uploaded when any part changes, and a concurrent edit of the
  same root on two phones keeps only the later one.
- Remote deletes must never break foreign keys, so a tombstone for a variant the phone has logged deprecates it
  instead (`AggregateStore`).
- New synced data needs triggers and a migration; the migration snapshots are verified in CI (`verifySqlDelightMigration`).
