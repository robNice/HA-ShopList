package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.data.dto.UpdateItemRequest
import de.robnice.homeshoplist.data.dto.TemplateRequest
import de.robnice.homeshoplist.data.dto.TodoGetItemsRequest
import de.robnice.homeshoplist.model.ShoppingItem
import de.robnice.homeshoplist.data.dto.AddItemRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
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

    @POST("api/services/todo/get_items?return_response")
    suspend fun getTodoItemsRaw(
        @Header("Authorization") token: String,
        @Body body: TodoGetItemsRequest
    ): ResponseBody

    @POST("api/template")
    suspend fun renderTemplate(
        @Header("Authorization") token: String
        ,
        @Body body: TemplateRequest
    ): ResponseBody

}

