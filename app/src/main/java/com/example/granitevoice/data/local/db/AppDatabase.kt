package com.example.granitevoice.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.data.local.dao.OrderDao
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.Order

@Database(
    entities = [MasterItem::class, Order::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun masterItemDao(): MasterItemDao
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new table with correct schema
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS master_items_new (
                        code TEXT NOT NULL PRIMARY KEY,
                        description TEXT NOT NULL,
                        remoteId INTEGER NOT NULL DEFAULT 0
                    )
                """
                )

                // Copy existing data if the old table exists
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO master_items_new (code, description, remoteId)
                    SELECT code, description, 0 as remoteId FROM master_items
                """
                )

                // Drop old table
                database.execSQL("DROP TABLE IF EXISTS master_items")

                // Rename new table
                database.execSQL("ALTER TABLE master_items_new RENAME TO master_items")

                // Create indices
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_master_items_description 
                    ON master_items (description)
                """
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_master_items_code 
                    ON master_items (code)
                """
                )
            }
        }

        // Migration from version 2 to 3 - Add UOM and other fields
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to existing table
                database.execSQL("ALTER TABLE master_items ADD COLUMN unitOfMeasure TEXT")
                database.execSQL("ALTER TABLE master_items ADD COLUMN category TEXT")
                database.execSQL("ALTER TABLE master_items ADD COLUMN itemType TEXT")
            }
        }

        // Migration from version 3 to 4 - Add orders table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create orders table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS orders (
                        id INTEGER NOT NULL PRIMARY KEY,
                        number TEXT NOT NULL,
                        tradingPartnerCode TEXT NOT NULL,
                        tradingPartnerDescription TEXT NOT NULL,
                        createDate TEXT NOT NULL,
                        site TEXT,
                        status TEXT NOT NULL,
                        auditDate TEXT NOT NULL,
                        auditUser TEXT NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT,
                        priority INTEGER,
                        erpLocation TEXT,
                        lastSyncAt INTEGER NOT NULL DEFAULT 0
                    )
                """
                )

                // Create indices for orders table
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_orders_number 
                    ON orders (number)
                """
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_orders_status 
                    ON orders (status)
                """
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_orders_tradingPartnerCode 
                    ON orders (tradingPartnerCode)
                """
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "granite_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
