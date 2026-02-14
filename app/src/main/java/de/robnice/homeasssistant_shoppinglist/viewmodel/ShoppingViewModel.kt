package de.robnice.homeasssistant_shoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robnice.homeasssistant_shoppinglist.data.HaRepository
import de.robnice.homeasssistant_shoppinglist.model.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShoppingViewModel(
    private val repository: HaRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val items: StateFlow<List<ShoppingItem>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val loading = MutableStateFlow(false)

    fun loadItems() {
        viewModelScope.launch {
            loading.value = true
            try {
                _items.value = repository.getItems()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                loading.value = false
            }
        }
    }


    fun addItem(name: String) {
        viewModelScope.launch {
            repository.addItem(name)
            loadItems()
        }
    }

    fun toggleItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.toggleItem(item)
            loadItems()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            try {
                repository.clearCompleted()
                loadItems()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }



}
