package de.robnice.homeshoplist.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HaStateDto(
    val entity_id: String,
    val attributes: HaStateAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class HaStateAttributesDto(
    val friendly_name: String? = null
)