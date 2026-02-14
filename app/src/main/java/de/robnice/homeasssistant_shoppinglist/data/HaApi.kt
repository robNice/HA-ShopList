package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.data.dto.UpdateItemRequest
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import de.robnice.homeasssistant_shoppinglist.data.dto.AddItemRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface HaApi {

    @GET("api/shopping_list")
    suspend fun getItems(
        @Header("Authorization") token: String
    ): List<ShoppingItem>

    @POST("api/shopping_list/item")
    suspend fun addItem(
        @Header("Authorization") token: String,
        @Body body: AddItemRequest
    )

    @POST("api/shopping_list/item/{id}")
    suspend fun updateItem(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateItemRequest
    )

    @POST("api/shopping_list/clear_completed")
    suspend fun clearCompleted(
        @Header("Authorization") token: String
    )

}

