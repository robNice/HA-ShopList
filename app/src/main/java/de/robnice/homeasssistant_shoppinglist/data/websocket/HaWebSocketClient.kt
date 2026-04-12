package de.robnice.homeasssistant_shoppinglist.data.websocket

import de.robnice.homeasssistant_shoppinglist.data.HaOkHttpFactory
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

    @Volatile
    private var reconnectAllowed = true

    private val client = HaOkHttpFactory.newBuilder().build()

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

    private val _disconnected = MutableSharedFlow<Unit>(replay = 1)
    val disconnected = _disconnected.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var manualDisconnect = false

    @Volatile
    private var isConnecting = false


    fun connect() {
        if (!reconnectAllowed) {
            Debug.log("WS connect skipped (not allowed)")
            return
        }

        if (isConnected) {
            Debug.log("WS connect skipped (already connected)")
            return
        }

        if (isConnecting) {
            Debug.log("WS connect skipped (already connecting)")
            return
        }

        if (webSocket != null) {
            Debug.log("WS connect skipped (socket already exists)")
            return
        }

        manualDisconnect = false
        isConnecting = true

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
            isConnecting = false
            this@HaWebSocketClient.webSocket = null
            Debug.log("WS CLOSED $code $reason")
            _disconnected.tryEmit(Unit)
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
                    isConnecting = false
                    _ready.tryEmit(Unit)
                }

                "auth_invalid" -> {
                    Debug.log("WS AUTH INVALID")
                    isAuthenticated = false
                    isConnected = false
                    isConnecting = false
                    _disconnected.tryEmit(Unit)
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
            isConnecting = false
            this@HaWebSocketClient.webSocket = null
            Debug.log("WS FAILURE")
            t.printStackTrace()

            _disconnected.tryEmit(Unit)
            _connectionErrors.tryEmit(
                t.message ?: "Connection failed"
            )
            scheduleReconnect()
        }
    }

    fun isConnected(): Boolean = isConnected

    fun isReady(): Boolean = isConnected && isAuthenticated && webSocket != null

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
        if (!reconnectAllowed) return
        if (isConnecting) return

        val fullyReady = isConnected && isAuthenticated && webSocket != null
        if (fullyReady) return

        Debug.log("WS ensureConnected(): stale/not-ready socket -> reconnect")

        try {
            webSocket?.cancel()
        } catch (_: Exception) {
        }

        webSocket = null
        isConnected = false
        isAuthenticated = false
        isConnecting = false

        connect()
    }


    fun setReconnectAllowed(allowed: Boolean) {
        reconnectAllowed = allowed
        Debug.log("WS reconnectAllowed=$allowed")

        if (!allowed) {
            reconnectJob?.cancel()
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
        if (!reconnectAllowed) {
            Debug.log("WS reconnect skipped (background / not allowed)")
            return
        }
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            delay(2_000)
            if (!manualDisconnect && reconnectAllowed) {
                connect()
            }
        }
    }
}
