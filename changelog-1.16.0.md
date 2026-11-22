## What's new

### Stores
- Every store is laid out differently. You can now create several **stores**, each with its own category order and its own set of categories (e.g. "Supermarket", "Discount store", "Farmers' market").
- New stores start with the default order or as a copy of an existing store. Stores can be renamed and deleted.
- Your existing category settings are kept as the first store, "Default".

### Lists
- Switch the list right from the main screen: tap the list name at the top left.
- Each list is linked to a store. Switching the list sorts its items in the order of that store right away. Lists without a link use the first store.
- New **Lists** tab in Settings: shows all your Home Assistant to-do lists with their store.
- With an administrator token you can create and delete lists directly in the app. Only lists of the "Local To-do" integration can be deleted.
- If the list you are using is deleted in Home Assistant, the app switches to another list and lets you know.

### More categories
- 69 new categories for drugstores, DIY stores, garden centres and drinks stores.
- They are turned off by default, so the category choice stays short for a normal supermarket. Turn them on per store as needed.

### Settings
- Settings are now organised in five tabs with icons: Connection, Lists, Stores, Data and Help. Swipe sideways to switch between them.
- Notifications, product history, backup and updates are now in the **Data** tab; the list display (simple/categorised) is in the **Lists** tab.
- The token is now called "long-lived access token" in every language, just like in Home Assistant.
- Backups now include your stores and which store belongs to which list.

### Help
- New **Help** tab with a full guide to the app, screenshots and a linked table of contents, in all 13 app languages.

### Wear OS app
- **Add items on the watch:** the new (+) button opens the watch's text input (keyboard or voice, depending on the watch) with your most used products as suggestions.
- Known products are added with their category automatically. For new products you pick the category from the icons of the list's store.
- **Switch lists on the watch:** tap the list button at the top. The phone follows the switch.
- New header: list button, number of open items in a circle and the add button. The house is now shown in the background.
- New splash screen with the app icon when starting.

### Fixes
- The app now reliably reconnects to Home Assistant after the network was lost, instead of sometimes staying disconnected.
- Changes made offline stay with their list: switching lists while changes are still waiting no longer sends them to the wrong list.
- Importing and exporting settings now shows a progress indicator, so the app no longer seems to hang.
- "Home Assistant URL" is now translated in every language.
- **Wear OS:** the list title now updates when the list is switched on the phone.
- **Wear OS:** the app no longer flickers while the list is loading.
