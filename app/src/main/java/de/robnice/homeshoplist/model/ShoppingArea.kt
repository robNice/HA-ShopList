package de.robnice.homeshoplist.model

enum class ShoppingArea(
    val key: String,
    val emoji: String
) {
    PRODUCE("produce", "\uD83E\uDD66"),
    BAKERY("bakery", "\uD83E\uDD56"),
    MEAT("meat", "\uD83E\uDD69"),
    FISH_SEAFOOD("fish_seafood", "\uD83D\uDC1F"),
    DAIRY_EGGS("dairy_eggs", "\uD83E\uDD5B"),
    CHEESE_DELI("cheese_deli", "\uD83E\uDDC0"),
    FROZEN("frozen", "\u2744\uFE0F"),
    DRY_GOODS("dry_goods", "\uD83C\uDF7E"),
    CANNED_JARS("canned_jars", "\uD83E\uDD6B"),
    SAUCES_SPICES("sauces_spices", "\uD83E\uDDC2"),
    BREAKFAST("breakfast", "\uD83E\uDD63"),
    SNACKS_SWEETS("snacks_sweets", "\uD83C\uDF6B"),
    DRINKS("drinks", "\uD83E\uDDC3"),
    ALCOHOL("alcohol", "\uD83C\uDF77"),
    COFFEE_TEA("coffee_tea", "\u2615"),
    HOUSEHOLD("household", "\uD83C\uDFE0"),
    CLEANING("cleaning", "\uD83E\uDDFD"),
    PAPER_GOODS("paper_goods", "\uD83E\uDDFB"),
    PERSONAL_CARE("personal_care", "\uD83E\uDDD4"),
    BABY("baby", "\uD83C\uDF7C"),
    PET("pet", "\uD83D\uDC3E"),
    HEALTH("health", "\uD83D\uDC8A"),
    NON_FOOD("non_food", "\uD83D\uDED2"),
    OTHER("other", "\u2754");

    companion object {
        fun fromKey(key: String?): ShoppingArea? {
            return entries.firstOrNull { it.key == key }
        }
    }
}
