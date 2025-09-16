package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.OrderRepository
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Basic in-memory implementation of OrderRepository for demonstration purposes.
 * In a production app, this would be backed by a proper database.
 */
@Singleton
class OrderRepositoryImpl @Inject constructor() : OrderRepository {

    companion object {
        private const val TAG = "OrderRepository"
    }

    // In-memory storage (replace with proper database in production)
    private val orders = ConcurrentHashMap<String, Order>()

    override suspend fun createOrder(item: MasterItem, quantity: Int): Result<Order> {
        Log.d(TAG, "Creating order for item: ${item.code}, quantity: $quantity")

        return try {
            // Simulate some processing time
            delay(100)

            val orderId = generateOrderId()
            val now = System.currentTimeMillis()

            val orderItem = OrderItem(
                masterItem = item,
                quantity = quantity,
                unitPrice = null, // Could be looked up from a pricing service
                notes = null
            )

            val order = Order(
                id = orderId,
                items = listOf(orderItem),
                status = OrderStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                totalQuantity = quantity,
                notes = "Created via voice command"
            )

            orders[orderId] = order
            Log.d(TAG, "Order created successfully: $orderId")

            Result.success(order)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create order", e)
            Result.failure(e)
        }
    }

    override suspend fun getOrderById(orderId: String): Result<Order?> {
        Log.d(TAG, "Looking up order: $orderId")

        return try {
            // Simulate some lookup time
            delay(50)

            val order = orders[orderId]
            Log.d(TAG, if (order != null) "Order found: $orderId" else "Order not found: $orderId")

            Result.success(order)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup order: $orderId", e)
            Result.failure(e)
        }
    }

    override suspend fun getAllOrders(): Result<List<Order>> {
        Log.d(TAG, "Getting all orders")

        return try {
            // Simulate some processing time
            delay(100)

            val orderList = orders.values.toList().sortedByDescending { it.createdAt }
            Log.d(TAG, "Retrieved ${orderList.size} orders")

            Result.success(orderList)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all orders", e)
            Result.failure(e)
        }
    }

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Order> {
        Log.d(TAG, "Updating order status: $orderId -> $status")

        return try {
            // Simulate some processing time
            delay(100)

            val existingOrder = orders[orderId]
            if (existingOrder == null) {
                Log.w(TAG, "Order not found for status update: $orderId")
                return Result.failure(NoSuchElementException("Order not found: $orderId"))
            }

            val updatedOrder = existingOrder.copy(
                status = status,
                updatedAt = System.currentTimeMillis()
            )

            orders[orderId] = updatedOrder
            Log.d(TAG, "Order status updated successfully: $orderId -> $status")

            Result.success(updatedOrder)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update order status: $orderId", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteOrder(orderId: String): Result<Boolean> {
        Log.d(TAG, "Deleting order: $orderId")

        return try {
            // Simulate some processing time
            delay(50)

            val removed = orders.remove(orderId) != null
            Log.d(
                TAG,
                "Order deletion ${if (removed) "successful" else "failed (not found)"}: $orderId"
            )

            Result.success(removed)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete order: $orderId", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a unique order ID
     */
    private fun generateOrderId(): String {
        // Use a shorter format for better voice command compatibility
        val uuid = UUID.randomUUID().toString()
        return "ORD-${uuid.substring(0, 8).uppercase()}"
    }

    /**
     * Get order count for debugging/testing
     */
    fun getOrderCount(): Int = orders.size

    /**
     * Clear all orders (for testing)
     */
    fun clearAllOrders() {
        orders.clear()
        Log.d(TAG, "All orders cleared")
    }
}