package de.robnice.homeshoplist.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private const val META_ITEM_PREFIX = ".__ha_shoplist_meta__:"
private const val META_TYPE = "ha-shoplist-meta"
private const val META_VERSION = 1
private const val AREA_DESCRIPTION_PREFIX = ".__ha_shoplist_area__:"
private const val AREA_TYPE = "ha-shoplist-area"
private const val AREA_NAME_SEPARATOR = "\u0000 | "

private val metaAdapter = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
    .adapter(MetaItemPayload::class.java)

private val areaAdapter = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
    .adapter(AreaDescriptionPayload::class.java)

data class ShoppingListMeta(
    val itemAreas: Map<String, ShoppingArea>
)

data class ManagedItemName(
    val visibleName: String,
    val area: ShoppingArea?
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

fun parseAreaDescription(description: String?): ShoppingArea? {
    val payload = parseAreaDescriptionPayload(description) ?: return null
    return ShoppingArea.fromKey(payload.area)
}

fun parseManagedItemName(name: String?): ManagedItemName {
    if (name.isNullOrEmpty()) {
        return ManagedItemName(
            visibleName = name.orEmpty(),
            area = null
        )
    }

    val separatorIndex = name.lastIndexOf(AREA_NAME_SEPARATOR)
    if (separatorIndex <= 0 || separatorIndex >= name.lastIndex) {
        return ManagedItemName(
            visibleName = name,
            area = null
        )
    }

    val encodedArea = name.substring(separatorIndex + AREA_NAME_SEPARATOR.length)
    val area = ShoppingArea.fromKey(encodedArea) ?: return ManagedItemName(
        visibleName = name,
        area = null
    )

    return ManagedItemName(
        visibleName = name.substring(0, separatorIndex),
        area = area
    )
}

fun encodeManagedItemName(name: String, area: ShoppingArea?): String {
    area ?: return name
    return name + AREA_NAME_SEPARATOR + area.key
}

fun encodeAreaDescription(area: ShoppingArea?): String? {
    area ?: return null
    return encodeAreaPayload(area)
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

private fun parseAreaDescriptionPayload(description: String?): AreaDescriptionPayload? {
    return parseAreaPayload(
        rawValue = description,
        prefix = AREA_DESCRIPTION_PREFIX,
        expectedType = AREA_TYPE
    )
}

private fun encodeAreaPayload(area: ShoppingArea): String {
    return AREA_DESCRIPTION_PREFIX + areaAdapter.toJson(
        AreaDescriptionPayload(
            type = AREA_TYPE,
            version = META_VERSION,
            area = area.key
        )
    )
}

private fun parseAreaPayload(
    rawValue: String?,
    prefix: String,
    expectedType: String
): AreaDescriptionPayload? {
    if (rawValue.isNullOrBlank() || !rawValue.startsWith(prefix)) {
        return null
    }

    val payload = rawValue.removePrefix(prefix)
    return runCatching { areaAdapter.fromJson(payload) }
        .getOrNull()
        ?.takeIf { it.type == expectedType }
}

@JsonClass(generateAdapter = true)
private data class MetaItemPayload(
    val type: String,
    val version: Int = META_VERSION,
    val areas: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
private data class AreaDescriptionPayload(
    val type: String,
    val version: Int = META_VERSION,
    val area: String
)
