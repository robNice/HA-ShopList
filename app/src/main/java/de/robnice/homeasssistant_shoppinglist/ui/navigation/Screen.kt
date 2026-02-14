package de.robnice.homeasssistant_shoppinglist.ui.navigation

sealed class Screen(val route: String) {
    object Shopping : de.robnice.homeasssistant_shoppinglist.ui.navigation.Screen("shopping")
    object Settings : de.robnice.homeasssistant_shoppinglist.ui.navigation.Screen("settings")
}
