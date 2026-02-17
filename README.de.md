# HA Shopping List (Android)

Standalone-Android-App für die **Home Assistant Shopping-List** (Todo-List) mit **Live-Updates via WebSocket**, **Drag’n’Drop-Reihenfolge**, **Inline-Editing** und optionalen **Push-Notifications bei neuen Einträgen** (auch im Hintergrund via Foreground Service).

---

## Features (aktueller Stand)

### Live via Home Assistant WebSocket API
- WebSocket-Connect auf `/api/websocket` (OkHttp)
- Auth via Long-Lived Access Token
- Automatisches **Reconnect**-Verhalten (bei Disconnect/Failure)
- Subscribe auf Item-Events und Initial-Load
    - `todo/item/subscribe`
    - `todo/item/list`

### Liste & Aktionen
- Items anzeigen (Compose, Flow/StateFlow)
- Item hinzufügen (`todo.add_item`)
- Item umschalten erledigt/offen (`todo.update_item`)
- Item umbenennen (`todo.update_item`)
- **Erledigte löschen** (Bulk) per Confirm-Dialog (`todo.remove_item` mit Array)
- **Drag’n’Drop Sortierung** für offene Items:
    - UI-Reorder lokal
    - Persistenz in HA per `todo/item/move` (mit `previous_uid`)

### UX / UI
- Settings-Screen (URL/Token + Notifications Toggle)
- Fehlerzustände:
    - Auth-Fehler (Token ungültig)
    - Connection-Fehler (Netzwerk/WebSocket down)
    - CTA zur Settings-Seite

### Notifications (neue Einträge)
- Notification bei neu auftauchenden Items (via `newItems` SharedFlow)
- Optional **auch im Hintergrund**, wenn Notifications aktiviert:
    - Foreground Service hält WS-Verbindung & sendet Notifications
- Runtime-Permission für Android 13+ (`POST_NOTIFICATIONS`)

> Die App vermeidet Doppel-Notifications für lokal hinzugefügte Items (Name wird kurzzeitig gemerkt).


---

## Lokalisierungen

Momentan steht die APP in folgenden Sprachen zur Verfügung

- Englisch (default)
- Deutsch
- Spanisch
- Französisch
- Italienisch
- Japanisch
- Koreanisch
- Niederländisch
- Polnisch
- Portugiesisch
- Russisch
- Türkisch

---

## Projektstruktur (Kurzüberblick)

- `MainActivity.kt`
    - Compose UI (ShoppingScreen + SettingsScreen)
    - Drag’n’Drop (reorderable LazyList)
    - Confirm-Dialog für „Erledigte löschen“
    - Start/Stop Foreground Service abhängig von Settings
- `data/`
    - `HaWebSocketRepository` – subscribe/list/events + Aktionen (add/toggle/rename/move/clearCompleted)
    - `websocket/HaWebSocketClient` – Connect/Auth/Events, Reconnect
    - `SettingsDataStore` – URL/Token/Notifications enabled
    - `HaApi`, `HaServiceFactory` – REST (derzeit nicht primär)
- `service/HaWsForegroundService`
    - hält Repository (Singleton) & versendet Notifications
- `util/`
    - `NotificationHelper` – Notification-Channel + Notification bauen
    - `UrlNormalizer` – URL normalisieren (Schema + Slash)
    - `Debug` – Debug-Logging
- `viewmodel/ShoppingViewModel`
    - UI-Bridge auf Repository (Flows + Aktionen)

---

## Setup / Konfiguration

### 1) Home Assistant vorbereiten
- Einen **Long-Lived Access Token** erstellen (Profil → Long-Lived Access Tokens)
- Sicherstellen, dass die ToDo-Entity existiert (aktuell erwartet: `todo.einkaufsliste`)

### 2) App konfigurieren (Settings)
- **Home Assistant URL**
    - Beispiel: `http://homeassistant.local:8123` oder `http://192.168.x.x:8123`
    - Die App normalisiert:
        - wenn kein Schema: default `https://`
        - entfernt doppelte Slashes, erzwingt trailing `/`
- **Token** (Long-Lived Access Token)
- **Notifications** (Toggle)
    - aktiviert → Foreground Service startet automatisch (sofern URL/Token gesetzt)
    - deaktiviert → Service wird gestoppt

---

## Permissions (Android 13+)

- `android.permission.POST_NOTIFICATIONS`
    - Wird in `MainActivity` zur Laufzeit angefragt (SDK 33+)
    - Ohne Erlaubnis: Notifications können ausbleiben

---

## Build & Run

### Android Studio
- Projekt öffnen → Gradle Sync → Run (Debug)

### CLI
- `./gradlew installDebug`

---

## WebSocket-Protokoll (vereinfacht)

1. Connect: `ws(s)://<base>/api/websocket`
2. Server → `auth_required`
3. Client → `{"type":"auth","access_token":"..."}`
4. Server → `auth_ok`
5. Client:
    - `todo/item/subscribe` (entity_id)
    - `todo/item/list` (entity_id)
6. Server → `result` (items) / `event` (items) → Repository parst → UI aktualisiert

Reconnect:
- bei `onClosed` / `onFailure` → Reconnect nach ~2s (wenn nicht manuell disconnected)

---

## Troubleshooting

### „Missing configuration“
- URL oder Token leer → Settings ausfüllen

### Auth failed
- Token ungültig/abgelaufen → neuen Long-Lived Token setzen

### Connection errors
- HA nicht erreichbar (Netzwerk/VPN/HTTPS/HTTP)
- URL prüfen (inkl. Port)
- bei Self-signed TLS ggf. HTTPS/Cert-Thema beachten

### Keine Items / Spinner hängt
- Prüfen, ob Entity `todo.einkaufsliste` existiert
- Debug-Logs ansehen (`HASL:`)

---

## Roadmap / TODO (konkret)

- Token-Feld härten (Copy/Screenshot/Autofill verhindern)
- Notifications verbessern (Localization, Aktionen, Deep-Link)
- Robustere Reconnect-Strategie (Backoff, Offline-Erkennung)
- REST-Teil entweder entfernen oder gezielt als Fallback integrieren

---
