package de.robnice.homeasssistant_shoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShoppingViewModel(
    private val repository: HaWebSocketRepository
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.items
    val authFailed = repository.authFailed
    val connectionErrors = repository.connectionErrors
    val newItems = repository.newItems

    val isLoading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            repository.items.first()
            isLoading.value = false
        }
    }

    fun loadItems() {
        repository.loadItems()
    }

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
        repository.disconnect()
    }
}
