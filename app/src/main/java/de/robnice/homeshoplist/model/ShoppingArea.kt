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
    GROCERIES("groceries", "\uD83D\uDED2"),
    MILK("milk", "\uD83E\uDD5B"),
    EGGS("eggs", "\uD83E\uDD5A"),
    COLD_CUTS("cold_cuts", "\uD83E\uDD53"),
    REFRIGERATED_CASES("refrigerated_cases", "\uD83E\uDDCA"),
    BUTCHER_COUNTER("butcher_counter", "\uD83D\uDD2A"),
    AREA_1("area_1", "1\uFE0F\u20E3"),
    AREA_2("area_2", "2\uFE0F\u20E3"),
    AREA_3("area_3", "3\uFE0F\u20E3"),
    AREA_4("area_4", "4\uFE0F\u20E3"),
    AREA_5("area_5", "5\uFE0F\u20E3"),
    AREA_6("area_6", "6\uFE0F\u20E3"),
    AREA_7("area_7", "7\uFE0F\u20E3"),
    AREA_8("area_8", "8\uFE0F\u20E3"),
    IMPULSE_BUY("impulse_buy", "\uD83C\uDF6C"),
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

        fun enabledFromStorage(
            storedEnabled: String?,
            orderedAreas: List<ShoppingArea> = entries.toList()
        ): List<ShoppingArea> {
            val enabledSet = storedEnabled
                .orEmpty()
                .split(',')
                .mapNotNull { fromKey(it.trim()) }
                .toSet()

            if (enabledSet.isEmpty()) {
                return orderedAreas
            }

            return orderedAreas.filter { it in enabledSet }
        }

        fun serializeOrder(areas: List<ShoppingArea>): String {
            return areas.joinToString(",") { it.key }
        }

        fun serializeEnabledAreas(areas: List<ShoppingArea>): String {
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
        ShoppingArea.GROCERIES -> R.string.area_groceries
        ShoppingArea.MILK -> R.string.area_milk
        ShoppingArea.EGGS -> R.string.area_eggs
        ShoppingArea.COLD_CUTS -> R.string.area_cold_cuts
        ShoppingArea.REFRIGERATED_CASES -> R.string.area_refrigerated_cases
        ShoppingArea.BUTCHER_COUNTER -> R.string.area_butcher_counter
        ShoppingArea.AREA_1 -> R.string.area_1
        ShoppingArea.AREA_2 -> R.string.area_2
        ShoppingArea.AREA_3 -> R.string.area_3
        ShoppingArea.AREA_4 -> R.string.area_4
        ShoppingArea.AREA_5 -> R.string.area_5
        ShoppingArea.AREA_6 -> R.string.area_6
        ShoppingArea.AREA_7 -> R.string.area_7
        ShoppingArea.AREA_8 -> R.string.area_8
        ShoppingArea.IMPULSE_BUY -> R.string.area_impulse_buy
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
