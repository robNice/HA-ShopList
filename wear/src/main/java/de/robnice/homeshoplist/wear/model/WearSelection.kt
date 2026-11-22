package de.robnice.homeshoplist.wear.model

import org.json.JSONArray

/** A product from the phone's history, offered when adding an item; [areaKey] is its category. */
data class WearProductSuggestion(val name: String, val areaKey: String?)

/** A category that can be chosen when adding (active in the list's store); [label] is for screen readers. */
data class WearAreaOption(val key: String, val label: String)

/** A todo list the watch can switch to. */
data class WearListOption(val id: String, val name: String)

object WearSelectionCodec {

    fun decodeLists(raw: String?): List<WearListOption> = decodeArray(raw) { item ->
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@decodeArray null
        WearListOption(id, item.optString("name").ifBlank { id })
    }

    fun decodeSuggestions(raw: String?): List<WearProductSuggestion> = decodeArray(raw) { item ->
        val name = item.optString("name").trim().takeIf { it.isNotEmpty() } ?: return@decodeArray null
        WearProductSuggestion(name, if (item.isNull("area")) null else item.optString("area").takeIf { it.isNotBlank() })
    }

    fun decodeAreas(raw: String?): List<WearAreaOption> = decodeArray(raw) { item ->
        val key = item.optString("key").takeIf { it.isNotBlank() } ?: return@decodeArray null
        WearAreaOption(key, item.optString("label").ifBlank { key })
    }

    /**
     * Categories for [entity] from the per-list data sent by the phone (stores + list links);
     * null if that data is missing or unreadable.
     */
    fun decodeAreasForList(raw: String?, entity: String): List<WearAreaOption>? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
        val markets = root.optJSONObject("markets") ?: return null
        val linked = root.optJSONObject("links")?.optString(entity)?.takeIf { it.isNotBlank() && markets.has(it) }
        val marketId = linked ?: root.optString("default").takeIf { it.isNotBlank() && markets.has(it) } ?: return null
        return decodeAreas(markets.optJSONArray(marketId)?.toString())
    }

    private fun <T> decodeArray(raw: String?, map: (org.json.JSONObject) -> T?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(map) }
    }
}
