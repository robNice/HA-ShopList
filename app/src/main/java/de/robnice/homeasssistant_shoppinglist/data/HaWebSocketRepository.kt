package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.data.websocket.HaWebSocketClient
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import de.robnice.homeasssistant_shoppinglist.util.Debug
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

class HaWebSocketRepository(
    baseUrl: String,
    token: String
) {

    private val client = HaWebSocketClient(baseUrl, token)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val _authFailed = MutableStateFlow(false)
    private val _connectionErrors = MutableStateFlow(false)
    private val _newItems = MutableSharedFlow<ShoppingItem>( replay = 1 )

    private val locallyAddedItemNames = mutableSetOf<String>()

    val authFailed = _authFailed.asStateFlow()
    val connectionErrors = _connectionErrors.asStateFlow()
    val items = _items.asStateFlow()

    val newItems = _newItems.asSharedFlow()

    init {

        client.connect()

        scope.launch {
            client.authFailed.collect {
                Debug.log("REPOSITORY: AUTH FAILED")
                _authFailed.value = true
            }
        }
        scope.launch {
            client.connectionErrors.collect {
                Debug.log("REPOSITORY: AUTH FAILED")
                _connectionErrors.value = true
            }
        }

        scope.launch {
            client.ready.collect {
                _authFailed.value = false
                _connectionErrors.value = false
                Debug.log("WS READY (RECONNECTED)")
                client.send(
                    type = "todo/item/subscribe",
                    payload = JSONObject()
                        .put("entity_id", "todo.einkaufsliste")
                )
                loadItems()
            }
        }

        scope.launch {
            client.events.collect { json ->
                try {
                    Debug.log("WS EVENT: $json")

                    when (json.optString("type")) {

                        "result" -> {
                            if (!json.optBoolean("success")) return@collect

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

    private fun parseItemsFromArray(array: org.json.JSONArray) {
        val parsed = mutableListOf<ShoppingItem>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            parsed.add(
                ShoppingItem(
                    id = item.getString("uid"),
                    name = item.getString("summary"),
                    complete = item.getString("status") == "completed"
                )
            )
        }

        val previousIds = _items.value.map { it.id }.toSet()
        val newIds = parsed.map { it.id }.toSet()
        val addedIds = newIds - previousIds

        if (_items.value.isNotEmpty()) {
            parsed
                .filter { it.id in addedIds }
                .forEach {

                    if (locallyAddedItemNames.contains(it.name.trim())) {
                        locallyAddedItemNames.remove(it.name.trim())
                        return@forEach
                    }

                    _newItems.tryEmit(it)
                }
        }

        _items.value = parsed
    }

    fun loadItems() {
        client.send(
            type = "todo/item/list",
            payload = JSONObject()
                .put("entity_id", "todo.einkaufsliste")
        )
    }

    fun addItem(name: String) {

        locallyAddedItemNames.add(name.trim())

        client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "add_item")
                .put("target", JSONObject()
                    .put("entity_id", "todo.einkaufsliste")
                )
                .put("return_response", false)
                .put("service_data", JSONObject()
                    .put("item", name)
                )
        )
    }


    fun toggleItem(item: ShoppingItem) {
        val newStatus = if (item.complete) {
            "needs_action"
        } else {
            "completed"
        }

        client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "update_item")
                .put("target", JSONObject()
                    .put("entity_id", "todo.einkaufsliste")
                )
                .put("service_data", JSONObject()
                    .put("item", item.id)
                    .put("rename", item.name)
                    .put("status", newStatus)
                )
                .put("return_response", false)
        )
    }

    fun renameItem(item: ShoppingItem, newName: String) {
        client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "update_item")
                .put("target", JSONObject()
                    .put("entity_id", "todo.einkaufsliste")
                )
                .put("service_data", JSONObject()
                    .put("item", item.id)
                    .put("rename", newName)
                    .put(
                        "status",
                        if (item.complete) "completed" else "needs_action"
                    )
                )
                .put("return_response", false)
        )
    }

    fun moveItem(itemId: String, previousItemId: String?) {
        client.send(
            type = "todo/item/move",
            payload = JSONObject()
                .put("entity_id", "todo.einkaufsliste")
                .put("uid", itemId)
                .put("previous_uid", previousItemId ?: JSONObject.NULL)
        )
    }

    fun clearCompleted() {
        val completedIds = _items.value
            .filter { it.complete }
            .map { it.id }

        if (completedIds.isEmpty()) return

        val jsonArray = org.json.JSONArray()
        completedIds.forEach { jsonArray.put(it) }

        client.send(
            type = "call_service",
            payload = JSONObject()
                .put("domain", "todo")
                .put("service", "remove_item")
                .put("target", JSONObject()
                    .put("entity_id", "todo.einkaufsliste")
                )
                .put("service_data", JSONObject()
                    .put("item", jsonArray)
                )
        )
    }

    fun disconnect() {
        client.disconnect()
    }

    fun ensureConnected() {
        client.ensureConnected()
    }


}
