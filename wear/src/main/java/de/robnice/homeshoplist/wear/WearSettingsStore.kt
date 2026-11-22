package de.robnice.homeshoplist.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap
import de.robnice.homeshoplist.BuildConfig
import de.robnice.homeshoplist.wear.model.WearAreaOption
import de.robnice.homeshoplist.wear.model.WearListOption
import de.robnice.homeshoplist.wear.model.WearProductSuggestion
import de.robnice.homeshoplist.wear.model.WearSelectionCodec

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

    /** Name of the list as shown on the phone; null if the phone did not send one (older app version). */
    fun getListName(): String? = prefs.getString("todo_list_name", null)?.takeIf { it.isNotBlank() }

    fun saveSettings(url: String, token: String, entity: String, listName: String? = null) {
        prefs.edit()
            .putString("ha_url", url)
            .putString("ha_token", token)
            .putString("todo_entity", entity)
            .apply {
                // Never keep the name of a previous list
                if (listName.isNullOrBlank()) remove("todo_list_name") else putString("todo_list_name", listName)
            }
            // A different server or list makes the cached count meaningless
            .remove("open_count")
            .remove("total_count")
            .remove("open_count_updated_at")
            .apply()
    }

    /** Lists the phone knows, for switching the list on the watch. */
    fun getKnownLists(): List<WearListOption> = WearSelectionCodec.decodeLists(prefs.getString("known_lists", null))

    /**
     * Applies the settings sent by the phone (path /ha_settings). Returns false if they are incomplete.
     * Used by the listener service, the view model and the initial load, so all three stay identical.
     */
    fun applyFromPhone(dataMap: DataMap): Boolean {
        val url = dataMap.getString("ha_url")?.takeIf { it.isNotBlank() } ?: return false
        val token = dataMap.getString("ha_token")?.takeIf { it.isNotBlank() } ?: return false
        val entity = dataMap.getString("todo_entity")?.takeIf { it.isNotBlank() } ?: return false
        saveSettings(url, token, entity, dataMap.getString("todo_list_name"))
        val editor = prefs.edit()
        dataMap.getString("list_display_mode")?.let { editor.putString("list_display_mode", it) }
        dataMap.getString("area_order")?.let { editor.putString("area_order", it) }
        dataMap.getString("known_lists")?.let { editor.putString("known_lists", it) }
        dataMap.getString("product_suggestions")?.let { editor.putString("product_suggestions", it) }
        dataMap.getString("market_areas")?.let { editor.putString("market_areas", it) }
        dataMap.getString("list_market_areas")?.let { editor.putString("list_market_areas", it) }
        editor.apply()
        return true
    }

    /** Most used products of the phone, for adding items on the watch. */
    fun getProductSuggestions(): List<WearProductSuggestion> =
        WearSelectionCodec.decodeSuggestions(prefs.getString("product_suggestions", null))

    /** Categories of the current list's store that are enabled there, in its order. */
    fun getMarketAreas(): List<WearAreaOption> {
        // Resolved locally, so a list switched on the watch shows its store's categories immediately
        val entity = prefs.getString("todo_entity", null).orEmpty()
        return WearSelectionCodec.decodeAreasForList(prefs.getString("list_market_areas", null), entity)
            ?: WearSelectionCodec.decodeAreas(prefs.getString("market_areas", null))
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
