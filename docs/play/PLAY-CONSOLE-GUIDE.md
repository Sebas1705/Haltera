# Guide: publishing Haltera on Google Play (manual steps)

What **only the account owner can do** in Play Console and Google Cloud. What can be automated (AAB upload from
CI, web pages, listing text and graphics) is already in the repo.

- App: **Haltera** (formerly Ironlog) · package `es.sebas1705.haltera` · free, no ads, no purchases.
- Developer account: **personal** (it has already published other apps).
- Checked in the repo on 2026-10-16: `minSdk 24`, `targetSdk 36`, `compileSdk 36`; release audit in
  [RELEASE-AUDIT.md](RELEASE-AUDIT.md); what the app collects in [DATA-INVENTORY.md](DATA-INVENTORY.md).
- Anything marked **[unverified]** could not be confirmed in the official documentation: trust what the Console says.
- Play Console labels are given in English, with the Spanish label in parentheses.

Public pages (served from `firebase/hosting` by `.github/workflows/deploy-firebase.yml`):

| Page | URL |
|---|---|
| Home | `https://haltera.web.app/` |
| Privacy policy | `https://haltera.web.app/privacy` (Spanish: `/privacidad`) |
| Account deletion | `https://haltera.web.app/delete-account` (Spanish: `/borrar-cuenta`) |

**Publish them before submitting any form** (merge the PR, or run the *Deploy Firebase Hosting* workflow; Play
checks the URLs).

---

## Checklist (in this order)

- [ ] **1.** Create the app in Play Console
- [ ] **2.** Play App Signing with **our** key (before uploading anything)
- [ ] **3.** App content: privacy policy, ads, app access, advertising ID, content rating, target audience, Data safety, Health apps, Health Connect permissions, foreground service, exact alarm, financial features and the rest
- [ ] **4.** Store listing (text and graphics already in the repo)
- [ ] **5.** Service account for the Play API, JSON in Doppler (`PLAY_SERVICE_ACCOUNT_JSON`)
- [ ] **6.** First AAB uploaded **by hand** to Closed testing
- [ ] **7.** 12 or more testers through a Google Group
- [ ] **8.** 14 days without opt-outs
- [ ] **9.** Apply for production
- [ ] **10.** Android developer verification
- [ ] **11.** Move the Ironlog testers to Haltera

> Do not use fake or purchased tester accounts: it breaks Play policies and can end in account suspension.

---

## Phase 1. Create the app

**Where:** Play Console → *Home (Inicio)* → **Create app (Crear aplicación)**.

| Field | Value |
|---|---|
| App name (Nombre de la aplicación) | `Haltera` |
| Default language (Idioma predeterminado) | Spanish (Spain) – es-ES (Español (España) – es-ES) |
| App or game? (¿Aplicación o juego?) | **App (Aplicación)** |
| Free or paid? (¿Gratuita o de pago?) | **Free (Gratuita)** (it cannot be changed to paid later) |
| Declarations (Declaraciones) | Tick the Developer Program Policies and US export laws boxes yourself. |

Category and tags are set later (Phase 4): **Health & Fitness (Salud y bienestar)**. The package name is fixed once the
first AAB is uploaded; creating the app registers it for Android developer verification too (Phase 10).

---

## Phase 2. App signing: use OUR key (critical)

### Why

Builds from GitHub Releases and Firebase App Distribution are signed with the release key
(`ironlog-release.jks`, alias `ironlog`, RSA 4096). Its fingerprints (docs/RELEASE.md):

- SHA-1 `27:D8:91:F6:CB:E7:87:78:8D:DD:18:FC:02:70:EF:5B:8A:93:7E:24`
- SHA-256 `45:53:E7:BB:C2:DB:B1:14:23:A1:06:37:B1:8C:45:63:C9:75:B1:21:95:A8:4D:14:4F:77:EE:27:F2:13:C9:7B`

That SHA is registered on **both** Firebase Android apps: the old one (`es.sebas1705.ironlog`) and the new one
(`es.sebas1705.haltera`), so Google sign-in works in either. If Play generated its own key, Google sign-in in the
Play build would break until Google's SHA was added in Firebase, and APKs from GitHub could not be updated by Play
builds. With our key as the *app signing key* nothing changes.

> New apps are enrolled in Play App Signing by default, with a Google-generated key. The key can only be replaced
> **before** a release reaches open testing or production. Do it before the first AAB.

### How

1. **Where:** when you create the first release (Phase 6), in **App integrity (Integridad de la app)** tap **Change app signing key (Cambiar clave de firma de apps)**. Also under *Protected with Play → Play Store distribution → Play App Signing* **[menu path unverified]**.
2. Choose **"Export and upload a key from Java keystore" ("Exportar y subir una clave de un almacén de claves Java")**. The Console offers two downloads: **`pepk.jar`** and **`encryption_public_key.pem`**.
3. Extract the keystore from Doppler (`ironlog/prd`, secret `SIGNING_KEYSTORE_BASE64`) to a folder **outside the repo**, or use your copy `Documents\ironlog-release.jks`:

   ```bash
   # Git Bash. Write the .jks outside the repo and delete it when done.
   doppler secrets get SIGNING_KEYSTORE_BASE64 --project ironlog --config prd --plain | base64 -d > ~/ironlog-release.jks
   doppler secrets get SIGNING_KEY_ALIAS --project ironlog --config prd --plain   # should be: ironlog
   ```

4. Run PEPK. **Copy the command the Console shows you**, that is the one that counts. It looks like (single line):

   ```bash
   java -jar pepk.jar --keystore=~/ironlog-release.jks --alias=ironlog --output=haltera-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```

   It asks for the store and key passwords (`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`; same value). Type them
   when prompted; do not paste them into the command. `--output` is a `.zip` **file**.
5. Upload `haltera-key.zip` in the Console and tap **Save (Guardar)**.
6. **Check:** *Play App Signing → App signing key certificate*: the SHA-256 must start with `45:53:E7:BB` and end with
   `F2:13:C9:7B`. If it does not match, **upload no AAB** and stop.
7. **Upload key:** CI signs the AAB with the same key, so upload key = signing key. Allowed (Google recommends
   different keys; request an *upload key reset* later if you want that and adapt CI).
8. Delete `~/haltera-key.zip` and `~/ironlog-release.jks`. Keep a copy of the `.jks` **off this PC**: it cannot be
   replaced once a build signed with it ships.

---

## Phase 3. App content

**Where:** *Monitor and improve (Supervisar y mejorar)* → **Policy and programs → App content (Política y programas → Contenido de la app)**. Complete each card.

### 3.1 Privacy policy
`https://haltera.web.app/privacy`

### 3.2 Ads
Does it contain ads? **No.**

### 3.3 App access
**"All functionality is available without special access"** ("Todas las funciones están disponibles sin acceso
especial"). Reason (code): the cloud copy is optional and off until the user turns it on (`SyncStarter`,
`CloudBackupStore`), and linking Google is optional. No login screen, no test credentials needed.

### 3.4 Advertising ID
Does it use the advertising ID? **No.** The merged release manifest has no `AD_ID` permission and there is no ads or
analytics SDK (DATA-INVENTORY.md section 3).

### 3.5 Content rating (IARC)
- Email: yours. Category: **All other app types** (utility, reference or similar; it is not a game or social network)
  **[exact label unverified]**.
- Violence, fear, sexuality, crude language, controlled substances, gambling: **No** to all.
- Users can interact or exchange content? **No.** There is no chat, no profiles, no public content; routines are shared
  by the user as a file or QR through the system share sheet (that is the user's own sharing, not an in-app
  social feature). If the questionnaire insists, answer **Yes** only for "share files" **[unverified]**.
- Shares the user's location with other users? **No.** Digital purchases? **No.** Unrestricted web access? **No.**
- Health and fitness note: the questionnaire may ask about health claims; the app makes none (logbook only).
- Expected: Everyone / PEGI 3.

### 3.6 Target audience and content
- Age groups: **only "18 and over" (18 años o más)**.
- Reasons: the app records body weight, calorie and macro goals, and sex, birth year and height for estimating
  energy needs. Body-weight and diet tracking is a sensitive area for minors (disordered eating), and the privacy
  policy says the app is for 18+. Including under-13s would bring the *Families* policy and certified SDKs, which
  is not worth it.
- Could it appeal to children? **No.**

### 3.7 Data safety (Seguridad de los datos)

Based on the inventory (`docs/play/DATA-INVENTORY.md`), `firebase/firestore.rules`, `data/remote`, `data/sync` and
the merged manifest. **The cloud copy is optional (off until the user turns it on, P3), so every cloud data type is declared as collected but optional.**
Installs that were already syncing before P3 keep their copy, which is why the data is still declared as collected.

**General**

| Question | Answer |
|---|---|
| Does it collect or share any of the required data types? | **Yes** |
| Is all data encrypted in transit? | **Yes** (HTTPS/TLS to Firebase, Open Food Facts and GitHub) |
| Account creation methods | **OAuth** (Google, optional) and **Other** (anonymous account created only if the user turns the cloud copy on) **[exact labels unverified]** |
| Account deletion URL | `https://haltera.web.app/delete-account` |
| Can users delete data without deleting the account? | **Yes**: individual items can be deleted in the app (and the delete is synced); full wipe is "Also erase data on this device" **[answer by what you want to claim]** |
| Can users request deletion? | **Yes**: in the app (Settings → Account → Delete account, or switch off "Back up to the cloud" and tick "Also delete my cloud data") and at the URL above |
| Data shared with third parties? | **No.** Google Firebase acts as a service provider (processor). Open Food Facts and GitHub receive a barcode or an image path with the IP address on a user action only; this is not "sharing" of user data **[judgement: Google treats service providers as not sharing; the IP and barcode requests to Open Food Facts are the closest case; keep as not shared and mention in the policy, as done]** |
| Security practices: independent security review | **No** |

**Data types collected** (for each: *Shared = No*, *Processed ephemerally = No* unless noted)

| Category → type | What it is in Haltera | Required / optional | Purposes |
|---|---|---|---|
| Personal info → **User IDs** | Firebase Auth uid | **Optional** (only if the cloud copy is on) | App functionality; Account management |
| Personal info → **Email address** | Google account email held by Firebase Auth | **Optional** (only if Google is linked) | Account management |
| Personal info → **Name** | Google display name (read from the provider) | **Optional** | Account management |
| Health and fitness → **Health info** | Sex, birth year, height, nutrition goals (nutrition profile) | **Optional** | App functionality |
| Health and fitness → **Fitness info** | Routines, sessions, sets, loads, durations, RPE | **Optional** (only if the cloud copy is on) | App functionality |
| Health and fitness → body weight and measurements | Body weight entries, the weight stored in sessions and body measurements (waist, arm… and body fat %, P6). Declare under **Health info** (Play has no separate weight or measurement type) | **Optional** | App functionality |
| Health and fitness → nutrition | Foods, meals, calories and macros. Declare under **Fitness info** or **Health info** (no separate nutrition type) **[unverified which Play expects]** | **Optional** | App functionality |
| App activity → **Other user-generated content** | Notes, routine and exercise names, custom foods | **Optional** | App functionality |
| Health and fitness → **Fitness info** (Health Connect) | Daily steps, active and total calories burned **read from Health Connect** and kept on the phone; finished workouts **written to** Health Connect | **Optional** (only if the user connects Health Connect and switches the type on) | App functionality |
| Health and fitness → **Health info** (Health Connect) | Body weight, resting heart rate and sleep duration **read from Health Connect**; body weight written to it | **Optional** (same) | App functionality |
| Device or other IDs | **Not collected by the app.** Firebase installation IDs are not used for analytics here. Do not tick unless the Console's Firebase disclosure page says Auth/Firestore need it | — | — |

Notes:
- **Health Connect data (P4) and Data safety:** Data safety defines "collected" as transmitted off the device, but the
  Health Connect policy asks apps to disclose the Health Connect data they access **[unverified how Play reviews a
  device-only read; declaring it is the safe answer]**. Haltera keeps the daily totals in a device-only table that is
  never synced; the only Health Connect data that can leave the phone is a **body-weight entry imported from Health
  Connect**, which becomes an ordinary log entry and is uploaded with the rest of the log **only if the cloud copy is
  on**. So: tick Fitness info and Health info (already ticked for the cloud copy), keep them **Optional**, purpose
  **App functionality**, **not shared**, do not tick "processed ephemerally". Nothing from Health Connect is used for
  ads or analytics, sold, or used to decide eligibility for anything.
- **Progress photos (P6) are not collected:** they stay in app-private storage on the phone (no upload, no export, no
  backup) and are picked with the system photo picker, so "Photos and videos" stays unticked.
- **Not collected:** location, contacts, photos or videos, audio, files, calendar, web history, financial info,
  messages, crash logs, diagnostics. Crash logs and diagnostics are **not** collected (no Crashlytics).
- The IP address is processed by Google as part of Firebase; Play's guidance (Firebase disclosure page) lists what
  each Firebase SDK collects; compare with it before submitting **[unverified: re-read the Firebase page in Sources]**.
- Android Auto Backup is Google's own device backup, not collection by the developer.
- ML Kit / Play services code scanner: runs on-device; any telemetry is Google's. **[uncertain, see inventory 2.4]**

### 3.8 Health apps (Aplicaciones de salud)

**Where:** App content → **Health apps (Aplicaciones de salud)** → **Start**. Every app must submit it, including
apps without health features.

Declare the features the app offers:
- **Health and fitness → Activity and Fitness (Actividad y forma física)**: yes (workout logging, progression, charts).
- **Health and fitness → Nutrition and Weight Management (Nutrición y control de peso)**: yes (food log, calorie and
  macro goals, body weight).
- **Sleep management**: Haltera only shows a sleep-duration trend read from Health Connect (optional, off by default).
  If the form asks whether the app offers sleep features, answer **yes: sleep-duration trend read from Health
  Connect** **[unverified: the category may target sleep apps only; answering yes is the honest side]**.
- Period tracking, stress/mental acuity: **no**.
- **Medical** categories (diseases and conditions, clinical decision support, medication, etc.): **no**.
- **Medical device app: no.** Haltera is not a medical device, makes no diagnostic or treatment claims, and shows a
  health disclaimer on analysis screens (`HealthDisclaimer`; AGENTS.md).
- **Health Connect: used** (P4, Settings → Integrations → Health Connect). Fill in the Health Connect part as in
  **3.8.1** below.
- If a second page appears asking about medical-device regulation (EU MDR): answer "not a medical device".

### 3.8.1 Health Connect permissions declaration

**Where:** App content → **Health apps (Aplicaciones de salud)** → the Health Connect section, or a separate **Health
Connect** card if the Console shows one **[menu path unverified]**. The form lists every `android.permission.health.*`
permission found in the uploaded bundle and asks, for each one, why the app needs it. Upload a bundle that contains
this feature first (closed testing is enough); otherwise the form does not list the permissions. Review can take
several days and **blocks the release that adds the permissions** until it is approved.

Facts for the form (code: `data/health`, `data/repository/.../health`, `composeApp/.../health`):

- Exactly these permissions are declared (`data/health/src/androidMain/AndroidManifest.xml`, kept in step with the code
  by `HealthConnectGatewayTest`): `READ_STEPS`, `READ_ACTIVE_CALORIES_BURNED`, `READ_TOTAL_CALORIES_BURNED`,
  `READ_WEIGHT`, `READ_RESTING_HEART_RATE`, `READ_SLEEP`, `WRITE_EXERCISE`, `WRITE_WEIGHT`. **No**
  `READ_HEALTH_DATA_IN_BACKGROUND`, **no** `READ_HEALTH_DATA_HISTORY`, no medical records.
- Nothing is requested until the user taps **Connect Health Connect**; each data type has its own switch and only
  the switched-on types are requested. Reads happen only while the app is in the foreground.
- Permission rationale (required by Health Connect): `HealthPermissionsRationaleActivity` (Android 13 and older:
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`; Android 14+: the `ViewPermissionUsageActivity` alias with
  `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS`). It opens the privacy policy, whose **Health Connect** section says
  what is read, why, that it is not shared or used for ads, and how to disconnect.
- What is read stays on the phone (device-only table, never synced). Disconnecting revokes the permissions and deletes
  the copied totals.

**Justification per permission (paste; shorten if a field is limited):**

| Permission | Justification |
|---|---|
| `READ_STEPS` | Haltera shows the daily steps in its Activity statistics (steps per day against a daily goal, 7-day average, weekly totals, streaks) and on the Today screen. Read only after the user connects Health Connect and switches Steps on, only while the app is open; kept on the device. |
| `READ_ACTIVE_CALORIES_BURNED` | Shown as a daily active-calories trend in the Activity statistics, next to the workouts the user logs. Optional, read in the foreground only, kept on the device. |
| `READ_TOTAL_CALORIES_BURNED` | Compared with the calories the user logs in the food diary ("eaten vs burned" in Nutrition statistics) and shown as a trend. Optional, read in the foreground only, kept on the device. |
| `READ_WEIGHT` | Body weight measured by smart scales or other apps is added to the body-weight log, which Haltera charts with a moving average and weekly averages. Optional; entries Haltera wrote itself are never imported back. |
| `READ_RESTING_HEART_RATE` | Shown as an optional daily resting-heart-rate trend in the Activity statistics, next to training load. Off by default, trend only, no medical interpretation. |
| `READ_SLEEP` | Shown as an optional sleep-duration trend (hours per night) in the Activity statistics, next to training load. Off by default, trend only. |
| `WRITE_EXERCISE` | Each finished workout is written to Health Connect as a strength-training exercise session (start, end, title) so other fitness apps see the gym sessions. Optional; editing or deleting the workout in Haltera updates or removes the same record. |
| `WRITE_WEIGHT` | Body weight logged in Haltera is written to Health Connect so other apps can use it. Optional, off by default. |

Do **not** add permissions to the manifest without updating this form, DATA-INVENTORY.md and the privacy pages: Health
Connect refuses permissions the app does not declare, and Play rejects declared permissions with no feature using
them.

### 3.9 Foreground service declaration

**Where:** App content → **Foreground service permissions (Permisos de servicio en primer plano)** (it appears
because the manifest declares `FOREGROUND_SERVICE_SPECIAL_USE` and the app targets Android 14+).

| Field | Answer |
|---|---|
| Type | **Special use (`specialUse`)** |
| Manifest subtype property | `Workout session timer: shows the current exercise and the remaining rest between sets` |
| Use case | Not in the preset list: choose "Other" and paste the description below |

**Description (paste):**

> Haltera is a gym logbook. When the user starts a workout, a foreground service keeps a persistent notification
> that shows the current exercise and a countdown of the rest time between sets, and keeps the rest timer running
> while the app is in the background or the screen is off. It runs only while a workout session is active, started
> by the user, and stops when the user finishes or discards the workout. It does not access location, camera,
> microphone or any media, and it does no data transfer.

**User impact if deferred or interrupted (paste):**

> If the system delayed or stopped the service, the rest countdown and its end alert would not fire while the user
> has the phone locked or is in another app between sets, so the user would miss the end of rest. The workout data
> already logged is never lost, because it is saved as the user logs it.

**Demo video** (30-60 s, unlisted YouTube, link in the form). It must show, in this order:
1. Haltera open on a routine day; the user taps **Start workout**.
2. The persistent notification appearing in the shade with the exercise name.
3. The user logs a set; the rest countdown starts and is visible in the notification.
4. The user locks the phone or switches to another app; the notification keeps counting; the alert fires at the end.
5. The user returns, finishes the workout; the notification disappears.

If review asks why not `shortService` or another type: the rest period lasts minutes, and no listed type matches a
user-started timer **[unverified how Google judges this]**.

### 3.10 Exact alarms (`SCHEDULE_EXACT_ALARM`)

Facts (RELEASE-AUDIT.md, audit-decisions.md): the manifest declares `SCHEDULE_EXACT_ALARM` (user-grantable), **not**
`USE_EXACT_ALARM`. The code uses an exact alarm for the rest-timer end when the user has granted it and falls back
to an inexact `setAndAllowWhileIdle` otherwise; the user is asked politely once and is never blocked.

- Play's policy page: the Console declaration form is required for apps that request **`USE_EXACT_ALARM`**
  (restricted: alarm clock or calendar core functions). For `SCHEDULE_EXACT_ALARM` the policy text only says it
  gives the same function with user grant. **Whether a declaration card appears for `SCHEDULE_EXACT_ALARM` is
  [unverified]**: if the Console shows one, answer as below.
- Honest rationale (paste if asked):

  > Haltera's rest timer ends a rest period between sets of a workout and alerts the user. The permission lets the
  > alert fire on time with the screen off. Core function: user-started countdown timers during a workout. The
  > user can deny the permission: the app then uses an inexact alarm that may fire a little late, and keeps working.

- Risk (already decided, audit-decisions.md): a rest timer may be judged "not core alarm functionality" and the
  permission rejected. Dropping it is a one-line manifest change; the inexact fallback already exists. If Play
  rejects it, remove `SCHEDULE_EXACT_ALARM` and re-run `node scripts/verify-release.mjs`.
- Do **not** add `USE_EXACT_ALARM`: it is granted at install and Play allows it only for alarm and calendar apps.

### 3.11 Financial features
**"My app doesn't provide any financial features"** (Mi app no ofrece ninguna función financiera). Mandatory for all
apps, including closed testing.

### 3.12 Remaining declarations
- Government app: **No**. News app: **No**. COVID-19 app: **No**.
- Other permissions in the release manifest, all explained in DATA-INVENTORY.md section 4: `INTERNET`,
  `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `VIBRATE`, `USE_BIOMETRIC`/`USE_FINGERPRINT` (library-provided),
  `READ_GSERVICES`. No camera, location, contacts, SMS, microphone or storage permission.

---

## Phase 4. Store listing

**Where:** *Grow users → Store presence → Main store listing (Hacer crecer usuarios → Presencia en la tienda → Ficha principal)* and *Store settings (Configuración de la tienda)*.

- Text and graphics are in the repo: `fastlane/metadata/android/{es-ES,en-US}` (`title.txt`, `short_description.txt`,
  `full_description.txt`, `images/icon.png`, `images/featureGraphic.png`, `images/phoneScreenshots/`). The feature
  graphic is generated by `node scripts/feature-graphic.mjs` (1024x500, no alpha). Copy and upload them by hand the
  first time (the listing must be complete to send closed testing for review); once the service account exists,
  the *Play listing* workflow publishes them on changes to `main`.
- **Title:** `Haltera` (both languages; 30 characters max).
- **Short description (80 max)**, from `short_description.txt`: es-ES and en-US versions in the repo.
- **Full description (4000 max)**, from `full_description.txt`. **Check before pasting:** the text must say the cloud copy is
  optional and off until the user turns it on (DATA-INVENTORY.md finding 1), otherwise the listing contradicts the
  privacy pages and the Data safety form. The repo text says: "Works offline. Everything stays on your phone unless you choose otherwise" and
  "Optional cloud backup ... off until you say yes".
- **Category:** **Health & Fitness (Salud y bienestar)**. Tags: up to 5 (e.g. workout log, strength training, nutrition
  tracker) **[labels as the Console offers]**.
- **Contact details:** Play requires a public email on the listing even though the website uses GitHub Issues; use an
  address created for the app. Website: `https://haltera.web.app`. **Privacy policy** is on the App content page.
- **Screenshots:** phone screenshots (min 2) already in `images/phoneScreenshots` (`node scripts/play-screenshots.mjs`),
  7-inch and 10-inch optional.
- **Countries:** *Closed testing → Countries/regions*: start with Spain.

---

## Phase 5. Service account for the Play API

No Cloud project link is needed: create a service account and **invite it as a user** in Play Console.

1. **Google Cloud Console**, project `ironlog-sebas1705`:
   - *APIs & Services → Library* → **Google Play Android Developer API** → **Enable**.
   - *IAM & Admin → Service accounts → Create*: name `play-publisher`
     (`play-publisher@ironlog-sebas1705.iam.gserviceaccount.com`). **No IAM roles.** Do not reuse `ironlog-ci`.
   - *Keys → Add key → JSON*: a `.json` file is downloaded.
2. **Play Console → Users and permissions (Usuarios y permisos) → Invite new users**: that email; *App permissions*
   → add **Haltera** with **View app information (read only)**, **Release to testing tracks**, **Manage testing tracks
   and edit tester lists**, **Manage store presence** (for *Play listing*). When production exists add
   **Release to production, exclude devices, and use Play App Signing** (for *Play promote*). No account or
   financial permissions.
3. **Doppler** (JSON never goes through the chat):
   ```bash
   doppler secrets set PLAY_SERVICE_ACCOUNT_JSON --project ironlog --config prd < ~/Downloads/<file>.json
   rm ~/Downloads/<file>.json
   ```
4. New invitations can take a while to activate **[delay unverified]**.

Then set the track: `gh variable set PLAY_TRACK --body alpha --repo Sebas1705/Haltera` (internal: `internal`).
`PLAY_RELEASE_STATUS=draft` (optional variable) is for an app that is not yet published.

---

## Phase 6. First AAB, uploaded by hand, to closed testing

The API cannot publish to an app that never received an AAB. Upload the first one by hand.

1. Download the `.aab` from the latest GitHub Release of the `Haltera` repo (`release.yml` attaches it), signed with
   the upload key.
2. **Where:** *Test and release → Testing → Closed testing (Pruebas → Prueba cerrada)* → **Alpha** → **Create release**.
3. **App integrity:** confirm our key is in use (Phase 2).
4. Upload the `.aab`. Release name: what it proposes. Notes: es-ES and en-US, short.
5. If the deobfuscation warning appears, the CI uploads `mapping.txt` from the next release on.
6. **Review release → Start rollout to Closed testing**. First review can take days.

---

## Phase 7. Testers with a Google Group

The owner already has tester groups from another app, `testers-community@googlegroups.com` and
`testers-para-sebas@googlegroups.com`; they can be **reused** (add them under *Closed testing → Testers → Google
Groups*). Or create `haltera-testers`.

1. Members need real Google accounts, the same used in Play Store.
2. Play Console → *Closed testing → Alpha → Testers (Testers)* → **Google Groups** → add the group → **Save**.
3. Copy the **opt-in link** (*Join on the web*) and send it. Each tester: join the group, open the link, **Become a
   tester**, install **from Play**.
4. Aim for **15 or more**: an opt-out restarts that person's count.

---

## Phase 8. The 14 days

Personal accounts **created after 2023-11-13** need **12 or more testers opted in continuously for 14 days** in a
closed test before applying for production. An older account may not need it (the Dashboard shows it).
Meanwhile CI uploads new releases to the track. Write down feedback and fixes for Phase 9.

---

## Phase 9. Apply for production

**Where:** *Dashboard (Panel)* → **Apply for production**. Three sections; save on each page.

- **About your closed test:** how testers were recruited, how easy, what they used and feedback, with 2 or 3 real
  changes made from it.
- **About your app:** audience (people who train with weights and want a simple logbook), value (logging any
  routine and date, rest timers, progression, nutrition log, free, no ads, open source), expected installs (a
  realistic number).
- **Production readiness:** changes made because of the test, and how you decided it is ready.
- Review usually takes 7 days or fewer.

Then publish with the **Play promote** workflow (*from* your testing track, *to* `production`, `rollout` `0.1` or `1`).
Give the service account the production permission first.

---

## Phase 10. Android developer verification (2026-2027)

Google requires registering developer **identity** and **package names** so apps can be installed on certified
devices, also outside Play (our GitHub APKs). Timeline in the sources below: mandatory from 2026-09-30 in Brazil,
Indonesia, Singapore and Thailand, worldwide in 2027 **[dates as read on 2026-10-13 in the Owlsplit guide; re-check]**.

1. *Settings → Developer account*: identity **verified**.
2. On Home, check that `es.sebas1705.haltera` appears registered (creating the app registers it).
3. If asked to prove you hold the signing key, follow the Console, using the same key as Phase 2 **[procedure unverified]**.
4. The old package `es.sebas1705.ironlog` (test builds on GitHub and App Distribution) may need registering too if
   it keeps being distributed outside Play **[unverified]**.

---

## Phase 11. Moving from the Ironlog test builds to Haltera

The package changes, so Haltera is a **new app**, not an update:

- Android treats `es.sebas1705.haltera` as a new install next to the old `es.sebas1705.ironlog`. Local data is
  **not** carried over by Android.
- **Cloud data is recoverable.** In the old Ironlog app: Settings → Account → **Link Google** and wait for sync.
  Then in Haltera sign in with the **same Google account** (Settings → Account → Sign in with Google): it signs in to
  the same uid and downloads everything. Both apps use the same Firebase project and the data under
  `users/{uid}` is untouched (docs/FIRESTORE-DATA.md has the safe order, backup and migration tools).
- Anonymous-only users (never linked Google) cannot recover their data in the new app, because the new install gets
  a new anonymous uid: link Google in the old app **first**. If this was missed, `scripts/firestore-migrate-user.mjs`
  can copy data between uids (owner only; never overwrites newer data).
- When Haltera has everything, the legacy Ironlog app can be **uninstalled**. Do not delete the old Firebase apps or
  users.
- Testers on App Distribution: invite them to the Play closed test (Phase 7) and ask them to uninstall Ironlog after
  checking their data.

---

## What to tell the assistant

| When | Message |
|---|---|
| Phase 2 | "Play App Signing with our key: SHA-256 matches" (or the fingerprint) |
| Phase 3 | Whether the Console showed a card for exact alarms, and whether it asked anything else |
| Phase 5 | "`PLAY_SERVICE_ACCOUNT_JSON` in Doppler ironlog/prd and invited" (never the JSON) |
| Phase 6 | Name of the track and that the first AAB is uploaded |
| Phase 9 | Production access granted |

## Sources (consulted on 2026-10-16 unless noted)

- Foreground service and full-screen intent requirements (declaration, special use): https://support.google.com/googleplay/android-developer/answer/13392821
- Health apps declaration and categories: https://support.google.com/googleplay/android-developer/answer/14738291
- Health Connect: developer guide (permissions, rationale activity, availability):
  https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started and Play policy for
  Health Connect permissions: https://support.google.com/googleplay/android-developer/answer/12991134 **[links from
  memory, not re-checked in this PR]**
- Exact alarm permissions policy (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, declaration form): https://support.google.com/googleplay/android-developer/answer/9888170
- Carried over from the Owlsplit guide (consulted 2026-10-13, not re-read today):
  - Testing requirements for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
  - Play App Signing and your own key: https://support.google.com/googleplay/android-developer/answer/9842756
  - Play API, service account invitation: https://developers.google.com/android-publisher/getting_started
  - Account deletion requirement: https://support.google.com/googleplay/android-developer/answer/13327111
  - Data safety form: https://support.google.com/googleplay/android-developer/answer/10787469
  - Data collected by Firebase SDKs: https://firebase.google.com/docs/android/play-data-disclosure
  - Financial features declaration: https://support.google.com/googleplay/android-developer/answer/13849271
  - Android developer verification: https://developer.android.com/developer-verification
  - PEPK syntax (community; the Console's command takes precedence): https://discuss.appstudio.dev/t/play-encrypt-private-key-google-play-pepk/3435
