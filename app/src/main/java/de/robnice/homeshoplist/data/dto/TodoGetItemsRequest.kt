package de.robnice.homeshoplist.data.dto

data class TodoGetItemsRequest(
    val entity_id: String,
    val status: List<String> = listOf("needs_action", "completed")
)
