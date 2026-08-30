package de.robnice.homeshoplist.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProductHistoryEntity)

    @Query(
        """
        SELECT * FROM product_history
        ORDER BY lastUsedAt DESC, useCount DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<ProductHistoryEntity>>

    @Query("SELECT * FROM product_history")
    suspend fun getAll(): List<ProductHistoryEntity>

    @Query("SELECT * FROM product_history WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): ProductHistoryEntity?

    @Query(
        """
        UPDATE product_history
        SET areaKey = :areaKey
        WHERE normalizedName = :normalizedName
        """
    )
    suspend fun updateArea(normalizedName: String, areaKey: String?)

    @Query(
        """
        SELECT * FROM product_history
        WHERE normalizedName LIKE :prefix || '%' OR displayName LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN normalizedName LIKE :prefix || '%' THEN 0 ELSE 1 END,
            useCount DESC,
            lastUsedAt DESC,
            displayName COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeSuggestions(query: String, prefix: String, limit: Int): Flow<List<ProductHistoryEntity>>

    @Query("DELETE FROM product_history WHERE normalizedName = :normalizedName")
    suspend fun deleteByNormalizedName(normalizedName: String)

    @Query("DELETE FROM product_history")
    suspend fun deleteAll()

    @Transaction
    suspend fun mergeImported(items: List<ProductHistoryEntity>) {
        items.forEach { candidate ->
            val existing = getByNormalizedName(candidate.normalizedName)
            val importedIsNewer = existing == null || candidate.lastUsedAt >= existing.lastUsedAt
            upsert(
                ProductHistoryEntity(
                    normalizedName = candidate.normalizedName,
                    displayName = if (importedIsNewer) candidate.displayName else existing.displayName,
                    areaKey = if (importedIsNewer) candidate.areaKey else existing.areaKey,
                    useCount = maxOf(candidate.useCount, existing?.useCount ?: 0),
                    lastUsedAt = maxOf(candidate.lastUsedAt, existing?.lastUsedAt ?: 0L)
                )
            )
        }
    }
}
