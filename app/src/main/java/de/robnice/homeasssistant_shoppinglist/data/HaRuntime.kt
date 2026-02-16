package de.robnice.homeasssistant_shoppinglist.data

object HaRuntime {
    @Volatile var repository: HaWebSocketRepository? = null
}
