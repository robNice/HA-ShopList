package de.robnice.homeshoplist.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.robnice.homeshoplist.data.HaRuntime
import de.robnice.homeshoplist.data.HaWebSocketRepository
import de.robnice.homeshoplist.viewmodel.ShoppingViewModel

class ShoppingViewModelFactory(
    private val context: android.content.Context,
    private val url: String,
    private val token: String,
    private val todoEntity: String,
    private val todoListName: String?
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            val repository = HaRuntime.repository ?: HaWebSocketRepository(
                url,
                token,
                context.applicationContext,
                todoEntity,
                todoListName
            ).also {
                HaRuntime.repository = it
            }
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
