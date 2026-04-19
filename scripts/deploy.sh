#!/usr/bin/env bash
# Build Ricotta, pick a connected device, install, and launch.
#
# Usage:
#   scripts/deploy.sh                 # interactive device picker, debug build
#   scripts/deploy.sh -r              # release build
#   scripts/deploy.sh -s <serial>     # non-interactive, target a specific serial
#   scripts/deploy.sh -n              # install only, don't launch
#   scripts/deploy.sh -l              # launch only, skip build+install
#   scripts/deploy.sh -c              # clean before building

# Re-exec under bash if invoked via another shell (e.g. `zsh deploy.sh`).
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/android"

APP_ID="dev.cannoli.ricotta"
LAUNCHER_ACTIVITY="com.retroarch.browser.mainmenu.MainMenuActivity"

BUILD_TYPE="debug"
SERIAL=""
LAUNCH=1
BUILD=1
CLEAN=0

while getopts "rs:nlch" opt; do
  case $opt in
    r) BUILD_TYPE="release" ;;
    s) SERIAL="$OPTARG" ;;
    n) LAUNCH=0 ;;
    l) BUILD=0 ;;
    c) CLEAN=1 ;;
    h|*)
      sed -n '2,11p' "$0"
      exit 0
      ;;
  esac
done

have() { command -v "$1" >/dev/null 2>&1; }
have adb || { echo "adb not found on PATH"; exit 1; }

# --- JDK selection -----------------------------------------------------------
# Kotlin 2.2.10 can't parse Java 25's version string, so force JDK 21 or 17.
pick_jdk() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    local major
    major="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F[\".] '/version/ {print $2; exit}')"
    if [[ "$major" == "17" || "$major" == "21" ]]; then
      return
    fi
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    for v in 21 17; do
      if home="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"; then
        export JAVA_HOME="$home"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "Using JDK $v at $JAVA_HOME"
        return
      fi
    done
  fi
  echo "No JDK 17 or 21 found. Install one (e.g. 'brew install openjdk@21') or set JAVA_HOME." >&2
  exit 1
}
pick_jdk

# --- Pick device -------------------------------------------------------------
pick_device() {
  local devices=()
  local line
  while IFS= read -r line; do
    [[ -n "$line" ]] && devices+=("$line")
  done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -eq 0 ]]; then
    echo "No adb devices connected. Plug in a device or start an emulator." >&2
    exit 1
  fi
  if [[ ${#devices[@]} -eq 1 ]]; then
    SERIAL="${devices[0]}"
    echo "Using sole device: $SERIAL"
    return
  fi
  echo "Connected devices:"
  local i=0
  for d in "${devices[@]}"; do
    local model
    model="$(adb -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    printf "  [%d] %-24s %s\n" "$i" "$d" "$model"
    i=$((i+1))
  done
  read -r -p "Pick device #: " choice
  if ! [[ "$choice" =~ ^[0-9]+$ ]] || (( choice < 0 || choice >= ${#devices[@]} )); then
    echo "Invalid choice." >&2; exit 1
  fi
  SERIAL="${devices[$choice]}"
}

if [[ -z "$SERIAL" ]]; then
  pick_device
fi

# --- Build -------------------------------------------------------------------
if (( BUILD )); then
  cd "$ANDROID_DIR"
  if (( CLEAN )); then
    ./gradlew clean
  fi
  if [[ "$BUILD_TYPE" == "release" ]]; then
    ./gradlew :assembleRelease
  else
    ./gradlew :assembleDebug
  fi

  APK_DIR="$ANDROID_DIR/build/outputs/apk/$BUILD_TYPE"
  APK="$(ls -1t "$APK_DIR"/*.apk 2>/dev/null | head -1 || true)"
  [[ -z "$APK" ]] && { echo "No APK found in $APK_DIR" >&2; exit 1; }

  echo "Installing $APK -> $SERIAL"
  adb -s "$SERIAL" install -r -d "$APK"
fi

# --- Launch ------------------------------------------------------------------
if (( LAUNCH )); then
  echo "Launching $APP_ID on $SERIAL"
  adb -s "$SERIAL" shell am start -n "$APP_ID/$LAUNCHER_ACTIVITY"
fi
