package de.robnice.homeasssistant_shoppinglist.data

import android.content.Context
import de.robnice.homeasssistant_shoppinglist.data.history.ProductHistoryRepository
import de.robnice.homeasssistant_shoppinglist.data.websocket.HaWebSocketClient
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import de.robnice.homeasssistant_shoppinglist.util.Debug
import de.robnice.homeasssistant_shoppinglist.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            val sentToServer: Boolean
        ) : PendingAction

        data class Remove(
            val itemId: String,
            val sentToServer: Boolean
        ) : PendingAction
    }

    private data class PendingLocalAdd(
        val tempId: String,
        val name: String,
        val complete: Boolean,
        val previousItemId: String?,
        val sentToServer: Boolean
    )

    private val client = HaWebSocketClient(baseUrl, token)
    private val productHistoryRepository = ProductHistoryRepository.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val tempIdCounter = AtomicLong(1)

    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val _authFailed = MutableStateFlow(false)
    private val _connectionErrors = MutableStateFlow(false)
    private val _isOffline = MutableStateFlow(false)
    private val _newItems = MutableSharedFlow<ShoppingItem>(replay = 1)
    private val _loaded = MutableStateFlow(false)
    private val _reconnected = MutableStateFlow(0L)

    val loaded = _loaded.asStateFlow()
    val authFailed = _authFailed.asStateFlow()
    val connectionErrors = _connectionErrors.asStateFlow()
    val isOffline = _isOffline.asStateFlow()
    val items = _items.asStateFlow()
    val newItems = _newItems.asSharedFlow()
    val reconnected = _reconnected.asStateFlow()

    private val locallyAddedItemNames = mutableSetOf<String>()
    private val pendingActions = mutableListOf<PendingAction>()
    private val pendingLocalAdds = linkedMapOf<String, PendingLocalAdd>()
    private var pendingOpenOrder: List<String>? = null
    private var pendingOpenOrderSentToServer = false
    private var lastRemoteItems: List<ShoppingItem> = emptyList()
    private var hadReadyOnce = false
    @Volatile
    private var syncInProgress = false

    init {
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

                val wasReconnect = hadReadyOnce
                hadReadyOnce = true

                Debug.log("WS READY (RECONNECTED=$wasReconnect)")

                if (wasReconnect) {
                    _reconnected.value = System.currentTimeMillis()
                }

                client.send(
                    type = "todo/item/subscribe",
                    payload = JSONObject()
                        .put("entity_id", todoEntity)
                )

                scope.launch {
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
                                parseItemsFromArray(event.getJSONArray("items"))
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
        parseItemsFromArray(resultObj.getJSONArray("items"))
    }

    private fun parseItemsFromArray(array: JSONArray) {
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
            pruneSatisfiedPendingOpenOrder(parsedRemote)
            val withPendingApplied = applyPendingActions(parsedRemote)
            val withPendingOrderApplied = applyPendingOpenOrder(withPendingApplied)
            withPendingOrderApplied + pendingLocalAdds.values.map { it.toShoppingItem() }
        }

        emitNotifications(previousItems = _items.value, newItems = finalItems)
        _items.value = finalItems
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
                    sentToServer = false
                )
            }

            if (pendingOpenOrder != null) {
                pendingOpenOrder = pendingOpenOrder?.map { id ->
                    if (id == pendingAdd.tempId) matched.id else id
                }
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

        client.send(
            type = "todo/item/list",
            payload = JSONObject()
                .put("entity_id", todoEntity)
        )
    }

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return
        }

        if (client.isReady()) {
            locallyAddedItemNames.add(trimmed)
            scope.launch {
                productHistoryRepository.recordProductUse(trimmed)
            }
            sendAddItem(trimmed)
            return
        }

        val tempId = nextTempId()
        synchronized(lock) {
            pendingLocalAdds[tempId] = PendingLocalAdd(
                tempId = tempId,
                name = trimmed,
                complete = false,
                previousItemId = _items.value.lastOrNull { !it.complete }?.id,
                sentToServer = false
            )
            _items.value = _items.value + ShoppingItem(
                id = tempId,
                name = trimmed,
                complete = false
            )
        }
        _isOffline.value = true
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
                }
            }
            return
        }

        enqueueOrSendUpdate(item.id, trimmed, item.complete)
    }

    fun moveItem(itemId: String, previousItemId: String?) {
        applyLocalMove(itemId, previousItemId)

        if (itemId.startsWith(LOCAL_ID_PREFIX)) {
            synchronized(lock) {
                pendingLocalAdds[itemId]?.let { add ->
                    pendingLocalAdds[itemId] = add.copy(previousItemId = previousItemId)
                    pendingOpenOrder = currentOpenOrderIds()
                    pendingOpenOrderSentToServer = false
                }
            }
            return
        }

        enqueueOrSendOpenOrder()
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
            pendingOpenOrder = pendingOpenOrder
                ?.filterNot { it in idsToRemove }
                ?.takeIf { it.isNotEmpty() }

            pendingActions.removeAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.itemId in idsToRemove
                    is PendingAction.Update -> action.itemId in idsToRemove
                }
            }
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
            _isOffline.value = true
        }
        client.ensureConnected()
    }

    fun setReconnectAllowed(allowed: Boolean) {
        client.setReconnectAllowed(allowed)
    }

    private fun enqueueOrSendUpdate(itemId: String, name: String, complete: Boolean) {
        val sentToServer = client.isReady()

        synchronized(lock) {
            pendingActions.removeAll { action ->
                action is PendingAction.Update && action.itemId == itemId
            }
            pendingActions += PendingAction.Update(
                itemId = itemId,
                name = name,
                complete = complete,
                sentToServer = sentToServer
            )
        }

        if (sentToServer) {
            sendUpdateItem(itemId, name, complete)
        } else {
            _isOffline.value = true
        }
    }

    private fun enqueueOrSendOpenOrder() {
        val sentToServer = client.isReady()

        synchronized(lock) {
            pendingOpenOrder = currentOpenOrderIds()
            pendingOpenOrderSentToServer = sentToServer
        }

        if (sentToServer) {
            sendOpenItemOrder()
        } else {
            _isOffline.value = true
        }
    }

    private fun enqueueOrSendRemove(itemId: String) {
        val sentToServer = client.isReady()

        synchronized(lock) {
            pendingActions.removeAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.itemId == itemId
                    is PendingAction.Update -> action.itemId == itemId
                }
            }
            pendingActions += PendingAction.Remove(
                itemId = itemId,
                sentToServer = sentToServer
            )
        }

        if (sentToServer) {
            sendRemoveItem(itemId)
        } else {
            _isOffline.value = true
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
            val openOrderToSend: List<String>?
            synchronized(lock) {
                localAdds = pendingLocalAdds.values.toList()
                queuedActions = pendingActions.toList()
                openOrderToSend = pendingOpenOrder
                localAdds
                    .filterNot { it.sentToServer }
                    .forEach { add ->
                        pendingLocalAdds[add.tempId] = add.copy(sentToServer = true)
                    }
                pendingActions.replaceAll { action ->
                    when (action) {
                        is PendingAction.Remove -> {
                            if (action.sentToServer) action else action.copy(sentToServer = true)
                        }
                        is PendingAction.Update -> {
                            if (action.sentToServer) action else action.copy(sentToServer = true)
                        }
                    }
                }
                if (pendingOpenOrder != null) {
                    pendingOpenOrderSentToServer = true
                }
            }

            localAdds
                .filterNot { it.sentToServer }
                .forEach { add ->
                locallyAddedItemNames.add(add.name.trim())
                sendAddItem(add.name)
            }

            queuedActions
                .filterNot { action ->
                    when (action) {
                        is PendingAction.Remove -> action.sentToServer
                        is PendingAction.Update -> action.sentToServer
                    }
                }
                .forEach { action ->
                when (action) {
                    is PendingAction.Remove -> sendRemoveItem(
                        itemId = resolveRemoteId(action.itemId) ?: action.itemId
                    )

                    is PendingAction.Update -> sendUpdateItem(
                        itemId = resolveRemoteId(action.itemId) ?: action.itemId,
                        name = action.name,
                        complete = action.complete
                    )
                }
            }

            if (openOrderToSend != null && openOrderToSend.none { it.startsWith(LOCAL_ID_PREFIX) }) {
                sendOpenItemOrder()
            }

            delay(150)
            loadItems()
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
                    action.sentToServer && remoteItems.none { it.id == action.itemId }
                }

                is PendingAction.Update -> {
                    action.sentToServer && remoteItems.any { remote ->
                        remote.id == action.itemId &&
                            remote.name == action.name &&
                            remote.complete == action.complete
                    }
                }
            }
        }
    }

    private fun sendAddItem(name: String) {
        client.send(
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

    private fun sendUpdateItem(itemId: String, name: String, complete: Boolean) {
        client.send(
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

    private fun sendOpenItemOrder() {
        val orderToSend = synchronized(lock) {
            pendingOpenOrder
                ?.mapNotNull { resolveRemoteId(it) }
                ?.takeIf { it.isNotEmpty() }
        } ?: return

        orderToSend.forEachIndexed { index, itemId ->
            sendMoveItem(
                itemId = itemId,
                previousItemId = orderToSend.getOrNull(index - 1)
            )
        }
    }

    private fun sendMoveItem(itemId: String, previousItemId: String?) {
        client.send(
            type = "todo/item/move",
            payload = JSONObject()
                .put("entity_id", todoEntity)
                .put("uid", itemId)
                .put("previous_uid", previousItemId ?: JSONObject.NULL)
        )
    }

    private fun sendRemoveItem(itemId: String) {
        client.send(
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

    private fun applyPendingOpenOrder(items: List<ShoppingItem>): List<ShoppingItem> {
        val desiredOrder = pendingOpenOrder ?: return items
        val openItemsById = items
            .filter { !it.complete }
            .associateBy { it.id }

        if (openItemsById.isEmpty()) {
            return items
        }

        val orderedOpenItems = buildList {
            desiredOrder.forEach { id ->
                openItemsById[id]?.let(::add)
            }
            items
                .filter { !it.complete && it.id !in desiredOrder }
                .forEach(::add)
        }

        return orderedOpenItems + items.filter { it.complete }
    }

    private fun pruneSatisfiedPendingOpenOrder(remoteItems: List<ShoppingItem>) {
        val desiredOrder = pendingOpenOrder ?: return
        if (!pendingOpenOrderSentToServer) {
            return
        }

        val remoteOpenOrder = remoteItems
            .filter { !it.complete }
            .map { it.id }

        val expectedOrder = desiredOrder.filter { id -> remoteOpenOrder.contains(id) }
        if (expectedOrder.isNotEmpty() && remoteOpenOrder == expectedOrder) {
            pendingOpenOrder = null
            pendingOpenOrderSentToServer = false
        }
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

    private fun currentOpenOrderIds(): List<String> =
        _items.value
            .filter { !it.complete }
            .map { it.id }

    private fun resolveRemoteId(itemId: String?): String? {
        if (itemId == null || !itemId.startsWith(LOCAL_ID_PREFIX)) {
            return itemId
        }

        return pendingLocalAdds[itemId]?.let { null }
    }

    private fun nextTempId(): String = "$LOCAL_ID_PREFIX${tempIdCounter.getAndIncrement()}"

    private fun PendingLocalAdd.toShoppingItem(): ShoppingItem = ShoppingItem(
        id = tempId,
        name = name,
        complete = complete
    )

    companion object {
        private const val LOCAL_ID_PREFIX = "local:"
    }
}
