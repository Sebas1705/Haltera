# ADR 0001: a sync upload pass runs to completion

- **Status:** accepted (2026-10-29)
- **Area:** `data:sync`, `SyncEngineImpl.uploadLoop` (sync design: docs/designs/2026-10-14-sync-design.md §5)

## Context

Haltera is local-first: every write lands in SQLite, triggers mark its aggregate root dirty, and the sync
engine uploads dirty roots to Firestore (last writer wins by `updatedAt`). The upload loop followed the
dirty count with `collectLatest`, so any change of the count cancelled the pass in progress and started a
new one.

The pass itself changes that count: each acknowledged batch clears its dirty flags. So does every set the
user logs while an upload is in flight. The cancelled pass was usually waiting for the ack of a batch that
Firestore had already accepted (the SDK keeps a write whose caller stopped waiting), and the next pass pushed
that batch again. Pushes are idempotent under last-writer-wins, so no data was lost, but:

- every batch after the first of a pass was written twice (wasted Firestore writes on the Spark quota);
- while the user logged sets faster than an ack came back, no pass ever finished and nothing was marked as
  synced until they stopped.

The same bug was found and fixed in Owlsplit (Sebas1705/Owlsplit#91).

## Decision

An upload pass is not cancelled by a new dirty count. `uploadLoop` still uses `collectLatest`, but runs each
pass in `withContext(NonCancellable)`; only the backoff wait between failed passes is cancellable, so a new
change after a failure retries at once. Changes made during a pass are picked up by that same pass (it keeps
taking batches until nothing is dirty) or by the next one, which starts after it ends.

A pass still stops when sync stops (sign-out, cloud copy turned off, account forgotten): it checks the
upload loop's own job and the forgotten uid before each batch, so it ends after the batch in flight. A
pass cannot hang: every push has its own timeout (`FirestoreRemoteStore`, 30 s), and a failed push ends the pass.

The download side needs no change: listeners use `collect`, and applying a remote batch (`receive`) does not
suspend, so it cannot be cancelled halfway. The `collectLatest` over the account session is intended: a new
session must stop the previous one.

## Consequences

- Each document version is pushed once (`UploadLoopTest` checks this with a fake remote whose acks are slow and
  which keeps writes whose ack nobody awaits).
- Stopping sync can take up to one push (bounded by its timeout) instead of being immediate.
- Sync tests read a controllable clock (`TestClock`): the engine's `now` and the triggers' `julianday('now')`
  both use it, so `updatedAt` values are deterministic and no test sleeps.
