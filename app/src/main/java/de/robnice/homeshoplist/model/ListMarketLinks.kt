package de.robnice.homeshoplist.model

import org.json.JSONObject

/**
 * Remembered store per list (list id -> market id), used when "remember store per list" is on.
 */
object ListMarketLinks {
    /** Key for the offline list, which has no Home Assistant entity id. */
    const val OFFLINE_LIST_KEY = "local:offline_list"

    fun listKey(offlineMode: Boolean, todoEntity: String?): String? =
        if (offlineMode) OFFLINE_LIST_KEY else todoEntity?.trim()?.takeIf { it.isNotEmpty() }

    fun encode(links: Map<String, String>): String = JSONObject(links as Map<*, *>).toString()

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence()
            .mapNotNull { key ->
                val value = json.optString(key).trim()
                if (key.isBlank() || value.isEmpty()) null else key to value
            }
            .toMap()
    }

    /** Drops links to markets that no longer exist. */
    fun prune(links: Map<String, String>, marketIds: Set<String>): Map<String, String> =
        links.filterValues { it in marketIds }

    /** Sets ([marketId] != null) or removes the link of [listKey]. */
    fun with(links: Map<String, String>, listKey: String, marketId: String?): Map<String, String> =
        if (marketId == null) links - listKey else links + (listKey to marketId)
}
