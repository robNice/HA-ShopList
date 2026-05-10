package de.robnice.homeshoplist.data

import android.content.Context
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.model.ShoppingArea

interface ProductHistoryRecorder {
    suspend fun recordProductUse(name: String, area: ShoppingArea? = null)
    suspend fun rememberProduct(name: String, area: ShoppingArea? = null)
}

class RepositoryProductHistoryRecorder(
    context: Context
) : ProductHistoryRecorder {
    private val repository = ProductHistoryRepository.getInstance(context)

    override suspend fun recordProductUse(name: String, area: ShoppingArea?) {
        repository.recordProductUse(name, area)
    }

    override suspend fun rememberProduct(name: String, area: ShoppingArea?) {
        repository.rememberProduct(name, area)
    }
}
