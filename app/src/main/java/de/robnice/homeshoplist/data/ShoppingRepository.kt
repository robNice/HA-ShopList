package de.robnice.homeshoplist.data

import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shopping screen needs from a list source: either Home Assistant
 * ([HaWebSocketRepository]) or the purely local offline list ([LocalShoppingRepository]).
 */
interface ShoppingRepository {
    val items: StateFlow<List<ShoppingItem>>
    val loaded: StateFlow<Boolean>
    val authFailed: StateFlow<Boolean>
    val connectionErrors: StateFlow<Boolean>
    val isOffline: StateFlow<Boolean>
    val isConnecting: StateFlow<Boolean>
    val newItems: SharedFlow<ShoppingItem>
    val remoteActivity: SharedFlow<Unit>
    val reconnected: StateFlow<Long>

    fun addItem(name: String, area: ShoppingArea?)
    fun toggleItem(item: ShoppingItem)
    fun updateItem(item: ShoppingItem, newName: String, area: ShoppingArea?)
    fun moveItem(itemId: String, previousItemId: String?, area: ShoppingArea? = null)
    fun clearCompleted()
    fun ensureConnected()
    fun setReconnectAllowed(allowed: Boolean)
    fun disconnect()
}
