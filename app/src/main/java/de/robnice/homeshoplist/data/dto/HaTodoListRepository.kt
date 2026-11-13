package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.model.ShoppingList
import org.json.JSONArray

class HaTodoListRepository(
    private val api: HaApi
) {
    // /api/states works for every user; /api/template is admin-only and answered non-admin tokens with 401
    suspend fun loadTodoLists(rawToken: String): List<ShoppingList> {
        val bearer = "Bearer ${rawToken.trim()}"
        val states = JSONArray(api.getStatesRaw(token = bearer).string())

        return buildList {
            for (index in 0 until states.length()) {
                val state = states.optJSONObject(index) ?: continue
                val entityId = state.optString("entity_id").takeIf { it.startsWith("todo.") } ?: continue
                val friendlyName = state.optJSONObject("attributes")
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
}
