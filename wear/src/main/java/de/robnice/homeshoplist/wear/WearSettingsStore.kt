package de.robnice.homeshoplist.wear

import android.content.Context

class WearSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("wear_ha_settings", Context.MODE_PRIVATE)

    data class WearSettings(val url: String, val token: String, val entity: String)

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
            .apply()
    }

    fun getDisplayMode(): String =
        prefs.getString("list_display_mode", "categorized") ?: "categorized"

    fun saveDisplayMode(mode: String) {
        prefs.edit().putString("list_display_mode", mode).apply()
    }
}