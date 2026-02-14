package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.model.ShoppingList
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface HaApi {

    @GET("api/shopping_list/lists")
    suspend fun getLists(
        @Header("Authorization") token: String
    ): List<ShoppingList>

    @GET("api/shopping_list/items")
    suspend fun getItems(
        @Header("Authorization") token: String,
        @Query("list_id") listId: String
    ): List<ShoppingItem>
}
