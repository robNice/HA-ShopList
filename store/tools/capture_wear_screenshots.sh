#!/usr/bin/env bash
# Takes the Wear OS store screenshots (list, further down, completed items, tile, category choice
# when adding) in all app languages and writes them to store/playstore/<locale>/wear_N.png.
# STORE_LOCALES="de-DE en-US" limits them to these store folders.
# Then the help screenshots (list, category choice) in all help languages, written to
# store/help_raw/<language>/wear_*.png; make_help_images.py turns them into the help images.
#
# STORE_ONLY=1 skips the help screenshots, HELP_ONLY=1 the store screenshots.
# Use a square Wear OS emulator for the store: Play does not allow masked (round) screenshots.
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
STORE="$ROOT/store/playstore"
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

# Active categories of the demo store, in its order (offered when adding an item)
MARKET_AREAS=produce,bakery,meat,fish_seafood,milk,dairy_eggs,cheese_deli,cold_cuts,frozen,dry_goods,canned_jars,sauces_spices,breakfast,snacks_sweets,drinks,coffee_tea,alcohol,household,cleaning,paper_goods,personal_care,baby,pet,health

# The same as JSON, XML escaped; the labels are only read by screen readers
market_areas_json() {
  local json="" key
  for key in ${MARKET_AREAS//,/ }; do
    json="$json${json:+,}{&quot;key&quot;:&quot;$key&quot;,&quot;label&quot;:&quot;$key&quot;}"
  done
  echo "[$json]"
}

# Title of the demo list; a second list makes the list button appear
LIST_NAME="Shopping list"
list_name() { echo "$LIST_NAME"; }

# Starts the demo server: the short watch list in English and German, the phone list otherwise
start_server() {
  local lang=$1 entity=$2
  case $lang in
    en|de)
      [ "$lang" = de ] && LIST_NAME="Einkaufsliste" || LIST_NAME="Shopping list"
      python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" > /dev/null 2>&1 & ;;
    *)
      LIST_NAME="$(python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" name)"
      python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" phone "$entity" > /dev/null 2>&1 & ;;
  esac
  SERVER=$!
  sleep 2
}

# Name of a new product in the category choice screenshot
add_name() {
  case $1 in
    de) echo "Hafermilch" ;; es) echo "Leche de avena" ;; fr) echo "Lait d’avoine" ;;
    it) echo "Latte d’avena" ;; ja) echo "オーツミルク" ;; ko) echo "귀리 우유" ;; nl) echo "Havermelk" ;;
    pl) echo "Mleko owsiane" ;; pt) echo "Leite de aveia" ;; ru) echo "Овсяное молоко" ;;
    tr) echo "Yulaf sütü" ;; uk) echo "Вівсяне молоко" ;; *) echo "Oat milk" ;;
  esac
}

# Starts the app; with a name it opens the category choice for that new item (debug builds only)
start_app() {
  adb shell am force-stop "$PKG"
  # Closes whatever is still open on top (e.g. the system input picker) and goes to the watch face
  adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_HOME; sleep 1
  if [ -n "${1:-}" ]; then
    adb shell "am start -W -n $PKG/.wear.WearMainActivity --es demo_add_name '$1'" > /dev/null
  else
    adb shell am start -W -n "$PKG/.wear.WearMainActivity" > /dev/null
  fi
  sleep 7
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
    <string name="area_order">$MARKET_AREAS</string>
    <string name="market_areas">$(market_areas_json)</string>
    <string name="todo_list_name">$(list_name "$entity")</string>
    <string name="known_lists">[{&quot;id&quot;:&quot;$entity&quot;,&quot;name&quot;:&quot;$(list_name "$entity")&quot;},{&quot;id&quot;:&quot;todo.second&quot;,&quot;name&quot;:&quot;2&quot;}]</string>
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
  local out="$STORE/$locale"
  mkdir -p "$out"

  start_server "$lang" "$entity"
  local server=$SERVER

  adb shell cmd locale set-app-locales "$PKG" --locales "$locale"
  # Right after installing, the paired phone may still deliver its settings and overwrite
  # the file, so check the demo settings are in place just before starting the app.
  for _ in 1 2 3 4 5; do
    use_demo_settings "$entity"
    sleep 3
    adb shell "run-as $PKG cat $PREFS_FILE" | grep -q 'screenshot_mode' && break
  done
  # The first start after switching the language loads slowly: warm up, then start again
  start_app
  start_app
  adb exec-out screencap -p > "$out/wear_1.png"                 # header and top of the list

  adb shell input swipe 227 380 227 140 500; sleep 2
  adb exec-out screencap -p > "$out/wear_2.png"                 # further down

  for _ in 1 2 3 4; do adb shell input swipe 227 400 227 60 200; done; sleep 2
  adb exec-out screencap -p > "$out/wear_3.png"                 # completed items + delete button (end of list)

  rebuild_tile
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI \
    --es operation show-tile --ei index 0 > /dev/null
  sleep 7
  adb exec-out screencap -p > "$out/wear_4.png"                 # tile

  start_app "$(add_name "$lang")"
  adb exec-out screencap -p > "$out/wear_5.png"                 # category choice when adding

  kill "$server"
  echo "Wrote $out"
}

# Help screenshots: the phone demo list exists in every help language
capture_help() {
  local lang=$1
  local out="$ROOT/store/help_raw/$lang"
  mkdir -p "$out"

  LIST_NAME="$(python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" name)"
  python "$(hostpath "$TOOLS/mock_ha.py")" "$lang" phone todo.shopping_list > /dev/null 2>&1 &
  local server=$!
  sleep 2

  adb shell cmd locale set-app-locales "$PKG" --locales "$lang"
  for _ in 1 2 3 4 5; do
    use_demo_settings todo.shopping_list
    sleep 3
    adb shell "run-as $PKG cat $PREFS_FILE" | grep -q 'screenshot_mode' && break
  done
  start_app
  adb exec-out screencap -p > "$out/wear_main.png"
  start_app "$(add_name "$lang")"
  adb exec-out screencap -p > "$out/wear_add.png"

  kill "$server"
  echo "Wrote $out/wear_*.png"
}

trap restore EXIT
if [ -z "${HELP_ONLY:-}" ]; then
  # App language and store folder; the other store languages use the listing's default language
  for pair in en:en-US de:de-DE es:es-ES fr:fr-FR it:it-IT ja:ja-JP ko:ko-KR nl:nl-NL pl:pl-PL               pt:pt-BR ru:ru-RU tr:tr-TR uk:uk; do
    lang=${pair%%:*}; locale=${pair#*:}
    [ -n "${STORE_LOCALES:-}" ] && [[ " $STORE_LOCALES " != *" $locale "* ]] && continue
    entity=todo.shopping_list; [ "$lang" = de ] && entity=todo.einkaufsliste
    capture "$lang" "$locale" "$entity"
  done
  # Latin American Spanish uses the Spanish screenshots, like the phone screenshots
  if [ -f "$STORE/es-ES/wear_1.png" ]; then
    mkdir -p "$STORE/es-419"
    cp "$STORE"/es-ES/wear_*.png "$STORE/es-419/"
  fi
fi
if [ -z "${STORE_ONLY:-}" ]; then
  for lang in ${HELP_LANGS:-en de es fr it ja ko nl pl pt ru tr uk}; do
    capture_help "$lang"
  done
fi
