package de.robnice.homeshoplist.model

import org.json.JSONArray
import org.json.JSONObject

data class ShoppingList(
    val id: String,
    val name: String
)

object ShoppingLists {
    fun encode(lists: List<ShoppingList>): String {
        val array = JSONArray()
        lists.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        return array.toString()
    }

    /** Tolerant parsing: entries without id are skipped, a missing name falls back to the id. */
    fun decode(raw: String?): List<ShoppingList> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ShoppingList(id, item.optString("name").trim().ifEmpty { id })
        }.distinctBy { it.id }
    }

    /**
     * Keeps the selected list valid for the current connection. Returns the list to store,
     * or null if nothing has to change (or Home Assistant has no lists at all):
     * - no list selected yet, or the selected one no longer exists (deleted, other server)
     *   -> the first available list (by name),
     * - the selected list was renamed in Home Assistant -> same list with the new name.
     */
    fun reconcile(currentId: String?, currentName: String?, available: List<ShoppingList>): ShoppingList? {
        if (available.isEmpty()) return null
        val current = currentId?.let { id -> available.firstOrNull { it.id == id } }
        return when {
            current == null -> available.minBy { it.name.lowercase() }
            current.name != currentName -> current
            else -> null
        }
    }

    /** Known lists plus the current one (if missing), sorted by name for menus. */
    fun withCurrent(lists: List<ShoppingList>, current: ShoppingList?): List<ShoppingList> =
        (if (current != null && lists.none { it.id == current.id }) lists + current else lists)
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
}
