package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.data.websocket.HaWebSocketClient
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.util.Debug
import de.robnice.homeshoplist.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class HaWebSocketRepository(
    baseUrl: String,
    token: String,
    private val appContext: Context,
    private val todoEntity: String
) {

    private sealed interface PendingAction {
        data class Update(
            val itemId: String,
            val name: String,
            val complete: Boolean,
            val lastSentAtMillis: Long?
        ) : PendingAction

        data class Remove(
            val itemId: String,
            val lastSentAtMillis: Long?
        ) : PendingAction
    }

    private data class PendingMove(
        val itemId: String,
        val previousItemId: String?,
        val lastSentAtMillis: Long?
    )

    private data class PendingLocalAdd(
        val tempId: String,
        val name: String,
        val complete: Boolean,
        val previousItemId: String?,
        val lastSentAtMillis: Long?
    )

    private val client = HaWebSocketClient(baseUrl, token)
    private val pendingSyncPrefs = appContext.getSharedPreferences(PENDING_SYNC_PREFS, Context.MODE_PRIVATE)
    private val productHistoryRepository = ProductHistoryRepository.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val tempIdCounter = AtomicLong(1)

    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val _authFailed = MutableStateFlow(false)
    private val _connectionErrors = MutableStateFlow(false)
    private val _isOffline = MutableStateFlow(false)
    private val _isConnecting = MutableStateFlow(false)
    private val _newItems = MutableSharedFlow<ShoppingItem>(replay = 1)
    private val _remoteActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val _loaded = MutableStateFlow(false)
    private val _reconnected = MutableStateFlow(0L)

    val loaded = _loaded.asStateFlow()
    val authFailed = _authFailed.asStateFlow()
    val connectionErrors = _connectionErrors.asStateFlow()
    val isOffline = _isOffline.asStateFlow()
    val isConnecting = _isConnecting.asStateFlow()
    val items = _items.asStateFlow()
    val newItems = _newItems.asSharedFlow()
    val remoteActivity = _remoteActivity.asSharedFlow()
    val reconnected = _reconnected.asStateFlow()

    private val locallyAddedItemNames = mutableSetOf<String>()
    private val pendingActions = mutableListOf<PendingAction>()
    private val pendingLocalAdds = linkedMapOf<String, PendingLocalAdd>()
    private var pendingMove: PendingMove? = null
    private var lastRemoteItems: List<ShoppingItem> = emptyList()
    private var hadReadyOnce = false
    private var pendingRetryJob: Job? = null
    private var pendingMoveDispatchJob: Job? = null
    @Volatile
    private var syncInProgress = false

    init {
        restorePendingChanges()
        client.connect()

        scope.launch {
            client.authFailed.collect {
                Debug.log("REPOSITORY: auth failed")
                _authFailed.value = true
                _isOffline.value = false
            }
        }

        scope.launch {
            client.connectionErrors.collect {
                Debug.log("REPOSITORY: connection error")
                _connectionErrors.value = true
                _isOffline.value = true
                _isConnecting.value = false
                if (_items.value.isEmpty()) {
                    _loaded.value = true
                }
            }
        }

        scope.launch {
            client.disconnected.collect {
                if (_authFailed.value) {
                    return@collect
                }

                Debug.log("REPOSITORY: disconnected")
                _isOffline.value = true
                _isConnecting.value = false
                if (_items.value.isEmpty()) {
                    _loaded.value = true
                }
            }
        }

        scope.launch {
            client.ready.collect {
                _authFailed.value = false
                _connectionErrors.value = false
                _isOffline.value = false
                _isConnecting.value = false

                val wasReconnect = hadReadyOnce
                hadReadyOnce = true

                Debug.log("WS READY (RECONNECTED=$wasReconnect)")

                if (wasReconnect) {
                    _reconnected.value = System.currentTimeMillis()
                }

                val subscribeSent = client.send(
                    type = "todo/item/subscribe",
                    payload = JSONObject()
                        .put("entity_id", todoEntity)
                )
                if (!subscribeSent) {
                    Debug.log("REPOSITORY: subscribe send failed after ready -> reconnect")
                    _isOffline.value = true
                    _isConnecting.value = false
                    client.ensureConnected()
                    return@collect
                }

                scope.launch {
                    loadItems()
                    flushPendingChanges()
                }
            }
        }

        scope.launch {
            client.events.collect { json ->
                try {
                    Debug.log("WS EVENT: $json")

                    when (json.optString("type")) {
                        "result" -> {
                            if (!json.optBoolean("success")) {
                                Debug.log("REPOSITORY: websocket command result failed -> retry pending changes")
                                markPendingChangesForRetry()
                                return@collect
                            }

                            val resultObj = json.optJSONObject("result")
                            if (resultObj != null && resultObj.has("items")) {
                                parseItemsFromResult(resultObj)
                            }
                        }

                        "event" -> {
                            val event = json.optJSONObject("event")
                            if (event != null && event.has("items")) {
                                parseItemsFromArray(
                                    array = event.getJSONArray("items"),
                                    isRealtimeEvent = true
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun parseItemsFromResult(resultObj: JSONObject) {
        if (!resultObj.has("items")) return
        parseItemsFromArray(
            array = resultObj.getJSONArray("items"),
            isRealtimeEvent = false
        )
    }

    private fun parseItemsFromArray(
        array: JSONArray,
        isRealtimeEvent: Boolean
    ) {
        val parsedRemote = mutableListOf<ShoppingItem>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            parsedRemote += ShoppingItem(
                id = item.getString("uid"),
                name = item.getString("summary"),
                complete = item.getString("status") == "completed"
            )
        }

        val previousRemoteIds = lastRemoteItems.map { it.id }.toSet()
        lastRemoteItems = parsedRemote

        val finalItems = synchronized(lock) {
            reconcileLocalAdds(parsedRemote, previousRemoteIds)
            pruneSatisfiedPendingActions(parsedRemote)
            pruneSatisfiedPendingMove(parsedRemote)
            val withPendingApplied = applyPendingActions(parsedRemote)
            persistPendingChangesLocked()
            withPendingApplied + pendingLocalAdds.values.map { it.toShoppingItem() }
        }

        val previousItems = _items.value
        emitNotifications(previousItems = previousItems, newItems = finalItems)
        _items.value = finalItems
        if (isRealtimeEvent && previousItems != finalItems && _loaded.value) {
            _remoteActivity.tryEmit(Unit)
        }
        _loaded.value = true

        scope.launch {
            finalItems.forEach { item ->
                productHistoryRepository.rememberProduct(item.name)
            }
        }

        if (client.isReady()) {
            scope.launch {
                flushPendingChanges()
            }
        }
    }

    private fun reconcileLocalAdds(
        parsedRemote: List<ShoppingItem>,
        previousRemoteIds: Set<String>
    ) {
        val usedRemoteIds = mutableSetOf<String>()
        val resolvedActions = mutableListOf<PendingAction>()

        pendingLocalAdds.values.toList().forEach { pendingAdd ->
            val matched = parsedRemote.firstOrNull { remote ->
                remote.id !in usedRemoteIds &&
                    remote.name == pendingAdd.name &&
                    remote.id !in previousRemoteIds
            } ?: parsedRemote.firstOrNull { remote ->
                remote.id !in usedRemoteIds &&
                    remote.name == pendingAdd.name
            }

            if (matched == null) {
                return@forEach
            }

            usedRemoteIds += matched.id
            pendingLocalAdds.remove(pendingAdd.tempId)

            if (pendingAdd.complete) {
                resolvedActions += PendingAction.Update(
                    itemId = matched.id,
                    name = pendingAdd.name,
                    complete = true,
                    lastSentAtMillis = null
                )
            }

            pendingMove = pendingMove?.let { move ->
                move.copy(
                    itemId = if (move.itemId == pendingAdd.tempId) matched.id else move.itemId,
                    previousItemId = if (move.previousItemId == pendingAdd.tempId) matched.id else move.previousItemId
                )
            }
        }

        pendingActions.addAll(0, resolvedActions)
    }

    private fun emitNotifications(
        previousItems: List<ShoppingItem>,
        newItems: List<ShoppingItem>
    ) {
        val previousIds = previousItems.map { it.id }.toSet()
        val newIds = newItems.map { it.id }.toSet()
        val addedIds = newIds - previousIds

        if (previousItems.isEmpty()) {
            return
        }

        newItems
            .filter { it.id in addedIds }
            .forEach { item ->
                if (item.id.startsWith(LOCAL_ID_PREFIX)) {
                    return@forEach
                }

                if (locallyAddedItemNames.contains(item.name.trim())) {
                    locallyAddedItemNames.remove(item.name.trim())
                    return@forEach
                }

                _newItems.tryEmit(item)
                NotificationHelper.showNewItemNotification(appContext, item)
            }
    }

    fun loadItems() {
        if (!client.isReady()) {
            return
        }

        val sent = client.send(
            type = "todo/item/list",
            payload = JSONObject()
                .put("entity_id", todoEntity)
        )
        if (!sent) {
            Debug.log("REPOSITORY: loadItems send failed -> reconnect")
            _isOffline.value = true
            _isConnecting.value = false
            client.ensureConnected()
        }
    }

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return
        }

        val tempId = nextTempId()
        synchronized(lock) {
            pendingLocalAdds[tempId] = PendingLocalAdd(
                tempId = tempId,
                name = trimmed,
                complete = false,
                previousItemId = _items.value.lastOrNull { !it.complete }?.id,
                lastSentAtMillis = null
            )
            _items.value = _items.value + ShoppingItem(
                id = tempId,
                name = trimmed,
                complete = false
            )
            persistPendingChangesLocked()
        }
        if (client.isReady()) {
            scope.launch {
                flushPendingChanges()
            }
        } else {
            _isOffline.value = true
            _isConnecting.value = false
            client.ensureConnected()
        }
        _loaded.value = true
        scope.launch {
            productHistoryRepository.recordProductUse(trimmed)
        }
    }

    fun toggleItem(item: ShoppingItem) {
        val newStatus = !item.complete
        updateLocalItem(item.id) { current ->
            current.copy(complete = newStatus)
        }

        if (item.id.startsWith(LOCAL_ID_PREFIX)) {
            synchronized(lock) {
                pendingLocalAdds[item.id]?.let { add ->
                    pendingLocalAdds[item.id] = add.copy(complete = newStatus)
                    persistPendingChangesLocked()
                }
            }
            return
        }

        enqueueOrSendUpdate(item.id, item.name, newStatus)
    }

    fun renameItem(item: ShoppingItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return
        }

        updateLocalItem(item.id) { current ->
            current.copy(name = trimmed)
        }
        scope.launch {
            productHistoryRepository.recordProductUse(trimmed)
        }

        if (item.id.startsWith(LOCAL_ID_PREFIX)) {
            synchronized(lock) {
                pendingLocalAdds[item.id]?.let { add ->
                    pendingLocalAdds[item.id] = add.copy(name = trimmed)
                    persistPendingChangesLocked()
                }
            }
            return
        }

        enqueueOrSendUpdate(item.id, trimmed, item.complete)
    }

    fun moveItem(itemId: String, previousItemId: String?) {
        applyLocalMove(itemId, previousItemId)

        synchronized(lock) {
            if (itemId.startsWith(LOCAL_ID_PREFIX)) {
                pendingLocalAdds[itemId]?.let { add ->
                    pendingLocalAdds[itemId] = add.copy(previousItemId = previousItemId)
                }
            }
            pendingMove = PendingMove(
                itemId = itemId,
                previousItemId = previousItemId,
                lastSentAtMillis = null
            )
            persistPendingChangesLocked()
        }

        schedulePendingMoveDispatch()
    }

    fun clearCompleted() {
        val itemsSnapshot = _items.value
        val completedItems = itemsSnapshot.filter { it.complete }
        if (completedItems.isEmpty()) {
            return
        }

        synchronized(lock) {
            val idsToRemove = completedItems.map { it.id }.toSet()
            pendingLocalAdds.keys
                .filter { it in idsToRemove }
                .forEach { pendingLocalAdds.remove(it) }
            pendingMove = pendingMove?.takeUnless { move ->
                move.itemId in idsToRemove || move.previousItemId in idsToRemove
            }

            pendingActions.removeAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.itemId in idsToRemove
                    is PendingAction.Update -> action.itemId in idsToRemove
                }
            }
            persistPendingChangesLocked()
        }

        _items.value = itemsSnapshot.filterNot { it.complete }

        completedItems
            .filterNot { it.id.startsWith(LOCAL_ID_PREFIX) }
            .forEach { enqueueOrSendRemove(it.id) }
    }

    fun disconnect() {
        client.disconnect()
    }

    fun ensureConnected() {
        _authFailed.value = false
        _connectionErrors.value = false

        if (client.isReady()) {
            _isOffline.value = false
            _isConnecting.value = false
            Debug.log("REPOSITORY: already connected -> sync pending + reload")
            scope.launch {
                flushPendingChanges()
            }
            return
        }

        if (_items.value.isEmpty()) {
            _loaded.value = false
        } else {
            _loaded.value = true
            _isConnecting.value = true
        }
        client.ensureConnected()
    }

    fun setReconnectAllowed(allowed: Boolean) {
        client.setReconnectAllowed(allowed)
    }

    private fun enqueueOrSendUpdate(itemId: String, name: String, complete: Boolean) {
        val sentAtMillis = if (client.isReady() && sendUpdateItem(itemId, name, complete)) {
            System.currentTimeMillis()
        } else {
            null
        }

        synchronized(lock) {
            pendingActions.removeAll { action ->
                action is PendingAction.Update && action.itemId == itemId
            }
            pendingActions += PendingAction.Update(
                itemId = itemId,
                name = name,
                complete = complete,
                lastSentAtMillis = sentAtMillis
            )
            persistPendingChangesLocked()
        }

        if (sentAtMillis == null) {
            _isOffline.value = true
            _isConnecting.value = false
            client.ensureConnected()
        }
    }

    private fun schedulePendingMoveDispatch() {
        pendingMoveDispatchJob?.cancel()
        pendingMoveDispatchJob = scope.launch {
            delay(PENDING_MOVE_DEBOUNCE_MILLIS)
            flushPendingChanges()
        }
    }

    private fun enqueueOrSendRemove(itemId: String) {
        val sentAtMillis = if (client.isReady() && sendRemoveItem(itemId)) {
            System.currentTimeMillis()
        } else {
            null
        }

        synchronized(lock) {
            pendingActions.removeAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.itemId == itemId
                    is PendingAction.Update -> action.itemId == itemId
                }
            }
            pendingActions += PendingAction.Remove(
                itemId = itemId,
                lastSentAtMillis = sentAtMillis
            )
            persistPendingChangesLocked()
        }

        if (sentAtMillis == null) {
            _isOffline.value = true
            _isConnecting.value = false
            client.ensureConnected()
        }
    }

    private suspend fun flushPendingChanges() {
        if (!client.isReady() || syncInProgress) {
            if (client.isReady()) {
                loadItems()
            }
            return
        }

        syncInProgress = true
        try {
            val localAdds: List<PendingLocalAdd>
            val queuedActions: List<PendingAction>
            val moveToSend: PendingMove?
            synchronized(lock) {
                localAdds = pendingLocalAdds.values.toList()
                queuedActions = pendingActions.toList()
                moveToSend = pendingMove
            }

            val sentLocalAddIds = mutableSetOf<String>()
            val sentUpdateIds = mutableSetOf<String>()
            val sentRemoveIds = mutableSetOf<String>()
            val now = System.currentTimeMillis()

            localAdds
                .filter { shouldSendPending(it.lastSentAtMillis, now) }
                .forEach { add ->
                    locallyAddedItemNames.add(add.name.trim())
                    if (sendAddItem(add.name)) {
                        sentLocalAddIds += add.tempId
                    }
                }

            queuedActions
                .filter { action ->
                    when (action) {
                        is PendingAction.Remove -> shouldSendPending(action.lastSentAtMillis, now)
                        is PendingAction.Update -> shouldSendPending(action.lastSentAtMillis, now)
                    }
                }
                .forEach { action ->
                    when (action) {
                        is PendingAction.Remove -> {
                            val resolvedId = resolveRemoteId(action.itemId) ?: action.itemId
                            if (sendRemoveItem(itemId = resolvedId)) {
                                sentRemoveIds += action.itemId
                            }
                        }

                        is PendingAction.Update -> {
                            val resolvedId = resolveRemoteId(action.itemId) ?: action.itemId
                            if (
                                sendUpdateItem(
                                    itemId = resolvedId,
                                    name = action.name,
                                    complete = action.complete
                                )
                            ) {
                                sentUpdateIds += action.itemId
                            }
                        }
                    }
                }

            val shouldSendPendingMove =
                moveToSend != null &&
                    shouldSendPending(moveToSend.lastSentAtMillis, now) &&
                    resolveRemoteId(moveToSend.itemId) != null &&
                    resolveRemoteId(moveToSend.previousItemId) != null
            val moveSent =
                if (shouldSendPendingMove) {
                    sendPendingMove(moveToSend!!)
                } else {
                    false
                }

            val sentAnyNonReorderChange =
                sentLocalAddIds.isNotEmpty() || sentUpdateIds.isNotEmpty() || sentRemoveIds.isNotEmpty()

            synchronized(lock) {
                sentLocalAddIds.forEach { tempId ->
                    pendingLocalAdds[tempId]?.let { add ->
                        pendingLocalAdds[tempId] = add.copy(lastSentAtMillis = now)
                    }
                }
                pendingActions.replaceAll { action ->
                    when (action) {
                        is PendingAction.Remove -> {
                            if (action.itemId in sentRemoveIds) action.copy(lastSentAtMillis = now) else action
                        }

                        is PendingAction.Update -> {
                            if (action.itemId in sentUpdateIds) action.copy(lastSentAtMillis = now) else action
                        }
                    }
                }
                if (moveSent) {
                    pendingMove = pendingMove?.copy(lastSentAtMillis = now)
                }
                persistPendingChangesLocked()
            }

            if (
                sentLocalAddIds.size != localAdds.count { shouldSendPending(it.lastSentAtMillis, now) } ||
                sentUpdateIds.size != queuedActions.count { it is PendingAction.Update && shouldSendPending(it.lastSentAtMillis, now) } ||
                sentRemoveIds.size != queuedActions.count { it is PendingAction.Remove && shouldSendPending(it.lastSentAtMillis, now) } ||
                (shouldSendPendingMove && !moveSent)
            ) {
                _isOffline.value = true
                _isConnecting.value = false
                client.ensureConnected()
            }

            if (sentAnyNonReorderChange) {
                delay(150)
                loadItems()
            }
            schedulePendingRetryIfNeeded()
        } finally {
            syncInProgress = false
        }
    }

    private fun applyPendingActions(remoteItems: List<ShoppingItem>): List<ShoppingItem> {
        var items = remoteItems

        pendingActions.forEach { action ->
            when (action) {
                is PendingAction.Remove -> {
                    items = items.filterNot { it.id == action.itemId }
                }

                is PendingAction.Update -> {
                    items = items.map { item ->
                        if (item.id == action.itemId) {
                            item.copy(
                                name = action.name,
                                complete = action.complete
                            )
                        } else {
                            item
                        }
                    }
                }
            }
        }

        return items
    }

    private fun pruneSatisfiedPendingActions(remoteItems: List<ShoppingItem>) {
        pendingActions.removeAll { action ->
            when (action) {
                is PendingAction.Remove -> {
                    action.lastSentAtMillis != null && remoteItems.none { it.id == action.itemId }
                }

                is PendingAction.Update -> {
                    action.lastSentAtMillis != null && remoteItems.any { remote ->
                        remote.id == action.itemId &&
                            remote.name == action.name &&
                            remote.complete == action.complete
                    }
                }
            }
        }
    }

    private fun pruneSatisfiedPendingMove(remoteItems: List<ShoppingItem>) {
        val move = pendingMove ?: return
        if (move.lastSentAtMillis == null) {
            return
        }

        val remoteOpenOrder = remoteItems
            .filter { !it.complete }
            .map { it.id }

        val itemIndex = remoteOpenOrder.indexOf(move.itemId)
        if (itemIndex < 0) {
            return
        }

        val actualPreviousItemId = remoteOpenOrder.getOrNull(itemIndex - 1)
        if (actualPreviousItemId == move.previousItemId) {
            pendingMove = null
        }
    }

    private fun sendAddItem(name: String): Boolean {
        return client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "add_item")
                .put("target", JSONObject()
                    .put("entity_id", todoEntity)
                )
                .put("return_response", false)
                .put("service_data", JSONObject()
                    .put("item", name)
                )
        )
    }

    private fun sendUpdateItem(itemId: String, name: String, complete: Boolean): Boolean {
        return client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "update_item")
                .put("target", JSONObject()
                    .put("entity_id", todoEntity)
                )
                .put("service_data", JSONObject()
                    .put("item", itemId)
                    .put("rename", name)
                    .put("status", if (complete) "completed" else "needs_action")
                )
                .put("return_response", false)
        )
    }

    private fun sendPendingMove(move: PendingMove): Boolean {
        val resolvedItemId = resolveRemoteId(move.itemId) ?: return false
        val resolvedPreviousItemId = resolveRemoteId(move.previousItemId) ?: return false
        return sendMoveItem(
            itemId = resolvedItemId,
            previousItemId = resolvedPreviousItemId
        )
    }

    private fun sendMoveItem(itemId: String, previousItemId: String?): Boolean {
        return client.send(
            type = "todo/item/move",
            payload = JSONObject()
                .put("entity_id", todoEntity)
                .put("uid", itemId)
                .put("previous_uid", previousItemId ?: JSONObject.NULL)
        )
    }

    private fun sendRemoveItem(itemId: String): Boolean {
        return client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "remove_item")
                .put("target", JSONObject()
                    .put("entity_id", todoEntity)
                )
                .put("service_data", JSONObject()
                    .put("item", itemId)
                )
        )
    }

    private fun updateLocalItem(
        itemId: String,
        transform: (ShoppingItem) -> ShoppingItem
    ) {
        _items.value = _items.value.map { current ->
            if (current.id == itemId) transform(current) else current
        }
    }

    private fun applyLocalMove(itemId: String, previousItemId: String?) {
        val currentItems = _items.value.toMutableList()
        val fromIndex = currentItems.indexOfFirst { it.id == itemId }
        if (fromIndex < 0) {
            return
        }

        val movedItem = currentItems.removeAt(fromIndex)

        val targetIndex = if (previousItemId == null) {
            0
        } else {
            currentItems.indexOfFirst { it.id == previousItemId }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: currentItems.size
        }

        currentItems.add(targetIndex.coerceIn(0, currentItems.size), movedItem)
        _items.value = currentItems
    }

    private fun resolveRemoteId(itemId: String?): String? {
        if (itemId == null || !itemId.startsWith(LOCAL_ID_PREFIX)) {
            return itemId
        }

        return pendingLocalAdds[itemId]?.let { null }
    }

    private fun shouldSendPending(lastSentAtMillis: Long?, now: Long): Boolean {
        return lastSentAtMillis == null || now - lastSentAtMillis >= PENDING_RETRY_AFTER_MILLIS
    }

    private fun schedulePendingRetryIfNeeded() {
        val retryDelayMillis = synchronized(lock) {
            val hasCriticalPendingChanges = pendingLocalAdds.isNotEmpty() || pendingActions.isNotEmpty()
            val hasPendingMove = pendingMove != null

            when {
                hasCriticalPendingChanges -> PENDING_RETRY_AFTER_MILLIS
                hasPendingMove -> PENDING_MOVE_RETRY_AFTER_MILLIS
                else -> null
            }
        }
        if (retryDelayMillis == null || pendingRetryJob?.isActive == true) {
            return
        }

        pendingRetryJob = scope.launch {
            delay(retryDelayMillis)
            if (client.isReady()) {
                flushPendingChanges()
            } else {
                client.ensureConnected()
            }
        }
    }

    private fun markPendingChangesForRetry() {
        synchronized(lock) {
            pendingLocalAdds.replaceAll { _, add ->
                add.copy(lastSentAtMillis = null)
            }
            pendingActions.replaceAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.copy(lastSentAtMillis = null)
                    is PendingAction.Update -> action.copy(lastSentAtMillis = null)
                }
            }
            pendingMove = pendingMove?.copy(lastSentAtMillis = null)
            if (pendingMove != null) {
                pendingMoveDispatchJob?.cancel()
            }
            persistPendingChangesLocked()
        }
        schedulePendingRetryIfNeeded()
    }

    private fun restorePendingChanges() {
        val raw = pendingSyncPrefs.getString(PENDING_SYNC_KEY, null) ?: return

        try {
            val root = JSONObject(raw)
            var maxRestoredTempId = 0L
            synchronized(lock) {
                pendingLocalAdds.clear()
                val adds = root.optJSONArray("localAdds") ?: JSONArray()
                for (index in 0 until adds.length()) {
                    val add = adds.optJSONObject(index) ?: continue
                    val tempId = add.optString("tempId").takeIf { it.isNotBlank() } ?: continue
                    tempId
                        .removePrefix(LOCAL_ID_PREFIX)
                        .toLongOrNull()
                        ?.let { maxRestoredTempId = maxOf(maxRestoredTempId, it) }
                    pendingLocalAdds[tempId] = PendingLocalAdd(
                        tempId = tempId,
                        name = add.optString("name"),
                        complete = add.optBoolean("complete"),
                        previousItemId = add.optNullableString("previousItemId"),
                        lastSentAtMillis = add.optNullableLong("lastSentAtMillis")
                    )
                }

                pendingActions.clear()
                val actions = root.optJSONArray("actions") ?: JSONArray()
                for (index in 0 until actions.length()) {
                    val action = actions.optJSONObject(index) ?: continue
                    when (action.optString("type")) {
                        "update" -> pendingActions += PendingAction.Update(
                            itemId = action.optString("itemId"),
                            name = action.optString("name"),
                            complete = action.optBoolean("complete"),
                            lastSentAtMillis = action.optNullableLong("lastSentAtMillis")
                        )

                        "remove" -> pendingActions += PendingAction.Remove(
                            itemId = action.optString("itemId"),
                            lastSentAtMillis = action.optNullableLong("lastSentAtMillis")
                        )
                    }
                }

                root.optJSONObject("move")?.let { move ->
                    val itemId = move.optString("itemId").takeIf { it.isNotBlank() }
                    if (itemId != null) {
                        pendingMove = PendingMove(
                            itemId = itemId,
                            previousItemId = move.optNullableString("previousItemId"),
                            lastSentAtMillis = move.optNullableLong("lastSentAtMillis")
                        )
                    }
                }
            }

            tempIdCounter.set(maxRestoredTempId + 1)
            if (pendingLocalAdds.isNotEmpty()) {
                _items.value = pendingLocalAdds.values.map { it.toShoppingItem() }
                _loaded.value = true
            }
            Debug.log("REPOSITORY: restored pending shopping-list changes")
        } catch (e: Exception) {
            Debug.log("REPOSITORY: failed to restore pending changes: ${e.message}")
            pendingSyncPrefs.edit().remove(PENDING_SYNC_KEY).apply()
        }
    }

    private fun persistPendingChangesLocked() {
        if (pendingLocalAdds.isEmpty() && pendingActions.isEmpty() && pendingMove == null) {
            pendingSyncPrefs.edit().remove(PENDING_SYNC_KEY).apply()
            return
        }

        val root = JSONObject()
        val adds = JSONArray()
        pendingLocalAdds.values.forEach { add ->
            adds.put(
                JSONObject()
                    .put("tempId", add.tempId)
                    .put("name", add.name)
                    .put("complete", add.complete)
                    .put("previousItemId", add.previousItemId ?: JSONObject.NULL)
                    .put("lastSentAtMillis", add.lastSentAtMillis ?: JSONObject.NULL)
            )
        }

        val actions = JSONArray()
        pendingActions.forEach { action ->
            when (action) {
                is PendingAction.Remove -> actions.put(
                    JSONObject()
                        .put("type", "remove")
                        .put("itemId", action.itemId)
                        .put("lastSentAtMillis", action.lastSentAtMillis ?: JSONObject.NULL)
                )

                is PendingAction.Update -> actions.put(
                    JSONObject()
                        .put("type", "update")
                        .put("itemId", action.itemId)
                        .put("name", action.name)
                        .put("complete", action.complete)
                        .put("lastSentAtMillis", action.lastSentAtMillis ?: JSONObject.NULL)
                )
            }
        }

        root
            .put("localAdds", adds)
            .put("actions", actions)
            .put(
                "move",
                pendingMove?.let { move ->
                    JSONObject()
                        .put("itemId", move.itemId)
                        .put("previousItemId", move.previousItemId ?: JSONObject.NULL)
                        .put("lastSentAtMillis", move.lastSentAtMillis ?: JSONObject.NULL)
                } ?: JSONObject.NULL
            )

        pendingSyncPrefs.edit().putString(PENDING_SYNC_KEY, root.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun nextTempId(): String = "$LOCAL_ID_PREFIX${tempIdCounter.getAndIncrement()}"

    private fun PendingLocalAdd.toShoppingItem(): ShoppingItem = ShoppingItem(
        id = tempId,
        name = name,
        complete = complete
    )

    companion object {
        private const val LOCAL_ID_PREFIX = "local:"
        private const val PENDING_SYNC_PREFS = "pending_shopping_list_sync"
        private const val PENDING_SYNC_KEY = "pending_changes"
        private const val PENDING_RETRY_AFTER_MILLIS = 10_000L
        private const val PENDING_MOVE_DEBOUNCE_MILLIS = 750L
        private const val PENDING_MOVE_RETRY_AFTER_MILLIS = 30_000L
    }
}
