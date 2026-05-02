package de.robnice.homeshoplist.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private const val META_TYPE = "ha-shoplist-meta"
private const val META_VERSION = 1

private val metaAdapter = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
    .adapter(NativeMeta::class.java)

fun parseAreaFromDescription(description: String?): ShoppingArea? {
    val meta = parseNativeMeta(description) ?: return null
    return ShoppingArea.fromKey(meta.area)
}

fun buildDescriptionWithArea(existing: String?, area: ShoppingArea?): String? {
    if (area == null) {
        val existingMeta = parseNativeMeta(existing)
        return when {
            existingMeta?.text?.isNotBlank() == true -> existingMeta.text
            existingMeta != null -> null
            else -> existing
        }
    }

    val existingMeta = parseNativeMeta(existing)
    val preservedText = when {
        existingMeta?.text?.isNotBlank() == true -> existingMeta.text
        !existing.isNullOrBlank() && existingMeta == null -> existing
        else -> null
    }

    return metaAdapter.toJson(
        NativeMeta(
            type = META_TYPE,
            version = META_VERSION,
            area = area.key,
            text = preservedText
        )
    )
}

fun isNativeWayMeta(description: String?): Boolean {
    return parseNativeMeta(description) != null
}

private fun parseNativeMeta(description: String?): NativeMeta? {
    if (description.isNullOrBlank()) {
        return null
    }

    return runCatching { metaAdapter.fromJson(description) }
        .getOrNull()
        ?.takeIf { it.type == META_TYPE }
}

@JsonClass(generateAdapter = true)
private data class NativeMeta(
    val type: String,
    val version: Int = META_VERSION,
    val area: String? = null,
    val text: String? = null
)
