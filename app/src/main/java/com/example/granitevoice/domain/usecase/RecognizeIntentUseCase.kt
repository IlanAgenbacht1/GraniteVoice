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

        // Enhanced order status detection using patterns instead of simple keywords
        Log.d(TAG, "=== Checking for order status intent (enhanced patterns) ===")
        val orderStatusMatch = checkForOrderStatusEnhanced(lowerTranscription)
        if (orderStatusMatch != null) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, " Intent: CheckOrderStatus (${duration}ms)")
            Log.d(TAG, "Order ID: '${orderStatusMatch.orderId}'")
            Log.d(TAG, "=== Intent recognition completed ===")
            return orderStatusMatch
        }

        Log.d(TAG, " Order status intent not detected, checking stock intent...")

        // Check for stock lookup intent (existing logic)
        Log.d(TAG, "=== Checking for stock lookup intent ===")
        val stockKeywords = listOf(
            "stock", "inventory", "item", "product", "part", "component",
            "lookup", "look up", "find", "search", "get", "show", "check",
            "what is", "where is", "do we have", "how much", "how many",
            "quantity", "available", "in stock", "locate", "tell me about"
        )

        Log.d(TAG, "Checking against ${stockKeywords.size} stock keywords")
        Log.d(TAG, "Stock keywords: ${stockKeywords.joinToString(", ")}")

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

    /**
     * Enhanced order status detection using flexible patterns
     */
    private fun checkForOrderStatusEnhanced(transcription: String): CommandIntent.CheckOrderStatus? {
        Log.d(TAG, "Checking for order status with enhanced patterns...")
        Log.d(TAG, "Input: '$transcription'")

        // Define flexible patterns that capture order intent variations
        val orderIntentPatterns = listOf(
            // Pattern 1: "show/get/tell me [the] order [anything]"
            Regex("(?:show me|get me|tell me) (?:the )?order\\s+(.+?)(?:[.,!?]|\\s*$)"),

            // Pattern 2: "order [ID/name] [status context]"
            Regex("order\\s+([\\w\\d\\s-_]+?)\\s*(?:status|info|details|progress)?(?:[.,!?]|\\s*$)"),

            // Pattern 3: "[check/track/find] [the] order [anything]"
            Regex("(?:check|track|find) (?:the )?order\\s+(.+?)(?:[.,!?]|\\s*$)"),

            // Pattern 4: "what is/where is [the] order [anything]"
            Regex("(?:what is|where is) (?:the )?order\\s+(.+?)(?:[.,!?]|\\s*$)"),

            // Pattern 5: "order status for/of [anything]"
            Regex("order status (?:for|of)\\s+(.+?)(?:[.,!?]|\\s*$)"),

            // Pattern 6: "status of order [anything]"
            Regex("status of order\\s+(.+?)(?:[.,!?]|\\s*$)"),

            // Pattern 7: Any sentence with "order" and "status" words
            Regex(".*order.*status.*"),

            // Pattern 8: Any sentence with "order" and NOT stock-specific words
            Regex(".*order\\s+([\\w\\d\\s-_]+?)(?:[.,!?]|\\s*$)")
        )

        for ((index, pattern) in orderIntentPatterns.withIndex()) {
            Log.d(TAG, "  Testing pattern ${index + 1}: ${pattern.pattern}")

            val match = pattern.find(transcription)
            if (match != null) {
                Log.d(TAG, "  Pattern ${index + 1} matched: '${match.value}'")

                // For patterns that capture an ID/name, extract it
                if (match.groupValues.size > 1) {
                    val extractedId = match.groupValues[1].trim()
                    if (extractedId.isNotBlank()) {
                        Log.d(TAG, "  Extracted order ID: '$extractedId'")
                        return CommandIntent.CheckOrderStatus(extractedId)
                    }
                }

                // For Pattern 7 (order + status words), try to extract ID from whole string
                if (index == 6) { // Pattern 7
                    val orderIdExtracted = extractOrderIdFromStatusContext(transcription)
                    if (orderIdExtracted != null) {
                        Log.d(TAG, "  Extracted order ID from context: '$orderIdExtracted'")
                        return CommandIntent.CheckOrderStatus(orderIdExtracted)
                    }
                }

                // For Pattern 8, make sure it's not a stock command
                if (index == 7) { // Pattern 8 
                    if (!hasStockSpecificWords(transcription)) {
                        val extractedId = match.groupValues[1].trim()
                        if (extractedId.isNotBlank()) {
                            Log.d(TAG, "  Non-stock order command, extracted ID: '$extractedId'")
                            return CommandIntent.CheckOrderStatus(extractedId)
                        }
                    } else {
                        Log.d(
                            TAG,
                            "  Pattern ${index + 1} matches but contains stock-specific words"
                        )
                    }
                }
            } else {
                Log.d(TAG, "  Pattern ${index + 1} did not match")
            }
        }

        Log.d(TAG, "❌ No order status patterns matched")
        return null
    }

    /**
     * Extract order ID when we know it's an order status context
     */
    private fun extractOrderIdFromStatusContext(transcription: String): String? {
        // Look for order-like identifiers in status context
        val orderIdPatterns = listOf(
            Regex("order\\s+([\\w\\d\\s-_]+?)(?:\\s+status|$)"),
            Regex("status.*order\\s+([\\w\\d\\s-_]+?)(?:[.,!?]|$)"),
            Regex("\\b(ord[\\s-]*\\d+[\\w\\d-]*)\\b"),
            Regex("\\b([\\w\\d]{3,})\\b") // Any alphanumeric 3+ chars as fallback
        )

        for (pattern in orderIdPatterns) {
            val match = pattern.find(transcription)
            if (match != null && match.groupValues.size > 1) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty()) {
                    return extracted
                }
            }
        }
        return null
    }

    /**
     * Check if transcription contains stock-specific words that suggest it's not an order command
     */
    private fun hasStockSpecificWords(transcription: String): Boolean {
        val stockSpecificWords = listOf(
            "inventory", "stock level", "in stock", "out of stock",
            "stock detail", "quantity available", "warehouse", "location",
            "product", "item", "part", "component"
        )

        return stockSpecificWords.any { word ->
            transcription.contains(word, ignoreCase = true)
        }
    }
}