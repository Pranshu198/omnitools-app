#!/usr/bin/env bash
# Manual release build for OmniTools (no Gradle).
#
# Why this exists: the Gradle project in this repo is the standard,
# Android-Studio-friendly build. THIS script is the pipeline that
# produced the published APK inside a sandbox whose loopback TCP is
# intercepted, which makes the Gradle daemon protocol unusable there.
# It drives the same Android SDK tools AGP drives (aapt2 -> javac ->
# R8 -> zipalign -> apksigner) and applies app/proguard-rules.pro.
#
# Env overrides: JAVA_HOME, ANDROID_SDK (default ~/workspace/android-sdk),
# R8_JAR (default ~/workspace/toolchain-dl/r8.jar), WEBKIT_JAR (androidx
# .webkit classes.jar), PWA_DIST (built PWA output to bundle; default the
# sibling omnitools checkout's dist/), SKIP_PWA_SYNC=1 to keep the assets
# already in the tree.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/workspace/toolchain/jdk17}"
SDK="${ANDROID_SDK:-$HOME/workspace/android-sdk}"
R8_JAR="${R8_JAR:-$HOME/workspace/toolchain-dl/r8.jar}"
WEBKIT_JAR="${WEBKIT_JAR:-$HOME/workspace/toolchain-dl/webkit/aar/classes.jar}"
WEBKIT_PG="${WEBKIT_PG:-$HOME/workspace/toolchain-dl/webkit/aar/proguard.txt}"
# androidx.webkit's WebViewAssetLoader.Builder uses androidx.core.util.Pair —
# a real runtime dependency, so core's classes ship in the dex too
# (Gradle resolves this transitively; the manual pipeline names it).
CORE_JAR="${CORE_JAR:-$HOME/workspace/toolchain-dl/webkit/core-aar/classes.jar}"
CORE_PG="${CORE_PG:-$HOME/workspace/toolchain-dl/webkit/core-aar/proguard.txt}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

VERSION_CODE=3
VERSION_NAME="2.0.1"
APK_NAME="OmniTools-${VERSION_NAME}.apk"

# ---- bundle the PWA: the app is standalone, the site ships inside ----
if [ "${SKIP_PWA_SYNC:-0}" != "1" ]; then
    PWA_DIST="${PWA_DIST:-$HOME/workspace/omnitools/dist}"
    if [ -f "$PWA_DIST/index.html" ]; then
        echo "== sync PWA bundle ($PWA_DIST -> app/src/main/assets) =="
        rm -rf app/src/main/assets
        mkdir -p app/src/main/assets
        cp -r "$PWA_DIST/." app/src/main/assets/
    fi
fi
if [ ! -f app/src/main/assets/index.html ]; then
    echo "ERROR: no bundled PWA (app/src/main/assets/index.html missing)." >&2
    echo "Build the PWA first (npm run build in the omnitools repo) or set PWA_DIST." >&2
    exit 1
fi
echo "bundled files: $(find app/src/main/assets -type f | wc -l), bytes: $(du -sb app/src/main/assets | cut -f1)"

OUT=build/manual
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/compiled.zip"

echo "== aapt2 link =="
# AGP injects the manifest package from the Gradle namespace; standalone
# aapt2 wants it in the manifest itself, so link against a patched copy.
sed 's|<manifest |<manifest package="com.jackbhai.omnitools" |' \
    app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
"$BT/aapt2" link -o "$OUT/app-res.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    --java "$OUT/gen" \
    -A app/src/main/assets \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    "$OUT/compiled.zip"

echo "== javac =="
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 \
    -cp "$ANDROID_JAR:$WEBKIT_JAR:$CORE_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
(cd "$OUT/classes" && jar cf "../classes.jar" .)

echo "== R8 (minify, proguard-rules.pro + library consumer rules) =="
java -cp "$R8_JAR" com.android.tools.r8.R8 \
    --release --min-api 26 \
    --lib "$ANDROID_JAR" \
    --pg-conf app/proguard-rules.pro \
    --pg-conf "$WEBKIT_PG" \
    --pg-conf "$CORE_PG" \
    --output "$OUT/dex" \
    "$OUT/classes.jar" "$WEBKIT_JAR" "$CORE_JAR"

echo "== package + align + sign =="
cp "$OUT/app-res.apk" "$OUT/app-unsigned.apk"
(cd "$OUT/dex" && zip -q "../app-unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

KS_PASS="$(cat keystore/keystore-password.txt)"
OMNI_KS_PASS="$KS_PASS" "$BT/apksigner" sign \
    --ks keystore/omnitools-release.jks \
    --ks-key-alias omnitools \
    --ks-pass env:OMNI_KS_PASS \
    --key-pass env:OMNI_KS_PASS \
    --out "$APK_NAME" \
    "$OUT/app-aligned.apk"

echo "== verify =="
"$BT/apksigner" verify --print-certs "$APK_NAME" | head -4
"$BT/aapt" dump badging "$APK_NAME" | head -4
ls -la "$APK_NAME"
echo "BUILD OK -> $APK_NAME"
