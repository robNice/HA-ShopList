package de.robnice.homeasssistant_shoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
class ShoppingViewModel(
    private val repository: HaWebSocketRepository
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.items
    val authFailed = repository.authFailed
    val connectionErrors = repository.connectionErrors
    val newItems = repository.newItems


    val isLoading = repository.loaded
        .map { loaded -> !loaded }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

    fun addItem(name: String) {
        repository.addItem(name)
    }

    fun toggleItem(item: ShoppingItem) {
        repository.toggleItem(item)
    }

    fun clearCompleted() {
        repository.clearCompleted()
    }

    fun renameItem(item: ShoppingItem, newName: String) {
        repository.renameItem(item, newName)
    }

    fun ensureConnection() {
        repository.ensureConnected()
    }

    fun moveItem(itemId: String, previousItemId: String?) {
        repository.moveItem(itemId, previousItemId)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
