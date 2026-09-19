#!/bin/zsh
# Build a release: bump versionCode, assemble signed APK, stage it in dist/ for
# the shared server's reader module (/reader/version.json), and push over adb
# if a phone is attached.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$APP_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

code=$(grep '^versionCode=' version.properties | cut -d= -f2)
name=$(grep '^versionName=' version.properties | cut -d= -f2)
new_code=$((code + 1))
sed -i '' "s/^versionCode=.*/versionCode=$new_code/" version.properties
echo "Building versionCode=$new_code versionName=$name"

./gradlew --quiet assembleRelease

apk_src="app/build/outputs/apk/release/app-release.apk"
mkdir -p dist
apk_name="reader-v$new_code.apk"
cp "$apk_src" "dist/$apk_name"
rm -f dist/reader-v*.apk(Nom[2,-1])  # keep only the newest APK
cat > dist/version.json <<JSON
{"versionCode": $new_code, "versionName": "$name", "apk": "$apk_name"}
JSON
echo "Staged dist/$apk_name"

ADB="$ANDROID_HOME/platform-tools/adb"
if "$ADB" get-state >/dev/null 2>&1; then
    "$ADB" install -r "$apk_src"
    echo "Installed on phone via adb."
else
    echo "No adb device — the phone can pull it: Settings → Check for updates"
fi
