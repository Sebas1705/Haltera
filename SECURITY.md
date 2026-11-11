# Security Policy

## Supported versions

Only the latest release (the one on Google Play) receives security fixes.

## Reporting a vulnerability

Please use GitHub private vulnerability reporting: open the **Security** tab of this repository and choose
**Report a vulnerability**. Do not open a public issue or pull request for a security problem.

## How Haltera handles data

The full inventory is in [docs/play/DATA-INVENTORY.md](docs/play/DATA-INVENTORY.md). In short:

- **On the device.** Training, nutrition and body-weight data live in a local SQLite database (`haltera.db`) and
  settings in app-private storage. The app does not add its own encryption on top of Android/iOS app sandboxing.
  Android Auto Backup is enabled, so Google's device backup can include this data.
- **Cloud copy (optional, off by default).** Only if the user turns it on, the app signs in with Firebase
  Authentication (anonymous, optionally linked to a Google account) and syncs the data to Cloud Firestore under
  `users/{uid}`. The Firestore rules (`firebase/firestore.rules`, tested in `firebase/rules-test`) let a signed-in
  user read and write only their own `users/{uid}/**` tree. Users can delete their cloud data and account from
  Settings > Account.
- **Other network calls.** Open Food Facts (a barcode lookup when the user scans a product), GitHub raw content
  (exercise images) and, on Android release builds, Firebase Crashlytics crash reports (opt-out in Settings > Privacy).
- **Secrets.** The repository holds no signing keys or service-account credentials. The Firebase client
  configuration is not a secret; access is enforced by the Firestore rules.
