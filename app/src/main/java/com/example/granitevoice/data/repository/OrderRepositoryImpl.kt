package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.local.dao.OrderDao
import com.example.granitevoice.data.remote.OrdersGraniteSource
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.OrderStatus
import com.example.granitevoice.domain.model.TradingPartner
import com.example.granitevoice.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation that manages orders with local database and remote sync
 */
@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val ordersGraniteSource: OrdersGraniteSource
) : OrderRepository {

    companion object {
        private const val TAG = "OrderRepository"
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Sync orders from the remote API to local database
     */
    suspend fun syncOrders(forceSync: Boolean = false): Result<Unit> {
        Log.d(TAG, "Starting order sync...")
        Log.d(TAG, "Parameters - forceSync: $forceSync")

        return try {
            withContext(Dispatchers.IO) {
                // Check if we need to sync
                if (!forceSync && !shouldSync()) {
                    Log.d(TAG, "Sync not needed, using cached data")
                    return@withContext Result.success(Unit)
                }

                Log.d(TAG, "Sync needed, starting order fetch from remote...")
                val syncStartTime = System.currentTimeMillis()

                // Fetch orders from API
                val fetchResult = ordersGraniteSource.fetchOrders()

                fetchResult.fold(
                    onSuccess = { orders ->
                        Log.d(TAG, "Successfully fetched ${orders.size} orders from API")
                        Log.d(TAG, "Storing orders in local database...")

                        val dbStartTime = System.currentTimeMillis()

                        // Replace all orders in local database
                        orderDao.syncReplace(orders)

                        val dbDuration = System.currentTimeMillis() - dbStartTime
                        val totalDuration = System.currentTimeMillis() - syncStartTime

                        val finalCount = orderDao.getCount()
                        Log.d(TAG, "Finished storing orders. Total orders: $finalCount")
                        Log.d(TAG, "Database operation took ${dbDuration}ms")
                        Log.d(TAG, "Total sync operation took ${totalDuration}ms")

                        // Log some sync statistics
                        if (orders.isNotEmpty()) {
                            val statusCounts = orders.groupingBy { it.status }.eachCount()
                            Log.d(TAG, "Synced orders by status: $statusCounts")

                            val activeCount = orders.count { it.isActive() }
                            Log.d(TAG, "Active orders: $activeCount/${orders.size}")

                            val latestOrder = orders.maxByOrNull { it.getAuditDateTimestamp() }
                            if (latestOrder != null) {
                                Log.d(
                                    TAG,
                                    "Latest order: ${latestOrder.number} (${latestOrder.auditDate})"
                                )
                            }
                        } else {
                            Log.d(TAG, "No orders found - database cleared")
                        }

                        Result.success(Unit)
                    },
                    onFailure = { exception ->
                        val totalDuration = System.currentTimeMillis() - syncStartTime

                        // Check if this is a "no orders found" case vs a real error
                        val isNoOrdersFound =
                            exception.message?.contains("Orders not found") == true ||
                                    exception.message?.contains("404") == true

                        if (isNoOrdersFound) {
                            Log.w(
                                TAG,
                                "No orders found in system - treating as success with empty dataset"
                            )
                            Log.d(TAG, "Clearing local orders database")

                            // Clear local database since no orders exist remotely
                            orderDao.clearAll()

                            Log.d(
                                TAG,
                                "Orders sync completed successfully with 0 orders after ${totalDuration}ms"
                            )
                            Result.success(Unit)
                        } else {
                            Log.e(
                                TAG,
                                "Failed to fetch orders for sync after ${totalDuration}ms",
                                exception
                            )
                            Result.failure(exception)
                        }
                    }
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during orders sync", e)
            Result.failure(e)
        }
    }

    /**
     * Get all orders from local database, with optional sync
     */
    suspend fun getAllOrdersWithSync(syncFirst: Boolean = false): Result<List<Order>> {
        Log.d(TAG, "Getting all orders")

        return try {
            withContext(Dispatchers.IO) {
                // Sync if requested or needed
                if (syncFirst || shouldSync()) {
                    Log.d(TAG, "Syncing before getting orders")
                    syncOrders().fold(
                        onSuccess = {
                            Log.d(TAG, "Sync completed successfully")
                        },
                        onFailure = { exception ->
                            Log.w(TAG, "Sync failed, using cached data", exception)
                        }
                    )
                }

                val orders = orderDao.getAll()
                Log.d(TAG, "Retrieved ${orders.size} orders from local database")

                Result.success(orders)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all orders", e)
            Result.failure(e)
        }
    }

    /**
     * Get order by ID from local database
     */
    suspend fun getOrderByIdInt(orderId: Int): Result<Order?> {
        Log.d(TAG, "Looking up order by ID: $orderId")

        return try {
            withContext(Dispatchers.IO) {
                val order = orderDao.getById(orderId)
                Log.d(
                    TAG,
                    if (order != null) "Order found: $orderId" else "Order not found: $orderId"
                )

                Result.success(order)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup order by ID: $orderId", e)
            Result.failure(e)
        }
    }

    /**
     * Get order by number from local database
     */
    suspend fun getOrderByNumber(orderNumber: String): Result<Order?> {
        Log.d(TAG, "Looking up order by number: $orderNumber")

        return try {
            withContext(Dispatchers.IO) {
                val order = orderDao.getByNumber(orderNumber)
                Log.d(
                    TAG,
                    if (order != null) "Order found: $orderNumber" else "Order not found: $orderNumber"
                )

                Result.success(order)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup order by number: $orderNumber", e)
            Result.failure(e)
        }
    }

    /**
     * Search orders by number pattern
     */
    suspend fun searchOrdersByNumber(
        searchTerm: String,
        limit: Int = 10
    ): Result<List<Order>> {
        Log.d(TAG, "Searching orders by number: '$searchTerm'")
        Log.d(TAG, "Search parameters - limit: $limit")

        return try {
            withContext(Dispatchers.IO) {
                // Try local search first
                Log.d(TAG, "Attempting local database search...")
                val localSearchStart = System.currentTimeMillis()
                val localResults = orderDao.searchByNumber(searchTerm, limit)
                val localSearchDuration = System.currentTimeMillis() - localSearchStart

                if (localResults.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "Local search found ${localResults.size} orders in ${localSearchDuration}ms"
                    )
                    localResults.forEachIndexed { index, order ->
                        if (index < 3) { // Log first 3 results
                            Log.d(
                                TAG,
                                "  Local result ${index + 1}: ${order.number} (${order.status})"
                            )
                        }
                    }
                    return@withContext Result.success(localResults)
                }

                // If no local results, try remote search and cache
                Log.d(TAG, "No local results found, searching remote API...")

                val remoteSearchStart = System.currentTimeMillis()
                val remoteResult =
                    ordersGraniteSource.fetchOrdersByNumber(searchTerm, limit = limit)
                val remoteSearchDuration = System.currentTimeMillis() - remoteSearchStart

                remoteResult.fold(
                    onSuccess = { orders ->
                        Log.d(
                            TAG,
                            "Remote search found ${orders.size} orders in ${remoteSearchDuration}ms"
                        )

                        // Cache the results
                        if (orders.isNotEmpty()) {
                            Log.d(TAG, "Caching ${orders.size} remote results to local database...")
                            val cacheStart = System.currentTimeMillis()
                            orders.forEach { order ->
                                orderDao.insert(order)
                            }
                            val cacheDuration = System.currentTimeMillis() - cacheStart
                            Log.d(TAG, "Cached results in ${cacheDuration}ms")

                            orders.forEachIndexed { index, order ->
                                if (index < 3) { // Log first 3 results
                                    Log.d(
                                        TAG,
                                        "  Remote result ${index + 1}: ${order.number} (${order.status})"
                                    )
                                }
                            }
                        }

                        Result.success(orders)
                    },
                    onFailure = { exception ->
                        Log.e(
                            TAG,
                            "Remote search failed after ${remoteSearchDuration}ms, returning empty list",
                            exception
                        )
                        Result.success(emptyList())
                    }
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to search orders by number: '$searchTerm'", e)
            Result.failure(e)
        }
    }

    /**
     * Get orders by status
     */
    suspend fun getOrdersByStatus(status: String): Result<List<Order>> {
        Log.d(TAG, "Getting orders by status: $status")

        return try {
            withContext(Dispatchers.IO) {
                val orders = orderDao.getByStatus(status)
                Log.d(TAG, "Found ${orders.size} orders with status: $status")

                Result.success(orders)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get orders by status: $status", e)
            Result.failure(e)
        }
    }

    /**
     * Get active orders (not cancelled or complete)
     */
    suspend fun getActiveOrders(): Result<List<Order>> {
        Log.d(TAG, "Getting active orders")

        return try {
            withContext(Dispatchers.IO) {
                val orders = orderDao.getActiveOrders()
                Log.d(TAG, "Found ${orders.size} active orders")

                Result.success(orders)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active orders", e)
            Result.failure(e)
        }
    }

    /**
     * Get recent orders
     */
    suspend fun getRecentOrders(limit: Int = 50): Result<List<Order>> {
        Log.d(TAG, "Getting recent orders (limit: $limit)")

        return try {
            withContext(Dispatchers.IO) {
                val orders = orderDao.getRecentOrders(limit)
                Log.d(TAG, "Found ${orders.size} recent orders")

                Result.success(orders)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent orders", e)
            Result.failure(e)
        }
    }

    /**
     * Get available order statuses
     */
    suspend fun getAvailableStatuses(): Result<List<String>> {
        Log.d(TAG, "Getting available statuses")

        return try {
            withContext(Dispatchers.IO) {
                val statuses = orderDao.getDistinctStatuses()
                Log.d(TAG, "Found statuses: $statuses")

                Result.success(statuses)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available statuses", e)
            Result.failure(e)
        }
    }

    /**
     * Get count of orders in local database
     */
    suspend fun getOrderCountSuspend(): Result<Int> {
        Log.d(TAG, "Getting order count")

        return try {
            withContext(Dispatchers.IO) {
                val count = orderDao.getCount()
                Log.d(TAG, "Order count: $count")

                Result.success(count)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get order count", e)
            Result.failure(e)
        }
    }

    /**
     * Get distinct trading partners from orders
     */
    suspend fun getDistinctTradingPartners(): Result<List<TradingPartner>> {
        Log.d(TAG, "Getting distinct trading partners")

        return try {
            withContext(Dispatchers.IO) {
                val partners = orderDao.getDistinctTradingPartners()
                Log.d(TAG, "Found ${partners.size} distinct trading partners")

                Result.success(partners)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get distinct trading partners", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all orders from local database
     */
    suspend fun clearAllOrdersSuspend(): Result<Unit> {
        Log.d(TAG, "Clearing all orders")

        return try {
            withContext(Dispatchers.IO) {
                orderDao.clearAll()
                Log.d(TAG, "All orders cleared")

                Result.success(Unit)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all orders", e)
            Result.failure(e)
        }
    }

    /**
     * Check if we should sync based on last sync time
     */
    private suspend fun shouldSync(): Boolean {
        Log.d(TAG, "Evaluating sync necessity...")

        return try {
            val count = orderDao.getCount()
            Log.d(TAG, "Current cached order count: $count")

            if (count == 0) {
                Log.d(TAG, "No cached orders found - sync needed")
                return true
            }

            // Check if we have any recent orders (simple heuristic)
            val syncThreshold = System.currentTimeMillis() - SYNC_INTERVAL_MS
            val recentOrders = orderDao.getOrdersNeedingSync(syncThreshold)
            val needsSyncCount = recentOrders.size

            // Additional heuristics
            val minOrderThreshold = 10 // Arbitrary threshold for minimum orders
            val lowOrderCount = count < minOrderThreshold

            val shouldSync = needsSyncCount > 0 || lowOrderCount

            Log.d(TAG, "Sync evaluation:")
            Log.d(TAG, "  - Cached orders: $count")
            Log.d(TAG, "  - Orders needing sync: $needsSyncCount")
            Log.d(TAG, "  - Below minimum threshold ($minOrderThreshold): $lowOrderCount")
            Log.d(TAG, "  - Sync interval: ${SYNC_INTERVAL_MS / (60 * 1000)} minutes")
            Log.d(TAG, "  - Decision: ${if (shouldSync) "SYNC NEEDED" else "NO SYNC NEEDED"}")

            if (shouldSync) {
                Log.d(TAG, "Sync will proceed based on heuristics")
            } else {
                Log.d(TAG, "Using cached data - sync not required")
            }

            shouldSync

        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating sync status - defaulting to sync", e)
            Log.w(TAG, "Failed to check sync status, will sync as safety measure")
            true
        }
    }

    // Legacy interface methods - all deprecated in favor of sync-based approach
    override suspend fun createOrder(item: MasterItem, quantity: Int): Result<Order> {
        Log.w(TAG, "createOrder called - this is deprecated for the new sync-based Order entity")
        return Result.failure(UnsupportedOperationException("createOrder is deprecated, use sync-based orders from API"))
    }

    override suspend fun getOrderById(orderId: String): Result<Order?> {
        Log.d(TAG, "getOrderById(String) called with: '$orderId'")

        return try {
            withContext(Dispatchers.IO) {
                // First, try to find by order number (most common case for voice commands)
                val orderByNumber = orderDao.getByNumber(orderId)
                if (orderByNumber != null) {
                    Log.d(TAG, "Found order by number: $orderId")
                    return@withContext Result.success(orderByNumber)
                }

                // If not found by number, try to convert to Int ID and search by ID
                val id = orderId.toIntOrNull()
                if (id != null) {
                    Log.d(TAG, "Trying to find order by ID: $id")
                    val orderById = orderDao.getById(id)
                    if (orderById != null) {
                        Log.d(TAG, "Found order by ID: $id")
                        return@withContext Result.success(orderById)
                    }
                }

                Log.d(TAG, "Order not found with identifier: $orderId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup order with identifier: $orderId", e)
            Result.failure(e)
        }
    }

    override suspend fun getAllOrders(): Result<List<Order>> {
        return getAllOrdersWithSync(syncFirst = false)
    }

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Order> {
        Log.w(
            TAG,
            "updateOrderStatus called - this is deprecated for the new sync-based Order entity"
        )
        return Result.failure(UnsupportedOperationException("updateOrderStatus is deprecated, orders are read-only from API"))
    }

    override suspend fun deleteOrder(orderId: String): Result<Boolean> {
        Log.w(TAG, "deleteOrder called - this is deprecated for the new sync-based Order entity")
        return Result.failure(UnsupportedOperationException("deleteOrder is deprecated, orders are read-only from API"))
    }
}