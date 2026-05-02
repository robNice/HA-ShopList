package de.robnice.homeshoplist.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProductHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ProductHistoryDatabase : RoomDatabase() {

    abstract fun productHistoryDao(): ProductHistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE product_history ADD COLUMN areaKey TEXT")
            }
        }

        @Volatile
        private var instance: ProductHistoryDatabase? = null

        fun getInstance(context: Context): ProductHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProductHistoryDatabase::class.java,
                    "product_history.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
