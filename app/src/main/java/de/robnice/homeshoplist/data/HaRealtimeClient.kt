package de.robnice.homeshoplist.data

import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

interface HaRealtimeClient {
    val events: SharedFlow<JSONObject>
    val ready: SharedFlow<Unit>
    val authFailed: SharedFlow<Unit>
    val connectionErrors: SharedFlow<String>
    val disconnected: SharedFlow<Unit>

    fun connect()
    fun isReady(): Boolean
    fun send(type: String, payload: JSONObject = JSONObject()): Boolean
    suspend fun request(type: String, payload: JSONObject = JSONObject(), timeoutMillis: Long = 5_000L): JSONObject?
    fun ensureConnected()
    fun setReconnectAllowed(allowed: Boolean)
    fun disconnect()
}
