package com.example.granitevoice.domain.model

/**
 * Domain model representing detailed order progress information
 * From QueryDocumentProgress API - shows completion status, quantities, etc.
 */
data class OrderProgress(
    val number: String,
    val type: String, // Always "ORDER"
    val site: String?,
    val status: String,
    val percentComplete: Float, // 0.0 to 100.0
    val actionQty: Double, // Quantity being processed/actioned
    val qty: Double, // Total quantity
    val tradingPartnerCode: String?,
    val tradingPartnerDescription: String?,
    val createDate: String?,
    val description: String?
) {
    /**
     * Get user-friendly status display
     */
    fun getStatusDisplay(): String = when (status.uppercase()) {
        "ENTERED" -> "Entered"
        "COMPLETE" -> "Complete"
        "CANCELLED" -> "Cancelled"
        "ONHOLD" -> "On Hold"
        "RELEASED" -> "Released"
        "PICKING" -> "Picking"
        "PACKING" -> "Packing"
        "SHIPPING" -> "Shipping"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * Get progress percentage as integer
     */
    fun getProgressPercent(): Int {
        // Handle the progress value - API might return 0-100 or 0-1 range
        val normalizedProgress = when {
            percentComplete <= 1.0f -> percentComplete * 100f // 0-1 range, convert to percentage
            else -> percentComplete // Already in 0-100 range
        }
        return normalizedProgress.toInt().coerceIn(0, 100)
    }

    /**
     * Get progress display text
     */
    fun getProgressDisplay(): String {
        return "${getProgressPercent()}% Complete"
    }

    /**
     * Get quantity progress display
     */
    fun getQuantityDisplay(): String {
        return when {
            qty > 0 && actionQty > 0 -> {
                "${actionQty.toInt()}/${qty.toInt()} items"
            }

            qty > 0 -> {
                "${qty.toInt()} items"
            }

            actionQty > 0 -> {
                "${actionQty.toInt()} items processed"
            }

            else -> "Quantity info unavailable"
        }
    }

    /**
     * Check if order is actively being processed
     */
    fun isInProgress(): Boolean {
        return status.uppercase() in listOf("PICKING", "PACKING", "SHIPPING", "RELEASED")
    }

    /**
     * Check if order is complete (based on actual status, not progress percentage)
     */
    fun isComplete(): Boolean {
        return status.uppercase() == "COMPLETE"
    }

    /**
     * Check if order is on hold or cancelled
     */
    fun isBlocked(): Boolean {
        return status.uppercase() in listOf("ONHOLD", "CANCELLED")
    }

    /**
     * Get progress color for UI
     */
    fun getProgressColor(): ProgressColor {
        return when {
            isComplete() -> ProgressColor.SUCCESS
            isBlocked() -> ProgressColor.ERROR
            isInProgress() -> ProgressColor.WARNING
            percentComplete > 0f -> ProgressColor.INFO
            else -> ProgressColor.NEUTRAL
        }
    }

    /**
     * Get detailed progress summary for voice responses
     */
    fun getProgressSummary(): String {
        return buildString {
            append("Order ${number}: ${getStatusDisplay()}")

            // Only show percentage if it adds meaningful information
            when {
                status.uppercase() == "COMPLETE" -> {
                    // Don't add percentage for completed orders - status is clear enough
                }

                percentComplete >= 100f && !isComplete() -> {
                    append(", Processing Complete")
                }

                percentComplete > 0f -> {
                    append(", ${getProgressDisplay()}")
                }
            }

            if (actionQty > 0 || qty > 0) {
                append(", ${getQuantityDisplay()}")
            }
            if (tradingPartnerDescription != null) {
                append(" for ${tradingPartnerDescription}")
            }
        }
    }
}

/**
 * Progress color states for UI
 */
enum class ProgressColor {
    SUCCESS,  // Complete
    ERROR,    // Cancelled/On Hold
    WARNING,  // In Progress
    INFO,     // Started
    NEUTRAL   // Not started
}