package de.robnice.homeshoplist

import de.robnice.homeshoplist.data.HaRealtimeClient
import de.robnice.homeshoplist.data.HaWebSocketRepository
import de.robnice.homeshoplist.data.PendingSyncStore
import de.robnice.homeshoplist.data.ProductHistoryRecorder
import de.robnice.homeshoplist.data.ShoppingNotifier
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.model.encodeManagedItemName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketRepositoryTest {

    @Test
    fun `toggleing pending local item updates optimistic state without websocket send`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        repository.addItem("Milk", ShoppingArea.DAIRY_EGGS)
        advanceUntilIdle()

        val addedItem = repository.items.value.single()
        repository.toggleItem(addedItem)
        advanceUntilIdle()

        assertEquals(
            ShoppingItem(
                id = addedItem.id,
                name = "Milk",
                complete = true,
                description = addedItem.description,
                area = ShoppingArea.DAIRY_EGGS
            ),
            repository.items.value.single()
        )
        assertTrue(repository.isOffline.value)
        assertTrue(client.sentMessages.isEmpty())
        assertEquals(1, client.ensureConnectedCalls)
    }

    @Test
    fun `ensureConnected marks repository as connecting when cached items exist`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        repository.addItem("Milk", null)
        advanceUntilIdle()

        assertTrue(repository.isOffline.value)
        assertFalse(repository.isConnecting.value)

        repository.ensureConnected()
        advanceUntilIdle()

        assertTrue(repository.isConnecting.value)
        assertEquals(2, client.ensureConnectedCalls)
        assertEquals("Milk", repository.items.value.single().name)
    }

    @Test
    fun `ensureConnected clears stale offline flag while reconnecting`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        setStateFlow(repository, "_isOffline", true)
        setStateFlow(repository, "_isConnecting", false)

        repository.ensureConnected()
        advanceUntilIdle()

        assertFalse(repository.isOffline.value)
        assertTrue(repository.isConnecting.value)
        assertEquals(1, client.ensureConnectedCalls)
    }

    @Test
    fun `add item sends managed name suffix in add_item request when repository is ready`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        client.readyState = true
        client.clearSentMessages()

        invokePrivate(
            target = repository,
            methodName = "sendAddItem",
            parameterTypes = arrayOf(String::class.java, ShoppingArea::class.java),
            args = arrayOf("Milk", ShoppingArea.DAIRY_EGGS)
        )

        val addMessage = client.findServiceMessage("add_item")
        assertNotNull(addMessage)
        assertEquals("call_service", addMessage?.type)
        assertEquals("todo.shopping", addMessage?.payload?.targetEntityId())
        assertEquals(
            managedItemName("Milk", ShoppingArea.DAIRY_EGGS),
            addMessage?.payload?.serviceData()?.optString("item")
        )
        assertFalse(addMessage?.payload?.serviceData()?.has("description") == true)
    }

    @Test
    fun `update item clears managed suffix when clearing area`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        client.readyState = true
        client.clearSentMessages()

        invokePrivate(
            target = repository,
            methodName = "sendUpdateItem",
            parameterTypes = arrayOf(
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                ShoppingArea::class.java
            ),
            args = arrayOf("uid-1", "Milk", false, null)
        )

        val updateMessage = client.findServiceMessage("update_item")
        assertNotNull(updateMessage)
        assertEquals("uid-1", updateMessage?.payload?.serviceData()?.optString("item"))
        assertEquals("Milk", updateMessage?.payload?.serviceData()?.optString("rename"))
        assertFalse(updateMessage?.payload?.serviceData()?.has("description") == true)
        assertEquals("needs_action", updateMessage?.payload?.serviceData()?.optString("status"))
    }

    @Test
    fun `editing only area on remote item enqueues update request`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        client.readyState = true
        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false
                )
            )
        )
        client.clearSentMessages()

        repository.updateItem(
            item = repository.items.value.single(),
            newName = "Milk",
            area = ShoppingArea.DAIRY_EGGS
        )
        advanceUntilIdle()

        val updateMessage = client.findServiceMessage("update_item")
        assertNotNull(updateMessage)
        assertEquals("uid-1", updateMessage?.payload?.serviceData()?.optString("item"))
        assertEquals(
            managedItemName("Milk", ShoppingArea.DAIRY_EGGS),
            updateMessage?.payload?.serviceData()?.optString("rename")
        )
        assertFalse(updateMessage?.payload?.serviceData()?.has("description") == true)
    }

    @Test
    fun `managed name from server summary is decoded into visible name and area`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        invokePrivate(
            target = repository,
            methodName = "parseItemsFromResult",
            parameterTypes = arrayOf(JSONObject::class.java),
            args = arrayOf(
                JSONObject().put(
                    "items",
                    JSONArray().put(
                        JSONObject()
                            .put("uid", "uid-1")
                            .put("summary", managedItemName("Milk", ShoppingArea.DAIRY_EGGS))
                            .put("status", "needs_action")
                            .put("description", JSONObject.NULL)
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false,
                    description = null,
                    area = ShoppingArea.DAIRY_EGGS
                )
            ),
            repository.items.value
        )
    }

    @Test
    fun `managed name uses last separator when visible name already contains pipe`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        invokePrivate(
            target = repository,
            methodName = "parseItemsFromResult",
            parameterTypes = arrayOf(JSONObject::class.java),
            args = arrayOf(
                JSONObject().put(
                    "items",
                    JSONArray().put(
                        JSONObject()
                            .put("uid", "uid-1")
                            .put("summary", managedItemName("Oil | Vinegar", ShoppingArea.DAIRY_EGGS))
                            .put("status", "needs_action")
                            .put("description", JSONObject.NULL)
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Oil | Vinegar", repository.items.value.single().name)
        assertEquals(ShoppingArea.DAIRY_EGGS, repository.items.value.single().area)
    }

    @Test
    fun `matched local add with area does not enqueue redundant update`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        repository.addItem("Salat", ShoppingArea.PRODUCE)
        advanceUntilIdle()

        invokePrivate(
            target = repository,
            methodName = "reconcileLocalAdds",
            parameterTypes = arrayOf(List::class.java, Set::class.java),
            args = arrayOf(
                listOf(
                    ShoppingItem(
                        id = "uid-1",
                        name = "Salat",
                        complete = false,
                        area = ShoppingArea.PRODUCE
                    )
                ),
                emptySet<String>()
            )
        )

        assertTrue(pendingActions(repository).isEmpty())
    }

    @Test
    fun `flush pending update does not trigger redundant list reload`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope)
        advanceUntilIdle()

        client.readyState = true
        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false
                )
            )
        )
        setPrivateField(repository, "hasLoadedRemoteItems", true)
        client.clearSentMessages()

        repository.toggleItem(repository.items.value.single())
        advanceUntilIdle()

        assertNotNull(client.findServiceMessage("update_item"))
        assertTrue(client.sentMessages.none { it.type == "todo/item/list" })
    }

    @Test
    fun `offline toggle of remote item persists pending update action`() = runTest {
        val client = FakeRealtimeClient()
        val store = InMemoryPendingSyncStore()
        val repository = HaWebSocketRepository(
            client = client,
            pendingSyncStore = store,
            productHistoryRecorder = NoOpProductHistoryRecorder(),
            notifier = NoOpShoppingNotifier(),
            todoEntity = "todo.shopping",
            scope = backgroundScope
        )
        advanceUntilIdle()

        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false
                )
            )
        )
        client.clearSentMessages()

        repository.toggleItem(repository.items.value.single())
        advanceUntilIdle()

        assertTrue(repository.isOffline.value)
        assertTrue(client.findServiceMessage("update_item") == null)
        assertEquals(1, client.ensureConnectedCalls)
        assertNotNull(store.raw)
        val storedRoot = JSONObject(store.raw!!)
        val actions = storedRoot.getJSONArray("actions")
        assertEquals(1, actions.length())
        assertEquals("update", actions.getJSONObject(0).getString("type"))
        assertEquals("uid-1", actions.getJSONObject(0).getString("itemId"))
        assertTrue(actions.getJSONObject(0).getBoolean("complete"))
    }

    @Test
    fun `matching remote state prunes sent pending update`() = runTest {
        val client = FakeRealtimeClient()
        val repository = createRepository(client, backgroundScope, currentTimeMillis = { 1_000L })
        advanceUntilIdle()

        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false
                )
            )
        )
        client.readyState = true

        repository.toggleItem(repository.items.value.single())
        advanceUntilIdle()

        assertEquals(1, pendingActions(repository).size)

        invokePrivate(
            target = repository,
            methodName = "pruneSatisfiedPendingActions",
            parameterTypes = arrayOf(List::class.java),
            args = arrayOf(
                listOf(
                    ShoppingItem(
                        id = "uid-1",
                        name = "Milk",
                        complete = true
                    )
                )
            )
        )

        assertTrue(pendingActions(repository).isEmpty())
    }

    @Test
    fun `clearCompleted removes local completed adds and queues remote remove`() = runTest {
        val client = FakeRealtimeClient()
        val store = InMemoryPendingSyncStore()
        val repository = HaWebSocketRepository(
            client = client,
            pendingSyncStore = store,
            productHistoryRecorder = NoOpProductHistoryRecorder(),
            notifier = NoOpShoppingNotifier(),
            todoEntity = "todo.shopping",
            scope = backgroundScope
        )
        advanceUntilIdle()

        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-remote",
                    name = "Bread",
                    complete = true
                )
            )
        )
        repository.addItem("Milk", ShoppingArea.DAIRY_EGGS)
        advanceUntilIdle()
        repository.toggleItem(repository.items.value.first { it.id.startsWith("local:") })
        advanceUntilIdle()

        repository.clearCompleted()
        advanceUntilIdle()

        assertTrue(repository.items.value.isEmpty())
        val root = JSONObject(store.raw!!)
        assertEquals(0, root.getJSONArray("localAdds").length())
        val actions = root.getJSONArray("actions")
        assertEquals(1, actions.length())
        assertEquals("remove", actions.getJSONObject(0).getString("type"))
        assertEquals("uid-remote", actions.getJSONObject(0).getString("itemId"))
    }

    @Test
    fun `move item persists move target and area update`() = runTest {
        val client = FakeRealtimeClient()
        val store = InMemoryPendingSyncStore()
        val repository = HaWebSocketRepository(
            client = client,
            pendingSyncStore = store,
            productHistoryRecorder = NoOpProductHistoryRecorder(),
            notifier = NoOpShoppingNotifier(),
            todoEntity = "todo.shopping",
            scope = backgroundScope
        )
        advanceUntilIdle()

        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-1",
                    name = "Milk",
                    complete = false
                ),
                ShoppingItem(
                    id = "uid-2",
                    name = "Bread",
                    complete = false
                )
            )
        )

        repository.moveItem(
            itemId = "uid-2",
            previousItemId = null,
            area = ShoppingArea.BAKERY
        )
        advanceUntilIdle()

        val root = JSONObject(store.raw!!)
        val move = root.getJSONObject("move")
        assertEquals("uid-2", move.getString("itemId"))
        assertTrue(move.isNull("previousItemId"))

        val actions = root.getJSONArray("actions")
        assertEquals(1, actions.length())
        assertEquals("update", actions.getJSONObject(0).getString("type"))
        assertEquals("uid-2", actions.getJSONObject(0).getString("itemId"))
        assertEquals("bakery", actions.getJSONObject(0).getString("area"))
        assertEquals("uid-2", repository.items.value.first().id)
        assertEquals(ShoppingArea.BAKERY, repository.items.value.first().area)
    }

    @Test
    fun `item_not_found failure drops stale pending action instead of retrying forever`() = runTest {
        val client = FakeRealtimeClient()
        val store = InMemoryPendingSyncStore()
        val repository = HaWebSocketRepository(
            client = client,
            pendingSyncStore = store,
            productHistoryRecorder = NoOpProductHistoryRecorder(),
            notifier = NoOpShoppingNotifier(),
            todoEntity = "todo.shopping",
            scope = backgroundScope
        )
        advanceUntilIdle()

        seedItems(
            repository,
            listOf(
                ShoppingItem(
                    id = "uid-missing",
                    name = "Milk",
                    complete = false
                )
            )
        )
        client.readyState = true

        repository.toggleItem(repository.items.value.single())
        advanceUntilIdle()
        assertEquals(1, pendingActions(repository).size)

        val handled = invokePrivate(
            target = repository,
            methodName = "handleFailedCommandResult",
            parameterTypes = arrayOf(JSONObject::class.java),
            args = arrayOf(
                JSONObject()
                    .put("type", "result")
                    .put("success", false)
                    .put(
                        "error",
                        JSONObject()
                            .put("code", "service_validation_error")
                            .put("translation_key", "item_not_found")
                            .put(
                                "translation_placeholders",
                                JSONObject().put("item", "uid-missing")
                            )
                    )
            )
        )
        assertEquals(true, handled)

        assertTrue(pendingActions(repository).isEmpty())
        assertTrue(repository.items.value.none { it.id == "uid-missing" })
        val root = JSONObject(store.raw ?: """{"actions":[]}""")
        assertEquals(0, root.optJSONArray("actions")?.length() ?: 0)
    }

    private fun createRepository(
        client: FakeRealtimeClient,
        scope: CoroutineScope,
        currentTimeMillis: () -> Long = System::currentTimeMillis
    ): HaWebSocketRepository {
        return HaWebSocketRepository(
            client = client,
            pendingSyncStore = InMemoryPendingSyncStore(),
            productHistoryRecorder = NoOpProductHistoryRecorder(),
            notifier = NoOpShoppingNotifier(),
            todoEntity = "todo.shopping",
            scope = scope,
            currentTimeMillis = currentTimeMillis
        )
    }

    private class FakeRealtimeClient : HaRealtimeClient {
        private val _events = MutableSharedFlow<JSONObject>(replay = 1, extraBufferCapacity = 16)
        private val _ready = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)
        private val _authFailed = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)
        private val _connectionErrors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
        private val _disconnected = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)

        override val events: SharedFlow<JSONObject> = _events
        override val ready: SharedFlow<Unit> = _ready
        override val authFailed: SharedFlow<Unit> = _authFailed
        override val connectionErrors: SharedFlow<String> = _connectionErrors
        override val disconnected: SharedFlow<Unit> = _disconnected

        var readyState = false
        var ensureConnectedCalls = 0
        var connectCalls = 0
        val sentMessages = mutableListOf<SentMessage>()

        override fun connect() {
            connectCalls += 1
        }

        override fun isReady(): Boolean = readyState

        override fun send(type: String, payload: JSONObject): Boolean {
            if (!readyState) {
                return false
            }

            sentMessages += SentMessage(
                type = type,
                payload = JSONObject(payload.toString())
            )
            return true
        }

        override fun ensureConnected() {
            ensureConnectedCalls += 1
        }

        override fun setReconnectAllowed(allowed: Boolean) {
        }

        override fun disconnect() {
            readyState = false
        }

        fun emitReady() {
            readyState = true
            _ready.tryEmit(Unit)
        }

        fun emitDisconnected() {
            _disconnected.tryEmit(Unit)
        }

        fun emitResult(vararg items: JSONObject) {
            _events.tryEmit(
                JSONObject()
                    .put("type", "result")
                    .put("success", true)
                    .put(
                        "result",
                        JSONObject().put("items", jsonArrayOf(*items))
                    )
            )
        }

        fun emitFailedResult(
            code: String,
            translationKey: String,
            itemId: String
        ) {
            _events.tryEmit(
                JSONObject()
                    .put("type", "result")
                    .put("success", false)
                    .put(
                        "error",
                        JSONObject()
                            .put("code", code)
                            .put("translation_key", translationKey)
                            .put(
                                "translation_placeholders",
                                JSONObject().put("item", itemId)
                            )
                    )
            )
        }

        fun clearSentMessages() {
            sentMessages.clear()
        }

        fun findServiceMessage(service: String): SentMessage? {
            return sentMessages.lastOrNull { message ->
                message.type == "call_service" &&
                    message.payload.optString("service") == service
            }
        }

        private fun jsonArrayOf(vararg items: JSONObject): JSONArray {
            val array = JSONArray()
            items.forEach { item -> array.put(item) }
            return array
        }
    }

    private data class SentMessage(
        val type: String,
        val payload: JSONObject
    )

    private class InMemoryPendingSyncStore : PendingSyncStore {
        var raw: String? = null

        override fun readPendingChanges(): String? = raw

        override fun writePendingChanges(raw: String) {
            this.raw = raw
        }

        override fun clearPendingChanges() {
            raw = null
        }
    }

    private class NoOpProductHistoryRecorder : ProductHistoryRecorder {
        override suspend fun recordProductUse(name: String, area: ShoppingArea?) {
        }

        override suspend fun rememberProduct(name: String, area: ShoppingArea?) {
        }
    }

    private class NoOpShoppingNotifier : ShoppingNotifier {
        override fun showNewItemNotification(item: ShoppingItem) {
        }
    }

    private fun managedItemName(name: String, area: ShoppingArea): String {
        return encodeManagedItemName(name, area)
    }

    private fun JSONObject.serviceData(): JSONObject {
        return getJSONObject("service_data")
    }

    private fun JSONObject.targetEntityId(): String? {
        return optJSONObject("target")?.optString("entity_id")
    }

    private fun seedItems(
        repository: HaWebSocketRepository,
        items: List<ShoppingItem>
    ) {
        @Suppress("UNCHECKED_CAST")
        val itemsFlow = getPrivateField(repository, "_items") as MutableStateFlow<List<ShoppingItem>>
        itemsFlow.value = items
    }

    private fun <T> setStateFlow(
        repository: HaWebSocketRepository,
        fieldName: String,
        value: T
    ) {
        @Suppress("UNCHECKED_CAST")
        val flow = getPrivateField(repository, fieldName) as MutableStateFlow<T>
        flow.value = value
    }

    private fun getPrivateField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pendingActions(repository: HaWebSocketRepository): List<Any> {
        return getPrivateField(repository, "pendingActions") as List<Any>
    }

    private fun invokePrivate(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>
    ): Any? {
        val method = target.javaClass.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        return method.invoke(target, *args)
    }
}
