# HA Shopping List (Android)

Standalone Android app for the **Home Assistant Shopping List** (ToDo list) with **live updates via WebSocket**, **drag-and-drop ordering**, **inline editing**, and optional **push notifications for new entries** (also in the background via a foreground service).

---

## Features (current status)

### Live via Home Assistant WebSocket API
- WebSocket connect to `/api/websocket` (OkHttp)
- Auth via Long-Lived Access Token
- Automatic **reconnect** behavior (on disconnect/failure)
- Subscribe to item events and initial load
    - `todo/item/subscribe`
    - `todo/item/list`

### List & actions
- Display items (Compose, Flow/StateFlow)
- Add item (`todo.add_item`)
- Toggle item done/open (`todo.update_item`)
- Rename item (`todo.update_item`)
- **Delete completed** (bulk) via confirm dialog (`todo.remove_item` with array)
- **Drag-and-drop sorting** for open items:
    - Local UI reorder
    - Persistence in HA via `todo/item/move` (with `previous_uid`)

### UX / UI
- Material 3 UI (Light/Dark, optional dynamic colors via theme)
- Settings screen (URL/token + notifications toggle)
- Error states:
    - Auth error (token invalid)
    - Connection error (network/WebSocket down)
    - CTA to the Settings screen

### Notifications (new entries)
- Notification for newly appearing items (via `newItems` SharedFlow)
- Optional **also in the background** when notifications are enabled:
    - Foreground service keeps the WS connection & sends notifications
- Runtime permission for Android 13+ (`POST_NOTIFICATIONS`)

> The app avoids duplicate notifications for locally added items (the name is remembered briefly).

---

## Localisations

Currently supported languages:

- English (default)
- German
- French

---

## Project structure (quick overview)

- `MainActivity.kt`
    - Compose UI (ShoppingScreen + SettingsScreen)
    - Drag-and-drop (reorderable LazyList)
    - Confirm dialog for “Delete completed”
    - Start/stop foreground service depending on settings
- `data/`
    - `HaWebSocketRepository` – subscribe/list/events + actions (add/toggle/rename/move/clearCompleted)
    - `websocket/HaWebSocketClient` – connect/auth/events, reconnect
    - `SettingsDataStore` – URL/token/notifications enabled
    - `HaApi`, `HaServiceFactory` – REST (currently not primary)
- `service/HaWsForegroundService`
    - holds repository (singleton) & sends notifications
- `util/`
    - `NotificationHelper` – notification channel + build notifications
    - `UrlNormalizer` – normalize URL (scheme + slash)
    - `Debug` – debug logging
- `viewmodel/ShoppingViewModel`
    - UI bridge to repository (flows + actions)

---

## Setup / configuration

### 1) Prepare Home Assistant
- Create a **Long-Lived Access Token** (Profile → Long-Lived Access Tokens)
- Make sure the ToDo entity exists (currently expected: `todo.einkaufsliste`)

### 2) Configure the app (Settings)
- **Home Assistant URL**
    - Example: `http://homeassistant.local:8123` or `http://192.168.x.x:8123`
    - The app normalizes:
        - if no scheme: default `https://`
        - removes double slashes, enforces trailing `/`
- **Token** (Long-Lived Access Token)
- **Notifications** (toggle)
    - enabled → foreground service starts automatically (as long as URL/token are set)
    - disabled → service is stopped

---

## Permissions (Android 13+)

- `android.permission.POST_NOTIFICATIONS`
    - Requested at runtime in `MainActivity` (SDK 33+)
    - Without permission: notifications may not appear

---

## Build & run

### Android Studio
- Open project → Gradle Sync → Run (Debug)

### CLI
- `./gradlew installDebug`

---

## WebSocket protocol (simplified)

1. Connect: `ws(s)://<base>/api/websocket`
2. Server → `auth_required`
3. Client → `{"type":"auth","access_token":"..."}`
4. Server → `auth_ok`
5. Client:
    - `todo/item/subscribe` (entity_id)
    - `todo/item/list` (entity_id)
6. Server → `result` (items) / `event` (items) → repository parses → UI updates

Reconnect:
- on `onClosed` / `onFailure` → reconnect after ~2s (if not manually disconnected)

---

## Troubleshooting

### “Missing configuration”
- URL or token empty → fill out settings

### Auth failed
- Token invalid/expired → set a new Long-Lived token

### Connection errors
- HA not reachable (network/VPN/HTTPS/HTTP)
- Check URL (including port)
- With self-signed TLS, note possible HTTPS/cert issues

### No items / spinner stuck
- Check whether entity `todo.einkaufsliste` exists
- Check debug logs (`HASL:`)

---

## Roadmap / TODO (concrete)

- Harden token field (prevent copy/screenshot/autofill)
- Improve notifications (localization, actions, deep link)
- More robust reconnect strategy (backoff, offline detection)
- Either remove REST part or integrate it selectively as a fallback

---
