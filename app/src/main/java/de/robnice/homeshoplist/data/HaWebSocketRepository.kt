package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.data.dto.TodoGetItemsRequest
import de.robnice.homeshoplist.data.websocket.HaWebSocketClient
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.model.ShoppingList
import de.robnice.homeshoplist.model.encodeManagedItemName
import de.robnice.homeshoplist.model.isMetaItemName
import de.robnice.homeshoplist.model.parseManagedItemName
import de.robnice.homeshoplist.model.parseMetaItemName
import de.robnice.homeshoplist.util.Debug
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

private fun parseTodoItemsFromServiceResponsePayload(
    payload: String,
    todoEntity: String
): List<ShoppingItem> {
    val root = JSONObject(payload)
    val serviceResponse = root.optJSONObject("service_response") ?: return emptyList()
    val entityResponse = serviceResponse.optJSONObject(todoEntity) ?: return emptyList()
    val items = entityResponse.optJSONArray("items") ?: return emptyList()

    val parsedVisibleItems = mutableListOf<ShoppingItem>()
    val parsedMetaNames = mutableListOf<String>()

    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val itemId = item.optString("uid").takeIf { it.isNotBlank() } ?: continue
        val itemName = item.optString("summary")
        val itemDescription = if (item.has("description") && !item.isNull("description")) {
            item.optString("description")
        } else {
            null
        }

        if (isMetaItemName(itemName)) {
            parsedMetaNames += itemName
            continue
        }

        val managedItemName = parseManagedItemName(itemName)
        parsedVisibleItems += ShoppingItem(
            id = itemId,
            name = managedItemName.visibleName,
            complete = item.optString("status") == "completed",
            description = itemDescription,
            area = managedItemName.area
        )
    }

    val parsedMetaAreas = parseMetaItemName(parsedMetaNames.firstOrNull())?.itemAreas.orEmpty()
    return parsedVisibleItems.map { item ->
        item.copy(
            area = item.area ?: parsedMetaAreas[item.id]
        )
    }
}

class HaWebSocketRepository(
    private val client: HaRealtimeClient,
    private val pendingSyncStore: PendingSyncStore,
    private val productHistoryRecorder: ProductHistoryRecorder,
    private val notifier: ShoppingNotifier,
    private val todoEntity: String,
    private val todoListName: String? = null,
    private val initialSnapshotBaseUrl: String? = null,
    private val initialSnapshotToken: String? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

    constructor(
        baseUrl: String,
        token: String,
        appContext: Context,
        todoEntity: String,
        todoListName: String? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) : this(
        client = HaWebSocketClient(baseUrl, token),
        pendingSyncStore = SharedPreferencesPendingSyncStore(appContext),
        productHistoryRecorder = RepositoryProductHistoryRecorder(appContext),
        notifier = SystemShoppingNotifier(appContext),
        todoEntity = todoEntity,
        todoListName = todoListName,
        initialSnapshotBaseUrl = baseUrl,
        initialSnapshotToken = token,
        scope = scope
    )

    private sealed interface PendingAction {
        data class Update(
            val itemId: String,
            val name: String,
            val complete: Boolean,
            val area: ShoppingArea?,
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
        val area: ShoppingArea?,
        val previousItemId: String?,
        val lastSentAtMillis: Long?
    )

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
    private var hasLoadedRemoteItems = false
    private var lastRemoteItems: List<ShoppingItem> = emptyList()
    private var hadReadyOnce = false
    private var pendingRetryJob: Job? = null
    private var pendingMoveDispatchJob: Job? = null
    @Volatile
    private var syncInProgress = false

    init {
        restorePendingChanges()
        _isConnecting.value = true
        _isOffline.value = false
        client.connect()

        if (!initialSnapshotBaseUrl.isNullOrBlank() && !initialSnapshotToken.isNullOrBlank()) {
            scope.launch {
                Debug.log("REPOSITORY: initial snapshot start")
                val startedAt = currentTimeMillis()
                val snapshot = runCatching {
                    val api = HaServiceFactory.create(initialSnapshotBaseUrl)
                    val response = api.getTodoItemsRaw(
                        token = "Bearer ${initialSnapshotToken.trim()}",
                        body = TodoGetItemsRequest(entity_id = todoEntity)
                    )
                    parseTodoItemsFromServiceResponsePayload(
                        payload = response.string(),
                        todoEntity = todoEntity
                    )
                }.onFailure { error ->
                    Debug.log("REPOSITORY: initial snapshot failed: ${error.message}")
                }.getOrNull() ?: return@launch
                if (hasLoadedRemoteItems) {
                    Debug.log("REPOSITORY: initial snapshot ignored because remote items already loaded")
                    return@launch
                }

                val previousItems = _items.value
                if (previousItems.isNotEmpty()) {
                    Debug.log("REPOSITORY: initial snapshot ignored because local items already exist")
                    return@launch
                }

                Debug.log(
                    "REPOSITORY: initial snapshot success items=${snapshot.size} took=${currentTimeMillis() - startedAt}ms"
                )
                _items.value = snapshot
                _loaded.value = true
            }
        }

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
                if (hasLoadedRemoteItems || _items.value.isNotEmpty()) {
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
                if (hasLoadedRemoteItems || _items.value.isNotEmpty()) {
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
                    _reconnected.value = currentTimeMillis()
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
                                if (!handleFailedCommandResult(json)) {
                                    Debug.log("REPOSITORY: websocket command result failed -> retry pending changes")
                                    markPendingChangesForRetry()
                                }
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
        val parsedVisibleItems = mutableListOf<ShoppingItem>()
        val parsedMetaIds = mutableListOf<String>()
        val parsedMetaNames = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val itemId = item.getString("uid")
            val itemName = item.getString("summary")
            val itemDescription = item.optNullableString("description")

            if (isMetaItemName(itemName)) {
                parsedMetaIds += itemId
                parsedMetaNames += itemName
                continue
            }

            val managedItemName = parseManagedItemName(itemName)

            parsedVisibleItems += ShoppingItem(
                id = itemId,
                name = managedItemName.visibleName,
                complete = item.getString("status") == "completed",
                description = itemDescription,
                area = managedItemName.area
            )
        }

        val parsedMetaAreas = parseMetaItemName(parsedMetaNames.firstOrNull())?.itemAreas.orEmpty()
        val parsedRemote = parsedVisibleItems.map { item ->
            item.copy(
                area = item.area ?: parsedMetaAreas[item.id]
            )
        }

        val previousRemoteIds = lastRemoteItems.map { it.id }.toSet()
        lastRemoteItems = parsedRemote

        val finalItems = synchronized(lock) {
            hasLoadedRemoteItems = true
            reconcileLegacyMetaItems(parsedMetaIds)
            reconcileLocalAdds(parsedRemote, previousRemoteIds)
            pruneSatisfiedPendingActions(parsedRemote)
            pruneSatisfiedPendingMove(parsedRemote)
            val withPendingApplied = applyPendingMove(applyPendingActions(parsedRemote))
            syncPendingMetaLocked(withPendingApplied)
            persistPendingChangesLocked()
            withPendingApplied + visiblePendingLocalAddsLocked()
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
                productHistoryRecorder.rememberProduct(item.name, item.area)
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
                    complete = pendingAdd.complete,
                    area = pendingAdd.area,
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
                notifier.showNewItemNotification(item)
            }
    }

    private fun parseTodoListsFromStates(states: JSONArray): List<ShoppingList> {
        return buildList {
            for (index in 0 until states.length()) {
                val state = states.optJSONObject(index) ?: continue
                val entityId = state.optString("entity_id").takeIf { it.startsWith("todo.") } ?: continue
                val friendlyName = state
                    .optJSONObject("attributes")
                    ?.optString("friendly_name")
                    ?.takeIf { it.isNotBlank() }

                add(
                    ShoppingList(
                        id = entityId,
                        name = friendlyName ?: entityId
                    )
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseShoppingItems(items: JSONArray): List<ShoppingItem> {
        val parsedVisibleItems = mutableListOf<ShoppingItem>()
        val parsedMetaNames = mutableListOf<String>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val itemId = item.optString("uid").takeIf { it.isNotBlank() } ?: continue
            val itemName = item.optString("summary")
            val itemDescription = item.optNullableString("description")

            if (isMetaItemName(itemName)) {
                parsedMetaNames += itemName
                continue
            }

            val managedItemName = parseManagedItemName(itemName)
            parsedVisibleItems += ShoppingItem(
                id = itemId,
                name = managedItemName.visibleName,
                complete = item.optString("status") == "completed",
                description = itemDescription,
                area = managedItemName.area
            )
        }

        val parsedMetaAreas = parseMetaItemName(parsedMetaNames.firstOrNull())?.itemAreas.orEmpty()
        return parsedVisibleItems.map { item ->
            item.copy(
                area = item.area ?: parsedMetaAreas[item.id]
            )
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

    suspend fun loadAvailableLists(timeoutMillis: Long = 3_000L): List<ShoppingList>? {
        if (!awaitReady(timeoutMillis)) {
            return null
        }

        val response = client.request(
            type = "get_states",
            timeoutMillis = timeoutMillis
        ) ?: return null

        if (!response.optBoolean("success")) {
            return null
        }

        val states = response.optJSONArray("result") ?: return null
        return parseTodoListsFromStates(states)
    }

    fun addItem(name: String, area: ShoppingArea?) {
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
                area = area,
                previousItemId = _items.value.lastOrNull { !it.complete }?.id,
                lastSentAtMillis = null
            )
            _items.value = _items.value + ShoppingItem(
                id = tempId,
                name = trimmed,
                complete = false,
                area = area
            )
            syncPendingMetaLocked(_items.value)
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
            productHistoryRecorder.recordProductUse(trimmed, area)
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
                    syncPendingMetaLocked(_items.value)
                    persistPendingChangesLocked()
                }
            }
            return
        }

        enqueueOrSendUpdate(
            itemId = item.id,
            name = item.name,
            complete = newStatus,
            area = item.area
        )
    }

    fun updateItem(item: ShoppingItem, newName: String, area: ShoppingArea?) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return
        }
        val nameChanged = trimmed != item.name
        val areaChanged = area != item.area

        updateLocalItem(item.id) { current ->
            current.copy(
                name = trimmed,
                area = area
            )
        }
        if (nameChanged) {
            scope.launch {
                productHistoryRecorder.recordProductUse(trimmed, area)
            }
        }

        if (item.id.startsWith(LOCAL_ID_PREFIX)) {
            synchronized(lock) {
                pendingLocalAdds[item.id]?.let { add ->
                    pendingLocalAdds[item.id] = add.copy(name = trimmed, area = area)
                    syncPendingMetaLocked(_items.value)
                    persistPendingChangesLocked()
                }
            }
            return
        }

        if (nameChanged) {
            enqueueOrSendUpdate(
                itemId = item.id,
                name = trimmed,
                complete = item.complete,
                area = area
            )
        } else if (areaChanged) {
            enqueueOrSendUpdate(
                itemId = item.id,
                name = trimmed,
                complete = item.complete,
                area = area
            )
        }
    }

    fun moveItem(itemId: String, previousItemId: String?, area: ShoppingArea? = null) {
        applyLocalMove(itemId, previousItemId)

        val currentItem = _items.value.firstOrNull { it.id == itemId }
        if (currentItem != null && area != currentItem.area) {
            updateLocalItem(itemId) { current ->
                current.copy(
                    area = area
                )
            }
        }

        synchronized(lock) {
            if (itemId.startsWith(LOCAL_ID_PREFIX)) {
                pendingLocalAdds[itemId]?.let { add ->
                    pendingLocalAdds[itemId] = add.copy(
                        area = area ?: add.area,
                        previousItemId = previousItemId
                    )
                }
            } else if (currentItem != null && area != currentItem.area) {
                pendingActions.removeAll { action ->
                    action is PendingAction.Update && action.itemId == itemId
                }
                pendingActions += PendingAction.Update(
                    itemId = itemId,
                    name = currentItem.name,
                    complete = currentItem.complete,
                    area = area,
                    lastSentAtMillis = null
                )
            }
            pendingMove = PendingMove(
                itemId = itemId,
                previousItemId = previousItemId,
                lastSentAtMillis = null
            )
            syncPendingMetaLocked(_items.value)
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

        synchronized(lock) {
            syncPendingMetaLocked(_items.value)
            persistPendingChangesLocked()
        }

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
                loadItems()
            }
            return
        }

        _isOffline.value = false
        _isConnecting.value = true
        if (_items.value.isEmpty()) {
            _loaded.value = false
        } else {
            _loaded.value = true
        }
        client.ensureConnected()
    }

    fun setReconnectAllowed(allowed: Boolean) {
        client.setReconnectAllowed(allowed)
    }

    private suspend fun awaitReady(timeoutMillis: Long): Boolean {
        if (client.isReady()) {
            return true
        }

        client.ensureConnected()
        val deadline = currentTimeMillis() + timeoutMillis
        while (currentTimeMillis() < deadline) {
            if (client.isReady()) {
                return true
            }
            delay(100)
        }

        return client.isReady()
    }

    private fun enqueueOrSendUpdate(
        itemId: String,
        name: String,
        complete: Boolean,
        area: ShoppingArea?
    ) {
        val sentAtMillis = if (client.isReady() && sendUpdateItem(itemId, name, complete, area)) {
            currentTimeMillis()
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
                area = area,
                lastSentAtMillis = sentAtMillis
            )
            syncPendingMetaLocked(_items.value)
            persistPendingChangesLocked()
        }

        if (client.isReady()) {
            scope.launch {
                flushPendingChanges()
            }
        } else if (sentAtMillis == null) {
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
            currentTimeMillis()
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
            syncPendingMetaLocked(_items.value)
            persistPendingChangesLocked()
        }

        if (client.isReady()) {
            scope.launch {
                flushPendingChanges()
            }
        } else if (sentAtMillis == null) {
            _isOffline.value = true
            _isConnecting.value = false
            client.ensureConnected()
        }
    }

    private suspend fun flushPendingChanges() {
        if (!client.isReady()) {
            return
        }

        if (!hasLoadedRemoteItems) {
            return
        }

        if (syncInProgress) {
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
            val now = currentTimeMillis()

            localAdds
                .filter { shouldSendPending(it.lastSentAtMillis, now) }
                .forEach { add ->
                    locallyAddedItemNames.add(add.name.trim())
                    if (sendAddItem(add.name, add.area)) {
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
                                    complete = action.complete,
                                    area = action.area
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
                    (moveToSend.previousItemId == null || resolveRemoteId(moveToSend.previousItemId) != null)
            val moveSent =
                if (shouldSendPendingMove) {
                    sendPendingMove(moveToSend!!)
                } else {
                    false
                }

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
                                complete = action.complete,
                                area = action.area
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

    private fun applyPendingMove(items: List<ShoppingItem>): List<ShoppingItem> {
        val move = pendingMove ?: return items
        val fromIndex = items.indexOfFirst { it.id == move.itemId }
        if (fromIndex < 0) return items

        val mutableItems = items.toMutableList()
        val movedItem = mutableItems.removeAt(fromIndex)
        val targetIndex = if (move.previousItemId == null) {
            0
        } else {
            mutableItems.indexOfFirst { it.id == move.previousItemId }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: mutableItems.size
        }
        mutableItems.add(targetIndex.coerceIn(0, mutableItems.size), movedItem)
        return mutableItems
    }

    private fun reconcileLegacyMetaItems(metaItemIds: List<String>) {
        metaItemIds.forEach { duplicateId ->
            pendingActions.removeAll { action ->
                action is PendingAction.Update && action.itemId == duplicateId
            }
            if (pendingActions.none { action -> action is PendingAction.Remove && action.itemId == duplicateId }) {
                pendingActions += PendingAction.Remove(
                    itemId = duplicateId,
                    lastSentAtMillis = null
                )
            }
        }
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
                            remote.complete == action.complete &&
                            remote.area == action.area
                    }
                }
            }
        }
    }

    private fun syncPendingMetaLocked(items: List<ShoppingItem>) {
        // Legacy meta-item sync is disabled. Area metadata is stored in the item name suffix.
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

    private fun sendAddItem(name: String, area: ShoppingArea?): Boolean {
        val serviceData = JSONObject()
            .put("item", managedNameForService(name, area))

        return client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "add_item")
                .put("target", JSONObject()
                    .put("entity_id", todoEntity)
                )
                .put("return_response", false)
                .put("service_data", serviceData)
        )
    }

    private fun sendUpdateItem(itemId: String, name: String, complete: Boolean, area: ShoppingArea?): Boolean {
        val serviceData = JSONObject()
            .put("item", itemId)
            .put("rename", managedNameForService(name, area))
            .put("status", if (complete) "completed" else "needs_action")

        return client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "update_item")
                .put("target", JSONObject()
                    .put("entity_id", todoEntity)
                )
                .put("service_data", serviceData)
                .put("return_response", false)
        )
    }

    private fun sendPendingMove(move: PendingMove): Boolean {
        val resolvedItemId = resolveRemoteId(move.itemId) ?: return false
        val resolvedPreviousItemId = move.previousItemId?.let { resolveRemoteId(it) } ?: move.previousItemId
        return sendMoveItem(
            itemId = resolvedItemId,
            previousItemId = resolvedPreviousItemId
        )
    }

    private fun sendMoveItem(itemId: String, previousItemId: String?): Boolean {
        val payload = JSONObject()
            .put("entity_id", todoEntity)
            .put("uid", itemId)

        if (previousItemId != null) {
            payload.put("previous_uid", previousItemId)
        }

        return client.send(
            type = "todo/item/move",
            payload = payload
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

    private fun visiblePendingLocalAddsLocked(): List<ShoppingItem> {
        return pendingLocalAdds.values.map { it.toShoppingItem() }
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

    private fun handleFailedCommandResult(json: JSONObject): Boolean {
        val error = json.optJSONObject("error") ?: return false
        val errorCode = error.optString("code")
        if (errorCode != "service_validation_error") {
            return false
        }

        val translationKey = error.optString("translation_key")
        if (translationKey != "item_not_found") {
            return false
        }

        val missingItemId = error
            .optJSONObject("translation_placeholders")
            ?.optNullableString("item")
            ?.takeIf { it.isNotBlank() }
            ?: return false

        Debug.log("REPOSITORY: dropping stale pending changes for missing item=$missingItemId")
        dropPendingChangesForMissingItem(missingItemId)
        return true
    }

    private fun dropPendingChangesForMissingItem(itemId: String) {
        synchronized(lock) {
            pendingActions.removeAll { action ->
                when (action) {
                    is PendingAction.Remove -> action.itemId == itemId
                    is PendingAction.Update -> action.itemId == itemId
                }
            }

            pendingMove = pendingMove?.takeUnless { move ->
                move.itemId == itemId || move.previousItemId == itemId
            }

            _items.value = _items.value.filterNot { it.id == itemId }
            persistPendingChangesLocked()
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
    }

    private fun restorePendingChanges() {
        val raw = pendingSyncStore.readPendingChanges() ?: return

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
                        area = ShoppingArea.fromKey(add.optNullableString("area")),
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
                            area = ShoppingArea.fromKey(action.optNullableString("area")),
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
                _items.value = visiblePendingLocalAddsLocked()
                _loaded.value = true
            }
            Debug.log("REPOSITORY: restored pending shopping-list changes")
        } catch (e: Exception) {
            Debug.log("REPOSITORY: failed to restore pending changes: ${e.message}")
            pendingSyncStore.clearPendingChanges()
        }
    }

    private fun persistPendingChangesLocked() {
        if (pendingLocalAdds.isEmpty() && pendingActions.isEmpty() && pendingMove == null) {
            pendingSyncStore.clearPendingChanges()
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
                    .put("area", add.area?.key ?: JSONObject.NULL)
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
                        .put("area", action.area?.key ?: JSONObject.NULL)
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

        pendingSyncStore.writePendingChanges(root.toString())
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun nextTempId(): String = "$LOCAL_ID_PREFIX${tempIdCounter.getAndIncrement()}"

    private fun managedNameForService(name: String, area: ShoppingArea?): String {
        return encodeManagedItemName(name, area)
    }

    private fun PendingLocalAdd.toShoppingItem(): ShoppingItem = ShoppingItem(
        id = tempId,
        name = name,
        complete = complete,
        area = area
    )

    companion object {
        private const val LOCAL_ID_PREFIX = "local:"
        private const val PENDING_RETRY_AFTER_MILLIS = 10_000L
        private const val PENDING_MOVE_DEBOUNCE_MILLIS = 750L
        private const val PENDING_MOVE_RETRY_AFTER_MILLIS = 30_000L
    }
}
