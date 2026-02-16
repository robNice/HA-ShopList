package de.robnice.homeasssistant_shoppinglist.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.viewmodel.ShoppingViewModel

class ShoppingViewModelFactory(
    private val url: String,
    private val token: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = HaWebSocketRepository(url, token)
        return ShoppingViewModel(repository) as T
    }
}
