package de.robnice.homeshoplist.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_history")
data class ProductHistoryEntity(
    @PrimaryKey
    val normalizedName: String,
    val displayName: String,
    val areaKey: String?,
    val useCount: Int,
    val lastUsedAt: Long
)
