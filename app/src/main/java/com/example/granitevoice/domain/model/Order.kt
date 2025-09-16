package com.example.granitevoice.domain.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents an order in the system
 */
data class Order(
    val id: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val createdAt: Long, // Unix timestamp in milliseconds
    val updatedAt: Long, // Unix timestamp in milliseconds
    val totalQuantity: Int,
    val notes: String? = null
) {
    fun getTotalItems(): Int = items.size

    fun getStatusDisplay(): String = when (status) {
        OrderStatus.PENDING -> "Pending"
        OrderStatus.IN_PROGRESS -> "In Progress"
        OrderStatus.COMPLETED -> "Completed"
        OrderStatus.CANCELLED -> "Cancelled"
    }

    /**
     * Format the creation date for display
     */
    fun getCreatedAtDisplay(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(createdAt))
    }

    /**
     * Format the updated date for display
     */
    fun getUpdatedAtDisplay(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(updatedAt))
    }
}

/**
 * Represents an item within an order
 */
data class OrderItem(
    val masterItem: MasterItem,
    val quantity: Int,
    val unitPrice: Double? = null,
    val notes: String? = null
) {
    fun getTotalPrice(): Double? = unitPrice?.times(quantity)
}

/**
 * Status of an order
 */
enum class OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}