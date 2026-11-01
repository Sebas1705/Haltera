# ADR 0010: uploads wait until the session has downloaded every collection

- **Status:** accepted (2026-11-01)
- **Area:** `data:sync`, `SyncEngineImpl.run` / `pass` / `listen` / `uploadLoop` (refines the upload side of [ADR 0004](0004-local-first-sqldelight-firestore-sync.md))

## Context

Conflicts are last writer wins by `updatedAt`, but the check only happens on the download side (`receive` skips a
remote document that is not newer than the local root). The push itself is a plain Firestore `set`: it does not
compare with what the cloud holds.

`pass` (the first download followed by the first upload) already skipped the upload when a download failed. But
`run` starts the upload loop right after `pass`, and the loop pushes whatever is dirty. With the first download
failed (offline start, timeout, a listener that errors) a root edited here before another device pushed a newer
version of it was written over the newer version, and that edit was lost on every device.

## Decision

The engine tracks which collections the current session has downloaded (`caughtUp`): all of them when `pass`
succeeds, or each one as its live listener delivers its first batch (the listener retries with backoff). The upload
loop waits for all of them before its first push, and the wait is cancelled with the loop when sync stops. A new
session starts with an empty set. The dirty flags are untouched, so what was edited meanwhile is uploaded afterwards,
and a pass still runs to completion once it has started ([ADR 0001](0001-sync-upload-pass-runs-to-completion.md)).

## Consequences

- With the cloud unreachable at start nothing is pushed until the download works. Local use is unaffected
  (local-first); only the upload is delayed, which it would have been anyway because the push needs the same network.
- This closes the stale-start case, not every race: a document pushed by another device between this phone's download
  and its push can still be overwritten. Making the push conditional would need a server-side check (security rules
  or a transaction per document) and is not done.
- Checked by `UploadWaitsForCatchUpTest`.
