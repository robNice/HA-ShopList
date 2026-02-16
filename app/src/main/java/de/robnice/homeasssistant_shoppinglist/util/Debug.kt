package de.robnice.homeasssistant_shoppinglist.util

object Debug {

    var enabled: Boolean = true

    fun log(message: Any?) {
        if (enabled) {
            println("HASL: $message")
        }
    }
}
