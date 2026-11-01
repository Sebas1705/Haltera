# ADR 0009: sync downloads follow the upload time, not `updatedAt`

- **Status:** accepted (2026-11-01)
- **Area:** `data:sync` (`SyncDocument.pushedAt`, `RemoteStore.changes`, `SyncEngineImpl`), `data:remote` (`FirestoreRemoteStore`, `RemoteDocument`), `docs/FIRESTORE-DATA.md`, `scripts/firestore-migrate-user.mjs` (amends the download side of [ADR 0004](0004-local-first-sqldelight-firestore-sync.md))

## Context

Each device downloaded a collection with `updatedAt > cursor - 7 days`, where the cursor was the largest `updatedAt`
it had seen. But `updatedAt` is the uploader's clock when the user changed the data, not when it reached the cloud.
A phone that stays offline for more than a week uploads documents dated before the cursor of every phone that kept
syncing, and those phones never download them: the data was in the cloud but only reachable from a fresh install.
The 7 day look-back only covered short offline periods.

## Decision

Every uploaded document carries `pushedAt`: the uploader's clock at the moment of the push. `RemoteStore.changes`
and the cursor follow `pushedAt`; conflicts are still decided by `updatedAt` (last writer wins), unchanged. The
7 day look-back stays, now covering differences between the phones' clocks instead of offline time.

Compatibility with documents and cursors that exist already:

- A filter on a field excludes the documents that lack it, so `FirestoreRemoteStore` queries with no filter when the
  cursor is 0 (the whole collection, ordered by `updatedAt`) and with `pushedAt > cursor` otherwise.
- The first time a device runs this version it discards its cursors (Meta `syncCursorKind`), so it downloads every
  collection once. That also recovers what the old rule had already missed. Applying a document that is not newer
  than the local root is a no-op, so the cost is one full read.
- A document written by an older app version has no `pushedAt` and is not seen by a device that has a cursor; all
  devices must update to this version. Haltera is in alpha with a single owner, so this is accepted.
- `scripts/firestore-migrate-user.mjs` stamps `pushedAt` on the documents it copies.

The cursor table keeps its column name `sinceUpdatedAt` (renaming would need a migration).

## Consequences

- A late upload reaches every device, however old its `updatedAt`.
- `pushedAt` uses the uploader's clock, like `updatedAt` always did. A phone with its clock far in the future would
  move other phones' cursors past later uploads; a server timestamp would avoid it, but it needs a pending-write
  placeholder in the snapshots and was not worth that complexity here.
- Every write gets one more small field; Firestore indexes it automatically (no composite index).
- Remote writes replace the whole document (`WriteBatch.set` without merge, and the payload is one JSON string),
  so there is no merging of nested maps. The emulator test in `firebase/rules-test` checks it.
- Checked by `LateUploadSyncTest` (fake cloud) and the "sync queries and writes" emulator tests.
