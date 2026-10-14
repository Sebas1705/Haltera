# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |

## Reporting a vulnerability

Open a **private security advisory** on GitHub (Security tab → Advisories → New draft advisory).
Do not open a public issue for security vulnerabilities.

## Token storage

- Android: `EncryptedSharedPreferences` (AES-256-GCM via Jetpack Security Crypto)
- Desktop: `java.util.prefs.Preferences` (system keychain on macOS, registry-backed on Windows)
- iOS: `NSUserDefaults` — consider migrating to Keychain for production use

## Network

All API calls require HTTPS. The Ktor client enforces no plaintext in release builds.
