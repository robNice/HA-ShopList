package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.data.dto.TemplateRequest
import de.robnice.homeshoplist.model.ShoppingList
import org.json.JSONArray

class HaTodoListRepository(
    private val api: HaApi
) {
    suspend fun loadTodoLists(rawToken: String): List<ShoppingList> {
        val bearer = "Bearer ${rawToken.trim()}"
        val rawBody = api.renderTemplate(
            token = bearer,
            body = TemplateRequest(
                template = TODO_LIST_TEMPLATE
            )
        )
        val payload = rawBody.string().trim()
        val lists = JSONArray(payload)

        return buildList {
            for (index in 0 until lists.length()) {
                val entry = lists.optJSONObject(index) ?: continue
                val entityId = entry.optString("id").takeIf { it.startsWith("todo.") } ?: continue
                val friendlyName = entry.optString("name").takeIf { it.isNotBlank() }

                add(
                    ShoppingList(
                        id = entityId,
                        name = friendlyName ?: entityId
                    )
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    private companion object {
        private const val TODO_LIST_TEMPLATE = """
            {% set ns = namespace(items=[]) %}
            {% for state in states.todo %}
              {% set ns.items = ns.items + [{
                'id': state.entity_id,
                'name': state.attributes.friendly_name if state.attributes.friendly_name else state.entity_id
              }] %}
            {% endfor %}
            {{ ns.items | to_json }}
        """
    }
}
