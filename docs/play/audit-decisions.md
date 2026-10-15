These are judgements, not measurements: they are written by hand and inserted into the generated report by
`scripts/verify-release.mjs`. Edit this file (`docs/play/audit-decisions.md`), not the report.

### Android Auto Backup: include the database (decided, implemented)

Haltera is local-first and the account is optional, so most users have **no** cloud copy of their logbook. Without
Auto Backup, a new phone (or a reinstall) starts empty for them. Therefore the manifest keeps
`allowBackup="true"` and now declares what is backed up (`data_extraction_rules.xml` for Android 12+ and
`backup_rules.xml` for Android 6 to 11):

- **Included**: `haltera.db` (with `-wal` and `-journal`) and `es.sebas1705.haltera_preferences.xml` (units,
  theme, rest and nutrition settings, load steps). The sync state (dirty flags, cursors) lives inside the
  database, so a restored database is self-consistent with whatever the account has.
- **Excluded** (everything not listed): Firebase Authentication state, Google Play services caches, LeakCanary
  and the `haltera_session.xml` live-session state. Restoring another device's auth tokens would be wrong, and
  after a restore the user simply signs in again; sync then merges by last write.
- Size is far below the 25 MB Auto Backup limit (the catalogue is read-only app data, not user data; only user
  rows are in the database). Data-safety form: the backup is Google's own device backup, not a transfer to us.
- Before the first production release, restore-test once on an emulator pair:
  `adb shell bmgr backupnow es.sebas1705.haltera` then reinstall and `adb shell bmgr restore`.

### Exact alarms (decided: keep declared for now)

`SCHEDULE_EXACT_ALARM` is declared so the rest-timer alert fires on time with the screen off. The code already
falls back to an inexact alarm (`setAndAllowWhileIdle`) when the user has not granted it (`RestAlarms`), and the
parallel `play-compliance` work adds a polite one-time prompt that explains why the permission helps and never
blocks the app.

Trade-off: on Android 14+ the permission is denied by default for new installs, and Google Play restricts it to
apps whose core function is alarms or calendar events. A rest timer is arguably not that, so the Console
declaration may be questioned. Decision: keep it declared for now (timers are accurate for users who allow it) and
revisit if Play review pushes back; dropping the permission is a one-line manifest change and the inexact fallback
already works. Re-run `scripts/verify-release.mjs` after any change: this report comes from the real merged manifest.

### Foreground service `specialUse`

The live-session service uses `specialUse` with the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property, which
is present in the manifest. Play Console still needs a **foreground service declaration** (App content) with a
description and a short demo video of the notification during a workout.

### Other items

- `INTERNET` and `ACCESS_NETWORK_STATE` come from the optional sync/sign-in (Firebase) and the Google code
  scanner; no advertising ID, no location, no contacts, no camera permission (QR scanning uses the Google code
  scanner UI).
- `USE_BIOMETRIC`/`USE_FINGERPRINT` come from the Firebase Auth / Credential Manager dependencies, not from app
  code; Play does not restrict them.
- Exported components other than the main activity are library-provided and permission-guarded
  (`RevocationBoundService`, `ProfileInstallReceiver`) or are the Firebase auth redirect activities.
- Release is minified by R8 and ships `mapping.txt`; `release.yml` uploads it with the AAB.
