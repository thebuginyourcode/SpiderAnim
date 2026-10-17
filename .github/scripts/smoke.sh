#!/usr/bin/env bash
# Emulator smoke test: drives the app through its main flows, screenshots each step and
# fails if the app crashed. Screenshots are attached to the commit as check runs.
set -u
PKG=com.spideranim
OUT=/tmp/shots
mkdir -p "$OUT"

adb install -r app-debug.apk
adb logcat -c

ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > /tmp/ui.xml; }

# tap_by text|content-desc "value" [nth]
tap_by() {
  ui
  local xy
  xy=$(python3 .github/scripts/find.py /tmp/ui.xml "$1" "$2" "${3:-0}")
  if [ -n "$xy" ]; then
    echo "tap $1=$2 at $xy"
    adb shell input tap $xy
  else
    echo "NOT FOUND: $1=$2"
  fi
  sleep 1.5
}

shot() { sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

launch() {
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/.MainActivity "$@"
  sleep 9
}

# ---- tablet (landscape)
adb shell wm size 2560x1600
adb shell wm density 320
launch --ez openLatest true
shot tablet-1-editor
adb shell input swipe 1000 450 1500 650 500
sleep 1
tap_by text "Mouth"
tap_by content-desc "Properties"
shot tablet-2-properties
tap_by text "Markers"
shot tablet-3-markers
tap_by text "O"
tap_by content-desc "Play"
sleep 3
tap_by content-desc "Play"
tap_by content-desc "Properties"
tap_by text "Mouth"
tap_by text "Edit Symbol"
shot tablet-4-in-symbol
adb shell input swipe 1100 500 1400 520 300
tap_by text "Add Drawing"
shot tablet-5-new-drawing
tap_by text "Scene 1"
tap_by text "Library"
shot tablet-6-library
tap_by text "Actions"
tap_by text "Export GIF"
sleep 25
adb shell input keyevent KEYCODE_BACK
sleep 2

# ---- phone (portrait)
adb shell wm size 1080x2340
adb shell wm density 440
launch --ez openLatest true
shot phone-1-editor
launch
shot phone-2-gallery

adb shell wm size reset
adb shell wm density reset

# ---- crash check
adb logcat -d > /tmp/logcat.txt
if grep -q "FATAL EXCEPTION" /tmp/logcat.txt; then
  echo "::error::App crashed"
  grep -A 40 "FATAL EXCEPTION" /tmp/logcat.txt
  CRASH=1
else
  echo "No crashes"
  CRASH=0
fi

python3 .github/scripts/upload_shots.py "$OUT" || true
exit $CRASH
