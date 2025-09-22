package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.data.remote.OrderProgressGraniteSource
import com.example.granitevoice.data.repository.OrderRepositoryImpl
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.OrderProgress
import com.example.granitevoice.domain.model.TradingPartner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for order-related operations
 * Provides high-level business logic for order management
 */
@Singleton
class GetOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepositoryImpl,
    private val orderProgressGraniteSource: OrderProgressGraniteSource
) {

    companion object {
        private const val TAG = "OrderUseCase"
    }

    /**
     * Get all orders with automatic sync if needed
     */
    suspend fun getAllOrders(forceSync: Boolean = false): Result<List<Order>> {
        Log.d(TAG, "Getting all orders (forceSync: $forceSync)")

        return orderRepository.getAllOrdersWithSync(syncFirst = forceSync)
    }

    /**
     * Search for orders by number pattern
     * This method is designed for voice command integration
     */
    suspend fun searchOrdersByNumber(
        searchTerm: String,
        limit: Int = 10
    ): Result<List<Order>> {
        Log.d(TAG, "Searching orders by number: '$searchTerm'")

        if (searchTerm.isBlank()) {
            Log.w(TAG, "Empty search term provided")
            return Result.success(emptyList())
        }

        return orderRepository.searchOrdersByNumber(searchTerm.trim(), limit)
    }

    /**
     * Get order by exact number match
     */
    suspend fun getOrderByNumber(orderNumber: String): Result<Order?> {
        Log.d(TAG, "Getting order by number: '$orderNumber'")

        if (orderNumber.isBlank()) {
            Log.w(TAG, "Empty order number provided")
            return Result.success(null)
        }

        return orderRepository.getOrderByNumber(orderNumber.trim())
    }

    /**
     * Get order by ID
     */
    suspend fun getOrderById(orderId: Int): Result<Order?> {
        Log.d(TAG, "Getting order by ID: $orderId")

        return orderRepository.getOrderByIdInt(orderId)
    }

    /**
     * Get orders by status
     */
    suspend fun getOrdersByStatus(status: String): Result<List<Order>> {
        Log.d(TAG, "Getting orders by status: '$status'")

        if (status.isBlank()) {
            Log.w(TAG, "Empty status provided")
            return Result.success(emptyList())
        }

        return orderRepository.getOrdersByStatus(status.uppercase())
    }

    /**
     * Get active orders (not cancelled or complete)
     */
    suspend fun getActiveOrders(): Result<List<Order>> {
        Log.d(TAG, "Getting active orders")

        return orderRepository.getActiveOrders()
    }

    /**
     * Get recent orders for dashboard/overview
     */
    suspend fun getRecentOrders(limit: Int = 20): Result<List<Order>> {
        Log.d(TAG, "Getting recent orders (limit: $limit)")

        return orderRepository.getRecentOrders(limit)
    }

    /**
     * Get order status options for UI
     */
    suspend fun getAvailableStatuses(): Result<List<String>> {
        Log.d(TAG, "Getting available order statuses")

        return orderRepository.getAvailableStatuses()
    }

    /**
     * Sync orders from remote API
     */
    suspend fun syncOrders(forceSync: Boolean = false): Result<Unit> {
        Log.d(TAG, "=== Order Sync Requested ===")
        Log.d(TAG, "Force sync: $forceSync")

        val startTime = System.currentTimeMillis()

        return orderRepository.syncOrders(forceSync).fold(
            onSuccess = {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "=== Order Sync Completed Successfully ===")
                Log.d(TAG, "Total sync duration: ${duration}ms")

                // Log current state after sync
                getOrderCount().fold(
                    onSuccess = { count ->
                        Log.d(TAG, "Post-sync order count: $count")
                    },
                    onFailure = {
                        Log.w(TAG, "Could not retrieve post-sync order count")
                    }
                )

                Result.success(Unit)
            },
            onFailure = { exception ->
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "=== Order Sync Failed ===")
                Log.e(TAG, "Failed after ${duration}ms", exception)
                Result.failure(exception)
            }
        )
    }

    /**
     * Get order count for statistics
     */
    suspend fun getOrderCount(): Result<Int> {
        Log.d(TAG, "Getting order count")

        return orderRepository.getOrderCountSuspend()
    }

    /**
     * Get distinct trading partners from all orders (for UI filters, analytics, etc.)
     */
    suspend fun getDistinctTradingPartners(): Result<List<TradingPartner>> {
        Log.d(TAG, "Getting distinct trading partners from all orders")

        return orderRepository.getDistinctTradingPartners()
    }

    /**
     * Check order status - useful for voice commands like "What's the status of order 12345?"
     */
    suspend fun checkOrderStatus(orderNumber: String): Result<OrderStatusInfo> {
        Log.d(TAG, "Checking status for order: '$orderNumber'")

        if (orderNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Order number cannot be empty"))
        }

        return getOrderByNumber(orderNumber).fold(
            onSuccess = { order ->
                if (order != null) {
                    // For COMPLETE orders, skip progress API call - they don't need progress details
                    val orderProgress = if (order.status.uppercase() == "COMPLETE") {
                        Log.d(TAG, "Order is COMPLETE - skipping progress API call")
                        null
                    } else {
                        Log.d(TAG, "Order status is ${order.status} - fetching progress details")
                        try {
                            orderProgressGraniteSource.fetchOrderProgress(order.number).getOrNull()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch progress for order ${order.number}", e)
                            null
                        }
                    }

                    val statusInfo = OrderStatusInfo(
                        orderNumber = order.number,
                        status = order.status,
                        statusDisplay = order.getStatusDisplay(),
                        createDate = order.createDate,
                        auditDate = order.auditDate,
                        description = order.description,
                        isActive = order.isActive(),
                        orderProgress = orderProgress,
                        customerName = order.tradingPartnerDescription,
                        customerCode = order.tradingPartnerCode
                    )

                    Log.d(TAG, "Found order $orderNumber with status: ${statusInfo.statusDisplay}")
                    if (orderProgress != null) {
                        Log.d(TAG, "Progress info: ${orderProgress.getProgressSummary()}")
                    } else if (order.status.uppercase() == "COMPLETE") {
                        Log.d(TAG, "Order is complete - no progress details needed")
                    }

                    Result.success(statusInfo)
                } else {
                    Log.w(TAG, "Order not found: $orderNumber")
                    Result.failure(NoSuchElementException("Order not found: $orderNumber"))
                }
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to check order status", exception)
                Result.failure(exception)
            }
        )
    }

    /**
     * Find orders matching fuzzy number search - useful for voice commands
     */
    suspend fun findOrdersByFuzzyNumber(
        spokenNumber: String,
        maxResults: Int = 5
    ): Result<List<Order>> {
        Log.d(TAG, "Fuzzy search for orders matching: '$spokenNumber'")

        if (spokenNumber.isBlank()) {
            return Result.success(emptyList())
        }

        // Try various patterns that might match the spoken input
        val searchPatterns = generateSearchPatterns(spokenNumber)
        val allResults = mutableSetOf<Order>()

        for (pattern in searchPatterns) {
            val result = searchOrdersByNumber(pattern, limit = maxResults)
            result.fold(
                onSuccess = { orders -> allResults.addAll(orders) },
                onFailure = { Log.w(TAG, "Pattern search failed for: $pattern") }
            )

            if (allResults.size >= maxResults) {
                break
            }
        }

        val finalResults = allResults.take(maxResults)
        Log.d(TAG, "Fuzzy search found ${finalResults.size} orders for '$spokenNumber'")

        return Result.success(finalResults)
    }

    /**
     * Generate search patterns for fuzzy matching
     */
    private fun generateSearchPatterns(input: String): List<String> {
        val patterns = mutableListOf<String>()
        val cleaned = input.replace(Regex("[^\\w]"), "").uppercase()

        // Exact match
        patterns.add(cleaned)

        // With common prefixes
        if (!cleaned.startsWith("ORD")) {
            patterns.add("ORD$cleaned")
            patterns.add("ORD-$cleaned")
        }

        // Partial matches (useful for longer numbers)
        if (cleaned.length > 3) {
            patterns.add("*$cleaned*") // Wildcard pattern
            patterns.add("${cleaned.substring(0, 3)}*") // First 3 chars
            patterns.add("*${cleaned.takeLast(3)}") // Last 3 chars
        }

        return patterns.distinct()
    }
}

/**
 * Information about an order's status for voice command responses
 */
data class OrderStatusInfo(
    val orderNumber: String,
    val status: String,
    val statusDisplay: String,
    val createDate: String,
    val auditDate: String,
    val description: String?,
    val isActive: Boolean,
    val orderProgress: OrderProgress? = null,
    val customerName: String? = null,
    val customerCode: String? = null
) {
    /**
     * Format create date with estimated time
     */
    fun getCreateDateWithTime(): String {
        return "$createDate 09:00" // Default morning time for create date
    }

    /**
     * Format audit date with estimated time
     */
    fun getAuditDateWithTime(): String {
        return "$auditDate 17:00" // Default evening time for audit date
    }
}