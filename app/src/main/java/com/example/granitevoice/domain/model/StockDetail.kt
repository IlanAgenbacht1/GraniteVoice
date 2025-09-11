package com.example.granitevoice.domain.model

/**
 * Domain model for stock detail information
 */
data class StockDetail(
    val code: String,
    val barcode: String?,
    val site: String?,
    val quantity: Double?,
    val availableQuantity: Double?,
    val location: String?,
    val description: String?,
    val unitOfMeasure: String?,
    val lastUpdated: String?
) {
    /**
     * Get a human-readable quantity string with proper pluralization
     */
    fun getQuantityDisplay(): String {
        return when {
            availableQuantity != null && availableQuantity > 0 -> {
                formatQuantityWithUOM(availableQuantity, unitOfMeasure, "available")
            }

            quantity != null && quantity > 0 -> {
                formatQuantityWithUOM(quantity, unitOfMeasure, "in stock")
            }

            availableQuantity != null && availableQuantity == 0.0 -> "Out of stock"
            quantity != null && quantity == 0.0 -> "Out of stock"
            else -> "Quantity unknown"
        }
    }

    /**
     * Get location display string
     */
    fun getLocationDisplay(): String {
        return when {
            !location.isNullOrBlank() && !site.isNullOrBlank() -> "$location ($site)"
            !location.isNullOrBlank() -> location
            !site.isNullOrBlank() -> site
            else -> "Location unknown"
        }
    }

    /**
     * Check if item is in stock
     */
    fun isInStock(): Boolean {
        return (availableQuantity ?: quantity ?: 0.0) > 0.0
    }
}

/**
 * Helper extension to format double as display string
 */
private fun Double.toDisplayString(): String {
    return if (this == this.toLong().toDouble()) {
        this.toLong().toString()
    } else {
        String.format("%.2f", this)
    }
}

/**
 * Helper function to format quantity with proper UOM pluralization
 */
private fun formatQuantityWithUOM(
    quantity: Double,
    unitOfMeasure: String?,
    status: String
): String {
    val quantityStr = quantity.toDisplayString()

    if (unitOfMeasure.isNullOrBlank()) {
        return "$quantityStr $status"
    }

    // Convert UOM to user-friendly format with proper pluralization
    val formattedUOM = when {
        quantity == 1.0 -> unitOfMeasure.toSingularForm()
        else -> unitOfMeasure.toPluralForm()
    }

    return "$quantityStr $formattedUOM $status"
}

/**
 * Convert UOM to singular form
 */
private fun String.toSingularForm(): String {
    return when (this.uppercase()) {
        "CASES", "CASE" -> "case"
        "BOXES", "BOX" -> "box"
        "PIECES", "PCS", "PC" -> "piece"
        "POUNDS", "LBS", "LB" -> "pound"
        "OUNCES", "OZ" -> "ounce"
        "GALLONS", "GAL" -> "gallon"
        "LITERS", "L" -> "liter"
        "EACH", "EA" -> "item"
        "BAGS", "BAG" -> "bag"
        "BOTTLES", "BTL" -> "bottle"
        "CANS", "CAN" -> "can"
        "PACKS", "PACK" -> "pack"
        else -> this.lowercase()
    }
}

/**
 * Convert UOM to plural form
 */
private fun String.toPluralForm(): String {
    return when (this.uppercase()) {
        "CASE", "CASES" -> "cases"
        "BOX", "BOXES" -> "boxes"
        "PIECE", "PIECES", "PCS", "PC" -> "pieces"
        "POUND", "POUNDS", "LBS", "LB" -> "pounds"
        "OUNCE", "OUNCES", "OZ" -> "ounces"
        "GALLON", "GALLONS", "GAL" -> "gallons"
        "LITER", "LITERS", "L" -> "liters"
        "EACH", "EA" -> "items"
        "BAG", "BAGS" -> "bags"
        "BOTTLE", "BOTTLES", "BTL" -> "bottles"
        "CAN", "CANS" -> "cans"
        "PACK", "PACKS" -> "packs"
        else -> this.lowercase() + "s"
    }
}

/**
 * Aggregate stock information for an item across multiple locations/sites
 */
data class ItemStockSummary(
    val masterItem: MasterItem,
    val stockDetails: List<StockDetail>,
    val totalQuantity: Double,
    val totalAvailable: Double,
    val locationCount: Int,
    val unitOfMeasure: String? = null // UOM from master item
) {
    fun getTotalQuantityDisplay(): String {
        return when {
            totalAvailable > 0 -> formatQuantityWithUOM(totalAvailable, unitOfMeasure, "available")
            totalQuantity > 0 -> formatQuantityWithUOM(totalQuantity, unitOfMeasure, "in stock")
            else -> "Out of stock"
        }
    }

    fun getLocationSummary(): String {
        return when {
            locationCount == 0 -> "No locations"
            locationCount == 1 -> "1 location"
            else -> "$locationCount locations"
        }
    }

    fun isInStock(): Boolean = totalAvailable > 0 || totalQuantity > 0
}