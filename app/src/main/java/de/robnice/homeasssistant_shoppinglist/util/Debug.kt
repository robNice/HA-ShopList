package de.robnice.homeasssistant_shoppinglist.util

object Debug {

    var enabled: Boolean = false

    fun log(message: Any?) {
        if (enabled) {
            println("HASL: $message")
        }
    }
}
