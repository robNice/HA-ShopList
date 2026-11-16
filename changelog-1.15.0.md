## What's new

### Offline list
- New switch **Offline list** at the top of the **Connection** tab in Settings.
- When it is on, the list is stored only on this device. No list data is read from or written to Home Assistant, so the list can always be edited – also without a connection. Useful if you want to use the list on your own or have no Home Assistant server available.
- The fields for URL, token and list are hidden while the offline list is on, and no Home Assistant data is needed to get started.
- **Enable notifications** is greyed out in this mode; no notifications are created.
- The offline list is kept separately from the Home Assistant list. Switching back shows your Home Assistant list again; the offline list stays saved for next time.

### Wear OS app
- The list now works without phone and internet, e.g. when the phone stays in the car: the watch keeps the last loaded list and shows it right away.
- Checking off items and **Delete completed** also work offline. The changes are queued and sent to Home Assistant as soon as the watch has a connection again (via the phone, Wi-Fi or LTE) – also in the background when the app is closed.
- An orange cloud icon next to the number of open items shows that the watch is offline.
- The tile sends queued changes before reloading and shows the count including your offline changes.
- If an item was deleted elsewhere in the meantime, the queued change for it is dropped instead of being retried.

### Fixes
- **Wear OS:** a failed load (e.g. while Home Assistant restarts) no longer shows an empty list or "0 open" on the tile.
