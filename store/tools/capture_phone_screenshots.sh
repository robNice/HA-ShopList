#!/usr/bin/env bash
# Takes the raw phone screenshots for the store in every app language and writes them to
# store/screenshots_raw/<store locale>/1.png ... 6.png:
#   1 simple list, 2 list by store section, 3 drag & drop, 4 suggestions while typing,
#   5 store section order in the settings, 6 dark mode
# store/tools/make_store_graphics.py turns them into the store images.
#
# Requirements:
#   - a running phone emulator with 1080 x 2400 px (e.g. "Medium Phone API 35") as $DEVICE
#   - the debug build: ./gradlew :app:assemblePlayDebug  (allows http:// to the demo server)
#   - python on PATH; on Windows also powershell (text is pasted via the host clipboard,
#     because "adb shell input text" cannot type non-ASCII characters)
#
# The app data on the emulator (settings, product history) is saved before and restored afterwards.
#
# Usage (from the project root, Git Bash or Linux/macOS):
#   store/tools/capture_phone_screenshots.sh [language ...]     e.g. ... en de
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
OUT="$ROOT/store/screenshots_raw"
BACKUP=cache/store_screenshots_backup.tar
TMP="$(mktemp -d)"

# language | app locale | store folder | entity id (the app shows it as the list title)
LANGUAGES="en|en-US|en-US|todo.shopping_list
de|de-DE|de-DE|todo.einkaufsliste
es|es-ES|es-ES|todo.compra
fr|fr-FR|fr-FR|todo.courses
it|it-IT|it-IT|todo.spesa
ja|ja-JP|ja-JP|todo.買い物
ko|ko-KR|ko-KR|todo.장보기
nl|nl-NL|nl-NL|todo.boodschappen
pl|pl-PL|pl-PL|todo.zakupy
pt|pt-BR|pt-BR|todo.compras
ru|ru-RU|ru-RU|todo.покупки
tr|tr-TR|tr-TR|todo.alışveriş
uk|uk-UA|uk|todo.покупки"

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
  adb shell cmd uimode night no
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

# Fresh app data with the demo connection; mode is "simple" or "categorized"
use_demo_settings() {
  local entity=$1 mode=$2
  python "$(hostpath "$TOOLS/phone_prefs.py")" "$(hostpath "$TMP/settings.preferences_pb")" "$entity" "" "$mode"
  adb shell am force-stop "$PKG"
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

capture() {
  local lang=$1 locale=$2 folder=$3 entity=$4
  local out="$OUT/$folder"
  mkdir -p "$out"

  python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" phone "$entity" > /dev/null 2>&1 &
  server=$!
  sleep 2

  adb shell am force-stop "$PKG"
  adb shell "run-as $PKG sh -c 'rm -rf databases shared_prefs files'"
  adb shell cmd locale set-app-locales "$PKG" --locales "$locale"
  adb shell cmd uimode night no
  use_demo_settings "$entity" categorized
  start_app
  shot "$out/2.png"                                            # list by store section

  # Suggestions: paste a letter into the input field and hide the keyboard
  local letter
  letter="$(python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" letter)"
  powershell -NoProfile -Command "Set-Clipboard -Value '$letter'"
  adb shell input tap 384 379; sleep 1
  adb shell input keyevent 279; sleep 5                        # paste; wait for the clipboard toast to go
  adb shell input keyevent KEYCODE_BACK; sleep 2
  shot "$out/4.png"

  start_app
  adb shell input tap 1007 164; sleep 3                        # settings
  adb shell input tap 540 283; sleep 2                         # second tab: view
  shot "$out/5.png"

  adb shell cmd uimode night yes
  start_app
  shot "$out/6.png"                                            # dark mode
  adb shell cmd uimode night no

  use_demo_settings "$entity" simple
  start_app
  shot "$out/1.png"                                            # simple list

  # Last, because dropping the item changes the list
  use_demo_settings "$entity" categorized
  start_app
  adb shell "input motionevent DOWN 520 1489; sleep 1.2; for y in 1450 1400 1350 1300 1250 1200 1150 1100 1050 1000 960 930 910; do input motionevent MOVE 520 \$y; sleep 0.05; done"
  sleep 1.5
  shot "$out/3.png"                                            # dragging the fourth item
  adb shell input motionevent UP 520 910; sleep 2

  stop_server
  echo "Wrote $out"
}

demo_status_bar
# adb reads stdin, so the list comes in on its own file descriptor
while IFS='|' read -r lang locale folder entity <&3; do
  if [ $# -eq 0 ] || [[ " $* " == *" $lang "* ]]; then
    capture "$lang" "$locale" "$folder" "$entity"
  fi
done 3<<< "$LANGUAGES"
