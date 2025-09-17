package com.example.granitevoice.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.TradingPartner

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<Order>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: Order)

    @Query("SELECT * FROM orders ORDER BY id DESC")
    suspend fun getAll(): List<Order>

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Order?

    @Query("SELECT * FROM orders WHERE number = :number LIMIT 1")
    suspend fun getByNumber(number: String): Order?

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY id DESC")
    suspend fun getByStatus(status: String): List<Order>

    @Query("SELECT * FROM orders WHERE tradingPartnerCode = :tradingPartnerCode ORDER BY id DESC")
    suspend fun getByTradingPartner(tradingPartnerCode: String): List<Order>

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getCount(): Int

    @Query("DELETE FROM orders")
    suspend fun clearAll()

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM orders WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    /**
     * Get orders by status with filtering for active orders
     */
    @Query("SELECT * FROM orders WHERE status NOT IN ('CANCELLED', 'COMPLETE') ORDER BY id DESC")
    suspend fun getActiveOrders(): List<Order>

    /**
     * Get orders created within a date range (using string comparison)
     * Note: This assumes dates are in DD/MM/YYYY format and may not work perfectly
     * for cross-month/year queries. For production, consider storing timestamps.
     */
    @Query("SELECT * FROM orders WHERE createDate = :date ORDER BY id DESC")
    suspend fun getOrdersByCreateDate(date: String): List<Order>

    /**
     * Search orders by number (fuzzy search)
     */
    @Query(
        """
        SELECT * FROM orders 
        WHERE LOWER(number) LIKE '%' || LOWER(:searchTerm) || '%'
        ORDER BY 
            CASE WHEN LOWER(number) = LOWER(:searchTerm) THEN 1
                 WHEN LOWER(number) LIKE LOWER(:searchTerm) || '%' THEN 2
                 ELSE 3 END,
            number
        LIMIT :limit
    """
    )
    suspend fun searchByNumber(searchTerm: String, limit: Int = 10): List<Order>

    /**
     * Get orders that need syncing (older than specified time)
     */
    @Query("SELECT * FROM orders WHERE lastSyncAt < :timestamp ORDER BY lastSyncAt ASC")
    suspend fun getOrdersNeedingSync(timestamp: Long): List<Order>

    /**
     * Update sync timestamp for specific orders
     */
    @Query("UPDATE orders SET lastSyncAt = :timestamp WHERE id IN (:orderIds)")
    suspend fun updateSyncTimestamp(orderIds: List<Int>, timestamp: Long)

    /**
     * Get distinct statuses for filtering UI
     */
    @Query("SELECT DISTINCT status FROM orders ORDER BY status")
    suspend fun getDistinctStatuses(): List<String>

    /**
     * Get distinct trading partners for filtering UI
     */
    @Query("SELECT DISTINCT tradingPartnerCode as code, tradingPartnerDescription as description FROM orders ORDER BY tradingPartnerDescription")
    suspend fun getDistinctTradingPartners(): List<com.example.granitevoice.domain.model.TradingPartner>

    /**
     * Efficient sync operation: clear and replace all orders atomically
     */
    @Transaction
    suspend fun syncReplace(newOrders: List<Order>) {
        clearAll()
        insertAll(newOrders)
    }

    /**
     * Get recent orders (last N orders)
     */
    @Query("SELECT * FROM orders ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentOrders(limit: Int = 50): List<Order>

    /**
     * Get orders by multiple statuses
     */
    @Query("SELECT * FROM orders WHERE status IN (:statuses) ORDER BY id DESC")
    suspend fun getOrdersByStatuses(statuses: List<String>): List<Order>
}