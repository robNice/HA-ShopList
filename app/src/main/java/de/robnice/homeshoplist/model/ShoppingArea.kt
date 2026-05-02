package de.robnice.homeshoplist.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.robnice.homeshoplist.R

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

        fun orderedFromStorage(storedOrder: String?): List<ShoppingArea> {
            val storedAreas = storedOrder
                .orEmpty()
                .split(',')
                .mapNotNull { fromKey(it.trim()) }
                .distinct()

            if (storedAreas.isEmpty()) {
                return entries.toList()
            }

            return storedAreas + entries.filterNot { it in storedAreas }
        }

        fun serializeOrder(areas: List<ShoppingArea>): String {
            return areas.joinToString(",") { it.key }
        }
    }
}

@Composable
fun ShoppingArea.label(): String {
    val resId = when (this) {
        ShoppingArea.PRODUCE -> R.string.area_produce
        ShoppingArea.BAKERY -> R.string.area_bakery
        ShoppingArea.MEAT -> R.string.area_meat
        ShoppingArea.FISH_SEAFOOD -> R.string.area_fish_seafood
        ShoppingArea.DAIRY_EGGS -> R.string.area_dairy_eggs
        ShoppingArea.CHEESE_DELI -> R.string.area_cheese_deli
        ShoppingArea.FROZEN -> R.string.area_frozen
        ShoppingArea.DRY_GOODS -> R.string.area_dry_goods
        ShoppingArea.CANNED_JARS -> R.string.area_canned_jars
        ShoppingArea.SAUCES_SPICES -> R.string.area_sauces_spices
        ShoppingArea.BREAKFAST -> R.string.area_breakfast
        ShoppingArea.SNACKS_SWEETS -> R.string.area_snacks_sweets
        ShoppingArea.DRINKS -> R.string.area_drinks
        ShoppingArea.ALCOHOL -> R.string.area_alcohol
        ShoppingArea.COFFEE_TEA -> R.string.area_coffee_tea
        ShoppingArea.HOUSEHOLD -> R.string.area_household
        ShoppingArea.CLEANING -> R.string.area_cleaning
        ShoppingArea.PAPER_GOODS -> R.string.area_paper_goods
        ShoppingArea.PERSONAL_CARE -> R.string.area_personal_care
        ShoppingArea.BABY -> R.string.area_baby
        ShoppingArea.PET -> R.string.area_pet
        ShoppingArea.HEALTH -> R.string.area_health
        ShoppingArea.NON_FOOD -> R.string.area_non_food
        ShoppingArea.OTHER -> R.string.area_other
    }
    return stringResource(resId)
}
