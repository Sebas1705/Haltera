# Haltera — brand and theme design

- **Date:** 2026-10-14
- **Status:** approved by the owner in brainstorming (concept A, "Barbell-I")

## Decisions

| Topic | Decision |
|---|---|
| Icon | Concept A, "Barbell-I": a vertical bar (the "I" of Haltera) with a plate collar at each end, drawn as two horizontal bars with orange plates at both ends, on a graphite squircle |
| Accent | Iron orange `#FF5A1F` (icon, fills, timer, primary buttons). Text-on-light accent darkened to `#C2410C` for WCAG AA |
| Neutrals | Graphite `#1E2126` (icon background, dark surfaces), light `#F4F4F5` |
| Semantic | Success green, warning amber, error red; **PR gold** for personal-record badges |
| Typography | Barlow Semi Condensed 600 for display/headline/title and big numbers (tabular figures for loads and times); Inter 400/500 for body and labels. Both OFL, bundled as Compose resources |
| Components | Material 3 stays the base. Brand touches: logo on the startup screen and Credits, accent rest timer, "↑" progression chip, gold PR badge |

## Single source: `brand/tokens.json`

Colours (light and dark), the logo geometry on a 108-unit grid (the Android adaptive-icon canvas) and the font names. Everything else is generated from it:

- `core:ui` theme colours (checked against the JSON by `BrandTokensTest`).
- `scripts/brand.mjs` (Node, `--check` mode in CI) writes:
  - Android: adaptive icon (`mipmap-anydpi-v26`, background graphite + foreground vector), **monochrome** layer for themed icons (Android 13+), **PNG mipmaps mdpi–xxxhdpi** plus round variants. The PNGs are required because Firebase App Distribution / App Tester cannot render adaptive icons.
  - iOS: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` (1024 px), referenced from `project.yml`.
  - Store: `brand/icon-512.png` (Play listing) and `brand/logo.svg`.
  - Android 12+ splash screen: logo on graphite.

## Verification

Screenshots on an emulator in light and dark (startup, Today, session with rest timer), the launcher icon, and the icon shown for the build in App Distribution.
