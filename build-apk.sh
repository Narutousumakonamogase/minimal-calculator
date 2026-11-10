#!/usr/bin/env bash
# Builds the release APK, re-signs it with v1+v2+v3 (AGP drops v1 when minSdk >= 24),
# verifies 16 KB alignment and signatures, and writes Calculator.apk.
# Signing credentials are read from keystore.properties (see keystore.properties.example).
set -euo pipefail
[ -f "$HOME/.android-env.sh" ] && source "$HOME/.android-env.sh"
cd "$(dirname "$0")"

PROPS=keystore.properties
if [ ! -f "$PROPS" ]; then
  echo "keystore.properties not found; run ./gradlew assembleRelease for a debug-signed build." >&2
  exit 1
fi
prop() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPS" | tail -1; }
KS=$(prop storeFile); ALIAS=$(prop keyAlias)
export KS_PASS=$(prop storePassword) KEY_PASS=$(prop keyPassword)

: "${ANDROID_HOME:?ANDROID_HOME is not set}"
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

./gradlew assembleRelease --console=plain -q
A=app/build/outputs/apk/release/app-release.apk
"$BT/zipalign" -c -P 16 4 "$A"
"$BT/apksigner" sign --ks "$KS" --ks-pass env:KS_PASS --ks-key-alias "$ALIAS" --key-pass env:KEY_PASS \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --min-sdk-version 21 \
  --out Calculator.apk "$A"
rm -f Calculator.apk.idsig
"$BT/zipalign" -c -P 16 -v 4 Calculator.apk | tail -1
"$BT/apksigner" verify --verbose Calculator.apk | grep -E "Verifies|scheme"
