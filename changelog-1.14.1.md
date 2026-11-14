## What's new

### Fixes
- **Settings:** the todo lists now load during setup even if the Home Assistant user is not an administrator. Before, the list selection failed with "lists could not be loaded" for tokens of regular users, because the app used an admin-only interface (`/api/template`). It now reads the lists via `/api/states`, which every user may use.
- **Wear OS:** fixed a possible crash of the tile on watches with Wear OS 5.

### Updated libraries
- `androidx.wear.tiles` 1.4.1 → 1.5.0 and `androidx.wear.protolayout` 1.2.1 → 1.3.0 (fixes a known `SecurityException` on Wear OS 5 with target API 35).
- `androidx.fragment` 1.8.9 in the Wear OS app (Google Play reported the previously used 1.1.0 as outdated).
