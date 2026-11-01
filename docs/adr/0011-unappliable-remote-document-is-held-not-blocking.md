# ADR 0011: a remote document that cannot be applied is held back, not blocking

- **Status:** accepted (2026-11-01)
- **Area:** `data:sync`, `SyncEngineImpl.receive` (amends the download side of [ADR 0004](0004-local-first-sqldelight-firestore-sync.md))

## Context

`receive` applies the documents of a batch oldest first. When one failed to apply (in practice a foreign key: a
session whose USER exercise variant, or a food of a meal day, has not reached this device) it logged the error and
`break`-ed out of the loop. That had two effects:

- every newer document of the same batch was skipped, even though it was unrelated;
- the cursor stayed before the failing document, so each later pass started from it again. If the dependency never
  arrives (it was deleted, or belongs to a catalogue this build does not have) the collection stays blocked on this
  device for good. And a later live batch that did apply moved the cursor past the failing document, which was then
  lost until a restart.

## Decision

A document that fails to apply is held back in memory, keyed by collection and id, and never stops the rest:

- the other documents of the batch are applied, and the failed ones are retried within the same call while that
  makes progress (a recipe and its ingredient can arrive in either order);
- whenever any document is applied, the held ones of every collection are retried, because that document may be
  what they were waiting for (a USER exercise arriving after the session that logged it);
- a newer remote version of a held document replaces it;
- the persisted cursor never goes past the oldest held document (it stays one millisecond before it), so a restart,
  `syncNow` or a new session fetches it again. Once nothing is held the cursor moves on as before;
- the held map is dropped when a session starts and when the account is forgotten.

## Consequences

- One broken document costs only itself. Its retries cost local work, not reads, while the session lives.
- A document that can never be applied pins the cursor, so every start re-reads the collection from that point
  (the documents are skipped cheaply because they are not newer than the local roots). It is logged on each attempt.
  Surfacing such documents to the user is not done.
- Checked by `UnappliableDocumentTest`.
