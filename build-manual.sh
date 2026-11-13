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
# R8_JAR (default ~/workspace/toolchain-dl/r8.jar).
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/workspace/toolchain/jdk17}"
SDK="${ANDROID_SDK:-$HOME/workspace/android-sdk}"
R8_JAR="${R8_JAR:-$HOME/workspace/toolchain-dl/r8.jar}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

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
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 1 --version-name 1.0 \
    "$OUT/compiled.zip"

echo "== javac =="
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 \
    -cp "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
(cd "$OUT/classes" && jar cf "../classes.jar" .)

echo "== R8 (minify, proguard-rules.pro) =="
java -cp "$R8_JAR" com.android.tools.r8.R8 \
    --release --min-api 26 \
    --lib "$ANDROID_JAR" \
    --pg-conf app/proguard-rules.pro \
    --output "$OUT/dex" \
    "$OUT/classes.jar"

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
    --out OmniTools-1.0.apk \
    "$OUT/app-aligned.apk"

echo "== verify =="
"$BT/apksigner" verify --print-certs OmniTools-1.0.apk | head -4
"$BT/aapt" dump badging OmniTools-1.0.apk | head -4
ls -la OmniTools-1.0.apk
echo "BUILD OK -> OmniTools-1.0.apk"
