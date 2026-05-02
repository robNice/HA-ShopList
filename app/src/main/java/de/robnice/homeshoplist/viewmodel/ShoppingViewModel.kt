package de.robnice.homeshoplist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robnice.homeshoplist.data.HaWebSocketRepository
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
class ShoppingViewModel(
    private val repository: HaWebSocketRepository
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.items
    val authFailed = repository.authFailed
    val connectionErrors = repository.connectionErrors
    val isOffline = repository.isOffline
    val isConnecting = repository.isConnecting
    val newItems = repository.newItems
    val remoteActivity = repository.remoteActivity


    val isLoading = combine(repository.loaded, repository.items) { loaded, items ->
        !loaded && items.isEmpty()
    }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

    fun addItem(name: String, area: ShoppingArea?) {
        repository.addItem(name, area)
    }

    fun toggleItem(item: ShoppingItem) {
        repository.toggleItem(item)
    }

    fun clearCompleted() {
        repository.clearCompleted()
    }

    fun updateItem(item: ShoppingItem, newName: String, area: ShoppingArea?) {
        repository.updateItem(item, newName, area)
    }

    fun ensureConnection() {
        repository.ensureConnected()
    }

    fun moveItem(itemId: String, previousItemId: String?, area: ShoppingArea? = null) {
        repository.moveItem(itemId, previousItemId, area)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
