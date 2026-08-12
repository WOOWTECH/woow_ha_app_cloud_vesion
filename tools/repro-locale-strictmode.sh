#!/usr/bin/env bash
# Reproduce the AppCompat auto-store-locales DiskReadViolation FailFast crash.
#
# The violation fires inside LaunchActivity.attachBaseContext() during a COLD START,
# before any UI exists -- so this is a cold-start loop, not a UI-automation problem.
# Suppressed by IgnoreAppCompatPersistLocalesDiskReadWrite (upstream PR #6768); this
# script is what proves the rule still works after an AppCompat upgrade.
#
# Requirements:
#   - a device/emulator running API 31 or 32 (Android 12 / 12L)
#     * API <= 30: HAStrictMode is never enabled (HomeAssistantApplication.kt:87)
#     * API >= 33: AppCompat syncs locales on a background executor -> no violation
#   - a DEBUG build installed (release uses LogOnlyFailFastHandler, no crash)
#
# Creating the emulator this was validated against:
#   sdkmanager "system-images;android-32;google_apis;arm64-v8a"
#   avdmanager create avd -n api32_repro -k "system-images;android-32;google_apis;arm64-v8a" -d pixel_5
#   emulator -avd api32_repro -no-snapshot -no-audio -no-boot-anim
#
# Expected: 0/N with the ignore rule in place, N/N without it.
#
# Usage: ./tools/repro-locale-strictmode.sh [iterations] [package]

set -uo pipefail

ITERATIONS="${1:-20}"
# NOTE: this fork's applicationId is com.woowtech.home (build-logic .../AndroidApplicationConventionPlugin.kt:8)
# while the Kotlin namespace is still io.homeassistant.companion.android.
PKG="${2:-com.woowtech.home.minimal.debug}"
ACTIVITY="io.homeassistant.companion.android.launch.LaunchActivity"
# Outside the working tree: these logs are throwaway and must never be committed.
OUTDIR="${TMPDIR:-/tmp}/repro-locale-strictmode-logs"
mkdir -p "$OUTDIR"

sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
model=$(adb shell getprop ro.product.model | tr -d '\r')
echo "Device: $model (API $sdk)"

if [ "$sdk" -ge 33 ]; then
  echo "!! API $sdk cannot reproduce: AppCompatDelegate.syncRequestedAndStoredLocales()"
  echo "!! takes the sSerialExecutorForLocalesStorage (background) branch on API >= 33."
  echo "!! Use an API 31 or 32 image."
  exit 2
fi
if [ "$sdk" -lt 31 ]; then
  echo "!! API $sdk cannot reproduce: StrictMode is only enabled on SDK_INT >= S (31)."
  exit 2
fi

adb shell pm list packages | grep -q "^package:${PKG}$" || { echo "!! $PKG not installed"; exit 2; }

hits=0
for i in $(seq 1 "$ITERATIONS"); do
  log="$OUTDIR/run-$(printf '%03d' "$i").log"

  # Full cold start: kill the process AND drop the app's locale storage file so
  # sStoredAppLocales starts null again on every iteration.
  adb shell am force-stop "$PKG"
  adb shell run-as "$PKG" rm -f \
    files/androidx.appcompat.app.AppCompatDelegate.application_locales_record_file 2>/dev/null
  adb logcat -c

  adb shell am start -W -n "$PKG/$ACTIVITY" >/dev/null 2>&1
  sleep 4

  adb logcat -d > "$log" 2>&1

  # Did the process survive? FailFast calls exitProcess(1).
  pid=$(adb shell pidof "$PKG" | tr -d '\r')

  if grep -q "CRITICAL FAILURE: FAIL-FAST" "$log"; then
    hits=$((hits + 1))
    echo "[$i] REPRODUCED (log: $log)"
    grep -A3 "strictmode" "$log" | head -8
  elif [ -z "$pid" ]; then
    echo "[$i] process died without a FailFast banner -- inspect $log"
  else
    echo "[$i] clean start (no FailFast: rule matched, or locales set after attachBaseContext)"
    rm -f "$log"
  fi
done

echo
echo "Reproduced $hits / $ITERATIONS cold starts."
echo "Logs for failing runs kept in $OUTDIR"
