package de.robnice.homeasssistant_shoppinglist.data

import de.robnice.homeasssistant_shoppinglist.data.dto.HaStateDto
import de.robnice.homeasssistant_shoppinglist.model.ShoppingList

class HaTodoListRepository(
    private val api: HaApi
) {
    suspend fun loadTodoLists(rawToken: String): List<ShoppingList> {
        val bearer = "Bearer ${rawToken.trim()}"
        val states = api.getStates(bearer)

        return states
            .asSequence()
            .filter { it.entity_id.startsWith("todo.") }
            .map { state ->
                ShoppingList(
                    id = state.entity_id,
                    name = state.attributes?.friendly_name?.ifBlank { state.entity_id } ?: state.entity_id
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}