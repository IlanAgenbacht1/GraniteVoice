package com.example.granitevoice.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.granitevoice.domain.model.MasterItem

@Dao
interface MasterItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MasterItem>)

    @Query("SELECT * FROM master_items")
    suspend fun getAll(): List<MasterItem>

    @Query("SELECT * FROM master_items WHERE code = :code")
    suspend fun getByCode(code: String): MasterItem?

    @Query("SELECT * FROM master_items WHERE description = :description")
    suspend fun getByDescription(description: String): MasterItem?

    @Query("DELETE FROM master_items")
    suspend fun clearAll()
}
