package de.robnice.homeasssistant_shoppinglist.data.history

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ProductHistoryRepository private constructor(
    private val dao: ProductHistoryDao
) {

    fun observeSuggestions(query: String, limit: Int = 5): Flow<List<ProductHistoryEntity>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return dao.observeSuggestions(query = "", prefix = "", limit = 0)
        }

        val normalizedQuery = normalizeName(trimmed)
        return dao.observeSuggestions(
            query = trimmed,
            prefix = normalizedQuery,
            limit = limit
        )
    }

    fun observeHistory(): Flow<List<ProductHistoryEntity>> = dao.observeAll()

    suspend fun recordProductUse(name: String) {
        val trimmed = name.trim()
        val normalized = normalizeName(trimmed)
        if (normalized.isBlank()) {
            return
        }

        val existing = dao.getByNormalizedName(normalized)
        val entity = ProductHistoryEntity(
            normalizedName = normalized,
            displayName = trimmed,
            useCount = (existing?.useCount ?: 0) + 1,
            lastUsedAt = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }

    suspend fun rememberProduct(name: String) {
        val trimmed = name.trim()
        val normalized = normalizeName(trimmed)
        if (normalized.isBlank()) {
            return
        }

        val existing = dao.getByNormalizedName(normalized)
        if (existing != null) {
            return
        }

        dao.upsert(
            ProductHistoryEntity(
                normalizedName = normalized,
                displayName = trimmed,
                useCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProduct(normalizedName: String) {
        dao.deleteByNormalizedName(normalizedName)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    companion object {
        @Volatile
        private var instance: ProductHistoryRepository? = null

        fun getInstance(context: Context): ProductHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: ProductHistoryRepository(
                    ProductHistoryDatabase.getInstance(context).productHistoryDao()
                ).also { instance = it }
            }

        fun normalizeName(name: String): String =
            name
                .trim()
                .lowercase()
                .replace(Regex("\\s+"), " ")
    }
}
