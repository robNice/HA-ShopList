package de.robnice.homeshoplist.wear

import android.content.Context
import de.robnice.homeshoplist.BuildConfig

class WearSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("wear_ha_settings", Context.MODE_PRIVATE)

    data class WearSettings(val url: String, val token: String, val entity: String)

    /**
     * Debug builds only: set by store/tools/capture_wear_screenshots.sh so that settings and
     * counts from a paired phone do not replace the demo data while taking screenshots.
     */
    fun isScreenshotMode(): Boolean = BuildConfig.DEBUG && prefs.getBoolean("screenshot_mode", false)

    fun getSettings(): WearSettings? {
        val url = prefs.getString("ha_url", null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("ha_token", null)?.takeIf { it.isNotBlank() } ?: return null
        val entity = prefs.getString("todo_entity", null)?.takeIf { it.isNotBlank() } ?: return null
        return WearSettings(url, token, entity)
    }

    fun saveSettings(url: String, token: String, entity: String) {
        prefs.edit()
            .putString("ha_url", url)
            .putString("ha_token", token)
            .putString("todo_entity", entity)
            // A different server or list makes the cached count meaningless
            .remove("open_count")
            .remove("total_count")
            .remove("open_count_updated_at")
            .apply()
    }

    fun getDisplayMode(): String =
        prefs.getString("list_display_mode", "categorized") ?: "categorized"

    fun saveDisplayMode(mode: String) {
        prefs.edit().putString("list_display_mode", mode).apply()
    }

    fun getAreaOrder(): String =
        prefs.getString("area_order", "") ?: ""

    fun saveAreaOrder(order: String) {
        prefs.edit().putString("area_order", order).apply()
    }

    /**
     * Last known number of open items and of all items (open + done, -1 if unknown),
     * shared by app, tile and background refresh.
     */
    data class OpenCount(val count: Int, val total: Int, val updatedAtMillis: Long)

    fun getOpenCount(): OpenCount? {
        if (!prefs.contains("open_count")) return null
        return OpenCount(
            prefs.getInt("open_count", 0),
            prefs.getInt("total_count", -1),
            prefs.getLong("open_count_updated_at", 0L)
        )
    }

    /** Stores the counts and returns true if they differ from the previous ones. */
    fun saveOpenCount(count: Int, total: Int): Boolean {
        val changed = !prefs.contains("open_count") ||
            prefs.getInt("open_count", -1) != count ||
            prefs.getInt("total_count", -1) != total
        prefs.edit()
            .putInt("open_count", count)
            .putInt("total_count", total)
            .putLong("open_count_updated_at", System.currentTimeMillis())
            .apply()
        return changed
    }
}
