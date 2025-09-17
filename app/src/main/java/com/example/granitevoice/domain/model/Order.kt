package com.example.granitevoice.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * Room entity representing an order from the Granite API
 * Matches the structure returned by QueryDocuments API for ORDER type
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["number"], unique = true),
        Index(value = ["status"]),
        Index(value = ["tradingPartnerCode"])
    ]
)
data class Order(
    @PrimaryKey
    val id: Int,
    val number: String,
    val tradingPartnerCode: String,
    val tradingPartnerDescription: String,
    val createDate: String, // Format: "dd/MM/yyyy"
    val site: String?,
    val status: String, // ENTERED, COMPLETE, CANCELLED, ONHOLD, RELEASED
    val auditDate: String, // Format: "dd/MM/yyyy"
    val auditUser: String,
    val type: String, // Always "ORDER"
    val description: String? = null, // Optional description with carrier info etc
    val priority: Int? = null,
    val erpLocation: String? = null,
    val lastSyncAt: Long = System.currentTimeMillis() // For sync tracking
) {
    /**
     * Parse the create date string to timestamp
     */
    fun getCreateDateTimestamp(): Long {
        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            formatter.parse(createDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Parse the audit date string to timestamp
     */
    fun getAuditDateTimestamp(): Long {
        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            formatter.parse(auditDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get display format for create date
     */
    fun getCreateDateDisplay(): String {
        return createDate
    }

    /**
     * Get display format for audit date
     */
    fun getAuditDateDisplay(): String {
        return auditDate
    }

    /**
     * Get user-friendly status display
     */
    fun getStatusDisplay(): String = when (status.uppercase()) {
        "ENTERED" -> "Entered"
        "COMPLETE" -> "Complete"
        "CANCELLED" -> "Cancelled"
        "ONHOLD" -> "On Hold"
        "RELEASED" -> "Released"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * Check if order is active (not cancelled or complete)
     */
    fun isActive(): Boolean {
        return status.uppercase() !in listOf("CANCELLED", "COMPLETE")
    }

    /**
     * Parse description for carrier information if present
     * Format: "Carrier:value|Driver:value|Seal:value|Temp:value"
     */
    fun parseDescription(): OrderDescription? {
        return description?.let { desc ->
            try {
                val parts = desc.split("|")
                val map = parts.associate { part ->
                    val (key, value) = part.split(":", limit = 2)
                    key.trim() to value.trim()
                }
                OrderDescription(
                    carrier = map["Carrier"],
                    driver = map["Driver"],
                    seal = map["Seal"],
                    temp = map["Temp"]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Parsed description data from order
 */
data class OrderDescription(
    val carrier: String? = null,
    val driver: String? = null,
    val seal: String? = null,
    val temp: String? = null
)

/**
 * Trading partner information for filtering and display
 */
data class TradingPartner(
    val code: String,
    val description: String
)

/**
 * Legacy OrderItem and OrderStatus classes for backward compatibility
 * These can be removed once all voice command processing is updated
 */
@Deprecated("Use Order entity directly with sync from API")
data class OrderItem(
    val masterItem: MasterItem,
    val quantity: Int,
    val unitPrice: Double? = null,
    val notes: String? = null
) {
    fun getTotalPrice(): Double? = unitPrice?.times(quantity)
}

@Deprecated("Use Order.status string field directly")
enum class OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}