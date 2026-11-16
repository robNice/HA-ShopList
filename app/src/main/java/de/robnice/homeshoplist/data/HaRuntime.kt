package de.robnice.homeshoplist.data

object HaRuntime {
    var repository: ShoppingRepository? = null
    var offlineMode: Boolean = false
    var baseUrl: String? = null
    var token: String? = null
    var todoEntity: String? = null
    var todoListName: String? = null
}
