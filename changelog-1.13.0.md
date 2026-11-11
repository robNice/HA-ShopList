## What's new

### Local HTTP connections
- Home Assistant can now also be reached via plain `http://` addresses (e.g. `http://192.168.1.10:8123`). This helps with local installations that have no SSL certificate. Works on the phone and on Wear OS.
- When you enter a new `http://` address, the app warns you that the connection and your Home Assistant token are transmitted unencrypted, and asks for confirmation.
- Choosing **Switch to https://** changes the address to `https://` automatically.
- Until an `http://` address is confirmed, the app does not contact the server, so your token is never sent unencrypted without your consent.
- Addresses that are already saved or restored from a backup do not trigger the warning again.

### Redesigned settings
- Settings are now organized in three tabs: **Connection**, **View** and **Data**.
- The notification toggle moved to the **View** tab.
- The store section order is now always visible instead of collapsible.
- Buttons now show icons. The backup buttons are now simply labeled **Export** and **Import**.

### Privacy policy
- Added information on encrypted (`https://`) and unencrypted (`http://`) connections.
- Added information on the update check via GitHub (GitHub version only; the Google Play version does not perform it).
