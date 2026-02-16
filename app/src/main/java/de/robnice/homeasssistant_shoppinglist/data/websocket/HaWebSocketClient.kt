package de.robnice.homeasssistant_shoppinglist.data.websocket

import de.robnice.homeasssistant_shoppinglist.util.Debug
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

class HaWebSocketClient(
    private val baseUrl: String,
    private val token: String
) {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val messageId = AtomicInteger(1)

    private val _events = MutableSharedFlow<JSONObject>(
        replay = 1
    )

    val events = _events.asSharedFlow()

    private val _ready = MutableSharedFlow<Unit>(replay = 1)
    val ready = _ready.asSharedFlow()

    private var isConnected = false
    private var isAuthenticated = false

    private val _authFailed = MutableSharedFlow<Unit>(replay = 1)
    val authFailed = _authFailed.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<String>(replay = 1)
    val connectionErrors = _connectionErrors.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var manualDisconnect = false

    fun connect() {
        manualDisconnect = false
        val cleanedBase = baseUrl.trimEnd('/')

        val wsUrl = cleanedBase
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/api/websocket"

        Debug.log("WS URL: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .header("Origin", baseUrl)
            .build()

        webSocket = client.newWebSocket(request, socketListener)
    }


    private val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Debug.log("WS OPEN")
            Debug.log("WS HTTP CODE: ${response.code}")
            Debug.log("WS HTTP HEADERS: ${response.headers}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Debug.log("WS CLOSING $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
            isAuthenticated = false
            this@HaWebSocketClient.webSocket = null
            Debug.log("WS CLOSED $code $reason")
            scheduleReconnect()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Debug.log("WS MESSAGE: $text")

            val json = JSONObject(text)

            when (json.getString("type")) {

                "auth_required" -> {
                    Debug.log("WS AUTH REQUIRED")
                    val auth = JSONObject()
                        .put("type", "auth")
                        .put("access_token", token)

                    webSocket.send(auth.toString())
                }

                "auth_ok" -> {
                    Debug.log("WS WebSocket Auth OK")
                    isAuthenticated = true
                    isConnected = true
                    _ready.tryEmit(Unit)
                }

                "auth_invalid" -> {
                    Debug.log("WS AUTH INVALID")
                    isAuthenticated = false
                    isConnected = false
                    _authFailed.tryEmit(Unit)
                }

                else -> {
                    _events.tryEmit(json)
                }
            }
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {
            isConnected = false
            isAuthenticated = false
            this@HaWebSocketClient.webSocket = null
            Debug.log("WS FAILURE")
            t.printStackTrace()

            _connectionErrors.tryEmit(
                t.message ?: "Connection failed"
            )
            scheduleReconnect()
        }
    }

    fun isConnected(): Boolean = isConnected
    fun send(type: String, payload: JSONObject = JSONObject()): Int {
        val id = messageId.getAndIncrement()

        val msg = JSONObject()
            .put("id", id)
            .put("type", type)

        payload.keys().forEach { key ->
            msg.put(key, payload.get(key))
        }

        webSocket?.send(msg.toString())

        return id
    }
    fun ensureConnected() {
        if (!isConnected || webSocket == null) {
            connect()
        }
    }
    fun disconnect() {
        Debug.log("WS disconnect() called")
        manualDisconnect = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Closing")
    }

    private fun scheduleReconnect() {
        if (manualDisconnect) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(2_000) // minimal: 2s
            connect()
        }
    }
}
