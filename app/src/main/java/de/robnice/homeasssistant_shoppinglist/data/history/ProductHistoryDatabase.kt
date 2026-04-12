package de.robnice.homeasssistant_shoppinglist.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProductHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProductHistoryDatabase : RoomDatabase() {

    abstract fun productHistoryDao(): ProductHistoryDao

    companion object {
        @Volatile
        private var instance: ProductHistoryDatabase? = null

        fun getInstance(context: Context): ProductHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProductHistoryDatabase::class.java,
                    "product_history.db"
                ).build().also { instance = it }
            }
    }
}
