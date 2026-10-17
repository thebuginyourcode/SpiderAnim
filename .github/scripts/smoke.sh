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
launch
shot tablet-0-gallery
tap_by text "New Project"
tap_by text "Create"
sleep 2
adb shell input swipe 1000 380 1400 520 400
tap_by text "Add Drawing"
adb shell input swipe 1000 520 1400 380 400
shot tablet-1-frames
# Draw past the camera frame, then drag the playback bar up to grow the timeline.
adb shell input swipe 700 300 230 640 400
ui
P=$(python3 .github/scripts/find.py /tmp/ui.xml content-desc "Previous frame" 0)
if [ -n "$P" ]; then set -- $P; adb shell input swipe $(( $1 + 320 )) $2 $(( $1 + 320 )) $(( $2 - 220 )) 700; fi
sleep 1
shot tablet-1b-offcamera-resized

# Make a symbol, draw two drawings inside it and mark them.
tap_by content-desc "Add"
tap_by text "Symbol…"
tap_by content-desc "New symbol"
adb shell input swipe 1100 400 1300 420 300
tap_by content-desc "Add"
tap_by text "Marker"
tap_by text "Add Drawing"
adb shell input swipe 1100 380 1300 480 300
tap_by content-desc "Add"
tap_by text "Marker"
shot tablet-2-in-symbol
tap_by text "Scene 1"

# Place it and pick a frame by marker.
tap_by text "Library"
tap_by text "Symbol 1"
shot tablet-3-placed
tap_by text "Markers" 1
shot tablet-4-markers
tap_by text "Marker 2"
tap_by content-desc "Play"
sleep 3
tap_by content-desc "Play"
tap_by content-desc "Open Symbol 1"
shot tablet-5-entered
tap_by text "Scene 1"
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
tap_by content-desc "Add"
shot phone-2-menu
adb shell input keyevent KEYCODE_BACK
launch
shot phone-3-gallery

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
