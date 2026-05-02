package de.robnice.homeshoplist.data.history

import android.content.Context
import de.robnice.homeshoplist.model.ShoppingArea
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

    suspend fun recordProductUse(name: String, area: ShoppingArea? = null) {
        val trimmed = name.trim()
        val normalized = normalizeName(trimmed)
        if (normalized.isBlank()) {
            return
        }

        val existing = dao.getByNormalizedName(normalized)
        val entity = ProductHistoryEntity(
            normalizedName = normalized,
            displayName = trimmed,
            areaKey = area?.key ?: existing?.areaKey,
            useCount = (existing?.useCount ?: 0) + 1,
            lastUsedAt = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }

    suspend fun rememberProduct(name: String, area: ShoppingArea? = null) {
        val trimmed = name.trim()
        val normalized = normalizeName(trimmed)
        if (normalized.isBlank()) {
            return
        }

        val existing = dao.getByNormalizedName(normalized)
        if (existing != null) {
            if (area?.key != null && area.key != existing.areaKey) {
                dao.updateArea(normalized, area.key)
            }
            return
        }

        dao.upsert(
            ProductHistoryEntity(
                normalizedName = normalized,
                displayName = trimmed,
                areaKey = area?.key,
                useCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateStoredProductArea(name: String, area: ShoppingArea?) {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) {
            return
        }

        val existing = dao.getByNormalizedName(normalized) ?: return
        if (existing.areaKey == area?.key) {
            return
        }

        dao.updateArea(normalized, area?.key)
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
