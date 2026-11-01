# ADR 0007: Crashlytics on Android only, with a user opt-out

- **Status:** accepted
- **Date:** 2026-10-29 (#57)
- **Area:** `androidApp` (plugin, `crashlyticsEnabled` manifest placeholder), `core:datastore` (`CrashReportsStore`), Settings > Privacy, `docs/play/`, `firebase/hosting`

## Context

Until then the app had no crash reporting. The core design only mentions that a failed catalogue import is logged,
"Crashlytics in release". Crashlytics was chosen because Owlsplit already uses it and Firebase is
already in the project. iOS is excluded because it is not distributed yet. The data inventory states the facts below.

## Decision

- Crashlytics is a dependency of `androidApp` only. The iOS app has no crash reporter.
- The Gradle plugin (build id and R8 mapping upload) is applied only when `androidApp/google-services.json` exists,
  so a build without Firebase config still works.
- Collection is off in debug builds and on in release builds (manifest placeholder).
- Settings > Privacy > "Send crash reports" (`CrashReportsStore`, device-local, **on by default**) is applied with
  `setCrashlyticsCollectionEnabled` at every start and whenever it changes. It is hidden on iOS and when there is no
  Firebase config.
- No analytics, performance monitoring, remote config or messaging (data inventory, section 2.1b and its checks).

## Consequences

- The privacy policy (en/es), data inventory and Play Console guide disclose the crash reports, and a hosting test
  checks the disclosure.
- In the Data safety form the data is declared optional because of the switch. The guide marks this as a judgement
  call, not a Play rule.
- On by default means users who never open Settings send reports.
