# ADR 0005: The cloud copy is opt-in

- **Status:** accepted; amends the first-launch behaviour of [ADR 0004](0004-local-first-sqldelight-firestore-sync.md)
- **Date:** 2026-10-16 (`Make the cloud copy optional: nothing leaves the device until the user opts in`, #32)
- **Area:** `composeApp` account (`CloudPrompt`, `SyncStarter`, `AccountSection`), `core:datastore` (`CloudBackupStore`), `firebase/hosting` pages

## Context

The original sync design signed in anonymously on first launch and synced from the start. The commit that changed it
also updated the privacy and delete-account pages, the data inventory and the Play Console guide, and a follow-up
(#28) changed the store descriptions to say the backup is optional. The reason is privacy: this is health and
training data, so nothing leaves the device until the user chooses it.

## Decision

- A fresh install does not sign in or touch Firestore. The first launch asks once ("Yes" / "Not now") on Today, and
  Settings > Account has a "Back up to the cloud" switch.
- Opting in signs in anonymously and uploads what was logged before. Installs that were already signed in keep
  syncing and see a one-time notice.
- Turning it off stops sync, keeps local data and offers to also delete the cloud data.
- The sync engine follows the same switch, so for example the nutrition profile (sex, birth year, height) is not
  uploaded while it is off. The choice is device-local (`CloudBackupStore`).

## Consequences

- The app behaves the same offline and without any account; sync is an add-on.
- The privacy page, data inventory and Play listing describe the cloud copy as optional and must stay in step
  with the switch.
- Account deletion remains available (Settings > Account, plus the public deletion page).
