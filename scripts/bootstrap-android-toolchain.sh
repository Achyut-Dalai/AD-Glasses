#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/android-app.yml"
MODE="${1:---sync}"

find_sdkmanager() {
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -n "$sdk_root" && -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]]; then
    printf '%s\n' "$sdk_root/cmdline-tools/latest/bin/sdkmanager"
    return
  fi
  echo "Android SDK Command-Line Tools are not installed or sdkmanager is not on PATH." >&2
  echo "Install the current stable Command-Line Tools from developer.android.com/studio, then rerun this script." >&2
  exit 2
}

SDKMANAGER="$(find_sdkmanager)"
ANDROID_API="$(sed -nE 's/.*"platforms;android-([0-9]+)".*/\1/p' "$WORKFLOW" | head -1)"
BUILD_TOOLS="$(sed -nE 's/.*"build-tools;([0-9.]+)".*/\1/p' "$WORKFLOW" | head -1)"
CMAKE="$(sed -nE 's/.*"cmake;([0-9.]+)".*/\1/p' "$WORKFLOW" | head -1)"
NDK="$(sed -nE 's/.*"ndk;([0-9.]+)".*/\1/p' "$WORKFLOW" | head -1)"

case "$MODE" in
  --sync)
    yes | "$SDKMANAGER" --licenses >/dev/null || true
    "$SDKMANAGER" --channel=0 \
      "platforms;android-$ANDROID_API" \
      "build-tools;$BUILD_TOOLS" \
      "cmake;$CMAKE" \
      "ndk;$NDK" \
      "platform-tools"
    git -C "$ROOT" submodule update --init --recursive
    echo "Local Android toolchain matches the repository pins."
    echo "Gradle is provided by android/CyanBridge/gradlew; no global Gradle install is required."
    ;;
  --latest)
    "$SDKMANAGER" --install "cmdline-tools;latest" --channel=0
    "$SDKMANAGER" --update --channel=0
    python3 "$ROOT/scripts/check-android-toolchain.py"
    echo "Local SDK packages are current on the stable channel."
    echo "The report above shows whether the repository pins need an automated update PR."
    ;;
  --check)
    python3 "$ROOT/scripts/check-android-toolchain.py"
    ;;
  *)
    echo "Usage: $0 [--sync|--latest|--check]" >&2
    exit 2
    ;;
esac
