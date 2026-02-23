package de.robnice.homeasssistant_shoppinglist.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.robnice.homeasssistant_shoppinglist.data.HaRuntime
import de.robnice.homeasssistant_shoppinglist.data.HaWebSocketRepository
import de.robnice.homeasssistant_shoppinglist.viewmodel.ShoppingViewModel

class ShoppingViewModelFactory(
    private val context: android.content.Context,
    private val url: String,
    private val token: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            val repository = HaRuntime.repository ?: HaWebSocketRepository(url, token, context.applicationContext).also {
                HaRuntime.repository = it
            }
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
