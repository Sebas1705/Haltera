# Release and distribution

Ironlog is distributed to testers through **Firebase App Distribution** (Firebase project
`ironlog-sebas1705`, group `testers`). Google Play comes later (see [Play Store](#play-store-later)).
The app has **no Firebase SDK**: Firebase is only used to hand out builds.

The app testers get is always the same: `es.sebas1705.ironlog`, signed with the release (upload) key.
Debug builds (`es.sebas1705.ironlog.debug`, `./gradlew :composeApp:installDebug`) are for local
development only and install alongside it.

## Secrets: Doppler only

Every secret lives in Doppler, project `ironlog`, config `prd`. Nothing secret is committed to the
repository, and GitHub only stores `DOPPLER_TOKEN`, a read-only Doppler service token for `ironlog/prd`.

| Doppler secret (`ironlog/prd`) | What it is | Set by |
|---|---|---|
| `SIGNING_KEYSTORE_BASE64` | Release keystore (PKCS12), base64 | `scripts/setup-release-keystore.sh` |
| `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_PASSWORD` | Its password (same for store and key) | `scripts/setup-release-keystore.sh` |
| `SIGNING_KEY_ALIAS` | `ironlog` | `scripts/setup-release-keystore.sh` |
| `FIREBASE_ANDROID_APP_ID` | Android app id in App Distribution | by hand (`doppler secrets set`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Key of the `ironlog-ci` service account | `scripts/setup-ci-access.mjs` |

## How distribution works

`.github/workflows/distribute.yml` runs on every push to `main` that changes more than docs, and
manually from Actions (`workflow_dispatch`):

1. `.github/actions/build-release` checks that Doppler has every secret, decodes the keystore to
   the runner's temp dir and builds the release APK and AAB with
   `VERSION_CODE` = commit count of `main` and `VERSION_NAME` = `main-<count>`.
2. The APK is uploaded to App Distribution, group `testers`, with the commit subject as release notes.
3. The keystore and the service account key are removed from the runner.

`versionCode` is the number of commits on `main`, so it always grows and each build installs over
the previous one. Testers get an email from App Distribution with the download link (the first time
they accept the invitation and install from the browser or the *Firebase App Tester* app).

Locally, without those variables, `./gradlew :composeApp:assembleRelease` builds a release APK
(R8 on) signed with the debug key and the version from the convention plugin. To build one signed
with the real key:

```bash
doppler run --project ironlog --config prd -- bash -c \
  'printf "%s" "$SIGNING_KEYSTORE_BASE64" | base64 -d > "$TEMP/ironlog-release.jks"'
SIGNING_KEYSTORE_PATH="$TEMP/ironlog-release.jks" \
  doppler run --project ironlog --config prd -- bash -c './gradlew :composeApp:assembleRelease'
rm -f "$TEMP/ironlog-release.jks"
```

## One-off scripts (already run on 2026-10-14)

- `scripts/setup-release-keystore.sh`: creates `~/Documents/ironlog-release.jks` (alias `ironlog`,
  RSA 4096, 30 years), stores it and its password in Doppler and registers its SHA-1/SHA-256 on the
  Firebase Android app. It refuses to run if the keystore or the Doppler secret already exists.
- `scripts/setup-ci-access.mjs [tester-email ...]`: service account `ironlog-ci` with only
  App Distribution Admin and Service Usage Consumer, its key in Doppler, the read-only Doppler token in
  the GitHub secret `DOPPLER_TOKEN`, and the App Distribution group `testers` (emails passed are added).
  Running it again adds a new key (rotation) and more testers.

## Setup state

- Firebase project `ironlog-sebas1705` (display name Ironlog; the ids `ironlog` and `ironlog-app`
  were taken). Android app `es.sebas1705.ironlog`: `1:695669078657:android:4ae26e75a4756aff962b80`.
- Release key `Documents\ironlog-release.jks`:
  SHA-1 `27:D8:91:F6:CB:E7:87:78:8D:DD:18:FC:02:70:EF:5B:8A:93:7E:24`,
  SHA-256 `45:53:E7:BB:C2:DB:B1:14:23:A1:06:37:B1:8C:45:63:C9:75:B1:21:95:A8:4D:14:4F:77:EE:27:F2:13:C9:7B`.
  **Keep a copy of the `.jks` off this PC**: it cannot be replaced once a build signed with it ships.
- Service account `ironlog-ci@ironlog-sebas1705.iam.gserviceaccount.com`, `DOPPLER_TOKEN` in GitHub,
  group `testers` with the owner as first tester.

## Play Store (later)

Not automated yet: there is no `release.yml` and no Play upload. Steps still to do, owner only:

1. Create the app `es.sebas1705.ironlog` in the Play Console (free app).
2. Enroll in **Play App Signing** and register our key as the **upload key** (Google keeps the app
   signing key; `ironlog-release.jks` stays our upload key, so the CI AAB can be uploaded as is).
3. Fill in the store listing (texts en/es, screenshots, icon, feature graphic), content rating, data
   safety and privacy policy.
4. Then (F5): a Play service account in Doppler and a `release.yml` that uploads the AAB from
   `build-release` to a Play track on `vX.Y.Z` tags.
