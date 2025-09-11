package com.example.granitevoice.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.domain.model.MasterItem

@Database(
    entities = [MasterItem::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun masterItemDao(): MasterItemDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "granite_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
