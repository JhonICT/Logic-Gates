#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f app/google-services.json ]; then cp app/google-services.json.example app/google-services.json; fi
GRADLE_VERSION=8.13
GRADLE_HOME_LOCAL="${HOME}/.gradle/74ls-logic-lab/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME_LOCAL}/bin/gradle"

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "${HOME}/Android/Sdk/platforms/android-36" ]; then
    export ANDROID_HOME="${HOME}/Android/Sdk"
  elif [ -d "${HOME}/Library/Android/sdk/platforms/android-36" ]; then
    export ANDROID_HOME="${HOME}/Library/Android/sdk"
  fi
fi
if [ -n "${ANDROID_HOME:-}" ]; then export ANDROID_SDK_ROOT="$ANDROID_HOME"; fi

if command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN="$(command -v gradle)"
elif [ ! -x "$GRADLE_BIN" ]; then
  if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
    echo "Install Gradle 8.13 or install curl and unzip so the build script can download it." >&2
    exit 1
  fi
  mkdir -p "$(dirname "$GRADLE_HOME_LOCAL")"
  TMP_ZIP="$(dirname "$GRADLE_HOME_LOCAL")/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Gradle was not found; downloading Gradle ${GRADLE_VERSION} for this project..."
  curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$TMP_ZIP"
  unzip -q -o "$TMP_ZIP" -d "$(dirname "$GRADLE_HOME_LOCAL")"
  rm -f "$TMP_ZIP"
fi

if [ -n "${ANDROID_HOME:-}" ] && [ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]; then
  echo "Android SDK Platform 36 was not found. Install it using Android Studio > SDK Manager." >&2
  exit 1
fi
"$GRADLE_BIN" --no-daemon assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
