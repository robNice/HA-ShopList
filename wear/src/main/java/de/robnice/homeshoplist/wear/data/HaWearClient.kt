package de.robnice.homeshoplist.wear.data

import de.robnice.homeshoplist.wear.model.WearShoppingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

// Matches the phone app's AREA_NAME_SEPARATOR constant
private const val AREA_NAME_SEPARATOR = "\u0000 | "

class HaWearClient(
    private val baseUrl: String,
    private val token: String,
    private val entityId: String
) {
    private val client = buildHttpClient()

    /** Outcome of a change sent to Home Assistant. */
    enum class WriteResult {
        /** Applied. */
        OK,
        /** Rejected for good (e.g. the item was already removed elsewhere): do not retry. */
        GONE,
        /** Not reachable or temporary error: retry later. */
        FAILED
    }

    suspend fun fetchItems(): List<WearShoppingItem> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("entity_id", entityId)
            .put("status", JSONArray().put("needs_action").put("completed"))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val response = client.newCall(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/services/todo/get_items?return_response")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()
        ).execute()

        // Throw instead of returning an empty list, so a failed load never replaces the cached list
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val responseBody = it.body?.string() ?: throw IOException("Empty response")
            parseItems(responseBody)
        }
    }

    suspend fun toggleItem(id: String, complete: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("entity_id", entityId)
            .put("item", id)
            .put("status", if (complete) "completed" else "needs_action")
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        execute(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/services/todo/update_item")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()
        )
    }

    /** Adds an item; the category is stored like the phone app does (name + separator + key). */
    suspend fun addItem(name: String, areaKey: String?): WriteResult = withContext(Dispatchers.IO) {
        val summary = if (areaKey.isNullOrBlank()) name else name + AREA_NAME_SEPARATOR + areaKey
        val body = JSONObject()
            .put("entity_id", entityId)
            .put("item", summary)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        execute(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/services/todo/add_item")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()
        )
    }

    suspend fun removeItem(id: String): WriteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("entity_id", entityId)
            .put("item", id)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        execute(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/services/todo/remove_item")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .post(body)
                .build()
        )
    }

    private fun execute(request: Request): WriteResult = try {
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> WriteResult.OK
                // Home Assistant answers 400 if the item no longer exists. Auth problems,
                // timeouts and rate limits are temporary from the queue's point of view.
                response.code in 400..499 && response.code !in RETRY_CODES -> WriteResult.GONE
                else -> WriteResult.FAILED
            }
        }
    } catch (e: IOException) {
        WriteResult.FAILED
    }

    private fun parseItems(json: String): List<WearShoppingItem> {
        val root = JSONObject(json)
        val serviceResponse = root.optJSONObject("service_response") ?: return emptyList()
        val entityResponse = serviceResponse.optJSONObject(entityId) ?: return emptyList()
        val items = entityResponse.optJSONArray("items") ?: return emptyList()

        val result = mutableListOf<WearShoppingItem>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val uid = item.optString("uid").takeIf { it.isNotBlank() } ?: continue
            val summary = item.optString("summary").takeIf { it.isNotBlank() } ?: continue
            if (summary.startsWith(".__ha_shoplist_meta__:")) continue
            val name = stripAreaSuffix(summary)
            val areaKey = parseAreaKey(summary)
            val complete = item.optString("status") == "completed"
            result += WearShoppingItem(id = uid, name = name, complete = complete, areaKey = areaKey)
        }
        return result
    }

    private fun stripAreaSuffix(summary: String): String {
        // Everything from the first NUL on is (a possibly mangled) area suffix, see phone app.
        val beforeNul = summary.substringBefore('\u0000').trim()
        return beforeNul.ifEmpty { summary.replace("\u0000", "").trim() }
    }

    private fun parseAreaKey(summary: String): String? {
        val idx = summary.lastIndexOf(AREA_NAME_SEPARATOR)
        if (idx <= 0) return null
        return summary.substring(idx + AREA_NAME_SEPARATOR.length).trim().takeIf { it.isNotBlank() }
    }

    private fun buildHttpClient(): OkHttpClient {
        // Debug builds may accept self-signed certificates; release builds never do
        // (see src/debug and src/release).
        return baseHttpClientBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private val RETRY_CODES = setOf(401, 403, 408, 429)
