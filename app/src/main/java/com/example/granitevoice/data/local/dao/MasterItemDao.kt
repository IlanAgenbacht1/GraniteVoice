package com.example.granitevoice.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.granitevoice.domain.model.MasterItem

@Dao
interface MasterItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MasterItem>)

    @Query("SELECT * FROM master_items ORDER BY code")
    suspend fun getAll(): List<MasterItem>

    @Query("SELECT * FROM master_items WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): MasterItem?

    @Query("SELECT * FROM master_items WHERE LOWER(description) = LOWER(:description) LIMIT 1")
    suspend fun getByDescription(description: String): MasterItem?

    @Query("DELETE FROM master_items")
    suspend fun clearAll()

    // Enhanced queries for voice command processing

    @Query(
        """
        SELECT * FROM master_items 
        WHERE LOWER(description) LIKE '%' || LOWER(:searchTerm) || '%'
        ORDER BY 
            CASE WHEN LOWER(description) = LOWER(:searchTerm) THEN 1 ELSE 2 END,
            LENGTH(description),
            description
        LIMIT :limit
    """
    )
    suspend fun searchByDescription(searchTerm: String, limit: Int = 10): List<MasterItem>

    @Query(
        """
        SELECT * FROM master_items 
        WHERE LOWER(code) LIKE '%' || LOWER(:searchTerm) || '%'
        OR LOWER(description) LIKE '%' || LOWER(:searchTerm) || '%'
        ORDER BY 
            CASE WHEN LOWER(code) = LOWER(:searchTerm) THEN 1
                 WHEN LOWER(description) = LOWER(:searchTerm) THEN 2
                 WHEN LOWER(code) LIKE LOWER(:searchTerm) || '%' THEN 3
                 WHEN LOWER(description) LIKE LOWER(:searchTerm) || '%' THEN 4
                 ELSE 5 END,
            LENGTH(description),
            code
        LIMIT :limit
    """
    )
    suspend fun fuzzySearch(searchTerm: String, limit: Int = 20): List<MasterItem>

    @Query("SELECT COUNT(*) FROM master_items")
    suspend fun getCount(): Int

    @Query("SELECT * FROM master_items ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSample(limit: Int = 5): List<MasterItem>

    /**
     * Efficient sync operation: clear and replace all items atomically
     */
    @Transaction
    suspend fun syncReplace(newItems: List<MasterItem>) {
        clearAll()
        insertAll(newItems)
    }

    /**
     * Get all codes for fuzzy matching candidates
     */
    @Query("SELECT code FROM master_items ORDER BY code")
    suspend fun getAllCodes(): List<String>

    /**
     * Get all descriptions for fuzzy matching candidates
     */
    @Query("SELECT description FROM master_items ORDER BY description")
    suspend fun getAllDescriptions(): List<String>
}
