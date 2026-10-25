#!/usr/bin/env bash
# Writes the Firebase config files of the `ironlog-sebas1705` Firebase project (Doppler ironlog/prd) to the paths
# the build expects. Both files are git-ignored.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

doppler secrets get GOOGLE_SERVICES_JSON --project ironlog --config prd --plain \
  > "$ROOT/androidApp/google-services.json"
mkdir -p "$ROOT/iosApp/iosApp"
doppler secrets get GOOGLE_SERVICE_INFO_PLIST --project ironlog --config prd --plain \
  > "$ROOT/iosApp/iosApp/GoogleService-Info.plist"

echo "Firebase config of ironlog-sebas1705 written to androidApp/ and iosApp/iosApp/"
