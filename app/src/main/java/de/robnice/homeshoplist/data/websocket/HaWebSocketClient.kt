package de.robnice.homeshoplist.data.websocket

import de.robnice.homeshoplist.data.HaRealtimeClient
import de.robnice.homeshoplist.data.HaOkHttpFactory
import de.robnice.homeshoplist.util.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
class HaWebSocketClient(

    private val baseUrl: String,
    private val token: String
) : HaRealtimeClient {

    private fun isStaleSocket(socket: WebSocket): Boolean {
        return socket !== webSocket
    }

    @Volatile
    private var reconnectAllowed = true

    private val client = HaOkHttpFactory.newBuilder().build()

    private var webSocket: WebSocket? = null
    private val messageId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()

    private val _events = MutableSharedFlow<JSONObject>(
        replay = 1
    )

    override val events = _events.asSharedFlow()

    private val _ready = MutableSharedFlow<Unit>(replay = 1)
    override val ready = _ready.asSharedFlow()

    private var isConnected = false
    private var isAuthenticated = false

    private val _authFailed = MutableSharedFlow<Unit>(replay = 1)
    override val authFailed = _authFailed.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<String>(replay = 1)
    override val connectionErrors = _connectionErrors.asSharedFlow()

    private val _disconnected = MutableSharedFlow<Unit>(replay = 1)
    override val disconnected = _disconnected.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var manualDisconnect = false

    @Volatile
    private var isConnecting = false
    @Volatile
    private var lastServerMessageAt = 0L


    override fun connect() {
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
            if (isStaleSocket(webSocket)) {
                Debug.log("WS OPEN ignored for stale socket")
                return
            }
            Debug.log("WS OPEN")
            Debug.log("WS HTTP CODE: ${response.code}")
            Debug.log("WS HTTP HEADERS: ${response.headers}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (isStaleSocket(webSocket)) {
                Debug.log("WS CLOSING ignored for stale socket")
                return
            }
            Debug.log("WS CLOSING $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isStaleSocket(webSocket)) {
                Debug.log("WS CLOSED ignored for stale socket")
                return
            }
            isConnected = false
            isAuthenticated = false
            isConnecting = false
            this@HaWebSocketClient.webSocket = null
            stopHeartbeat()
            failPendingRequests("WebSocket closed")
            Debug.log("WS CLOSED $code $reason")
            _disconnected.tryEmit(Unit)
            scheduleReconnect()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStaleSocket(webSocket)) {
                Debug.log("WS MESSAGE ignored for stale socket")
                return
            }
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
                    lastServerMessageAt = System.currentTimeMillis()
                    startHeartbeat()
                    _ready.tryEmit(Unit)
                }

                "auth_invalid" -> {
                    Debug.log("WS AUTH INVALID")
                    isAuthenticated = false
                    isConnected = false
                    isConnecting = false
                    stopHeartbeat()
                    failPendingRequests("WebSocket auth invalid")
                    try {
                        webSocket.close(1000, "Auth invalid")
                    } catch (_: Exception) {
                    }
                    this@HaWebSocketClient.webSocket = null
                    _disconnected.tryEmit(Unit)
                    _authFailed.tryEmit(Unit)
                }

                "pong" -> {
                    lastServerMessageAt = System.currentTimeMillis()
                    Debug.log("WS PONG")
                }

                else -> {
                    lastServerMessageAt = System.currentTimeMillis()
                    json.optInt("id")
                        .takeIf { it > 0 }
                        ?.let { id ->
                            pendingRequests.remove(id)?.complete(json)
                        }
                    _events.tryEmit(json)
                }
            }
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {
            if (isStaleSocket(webSocket)) {
                Debug.log("WS FAILURE ignored for stale socket")
                return
            }
            isConnected = false
            isAuthenticated = false
            isConnecting = false
            this@HaWebSocketClient.webSocket = null
            stopHeartbeat()
            failPendingRequests(t.message ?: "WebSocket failure")
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

    override fun isReady(): Boolean = isConnected && isAuthenticated && webSocket != null

    override fun send(type: String, payload: JSONObject): Boolean {
        return sendInternal(
            id = messageId.getAndIncrement(),
            type = type,
            payload = payload
        )
    }

    override suspend fun request(type: String, payload: JSONObject, timeoutMillis: Long): JSONObject? {
        if (!isReady()) {
            return null
        }

        val id = messageId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        val sent = sendInternal(
            id = id,
            type = type,
            payload = payload
        )

        if (!sent) {
            pendingRequests.remove(id)
            return null
        }

        return try {
            withTimeoutOrNull(timeoutMillis) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun sendInternal(id: Int, type: String, payload: JSONObject): Boolean {
        val msg = JSONObject()
            .put("id", id)
            .put("type", type)

        payload.keys().forEach { key ->
            msg.put(key, payload.get(key))
        }

        val sent = webSocket?.send(msg.toString()) == true
        if (!sent) {
            Debug.log("WS send failed for type=$type")
            isConnected = false
            isAuthenticated = false
        }

        return sent
    }

    override fun ensureConnected() {
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


    override fun setReconnectAllowed(allowed: Boolean) {
        reconnectAllowed = allowed
        Debug.log("WS reconnectAllowed=$allowed")

        if (!allowed) {
            reconnectJob?.cancel()
            stopHeartbeat()
        }
    }

    override fun disconnect() {
        Debug.log("WS disconnect() called")
        manualDisconnect = true
        reconnectJob?.cancel()
        stopHeartbeat()
        webSocket?.close(1000, "Closing")
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = scope.launch {
            while (reconnectAllowed && isReady()) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                if (!reconnectAllowed || !isReady()) {
                    return@launch
                }

                val pingSentAt = System.currentTimeMillis()
                val sent = send(
                    type = "ping",
                    payload = JSONObject()
                )
                if (!sent) {
                    markConnectionLost("Heartbeat ping send failed")
                    return@launch
                }

                delay(HEARTBEAT_TIMEOUT_MILLIS)
                if (reconnectAllowed && isReady() && lastServerMessageAt < pingSentAt) {
                    markConnectionLost("Heartbeat ping timed out")
                    return@launch
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun markConnectionLost(reason: String) {
        Debug.log("WS connection lost: $reason")
        isConnected = false
        isAuthenticated = false
        isConnecting = false

        try {
            webSocket?.cancel()
        } catch (_: Exception) {
        }

        webSocket = null
        failPendingRequests("WebSocket connection reset")
        _disconnected.tryEmit(Unit)
        _connectionErrors.tryEmit(reason)
        scheduleReconnect()
    }

    private fun failPendingRequests(reason: String) {
        if (pendingRequests.isEmpty()) {
            return
        }

        val error = CancellationException(reason)
        pendingRequests.values.forEach { deferred ->
            deferred.completeExceptionally(error)
        }
        pendingRequests.clear()
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

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 10_000L
        private const val HEARTBEAT_TIMEOUT_MILLIS = 5_000L
    }
}
