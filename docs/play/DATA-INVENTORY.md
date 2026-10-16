# Haltera: data inventory

Source of truth for the privacy policy (`firebase/hosting/privacy.html`, `privacidad.html`), the account
deletion pages and the Play Console Data safety form ([PLAY-CONSOLE-GUIDE.md](PLAY-CONSOLE-GUIDE.md)).
Derived from the code and the merged release manifest ([RELEASE-AUDIT.md](RELEASE-AUDIT.md)) on 2026-10-16, not
assumed. Haltera was called Ironlog until the rename; code paths below still show the former package name
(`es.sebas1705.ironlog`) until the rename lands, the released package will be `es.sebas1705.haltera`.

**If the code changes what is collected or sent, change this file, both privacy pages and the Data safety form in
the same PR.**

Legend: **Verified** = read in code or in the merged manifest. **Uncertain** = depends on a third-party SDK's own
behaviour that cannot be read from this repository.

## 1. What the app stores on the device (always)

| Data | Where | Notes |
|---|---|---|
| Routines (name, description, days, slots, targets, rest times, progression rules) | SQLite `ironlog.db` (becomes the Haltera database name; see the rename) | `data/local` `.sq` files |
| Workout sessions: dates, exercises, sets, reps, loads (grams), RPE, durations, notes, body weight at the time | same | `WorkoutSessionEntity`, `SetLogEntity` |
| Body weight entries (date, weight, note) | same | `BodyWeightEntity` |
| Custom exercises and variants; for one imported from a shared routine, its **creator name** (as typed by whoever shared it) and origin id | same | `ExerciseEntity.author`, `originId` (schema 6); synced with the exercise in `exercises` while the cloud copy is on |
| Nutrition: custom foods and recipes, foods logged from Open Food Facts (name, brand, barcode, macros), meal entries per day | same | `FoodEntity`, `MealEntryEntity` |
| Settings: units, bar weight, plates, keep screen on, rest sound and vibration, nutrition goals | DataStore preferences | `SettingsPrefs` |
| "Your name for shared routines" (user-entered, optional; filled once with the Google display name when a Google account is linked and the user never set it) | DataStore preferences (`settings.shareName`) | Device-local: never synced; cleared by "Also erase data on this device" |
| Nutrition profile used for calorie goals: **sex, birth year, height, activity level, objective**, macro goals | DataStore preferences | `NutritionPrefs`; all optional (null by default) |
| Live workout state (current session) | `ironlog_session.xml` | Excluded from Android backup |

Android Auto Backup is on (`allowBackup="true"` with `data_extraction_rules.xml` and `backup_rules.xml`): the database
and the preferences file are included in Google's own device backup, which is the user's Google account backup, not
a transfer to the developer (docs/play/audit-decisions.md). **Verified** (manifest and decisions file).

## 2. What leaves the device, and to whom

### 2.1 Firebase Authentication and Cloud Firestore (Google LLC / Google Ireland, project `ironlog-sebas1705`, Firestore in `eur3`, Europe)

**The cloud copy is optional and off by default** (P3, finding 1 below). **Verified in code:**

- The choice lives in `CloudBackupStore` (`core:datastore`, keys `cloud.enabled`, `cloud.promptHandled`,
  `cloud.noticePending` in the settings file): device-local, never synced, kept by "Also erase data on this device".
- `SyncStarter.start()` no longer signs in. It calls `ensureSignedIn` (anonymous sign-in, retrying while offline)
  **only while the switch is on**. `SyncEngineImpl` takes the same switch (`enabled`) and does not run, so no
  Firestore read or write happens, while it is off, even if a user is signed in. The sensitive nutrition-profile
  fields (sex, birth year, height) travel inside `settings/user`, which only the running engine uploads, so they
  stay on the device unless the cloud copy is on. Covered by `SyncEngineTest` and `SyncStarterTest`.
- **Fresh install:** no account, no network call to Firebase, until the user taps "Yes, back up" on the one-time
  card on Today or turns on Settings → Account → "Back up to the cloud". "Not now" is remembered and the card is not
  shown again. Opting in signs in anonymously, marks every local aggregate dirty (`SyncEngine.markAllDirty`) so what
  was logged before is uploaded, and syncs. Linking Google is offered only while the switch is on.
- **Existing installs** that already had a Firebase user when this version first starts (anonymous or Google) are
  migrated to "on" (`CloudBackupStore.adoptExistingAccount`) and see a one-time notice on Today saying the cloud copy
  is now optional and where the switch is. Nobody is silently stopped and nothing is deleted automatically.
- **Turning it off:** the engine stops, the local data stays, the Firebase user and the cloud documents are kept
  (turning it on again resumes under the same uid) unless the user ticks "Also delete my cloud data" in the
  confirmation dialog, which runs the existing delete path (`AccountViewModel.deleteAccount`: forget account, delete
  every document, delete the Auth user) without touching the data on the phone. "Delete account" also turns the
  switch off.
- Android Auto Backup copies the settings file, so after a restore the switch keeps its value: if it was on, the
  app signs in anonymously on the restored phone (a new uid) and uploads the restored data.

Below, "sent when" means: only while the cloud copy is on.

| Item | Sent when | Where it ends up |
|---|---|---|
| Firebase Auth anonymous user id (uid) | when the cloud copy is turned on | Firebase Auth |
| Google account email, display name, Google account id, profile photo URL (held by Firebase Auth when the provider is linked; the app only reads email and display name) | only if the user links or signs in with Google | Firebase Auth |
| `users/{uid}/routines`, `sessions` (with exercises and sets, loads, notes, `bodyWeightGrams`), `bodyWeight`, `exercises` (custom), `settings/user` (preferences and nutrition goals including sex, birth year, height), `foods`, `mealDays` | while the cloud copy is on: when it is turned on, then continuously while online, on every change | Firestore, as documents `{payload (JSON string), updatedAt, deleted, schema}` |
| IP address and technical metadata of the requests | every request made while the cloud copy is on | Google (Auth, Firestore). Processed by Google as processor; not stored by the developer. |

Security rules (`firebase/firestore.rules`): a signed-in user reads and writes only `users/{their uid}/**`.
**Verified.**

Turning the cloud copy off or signing out keeps the local data and stops syncing. **Delete account** (Settings → Account) does, in this order
(`AccountViewModel.deleteAccount`): stop sync for the uid and drop pending uploads, delete **every document** of the
user in every synced collection (`RemoteStore.deleteAll`, batches of 450), delete the Firebase Auth user
(re-authenticating with Google once if Firebase asks, an anonymous user is just signed out because its cloud data is
already gone), and, only if the box "Also erase data on this device" is ticked (default off), wipe the user tables,
settings and load steps on the phone (`LocalDataEraser`). The bundled catalogue stays. **Verified.**

Tombstones: while the account exists, deleting one item in the app writes a tombstone document (`deleted = true`,
empty payload) rather than removing the document; they disappear with the account deletion. **Verified** (design §3).

### 2.2 Open Food Facts (Open Food Facts association, France; `world.openfoodfacts.org`)

**Verified** (`OpenFoodFactsClient`): `GET /api/v3/product/{barcode}` when the user scans or types a product barcode
in Nutrition. Sent: the barcode, the IP address (inherent to HTTPS), and the User-Agent
`Ironlog/<appVersion> (+https://github.com/Sebas1705/Ironlog)` (becomes Haltera/GitHub Haltera after the rename). No
account, no user id, no cookies. Rate limited client side to 15 reads per minute. Only on user action.
Open Food Facts publishes its own privacy policy; the developer does not control it.

### 2.3 GitHub raw content (GitHub, Inc.; `raw.githubusercontent.com`)

**Verified** (`FRAMES_BASE_URL`, Coil with `coil-network-ktor3`): exercise demonstration frames are loaded from
`https://raw.githubusercontent.com/yuhonas/free-exercise-db/<pinned commit>/exercises/...` when an exercise detail
is shown. Sent: the image path, the IP address and the User-Agent of the HTTP client; no account, no user id.
The images are not bundled and are not cached by the developer. Cache behaviour on the device is Coil's.

### 2.4 Google Play services

- **Credential Manager / Google Sign-In** (`androidx.credentials`, `googleid`): shown only when the user taps "Link
  Google" or "Sign in with Google". Handled by Google.
- **Google code scanner** (`play-services-code-scanner`, ML Kit): scans routine QR codes and product barcodes with
  Google's own scanner UI, **no CAMERA permission in the app** (Verified: not in the merged manifest). The image
  processing is on-device. **Uncertain:** the ML Kit / Play services module may send usage and performance
  metrics to Google (the merged manifest contains `MlKitComponentDiscoveryService`, `MlKitInitProvider` and the
  datatransport scheduler). Google's ML Kit data-disclosure page lists what it collects; the app does not
  control it and sends no image to the developer.
- **Google Play** (store, updates, optional Auto Backup): Google's own processing.

### 2.5 The developer

No server of the developer's own. The developer can read Firestore through the Firebase console and tools in the
repository (`scripts/firestore-backup.mjs`, `scripts/firestore-migrate-user.mjs`, `docs/FIRESTORE-DATA.md`), and
manual backups written by `firestore-backup.mjs` contain personal data and live outside the repository. They are
exceptional (used for the Ironlog → Haltera migration). **Uncertain / owner decision:** there is no automated
deletion of such backups; the policy states they are deleted when no longer needed, which the owner must honour.

### 2.6 Things the user does that the developer never sees

Routine sharing (`.ironlog` file and QR code) goes through the Android share sheet or the scanner, device to device.
Nothing is sent to a server of the developer. **Verified** (no endpoint in `data/repository/.../share`). A shared routine
carries the custom exercises it uses and, on the ones the sharer created, the sharer's "Your name for shared routines"
(nothing when it is empty). That name leaves the phone **only inside the files and QR codes the user chooses to
share**, to whoever they give them to; the receiver's app stores it as the creator of the imported exercise ("by
<name>") and keeps it when that exercise is shared again. **Verified** (`RoutineExport.toFile`,
`SharedCustomExercisesTest`).

## 3. What the app does NOT do (each verified)

| Claim | How it was verified |
|---|---|
| No ads, no advertising ID | No ads SDK in `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` or any module build file; no `com.google.android.gms.permission.AD_ID` in the merged release manifest (RELEASE-AUDIT, permissions list). |
| No analytics, no Crashlytics, no performance monitoring, no remote config, no FCM | `grep -riE "crashlytics\|analytics\|firebase-messaging\|remote-config\|performance"` over `*.kts`, `*.toml`, manifests returns nothing relevant. Firebase components in the merged manifest: only `ComponentDiscoveryService`, `FirebaseInitProvider`, auth activities; no `AppMeasurement*`, no Crashlytics provider. Only `firebase-auth` and `firebase-firestore` (GitLive 2.7.0) are dependencies. |
| No location | No `ACCESS_*_LOCATION` in the merged manifest. |
| No contacts, SMS, calendar, microphone, storage permission | Not in the merged manifest (permissions: ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_BIOMETRIC, USE_FINGERPRINT, VIBRATE, READ_GSERVICES, the app's own dynamic receiver permission). |
| No camera permission | Not in the manifest; scanning uses the Google code scanner. |
| No in-app purchases, no billing, no subscriptions | No billing library in the catalogue. |
| No health platform integration | No Health Connect, Google Fit or Samsung Health dependency or permission. |
| Biometrics not used by the app | `USE_BIOMETRIC` and `USE_FINGERPRINT` come from Firebase Auth / Credential Manager (audit-decisions.md); there is no app code calling BiometricPrompt. |
| No third-party tracking of users by the developer | No SDK other than the ones listed in section 2. |

## 4. Permissions and why

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Sync and sign-in (Firebase), barcode lookup, exercise images |
| `POST_NOTIFICATIONS` | Rest timer and live workout notification; the app works if denied |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Live workout notification (`SessionService`, `specialUse`, subtype property present) |
| `SCHEDULE_EXACT_ALARM` | The rest-timer alert on time with the screen off; falls back to an inexact alarm |
| `VIBRATE` | Optional rest-timer vibration |
| `USE_BIOMETRIC`, `USE_FINGERPRINT`, `READ_GSERVICES` | Library-provided (Firebase Auth, Credential Manager, Play services) |

## 5. Findings the owner should know

1. **Cloud copy is opt-in (resolved by P3).** It used to be automatic: every install with the Firebase config signed
   in anonymously on first launch and uploaded the whole logbook, including sex, birth year and height. Now nothing
   is sent until the user says yes (section 2.1). The policy pages, the delete-account pages, the Data safety answers
   (collection is **optional**) and the store descriptions must all say the same: "works offline; an optional
   cloud copy, off until you turn it on". Remaining risk: installs that were already syncing before the change keep
   their cloud copy (with a notice), so the Data safety form still declares the data as collected, now as optional.
2. **The app does not show the account identifier.** Settings → Account shows only "Backed up anonymously" or the
   Google email. An anonymous user therefore has no identifier to quote in a deletion request. The deletion pages
   say so honestly and give the in-app route as the real one. Suggested improvement: show the uid (selectable text
   or a copy button) in Settings → Account.
3. **Sensitive-ish fields are synced only with the cloud copy on:** sex, birth year and height of the nutrition
   profile, body weight, and free text notes. The policy lists them; none of this is shared with anyone.
4. **Identity cannot be verified for a request outside the app.** The developer cannot tell who owns an
   anonymous uid or an email posted in a GitHub issue. The deletion page describes what is actually possible.
5. **Manual backups of Firestore** exist outside the repo during the migration (section 2.5).
6. **Uncertain: ML Kit / Play services telemetry** (section 2.4).
