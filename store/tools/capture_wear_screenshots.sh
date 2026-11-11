#!/usr/bin/env bash
# Takes the Wear OS store screenshots (list, further down, completed items, tile) in English
# and German and writes them to fastlane/metadata/android/<locale>/images/wearScreenshots/.
#
# Requirements:
#   - a running round Wear OS emulator (e.g. "Wear OS Large Round", 454 x 454 px) as $DEVICE
#   - the debug build: ./gradlew :wear:assembleDebug  (allows http:// to the demo server)
#   - python on PATH
#
# The app settings on the emulator (Home Assistant address, token, list) are saved before
# and restored afterwards. While capturing, the debug-only "screenshot_mode" keeps a paired
# phone from replacing the demo settings and counts.
#
# Usage (from the project root, Git Bash or Linux/macOS):
#   store/tools/capture_wear_screenshots.sh
set -u
export MSYS_NO_PATHCONV=1

DEVICE="${DEVICE:-emulator-5554}"
ADB_BIN="${ADB:-adb}"
if ! command -v "$ADB_BIN" > /dev/null && [ -n "${LOCALAPPDATA:-}" ]; then
  ADB_BIN="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
fi
adb() { "$ADB_BIN" -s "$DEVICE" "$@"; }
# Paths handed to Windows programs (adb.exe, python.exe) under Git Bash need Windows form
hostpath() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }

TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$TOOLS/../.." && pwd)"
PKG=de.robnice.homeshoplist
TILE="$PKG/.wear.tile.ShoppingTileService"
STORE="$ROOT/fastlane/metadata/android"
PREFS_FILE=shared_prefs/wear_ha_settings.xml
BACKUP=files/wear_ha_settings.backup.xml

adb install -r "$(hostpath "$ROOT/wear/build/outputs/apk/debug/wear-debug.apk")" > /dev/null

# Save the current settings
had_settings=false
if adb shell "run-as $PKG test -f $PREFS_FILE" ; then
  adb shell "run-as $PKG sh -c 'mkdir -p files && cp $PREFS_FILE $BACKUP'"
  had_settings=true
fi

restore() {
  adb shell am force-stop "$PKG"
  if [ "$had_settings" = true ]; then
    adb shell "run-as $PKG sh -c 'cp $BACKUP $PREFS_FILE && rm $BACKUP'"
  else
    adb shell "run-as $PKG rm -f $PREFS_FILE"
  fi
  adb shell cmd locale set-app-locales "$PKG" --locales ""
  rebuild_tile
  adb shell input keyevent KEYCODE_HOME
  echo "Settings restored"
}

# Points the app to the demo server with the given list
use_demo_settings() {
  local entity=$1
  local prefs
  prefs="$(mktemp)"
  cat > "$prefs" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="ha_url">http://10.0.2.2:8123</string>
    <string name="ha_token">demo</string>
    <string name="todo_entity">$entity</string>
    <string name="list_display_mode">categorized</string>
    <string name="area_order">produce,meat,milk,bakery,coffee_tea,pet</string>
    <boolean name="screenshot_mode" value="true" />
</map>
XML
  adb shell am force-stop "$PKG"
  adb push "$(hostpath "$prefs")" /data/local/tmp/wear_ha_settings.xml > /dev/null
  rm "$prefs"
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/wear_ha_settings.xml $PREFS_FILE'"
  adb shell rm /data/local/tmp/wear_ha_settings.xml
}

# Removing and adding the tile makes the system request a fresh one right away
rebuild_tile() {
  adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation remove-tile --ecn component "$TILE" > /dev/null
  sleep 2
  adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component "$TILE" > /dev/null
}

capture() {
  local lang=$1 locale=$2 entity=$3
  local out="$STORE/$locale/images/wearScreenshots"
  mkdir -p "$out"

  python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" > /dev/null 2>&1 &
  local server=$!
  sleep 2

  adb shell cmd locale set-app-locales "$PKG" --locales "$locale"
  # Right after installing, the paired phone may still deliver its settings and overwrite
  # the file, so check the demo settings are in place just before starting the app.
  for _ in 1 2 3 4 5; do
    use_demo_settings "$entity"
    sleep 3
    adb shell "run-as $PKG cat $PREFS_FILE" | grep -q 'screenshot_mode' && break
  done
  adb shell am start -W -n "$PKG/.wear.WearMainActivity" > /dev/null
  sleep 7
  adb exec-out screencap -p > "$out/1.png"                 # header and top of the list

  adb shell input swipe 227 380 227 140 500; sleep 2
  adb exec-out screencap -p > "$out/2.png"                 # further down

  for _ in 1 2 3 4; do adb shell input swipe 227 400 227 60 200; done; sleep 2
  adb shell input swipe 227 150 227 330 600; sleep 2
  adb exec-out screencap -p > "$out/3.png"                 # completed items + delete button

  rebuild_tile
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI \
    --es operation show-tile --ei index 0 > /dev/null
  sleep 7
  adb exec-out screencap -p > "$out/4.png"                 # tile

  kill "$server"
  echo "Wrote $out"
}

trap restore EXIT
capture en en-US todo.shopping_list
capture de de-DE todo.einkaufsliste
