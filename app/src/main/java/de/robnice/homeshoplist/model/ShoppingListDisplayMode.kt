package de.robnice.homeshoplist.model

enum class ShoppingListDisplayMode(
    val storageValue: String
) {
    CATEGORIZED("categorized"),
    SIMPLE("simple");

    companion object {
        fun fromStorage(value: String): ShoppingListDisplayMode =
            entries.firstOrNull { it.storageValue == value } ?: CATEGORIZED
    }
}
