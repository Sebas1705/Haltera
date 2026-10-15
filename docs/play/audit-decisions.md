These are judgements, not measurements: they are written by hand and inserted into the generated report by
`scripts/verify-release.mjs`. Edit this file (`docs/play/audit-decisions.md`), not the report.

### Android Auto Backup: include the database (decided, implemented)

Ironlog is local-first and the account is optional, so most users have **no** cloud copy of their logbook. Without
Auto Backup, a new phone (or a reinstall) starts empty for them. Therefore the manifest keeps
`allowBackup="true"` and now declares what is backed up (`data_extraction_rules.xml` for Android 12+ and
`backup_rules.xml` for Android 6 to 11):

- **Included**: `ironlog.db` (with `-wal` and `-journal`) and `es.sebas1705.ironlog_preferences.xml` (units,
  theme, rest and nutrition settings, load steps). The sync state (dirty flags, cursors) lives inside the
  database, so a restored database is self-consistent with whatever the account has.
- **Excluded** (everything not listed): Firebase Authentication state, Google Play services caches, LeakCanary
  and the `ironlog_session.xml` live-session state. Restoring another device's auth tokens would be wrong, and
  after a restore the user simply signs in again; sync then merges by last write.
- Size is far below the 25 MB Auto Backup limit (the catalogue is read-only app data, not user data; only user
  rows are in the database). Data-safety form: the backup is Google's own device backup, not a transfer to us.
- Before the first production release, restore-test once on an emulator pair:
  `adb shell bmgr backupnow es.sebas1705.ironlog` then reinstall and `adb shell bmgr restore`.

### Exact alarms

`SCHEDULE_EXACT_ALARM` is declared for the rest timer; the code already falls back to an inexact alarm when the
user has not granted it (`RestAlarms`). On Android 14+ the permission is denied by default for new installs,
and Google Play restricts it to apps whose core function is alarms or calendar events. A rest timer notification
is not a core alarm-clock function, so expect the Console declaration to be questioned. The safest policy
position is not to declare `SCHEDULE_EXACT_ALARM` at all and to use an inexact `setAndAllowWhileIdle` alarm (or the foreground
service while the workout is open). Re-run the script after the exact-alarm compliance change lands: this report is regenerated from the
real merged manifest, and the flag disappears when the permission does.

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
