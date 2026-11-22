#!/usr/bin/env bash
# Takes the screenshots for the help in the settings (tab "?") in every app language and writes them to
# store/help_raw/<language>/<name>.png. store/tools/make_help_images.py then crops and converts them
# into app/src/main/assets/help/<language>/<name>.webp, which help.md references.
#
# Requirements: like capture_phone_screenshots.sh (phone emulator 1080 x 2400 px as $DEVICE, debug build
# ./gradlew :app:assemblePlayDebug, python on PATH). The app data on the emulator is saved before and
# restored afterwards.
#
# Usage (from the project root, Git Bash or Linux/macOS):
#   store/tools/capture_help_screenshots.sh [language ...]     e.g. ... en de
#   HELP_ONLY=connection store/tools/capture_help_screenshots.sh   only the "Connection" tab
set -u
export MSYS_NO_PATHCONV=1

DEVICE="${DEVICE:-emulator-5554}"
ADB_BIN="${ADB:-adb}"
if ! command -v "$ADB_BIN" > /dev/null && [ -n "${LOCALAPPDATA:-}" ]; then
  ADB_BIN="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
fi
adb() { "$ADB_BIN" -s "$DEVICE" "$@"; }
hostpath() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }

TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$TOOLS/../.." && pwd)"
PKG=de.robnice.homeshoplist
OUT="$ROOT/store/help_raw"
BACKUP=cache/help_screenshots_backup.tar
TMP="$(mktemp -d)"

# language | app locale | entity id
LANGUAGES="en|en-US|todo.shopping_list
de|de-DE|todo.einkaufsliste
es|es-ES|todo.compra
fr|fr-FR|todo.courses
it|it-IT|todo.spesa
ja|ja-JP|todo.kaimono
ko|ko-KR|todo.jangbogi
nl|nl-NL|todo.boodschappen
pl|pl-PL|todo.zakupy
pt|pt-BR|todo.compras
ru|ru-RU|todo.pokupki
tr|tr-TR|todo.alisveris
uk|uk-UA|todo.pokupky"

adb install -r -g "$(hostpath "$ROOT/app/build/outputs/apk/play/debug/app-play-debug.apk")" > /dev/null

adb shell am force-stop "$PKG"
if ! adb shell "run-as $PKG test -f $BACKUP"; then
  adb shell "run-as $PKG sh -c 'mkdir -p cache && tar -cf $BACKUP databases shared_prefs files 2>/dev/null'"
fi

server=""
stop_server() { [ -n "$server" ] && kill "$server" 2> /dev/null; server=""; }

restore() {
  stop_server
  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'rm -rf databases shared_prefs files && tar -xf $BACKUP && rm $BACKUP'"
  adb shell cmd locale set-app-locales "$PKG" --locales ""
  adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
  adb shell input keyevent KEYCODE_HOME
  rm -rf "$TMP"
  echo "App data restored"
}
trap restore EXIT

demo_status_bar() {
  adb shell settings put global sysui_demo_allowed 1
  for command in "enter" "clock -e hhmm 1200" "network -e wifi show -e level 4 -e fully true" "network -e mobile hide" \
                 "battery -e level 100 -e plugged false" "notifications -e visible false"; do
    adb shell am broadcast -a com.android.systemui.demo -e command $command > /dev/null
  done
}

# Fresh app data: demo connection, three stores, a store per list
use_help_settings() {
  local lang=$1 entity=$2 name=$3 url=${4:-http://10.0.2.2:8123}
  python "$(hostpath "$TOOLS/help_prefs.py")" "$(hostpath "$TMP/settings.preferences_pb")" "$lang" "$entity" "$name" "$url"
  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'rm -rf databases shared_prefs files'"
  adb push "$(hostpath "$TMP/settings.preferences_pb")" /data/local/tmp/settings.preferences_pb > /dev/null
  adb shell "run-as $PKG sh -c 'mkdir -p files/datastore && cp /data/local/tmp/settings.preferences_pb files/datastore/'"
  adb shell rm /data/local/tmp/settings.preferences_pb
}

start_app() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 9
}

shot() { adb exec-out screencap -p > "$1"; }

# Taps the element with the given Compose test tag (see testTagsAsResourceId in the app)
tap() {
  local pos
  pos="$(adb exec-out uiautomator dump /dev/tty | python "$(hostpath "$TOOLS/ui_find.py")" "$1")" || {
    echo "  element $1 not found" >&2; return 1; }
  adb shell input tap $pos
}

# Waits (max. 20 s) until the element with the given test tag is shown
wait_for() {
  for _ in $(seq 1 20); do
    adb exec-out uiautomator dump /dev/tty | python "$(hostpath "$TOOLS/ui_find.py")" "$1" > /dev/null && { sleep 1; return 0; }
    sleep 1
  done
  echo "  element $1 did not appear" >&2
  return 1
}

capture() {
  local lang=$1 locale=$2 entity=$3
  local out="$OUT/$lang" name
  mkdir -p "$out"
  name="$(python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" name)"

  python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" phone "$entity" help > /dev/null 2>&1 &
  server=$!
  sleep 2

  adb shell cmd locale set-app-locales "$PKG" --locales "$locale"
  if [ "${HELP_ONLY:-}" != connection ]; then
  use_help_settings "$lang" "$entity" "$name"
  start_app
  shot "$out/main.png"

  tap header_list; sleep 3
  shot "$out/list_menu.png"
  adb shell input keyevent KEYCODE_BACK; sleep 1

  tap open_settings; sleep 3
  tap tab_lists
  # Lists and admin check loaded; if the check came back empty, leaving the tab and coming back repeats it
  for _ in 1 2 3; do
    wait_for list_delete && break
    tap tab_markets; sleep 2
    tap tab_lists
  done
  sleep 1                                                    # page animation finished
  shot "$out/settings_lists.png"
  tap tab_markets; sleep 2
  shot "$out/settings_markets.png"
  tap market_add; sleep 2
  shot "$out/market_add.png"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  tap tab_data; sleep 2
  shot "$out/settings_data.png"

  fi

  # Connection last, with a typical address instead of the emulator's host address
  use_help_settings "$lang" "$entity" "$name" "http://homeassistant.local:8123"
  start_app
  tap open_settings; sleep 3
  tap tab_connection; sleep 2
  shot "$out/settings_connection.png"

  stop_server
  echo "Wrote $out"
}

demo_status_bar
adb shell cmd uimode night no
# adb reads stdin, so the list comes in on its own file descriptor
while IFS='|' read -r lang locale entity <&3; do
  if [ $# -eq 0 ] || [[ " $* " == *" $lang "* ]]; then
    capture "$lang" "$locale" "$entity"
  fi
done 3<<< "$LANGUAGES"
