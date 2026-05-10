# Home ShopList (Android)

## Important note for updates

- Starting with version `1.8`, the app uses a new app ID.
- If you currently use a version older than `1.8`, you cannot update directly to `1.8` via in-app update.
- In that case, please uninstall the old app first and then install version `1.8` or newer manually.
- After that, future updates can be installed in-app again as usual.

---

Standalone Android app for **Home Assistant Todo Lists** with live updates via the Home Assistant WebSocket API.

---

## Table of Contents

- [Installation](#installation)
- [Create a Long-Lived Access Token in Home Assistant](#create-a-long-lived-access-token-in-home-assistant)
- [Configuration](#configuration)
- [Categorize Items](#categorize-items)
- [Rename Items](#rename-items)
- [Sort Items](#sort-items)
- [Settings](#settings)
- [Delete Completed Items](#delete-completed-items)
- [Screenshots](#screenshots)

---

## Installation

Download the latest APK from **[Releases](https://github.com/robNice/HA-ShopList/releases)**.

---

## Create a Long-Lived Access Token in Home Assistant

The app requires a **Long-Lived Access Token** to connect to Home Assistant.

To create one:

1. Open your **Home Assistant interface**
2. Click your **user profile** in the bottom left
3. Scroll to the **Long-Lived Access Tokens** section
4. Click **Create Token**
5. Enter a name (e.g. `HA Shopping List`)
6. Copy the generated token

⚠️ The token is **only shown once**, so make sure to save it immediately.

---

## Configuration

When the app is started for the first time, the **Settings screen** opens automatically.

The following settings must be configured:

### Home Assistant URL
The URL of your Home Assistant instance (including port if necessary).  
The app automatically normalizes the URL.

### Token
Enter the previously created **Long-Lived Access Token** here.

### List

After entering the URL and token, the app automatically loads all available **Home Assistant Todo Lists (`todo.*`)**.

These appear as a **dropdown selection**.

The selected list is saved and automatically reused the next time the app starts.

---

## Categorize Items

Items can be assigned to categories in multiple ways.

### When adding a new item

Choose the desired category next to the input field before adding the item.

### In the list

Open items can be categorized directly in the list:

1. Drag an item into another category to reorder and reassign it at the same time
2. Change the category with the category button on the item row

---

## Rename Items

An existing item can be renamed directly in the list.

Steps:

1. Tap the **item text**
2. The input field appears
3. Enter the new name
4. Confirm with **Enter / Done**

The change is immediately sent to Home Assistant.

---

## Sort Items

Open items can be reordered using **drag & drop**.

Steps:

1. **Press and hold** an item
2. Drag the item to the desired position
3. Release it

The new order is automatically saved in Home Assistant.

---

## Settings

The Settings screen also contains list-specific display and area options.

### List display

You can choose whether the list is shown as:

- **Simple**
- **Categorized**

### Area editor

The area editor lets you:

- enable or disable areas
- change the area order

---

## Delete Completed Items

Completed items can be removed in bulk.

Steps:

1. Scroll to the bottom area of the list
2. Select **“Delete completed items”**
3. Confirm the deletion in the dialog

All completed items will then be removed from the list.


## Screenshots
<img src="docs/hsl_dark_categories.png" alt="Dark mode categories" width="250">
<img src="docs/hsl_dark_categorized.png" alt="Dark mode categorized list" width="250">
<img src="docs/hsl_dark_categorized_completed.png" alt="Dark mode categorized completed items" width="250">
<img src="docs/hsl_dark_simple.png" alt="Dark mode simple list" width="250">
<img src="docs/hsl_dark_simple_completed.png" alt="Dark mode simple completed items" width="250">
<img src="docs/hsl_light_categories.png" alt="Light mode categories" width="250">
<img src="docs/hsl_light_categorized.png" alt="Light mode categorized list" width="250">
<img src="docs/hsl_light_categorized_completed.png" alt="Light mode categorized completed items" width="250">
<img src="docs/hsl_light_simple.png" alt="Light mode simple list" width="250">
<img src="docs/hsl_light_simple_completed.png" alt="Light mode simple completed items" width="250">
