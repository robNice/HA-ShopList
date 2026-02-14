package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.data.dto.AddItemRequest
import de.robnice.homeasssistant_shoppinglist.data.dto.UpdateItemRequest
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem

class HaRepository(
    private val api: HaApi,
    private val token: String
) {

    suspend fun getItems() =
        api.getItems("Bearer $token")

    suspend fun addItem(name: String) {
        api.addItem(
            "Bearer $token",
            AddItemRequest(name)
        )
    }

    suspend fun toggleItem(item: ShoppingItem) {
        api.updateItem(
            "Bearer $token",
            item.id,
            UpdateItemRequest(!item.complete)
        )
    }

    suspend fun clearCompleted() {
        api.clearCompleted("Bearer $token")
    }

    suspend fun updateItemComplete(id: String, complete: Boolean) {
        api.updateItem(
            token = "Bearer $token",
            id = id,
            body = UpdateItemRequest(complete = complete)
        )
    }

}
