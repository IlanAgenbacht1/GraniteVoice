package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.CommandIntent
import javax.inject.Inject

class RecognizeIntentUseCase @Inject constructor() {

    companion object {
        private const val TAG = "IntentRecognition"
    }

    operator fun invoke(transcription: String): CommandIntent {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Starting intent recognition ===")
        Log.d(TAG, "Input: '$transcription'")

        val lowerTranscription = transcription.lowercase()
        Log.d(TAG, "Normalized: '$lowerTranscription'")

        // Check for order creation intent first (most specific)
        val orderCreationKeywords = listOf(
            "create order", "add order", "make order", "new order",
            "order", "add to cart", "add item", "purchase"
        )

        val orderCreationMatch = checkForOrderCreation(lowerTranscription, orderCreationKeywords)
        if (orderCreationMatch != null) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, " Intent: CreateOrderItem (${duration}ms)")
            Log.d(
                TAG,
                "Item: '${orderCreationMatch.itemName}', Quantity: ${orderCreationMatch.quantity}"
            )
            Log.d(TAG, "=== Intent recognition completed ===")
            return orderCreationMatch
        }

        // Check for order status intent
        val orderStatusKeywords = listOf(
            "check order", "order status", "status of order", "order info",
            "where is order", "track order", "find order"
        )

        val orderStatusMatch = checkForOrderStatus(lowerTranscription, orderStatusKeywords)
        if (orderStatusMatch != null) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, " Intent: CheckOrderStatus (${duration}ms)")
            Log.d(TAG, "Order ID: '${orderStatusMatch.orderId}'")
            Log.d(TAG, "=== Intent recognition completed ===")
            return orderStatusMatch
        }

        // Check for stock lookup intent (existing logic)
        val stockKeywords = listOf(
            "stock", "inventory", "item", "product", "part", "component",
            "lookup", "look up", "find", "search", "get", "show", "check",
            "what is", "where is", "do we have", "how much", "how many",
            "quantity", "available", "in stock", "locate", "tell me about"
        )

        Log.d(TAG, "Checking against ${stockKeywords.size} stock keywords")

        val matchedKeywords = mutableListOf<String>()
        val hasStockIntent = stockKeywords.any { keyword ->
            val matches = lowerTranscription.contains(keyword)
            if (matches) {
                matchedKeywords.add(keyword)
                Log.d(TAG, "  Matched keyword: '$keyword'")
            }
            matches
        }

        val intent = if (hasStockIntent) {
            CommandIntent.StockLookup
        } else {
            CommandIntent.Unknown
        }

        val duration = System.currentTimeMillis() - startTime

        if (hasStockIntent) {
            Log.d(TAG, " Intent: StockLookup (${duration}ms)")
            Log.d(
                TAG,
                "Matched ${matchedKeywords.size} keywords: ${matchedKeywords.joinToString(", ")}"
            )
        } else {
            Log.d(TAG, " Intent: Unknown (${duration}ms)")
            Log.d(TAG, "No recognized intents found in transcription")

            // Log for debugging - what words were in the transcription?
            val words = lowerTranscription.split(" ").filter { it.isNotBlank() }
            Log.d(TAG, "Transcription contained ${words.size} words: ${words.joinToString(", ")}")
        }

        Log.d(TAG, "=== Intent recognition completed ===")
        return intent
    }

    private fun checkForOrderCreation(
        transcription: String,
        keywords: List<String>
    ): CommandIntent.CreateOrderItem? {
        Log.d(TAG, "Checking for order creation intent...")

        val hasOrderKeyword = keywords.any { keyword ->
            val matches = transcription.contains(keyword)
            if (matches) {
                Log.d(TAG, "  Matched order keyword: '$keyword'")
            }
            matches
        }

        if (!hasOrderKeyword) {
            Log.d(TAG, "No order creation keywords found")
            return null
        }

        // Try to extract item name and quantity
        // Patterns like: "create order for ABC123 quantity 5", "order 10 of item XYZ"
        val patterns = listOf(
            // "create order for [item] quantity [number]"
            Regex("(?:create|add|make|new)?\\s*order\\s+(?:for\\s+)?([a-zA-Z0-9\\s-_]+?)\\s+(?:quantity|qty|count)\\s+(\\d+)"),
            // "order [number] of [item]"  
            Regex("order\\s+(\\d+)\\s+(?:of|pieces|units)?\\s+(?:of\\s+)?([a-zA-Z0-9\\s-_]+)"),
            // "[number] of [item]"
            Regex("(\\d+)\\s+(?:of|pieces|units)?\\s+(?:of\\s+)?([a-zA-Z0-9\\s-_]+)"),
            // "add [item] [number]"
            Regex("(?:add|purchase)\\s+([a-zA-Z0-9\\s-_]+?)\\s+(\\d+)"),
            // Generic fallback: assume last number is quantity, everything else is item
            Regex("([a-zA-Z0-9\\s-_]+?)\\s+(\\d+)\\s*$")
        )

        for (pattern in patterns) {
            val match = pattern.find(transcription)
            if (match != null && match.groupValues.size >= 3) {
                val group1 = match.groupValues[1].trim()
                val group2 = match.groupValues[2].trim()

                // Determine which group is item name and which is quantity
                val quantityInt = group2.toIntOrNull() ?: group1.toIntOrNull()
                val itemName = if (group2.toIntOrNull() != null) group1 else group2

                if (quantityInt != null && quantityInt > 0 && itemName.isNotBlank()) {
                    Log.d(
                        TAG,
                        "Extracted order details - Item: '$itemName', Quantity: $quantityInt"
                    )
                    return CommandIntent.CreateOrderItem(itemName.trim(), quantityInt)
                }
            }
        }

        Log.d(TAG, "Could not extract item name and quantity from order command")
        return null
    }

    private fun checkForOrderStatus(
        transcription: String,
        keywords: List<String>
    ): CommandIntent.CheckOrderStatus? {
        Log.d(TAG, "Checking for order status intent...")

        val hasStatusKeyword = keywords.any { keyword ->
            val matches = transcription.contains(keyword)
            if (matches) {
                Log.d(TAG, "  Matched order status keyword: '$keyword'")
            }
            matches
        }

        if (!hasStatusKeyword) {
            Log.d(TAG, "No order status keywords found")
            return null
        }

        // Try to extract order ID
        // Patterns like: "check order ABC123", "status of order 12345", "where is order XYZ-456"
        val patterns = listOf(
            // "check/track/find order [ID]"
            Regex("(?:check|track|find|status\\s+of)\\s+order\\s+([a-zA-Z0-9\\s-_]+)"),
            // "order [ID] status"
            Regex("order\\s+([a-zA-Z0-9\\s-_]+?)\\s+(?:status|info)"),
            // Generic "order [ID]" pattern
            Regex("order\\s+([a-zA-Z0-9\\s-_]+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(transcription)
            if (match != null && match.groupValues.size >= 2) {
                val orderId = match.groupValues[1].trim()
                if (orderId.isNotBlank()) {
                    Log.d(TAG, "Extracted order ID: '$orderId'")
                    return CommandIntent.CheckOrderStatus(orderId)
                }
            }
        }

        Log.d(TAG, "Could not extract order ID from status command")
        return null
    }
}