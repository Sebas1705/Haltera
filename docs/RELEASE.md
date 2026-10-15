# Release and distribution

Ironlog is distributed to testers through **Firebase App Distribution** (Firebase project
`ironlog-sebas1705`, group `testers`). Google Play is wired up but waits for the owner (see [Google Play](#google-play)).
The same Firebase project backs the account and sync feature (Authentication + Firestore, see
[Firebase in the app](#firebase-in-the-app) and docs/designs/2026-10-14-sync-design.md).

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
| `GOOGLE_SERVICES_JSON` | `google-services.json` with both Android apps (release and `.debug`) | by hand, from `firebase apps:sdkconfig` |
| `GOOGLE_SERVICE_INFO_PLIST` | `GoogleService-Info.plist` of the iOS app | by hand, from `firebase apps:sdkconfig` |

## How distribution works

`.github/workflows/distribute.yml` runs on every push to `main` that changes more than docs, and
manually from Actions (`workflow_dispatch`):

1. `.github/actions/build-release` checks that Doppler has every secret, decodes the keystore to
   the runner's temp dir, writes `composeApp/google-services.json` from `GOOGLE_SERVICES_JSON` and builds the release APK and AAB with
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
- Firestore `(default)` in `eur3`, Authentication with Anonymous and Google, rules deployed, config in
  Doppler (2026-10-14).

## Firebase in the app

Account and sync (F8) use Firebase Authentication (Anonymous + Google) and Cloud Firestore (`eur3`)
through GitLive Firebase KMP. The config files are **not** in the repository (git-ignored):

```bash
scripts/doppler-sync-local.sh   # writes composeApp/google-services.json and iosApp/iosApp/GoogleService-Info.plist
```

Without them the app builds and runs with sync off (`AccountSession.NotConfigured`): that is how the CI
checks build. The google-services Gradle plugin is applied only when `composeApp/google-services.json`
exists; iOS calls `FirebaseApp.configure()` only when the plist is bundled.

- Firebase apps: Android `es.sebas1705.ironlog` (release key SHA-1/256), Android
  `es.sebas1705.ironlog.debug` (`1:695669078657:android:9fbc91c513c691f3962b80`, debug keystore SHA-1/256
  of the owner's PC), iOS `es.sebas1705.ironlog` (`1:695669078657:ios:3fea52ea88df0696962b80`).
  A new developer machine needs its debug SHA-1 added (`firebase apps:android:sha:create`) for Google sign-in.
- Providers (enabled on 2026-10-14 through the Firebase provisioning API, no console step needed). To check or
  redo by hand: Console → Authentication → Sign-in method → Anonymous / Google → Enable.
- After adding an app or a SHA, regenerate the config and store it again:
  `firebase apps:sdkconfig ANDROID <app id> -o google-services.json` (it contains every Android app) and
  `doppler secrets set GOOGLE_SERVICES_JSON --project ironlog --config prd < google-services.json`
  (same for the plist), then delete the local copies and run `scripts/doppler-sync-local.sh`.
- Security rules: `firebase/firestore.rules` (each user reads/writes only `users/{uid}/**`), tested by
  `firebase/rules-test` on the emulator in CI (`firestore-rules` job). Deployed by hand, not from CI:
  `cd firebase && firebase deploy --only firestore:rules --project ironlog-sebas1705`.
- iOS: the URL scheme `GOOGLE_REVERSED_CLIENT_ID` in `iosApp/project.yml` must match the plist's
  `REVERSED_CLIENT_ID`.

## Releases (tags)

`.github/workflows/release.yml` makes a version. Push a tag `vX.Y.Z` (or `vX.Y.Z-beta.N`, a pre-release), or run the
workflow by hand with the version. It:

1. builds the signed APK and AAB with `.github/actions/build-release` (`VERSION_NAME` = the version,
   `VERSION_CODE` = commit count);
2. creates the GitHub Release with the APK, the AAB, their SHA-256 and the notes GitHub generates from the PRs
   since the previous tag;
3. turns those into en-US and es-ES notes (`scripts/release-notes.mjs`, only feat/fix/perf PRs, 500 characters)
   and uploads the APK to App Distribution with them;
4. uploads the AAB and R8 `mapping.txt` to Google Play, **only if** the repository variable `PLAY_TRACK` is set
   and `PLAY_SERVICE_ACCOUNT_JSON` exists in Doppler; otherwise those steps are skipped with a notice and the
   workflow still succeeds.

Before tagging, run `node scripts/verify-release.mjs` (release build, 16 KB alignment, manifest audit,
SDK levels, mapping) and read the diff of [docs/play/RELEASE-AUDIT.md](play/RELEASE-AUDIT.md).

## Google Play

Package `es.sebas1705.ironlog`. The pipeline is complete; what is missing is Console setup (owner only). The step by
step Console guide is coming as [docs/play/PLAY-CONSOLE-GUIDE.md](play/PLAY-CONSOLE-GUIDE.md).

| Piece | What it does |
|---|---|
| `.github/actions/play` | Runs `fastlane supply` with `PLAY_SERVICE_ACCOUNT_JSON` from Doppler `ironlog/prd`. Does nothing, and says so, while the secret is missing. |
| `release.yml` | Uploads the AAB (with mapping and changelogs) to the track in the variable `PLAY_TRACK`. `PLAY_RELEASE_STATUS` (optional variable, default `completed`) can be `draft` for an app that is not yet published. |
| `play-listing.yml` | On changes to `fastlane/metadata/android/**` on `main` (or by hand): publishes texts, icon and screenshots. Skipped while `PLAY_TRACK` is unset. |
| `play-promote.yml` | By hand: moves the latest release between tracks (internal, alpha, beta, production), optionally as a staged rollout. |

The listing lives in `fastlane/metadata/android/{en-US,es-ES}`: `title.txt`, `short_description.txt` (80 characters),
`full_description.txt` (4000) and `images/` (`icon.png`, `featureGraphic.png`, `phoneScreenshots`,
`sevenInchScreenshots`, `tenInchScreenshots`). `title.txt` currently holds the placeholder `Ironlog`; the display name is read
from one place (`app_name` in `composeApp/src/commonMain/composeResources/values/strings.xml`, via
`scripts/lib/app-name.mjs`) and the feature graphic is not generated until the name is final. The screenshots are real
captures from an emulator with seeded data: `node scripts/play-screenshots.mjs` (see its header).

### Owner-only steps

1. Create the app in Play Console, enrol in Play App Signing and register `ironlog-release.jks` as the upload key.
2. Complete App content (privacy policy, data safety, content rating, foreground service declaration for
   `specialUse`, the exact-alarm declaration if the permission stays), and the first manual upload of an AAB.
3. Create a service account with Play Console access (Release manager on this app), store its JSON key in Doppler:
   `doppler secrets set PLAY_SERVICE_ACCOUNT_JSON --project ironlog --config prd < play-key.json`
   and delete the local file.
4. Set the repository variable: `gh variable set PLAY_TRACK --body internal` (or `alpha` for closed testing).
5. Tag a release. Promote with *Play promote* when the testing requirements are met.
