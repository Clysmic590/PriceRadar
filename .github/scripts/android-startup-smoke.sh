#!/usr/bin/env bash
set -euo pipefail

apk=${1:?Pass the optimized APK path}
serial=${ANDROID_SERIAL:-emulator-5554}
reports=${SMOKE_REPORTS:-smoke-reports}
package=ro.priceradar
adb_cmd=(adb -s "$serial")

# This test deliberately clears app data and may run only on a disposable emulator.
[[ "$serial" == emulator-* ]]
[[ "$("${adb_cmd[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
mkdir -p "$reports"
collect() {
  status=$?
  "${adb_cmd[@]}" logcat -d > "$reports/logcat.txt" 2>&1 || true
  "${adb_cmd[@]}" shell dumpsys activity activities > "$reports/activities.txt" 2>&1 || true
  "${adb_cmd[@]}" exec-out screencap -p > "$reports/screen.png" 2>/dev/null || true
  return "$status"
}
trap collect EXIT

"${adb_cmd[@]}" install -r "$apk"
"${adb_cmd[@]}" shell pm clear "$package"
"${adb_cmd[@]}" logcat -c
for attempt in 1 2; do
  "${adb_cmd[@]}" shell am force-stop "$package"
  "${adb_cmd[@]}" shell am start -W -n "$package/.MainActivity"
  sleep 5
  [[ -n "$("${adb_cmd[@]}" shell pidof "$package" | tr -d '\r')" ]] || { echo "App crashed on launch $attempt"; exit 1; }
  "${adb_cmd[@]}" shell uiautomator dump /sdcard/priceradar-window.xml
  "${adb_cmd[@]}" pull /sdcard/priceradar-window.xml "$reports/window-$attempt.xml"
  python3 - "$reports/window-$attempt.xml" <<'PY'
import sys, xml.etree.ElementTree as E
nodes = E.parse(sys.argv[1]).iter('node')
texts = {n.attrib.get('text') for n in nodes if n.attrib.get('package') == 'ro.priceradar'}
assert {'PriceRadar', 'Watchlist', 'Settings'} <= texts, f'Main screen did not render: {texts}'
PY
  echo "Launch $attempt: app alive and main screen rendered"
done
"${adb_cmd[@]}" shell settings put system accelerometer_rotation 0
"${adb_cmd[@]}" shell settings put system user_rotation 1
sleep 3
[[ -n "$("${adb_cmd[@]}" shell pidof "$package" | tr -d '\r')" ]]
echo 'Optimized APK passed Android startup/restart/rotation smoke checks'
