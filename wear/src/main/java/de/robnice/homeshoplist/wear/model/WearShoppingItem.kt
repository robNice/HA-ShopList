package de.robnice.homeshoplist.wear.model

data class WearShoppingItem(
    val id: String,
    val name: String,
    val complete: Boolean,
    val areaKey: String? = null
)