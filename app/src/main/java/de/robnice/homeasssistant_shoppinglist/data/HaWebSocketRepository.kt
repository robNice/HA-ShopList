package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.data.websocket.HaWebSocketClient
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
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
    val items = _items.asStateFlow()



    init {

        client.connect()

        scope.launch {
            client.ready.collect {
                println("WS READY (RECONNECTED)")

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
                    println("WS EVENT: $json")

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
