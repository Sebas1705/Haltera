# Architecture decision records

An ADR records one decision that shapes the code, why it was taken and what it costs. Write one when a decision is
hard to reverse or would surprise a new contributor. Copy [TEMPLATE.md](TEMPLATE.md), number it with the next free
`NNNN`, and keep it short. An accepted ADR is not rewritten: a later decision gets a new ADR that supersedes it.

Dates come from the git history of the change that introduced the decision. Rationale that is not written down in
the repository is not invented: an ADR says so instead.

| # | Decision | Status | Date |
|---|---|---|---|
| [0001](0001-sync-upload-pass-runs-to-completion.md) | A sync upload pass runs to completion | Accepted | 2026-10-29 |
| [0002](0002-logbook-not-coach.md) | Haltera is a logbook, not a coach | Accepted | 2026-10-14 |
| [0003](0003-kmp-compose-multiplatform-modules.md) | Kotlin Multiplatform with Compose Multiplatform and a layered module graph | Accepted | 2026-10-14 |
| [0004](0004-local-first-sqldelight-firestore-sync.md) | Local-first SQLDelight storage with a dirty-flag Firestore sync | Accepted | 2026-10-14 |
| [0005](0005-cloud-copy-is-opt-in.md) | The cloud copy is opt-in | Accepted | 2026-10-16 |
| [0006](0006-agp-9-androidapp-split.md) | AGP 9 and a pure-Android `androidApp` module | Accepted | 2026-10-25 |
| [0007](0007-crashlytics-android-only-opt-out.md) | Crashlytics on Android only, with a user opt-out | Accepted | 2026-10-29 |
| [0008](0008-rename-to-haltera-keep-firebase.md) | Rename Ironlog to Haltera and keep the Firebase project | Accepted | 2026-10-16 |
| [0009](0009-sync-downloads-follow-the-upload-time.md) | Sync downloads follow the upload time, not `updatedAt` | Accepted | 2026-11-01 |
| [0010](0010-upload-waits-for-the-first-download.md) | Uploads wait until the session has downloaded every collection | Accepted | 2026-11-01 |
| [0011](0011-unappliable-remote-document-is-held-not-blocking.md) | A remote document that cannot be applied is held back, not blocking | Accepted | 2026-11-01 |
