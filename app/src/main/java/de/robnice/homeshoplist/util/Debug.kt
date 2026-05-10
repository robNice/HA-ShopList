package de.robnice.homeshoplist.util

import android.util.Log
import de.robnice.homeshoplist.BuildConfig

object Debug {

    private const val TAG = "HASL"

    var enabled: Boolean = BuildConfig.DEBUG

    fun log(message: Any?) {
        if (enabled) {
            val rendered = message?.toString() ?: "null"
            try {
                Log.d(TAG, rendered)
            } catch (_: RuntimeException) {
                println("$TAG: $rendered")
            }
        }
    }
}
