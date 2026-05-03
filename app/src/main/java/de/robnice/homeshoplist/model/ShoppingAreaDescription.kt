package de.robnice.homeshoplist.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private const val META_ITEM_PREFIX = ".__ha_shoplist_meta__:"
private const val META_TYPE = "ha-shoplist-meta"
private const val META_VERSION = 1

private val metaAdapter = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
    .adapter(MetaItemPayload::class.java)

data class ShoppingListMeta(
    val itemAreas: Map<String, ShoppingArea>
)

fun parseMetaItemName(name: String?): ShoppingListMeta? {
    val payload = parseMetaItemPayload(name) ?: return null
    return ShoppingListMeta(
        itemAreas = payload.areas
            .mapNotNull { (itemId, areaKey) ->
                ShoppingArea.fromKey(areaKey)?.let { area -> itemId to area }
            }
            .toMap()
    )
}

fun encodeMetaItemName(itemAreas: Map<String, ShoppingArea>): String {
    val sortedAreas = linkedMapOf<String, String>()
    itemAreas.toSortedMap().forEach { (itemId, area) ->
        sortedAreas[itemId] = area.key
    }

    return META_ITEM_PREFIX + metaAdapter.toJson(
        MetaItemPayload(
            type = META_TYPE,
            version = META_VERSION,
            areas = sortedAreas
        )
    )
}

fun isMetaItemName(name: String?): Boolean {
    return parseMetaItemPayload(name) != null
}

private fun parseMetaItemPayload(name: String?): MetaItemPayload? {
    if (name.isNullOrBlank() || !name.startsWith(META_ITEM_PREFIX)) {
        return null
    }

    val payload = name.removePrefix(META_ITEM_PREFIX)
    return runCatching { metaAdapter.fromJson(payload) }
        .getOrNull()
        ?.takeIf { it.type == META_TYPE }
}

@JsonClass(generateAdapter = true)
private data class MetaItemPayload(
    val type: String,
    val version: Int = META_VERSION,
    val areas: Map<String, String> = emptyMap()
)
