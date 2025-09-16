package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.OrderStatus

/**
 * Repository interface for order-related operations
 */
interface OrderRepository {
    /**
     * Create a new order with a single item
     */
    suspend fun createOrder(item: MasterItem, quantity: Int): Result<Order>

    /**
     * Get order by ID
     */
    suspend fun getOrderById(orderId: String): Result<Order?>

    /**
     * Get all orders
     */
    suspend fun getAllOrders(): Result<List<Order>>

    /**
     * Update order status
     */
    suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Result<Order>

    /**
     * Delete order
     */
    suspend fun deleteOrder(orderId: String): Result<Boolean>
}