package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.data.websocket.HaWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Integration whose lists can be created and deleted from the app (one config entry = one list). */
const val LOCAL_TODO_DOMAIN = "local_todo"

/** Entity registry data of a todo list. */
data class TodoListRegistryInfo(
    val entityId: String,
    val platform: String?,
    val configEntryId: String?
) {
    /**
     * Only lists of "Local To-do" may be deleted: removing their config entry removes exactly this list.
     * For other integrations (Shopping List, Google Tasks, Todoist, ...) it would remove the whole integration.
     */
    val isDeletable: Boolean
        get() = platform == LOCAL_TODO_DOMAIN && !configEntryId.isNullOrBlank()
}

/** Result of the admin check: rights of the token and which lists can be deleted. */
data class HaListAdminInfo(
    val isAdmin: Boolean,
    val registry: Map<String, TodoListRegistryInfo>
)

/** WebSocket queries for the admin check; usable with any connected [HaRealtimeClient]. */
object HaListAdminQueries {

    suspend fun isAdmin(client: HaRealtimeClient, timeoutMillis: Long = 5_000L): Boolean? {
        val response = client.request(type = "auth/current_user", timeoutMillis = timeoutMillis) ?: return null
        if (!response.optBoolean("success")) return null
        return HaListAdminParser.parseIsAdmin(response.optJSONObject("result"))
    }

    suspend fun registryInfo(
        client: HaRealtimeClient,
        entityIds: List<String>,
        timeoutMillis: Long = 5_000L
    ): Map<String, TodoListRegistryInfo> = buildMap {
        entityIds.forEach { entityId ->
            val response = client.request(
                type = "config/entity_registry/get",
                payload = JSONObject().put("entity_id", entityId),
                timeoutMillis = timeoutMillis
            )
            if (response?.optBoolean("success") == true) {
                HaListAdminParser.parseRegistryInfo(entityId, response.optJSONObject("result"))
                    ?.let { put(entityId, it) }
            }
        }
    }

    /**
     * Uses its own short-lived connection, so the check works in the settings even when the
     * shopping screen has closed its connection (notifications off). Null if not reachable.
     */
    suspend fun probe(
        baseUrl: String,
        token: String,
        entityIds: List<String>,
        timeoutMillis: Long = 10_000L
    ): HaListAdminInfo? {
        val client = HaWebSocketClient(baseUrl, token)
        return try {
            client.connect()
            val ready = withTimeoutOrNull(timeoutMillis) {
                while (!client.isReady()) delay(100)
                true
            } ?: return null
            if (!ready) return null
            val admin = isAdmin(client) ?: return null
            HaListAdminInfo(
                isAdmin = admin,
                registry = if (admin) registryInfo(client, entityIds) else emptyMap()
            )
        } finally {
            client.setReconnectAllowed(false)
            client.disconnect()
        }
    }
}

class HaListAdminException(message: String, val httpCode: Int? = null) : Exception(message)

object HaListAdminParser {

    fun parseIsAdmin(result: JSONObject?): Boolean? =
        result?.takeIf { it.has("is_admin") }?.optBoolean("is_admin")

    fun parseRegistryInfo(entityId: String, result: JSONObject?): TodoListRegistryInfo? {
        result ?: return null
        return TodoListRegistryInfo(
            entityId = entityId,
            platform = result.optString("platform").takeIf { it.isNotBlank() },
            configEntryId = result.optString("config_entry_id")
                .takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /** Start of the config flow: returns the flow id of the form step. */
    fun parseFlowStart(body: String): String {
        val json = JSONObject(body)
        val flowId = json.optString("flow_id")
        if (flowId.isBlank()) throw HaListAdminException("No flow id in response")
        when (json.optString("type")) {
            "form" -> return flowId
            "abort" -> throw HaListAdminException(json.optString("reason").ifBlank { "Flow aborted" })
            else -> throw HaListAdminException("Unexpected flow step: ${json.optString("type")}")
        }
    }

    /** Last step of the config flow: succeeds only if the entry was created. */
    fun parseFlowResult(body: String) {
        val json = JSONObject(body)
        when (json.optString("type")) {
            "create_entry" -> Unit
            "abort" -> throw HaListAdminException(json.optString("reason").ifBlank { "Flow aborted" })
            "form" -> {
                // The form came back, e.g. with validation errors
                val errors = json.optJSONObject("errors")
                val reason = errors?.keys()?.asSequence()?.firstOrNull()?.let { errors.optString(it) }
                throw HaListAdminException(reason ?: "Invalid input")
            }
            else -> throw HaListAdminException("Unexpected flow result: ${json.optString("type")}")
        }
    }
}

/**
 * Creates and deletes "Local To-do" lists via the Home Assistant REST API. Both need an admin token.
 */
class HaListAdminClient(
    baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = HaOkHttpFactory.newBuilder().build()
) {
    private val baseUrl = baseUrl.trim().removeSuffix("/")
    private val jsonType = "application/json".toMediaType()

    suspend fun createLocalTodoList(name: String) = withContext(Dispatchers.IO) {
        val start = post(
            "/api/config/config_entries/flow",
            JSONObject().put("handler", LOCAL_TODO_DOMAIN).put("show_advanced_options", false)
        )
        val flowId = HaListAdminParser.parseFlowStart(start)
        val result = post(
            "/api/config/config_entries/flow/$flowId",
            JSONObject().put("todo_list_name", name.trim())
        )
        HaListAdminParser.parseFlowResult(result)
    }

    suspend fun deleteConfigEntry(entryId: String) = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url("$baseUrl/api/config/config_entries/entry/$entryId")
                .delete()
        )
        Unit
    }

    private fun post(path: String, body: JSONObject): String =
        execute(
            Request.Builder()
                .url("$baseUrl$path")
                .post(body.toString().toRequestBody(jsonType))
        )

    private fun execute(builder: Request.Builder): String {
        val request = builder.header("Authorization", "Bearer ${token.trim()}").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HaListAdminException("HTTP ${response.code}", response.code)
            }
            return body
        }
    }
}
