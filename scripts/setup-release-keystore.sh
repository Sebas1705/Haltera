#!/usr/bin/env bash
# One-off: creates the release (upload) keystore, stores it in Doppler (ironlog/prd) and registers
# its fingerprints on the Firebase Android app. Run it once, by hand: the keystore can never be
# replaced once a build signed with it is distributed or uploaded to Google Play.
#
#   scripts/setup-release-keystore.sh [path]      (default: ~/Documents/ironlog-release.jks)
#
# Needs: keytool (JDK 21, through JAVA_HOME), doppler (logged in), npx firebase-tools (logged in).
# The password is generated here and only ever goes to Doppler: it is never printed.
set -euo pipefail

KEYSTORE="${1:-$HOME/Documents/ironlog-release.jks}"
ALIAS="ironlog"
DOPPLER_PROJECT="ironlog"
DOPPLER_CONFIG="prd"
FIREBASE_PROJECT="ironlog-sebas1705"
ANDROID_APP="1:695669078657:android:4ae26e75a4756aff962b80"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

if [ -e "$KEYSTORE" ]; then
  echo "Refusing to overwrite $KEYSTORE" >&2
  exit 1
fi
if doppler secrets get SIGNING_KEYSTORE_BASE64 --project "$DOPPLER_PROJECT" --config "$DOPPLER_CONFIG" --plain >/dev/null 2>&1; then
  echo "Doppler $DOPPLER_PROJECT/$DOPPLER_CONFIG already has SIGNING_KEYSTORE_BASE64: the release key exists." >&2
  exit 1
fi

# PKCS12 uses one password for the store and the key.
if command -v openssl >/dev/null 2>&1; then
  PASSWORD="$(openssl rand -hex 24)"
else
  PASSWORD="$(node -e 'process.stdout.write(require("crypto").randomBytes(24).toString("hex"))')"
fi
"$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Haltera, O=Sebas1705, C=ES" >/dev/null 2>&1

doppler secrets set --project "$DOPPLER_PROJECT" --config "$DOPPLER_CONFIG" --silent \
  SIGNING_KEY_ALIAS="$ALIAS" SIGNING_STORE_PASSWORD="$PASSWORD" SIGNING_KEY_PASSWORD="$PASSWORD" >/dev/null
base64 -w0 "$KEYSTORE" | doppler secrets set SIGNING_KEYSTORE_BASE64 --project "$DOPPLER_PROJECT" --config "$DOPPLER_CONFIG" --silent >/dev/null

FINGERPRINTS="$("$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS")"
unset PASSWORD
SHA1="$(echo "$FINGERPRINTS" | awk '/SHA1:/ {print $2}')"
SHA256="$(echo "$FINGERPRINTS" | awk '/SHA256:/ {print $2}')"

for sha in "$SHA1" "$SHA256"; do
  # On Windows Node can abort while exiting after it succeeded: check the list instead of the exit code.
  npx --yes firebase-tools@15.32.1 apps:android:sha:create "$ANDROID_APP" "$sha" --project "$FIREBASE_PROJECT" >/dev/null 2>&1 || true
done

echo "Release keystore: $KEYSTORE (keep a copy outside this PC)"
echo "Stored in Doppler $DOPPLER_PROJECT/$DOPPLER_CONFIG: SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD"
echo "SHA-1:   $SHA1"
echo "SHA-256: $SHA256"
echo "Registered on the Firebase Android app. Check with:"
echo "  npx --yes firebase-tools@15.32.1 apps:android:sha:list $ANDROID_APP --project $FIREBASE_PROJECT"
