package de.robnice.homeshoplist.ui.navigation

sealed class Screen(val route: String) {
    object Shopping : de.robnice.homeshoplist.ui.navigation.Screen("shopping")
    object Settings : de.robnice.homeshoplist.ui.navigation.Screen("settings")
}
