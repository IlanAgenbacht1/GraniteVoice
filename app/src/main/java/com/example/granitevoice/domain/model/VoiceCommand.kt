package com.example.granitevoice.domain.model

import com.example.granitevoice.domain.usecase.OrderStatusInfo

/**
 * Represents the result of voice command processing
 */
data class VoiceCommandResult(
    val originalTranscription: String,
    val intent: CommandIntent,
    val extractedQuery: String,
    val matchedItem: MasterItem?,
    val confidence: Float,
    val alternativeMatches: List<MasterItem> = emptyList(),
    val processingSteps: List<ProcessingStep> = emptyList(),
    // New fields for multiple stock lookups
    val stockSummaries: Map<String, ItemStockSummary> = emptyMap(),
    val stockLookupErrors: Map<String, String> = emptyMap(),
    // Order status information
    val orderStatusInfo: OrderStatusInfo? = null,
    // Legacy fields for backward compatibility
    val stockSummary: ItemStockSummary? = null,
    val stockLookupError: String? = null
) {
    /**
     * Get display-friendly result summary
     */
    fun getResultSummary(): String {
        return when {
            // Order status results
            orderStatusInfo != null -> {
                val orderInfo = orderStatusInfo
                if (orderInfo.orderProgress != null) {
                    "Order ${orderInfo.orderNumber}: ${orderInfo.orderProgress.getProgressSummary()}"
                } else {
                    "Order ${orderInfo.orderNumber}: ${orderInfo.statusDisplay}"
                }
            }

            // Stock lookup results
            matchedItem != null && stockSummaries.isNotEmpty() -> {
                val primaryMatchSummary = stockSummaries[matchedItem.code]
                if (primaryMatchSummary != null) {
                    "${matchedItem.description}: ${primaryMatchSummary.getTotalQuantityDisplay()}"
                } else {
                    "${matchedItem.description} (Stock info unavailable)"
                }
            }

            matchedItem != null -> {
                "${matchedItem.description} (Stock info unavailable)"
            }

            // Handle case where no primary match but we have alternatives with stock
            alternativeMatches.isNotEmpty() && stockSummaries.isNotEmpty() -> {
                val firstAlt = alternativeMatches.first()
                val altSummary = stockSummaries[firstAlt.code]
                if (altSummary != null) {
                    "Similar: ${firstAlt.description}: ${altSummary.getTotalQuantityDisplay()}"
                } else {
                    "Similar items found: ${alternativeMatches.size} matches"
                }
            }

            alternativeMatches.isNotEmpty() -> {
                "Similar items found: ${alternativeMatches.size} matches"
            }

            else -> "No match found for '$extractedQuery'"
        }
    }

    /**
     * Check if we have complete stock information
     */
    fun hasCompleteStockInfo(): Boolean {
        // We have complete stock info if we have any stock summaries and no errors
        return stockSummaries.isNotEmpty() && stockLookupErrors.isEmpty()
    }

    /**
     * Get user-friendly status message
     */
    fun getStatusMessage(): String {
        return when {
            // Order status results
            orderStatusInfo != null -> {
                val orderInfo = orderStatusInfo
                when {
                    orderInfo.orderProgress?.isComplete() == true -> {
                        "✅ ${orderInfo.orderProgress.getProgressSummary()}"
                    }

                    orderInfo.orderProgress?.isBlocked() == true -> {
                        "❌ ${orderInfo.orderProgress.getProgressSummary()}"
                    }

                    orderInfo.orderProgress?.isInProgress() == true -> {
                        "🚚 ${orderInfo.orderProgress.getProgressSummary()}"
                    }

                    orderInfo.orderProgress != null -> {
                        "📋 ${orderInfo.orderProgress.getProgressSummary()}"
                    }

                    else -> {
                        "📋 Order ${orderInfo.orderNumber}: ${orderInfo.statusDisplay}"
                    }
                }
            }

            // Stock lookup results
            hasCompleteStockInfo() -> {
                if (stockSummaries.values.all { it.isInStock() }) {
                    "✅ In Stock: ${
                        stockSummaries.values.map { it.getTotalQuantityDisplay() }.joinToString()
                    }"
                } else {
                    "❌ Out of Stock"
                }
            }

            matchedItem != null && hasStockError(matchedItem.code) -> {
                "⚠️ Item found but stock lookup failed: ${getStockLookupError(matchedItem.code)}"
            }

            matchedItem != null -> {
                "ℹ️ Item found, checking stock..."
            }

            // Handle alternatives when no primary match
            alternativeMatches.isNotEmpty() && stockSummaries.isNotEmpty() -> {
                val inStockCount =
                    alternativeMatches.count { hasStockInfo(it.code) && getStockSummary(it.code)!!.isInStock() }
                "📋 ${alternativeMatches.size} similar items found, $inStockCount in stock"
            }

            alternativeMatches.isNotEmpty() -> {
                "📋 ${alternativeMatches.size} similar items found"
            }

            confidence > 0.4f -> {
                "❓ No exact match found. Try being more specific."
            }

            else -> {
                "❌ Command not understood. Try: 'Find stock for [item name]' or 'Check order [number]'"
            }
        }
    }

    /**
     * Get information about alternative matches
     */
    fun getAlternativesInfo(): String {
        return when {
            alternativeMatches.isEmpty() -> "No alternatives found"
            alternativeMatches.size == 1 -> "1 alternative: ${alternativeMatches[0].description}"
            else -> "${alternativeMatches.size} alternatives available"
        }
    }

    /**
     * Get all matches (primary + alternatives) for display
     */
    fun getAllMatches(): List<MasterItem> {
        return if (matchedItem != null) {
            listOf(matchedItem) + alternativeMatches
        } else {
            alternativeMatches
        }
    }

    /**
     * Check if there are alternative matches available
     */
    fun hasAlternatives(): Boolean = alternativeMatches.isNotEmpty()

    /**
     * Get confidence level description
     */
    fun getConfidenceLevel(): String {
        return when {
            confidence >= 0.9f -> "Very High"
            confidence >= 0.8f -> "High"
            confidence >= 0.7f -> "Good"
            confidence >= 0.6f -> "Moderate"
            confidence >= 0.5f -> "Low"
            else -> "Very Low"
        }
    }

    /**
     * Get stock summary for a specific item by code
     */
    fun getStockSummary(itemCode: String): ItemStockSummary? {
        return stockSummaries[itemCode]
    }

    /**
     * Get stock lookup error for a specific item by code
     */
    fun getStockLookupError(itemCode: String): String? {
        return stockLookupErrors[itemCode]
    }

    /**
     * Check if stock info is available for a specific item
     */
    fun hasStockInfo(itemCode: String): Boolean {
        return stockSummaries.containsKey(itemCode)
    }

    /**
     * Check if there was a stock lookup error for a specific item
     */
    fun hasStockError(itemCode: String): Boolean {
        return stockLookupErrors.containsKey(itemCode)
    }

    /**
     * Get all items that have stock information
     */
    fun getItemsWithStockInfo(): List<MasterItem> {
        val allItems = getAllMatches()
        return allItems.filter { hasStockInfo(it.code) }
    }

    /**
     * Get status message for a specific item
     */
    fun getItemStatusMessage(item: MasterItem): String {
        return when {
            hasStockInfo(item.code) -> {
                val summary = getStockSummary(item.code)!!
                if (summary.isInStock()) {
                    "✅ In Stock: ${summary.getTotalQuantityDisplay()}"
                } else {
                    "❌ Out of Stock"
                }
            }

            hasStockError(item.code) -> {
                "⚠️ Stock lookup failed: ${getStockLookupError(item.code)}"
            }

            else -> {
                "ℹ️ Stock info not available"
            }
        }
    }

    /**
     * Get total number of matches (primary + alternatives)
     */
    fun getTotalMatchCount(): Int {
        return if (matchedItem != null) {
            1 + alternativeMatches.size
        } else {
            alternativeMatches.size
        }
    }

    /**
     * Get item at specific index for cycling (0-based)
     */
    fun getItemAtIndex(index: Int): MasterItem? {
        val allItems = getAllMatches()
        return if (index >= 0 && index < allItems.size) {
            allItems[index]
        } else {
            null
        }
    }

    /**
     * Get index of a specific item in the cycling list
     */
    fun getIndexOfItem(item: MasterItem): Int {
        val allItems = getAllMatches()
        return allItems.indexOfFirst { it.code == item.code }
    }

    /**
     * Check if cycling through items is available
     */
    fun canCycle(): Boolean {
        return getTotalMatchCount() > 1
    }

    /**
     * Get next item index for cycling
     */
    fun getNextIndex(currentIndex: Int): Int {
        val totalCount = getTotalMatchCount()
        return if (totalCount > 1) {
            (currentIndex + 1) % totalCount
        } else {
            currentIndex
        }
    }

    /**
     * Get previous item index for cycling
     */
    fun getPreviousIndex(currentIndex: Int): Int {
        val totalCount = getTotalMatchCount()
        return if (totalCount > 1) {
            if (currentIndex == 0) totalCount - 1 else currentIndex - 1
        } else {
            currentIndex
        }
    }

    /**
     * Check if we have any results to display (primary match or alternatives)
     */
    fun hasResults(): Boolean {
        return matchedItem != null || alternativeMatches.isNotEmpty()
    }

    /**
     * Get comprehensive item info for display
     */
    fun getItemInfo(item: MasterItem): ItemDisplayInfo {
        val stockSummary = getStockSummary(item.code)
        val stockError = getStockLookupError(item.code)

        return ItemDisplayInfo(
            item = item,
            stockSummary = stockSummary,
            stockError = stockError,
            statusMessage = getItemStatusMessage(item),
            isPrimaryMatch = item.code == matchedItem?.code
        )
    }
}

/**
 * Display information for an item with stock details
 */
data class ItemDisplayInfo(
    val item: MasterItem,
    val stockSummary: ItemStockSummary?,
    val stockError: String?,
    val statusMessage: String,
    val isPrimaryMatch: Boolean
)

/**
 * Represents the intent extracted from voice command
 */
sealed class CommandIntent {
    object StockLookup : CommandIntent()
    data class CheckOrderStatus(val orderId: String) : CommandIntent()
    object Unknown : CommandIntent()
    data class Other(val description: String) : CommandIntent()
}

/**
 * Represents a step in the voice command processing pipeline
 */
data class ProcessingStep(
    val step: String,
    val input: String,
    val output: String,
    val confidence: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Configuration for voice command processing
 */
data class VoiceProcessingConfig(
    val maxAlternatives: Int = 5,
    val minimumConfidence: Float = 0.6f,
    val useAiFuzzyMatching: Boolean = true,
    val enableProcessingLogging: Boolean = true,
    val maxProcessingSteps: Int = 4, // Updated to include stock lookup step
    val enableStockLookup: Boolean = true,
    val stockLookupTimeoutMs: Long = 10000L // 10 second timeout for stock lookups
)