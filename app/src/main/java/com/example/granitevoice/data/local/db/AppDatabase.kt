package com.example.granitevoice.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.domain.model.MasterItem

@Database(entities = [MasterItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun masterItemDao(): MasterItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "granite_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
