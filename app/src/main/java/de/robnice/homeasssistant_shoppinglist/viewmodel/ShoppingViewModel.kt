package de.robnice.homeasssistant_shoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShoppingViewModel(
    private val repository: HaWebSocketRepository
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.items

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

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
