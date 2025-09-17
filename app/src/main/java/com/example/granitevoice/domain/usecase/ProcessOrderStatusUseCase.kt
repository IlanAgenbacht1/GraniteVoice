package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.data.remote.client.OrderFuzzyMatchClient
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Use case responsible for processing order status lookup commands
 * Follows the same pattern as ProcessStockLookupUseCase but adapted for orders
 * Implements 3-tier matching: Database search → Algorithmic fuzzy → AI fallback
 */
class ProcessOrderStatusUseCase @Inject constructor(
    private val orderUseCase: OrderUseCase,
    private val voiceProcessingRepository: VoiceProcessingRepository,
    private val config: VoiceProcessingConfig
) {

    companion object {
        private const val TAG = "OrderStatusUseCase"
        private const val ORDER_LOOKUP_TIMEOUT_MS = 10000L
    }

    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        Log.d(TAG, "=== Starting order status processing ===")
        Log.d(TAG, "Transcription: '$transcription'")

        val processingSteps = mutableListOf<ProcessingStep>()

        // Step 1: Use the GENERIC repository to clean the transcription
        val cleanTranscriptionStartTime = System.currentTimeMillis()
        val cleanedTranscription = voiceProcessingRepository.cleanTranscription(transcription)
            .getOrDefault(transcription)
        val cleanTranscriptionDuration = System.currentTimeMillis() - cleanTranscriptionStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Generic Text Cleaning",
                input = transcription,
                output = cleanedTranscription
            )
        )

        Log.d(
            TAG,
            "Generic cleaning completed in ${cleanTranscriptionDuration}ms: '$transcription' -> '$cleanedTranscription'"
        )

        // Step 2: Extract order number and customer information
        val extractionStartTime = System.currentTimeMillis()
        val extractedInfo = extractOrderInfoFromCleanedText(cleanedTranscription)
        val extractionDuration = System.currentTimeMillis() - extractionStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Order-Specific Query Extraction",
                input = cleanedTranscription,
                output = "Order: '${extractedInfo.orderNumber}', Customer: '${extractedInfo.customerName ?: "none"}'"
            )
        )

        Log.d(
            TAG,
            "Order extraction completed in ${extractionDuration}ms: Order='${extractedInfo.orderNumber}', Customer='${extractedInfo.customerName}'"
        )

        // Step 3: Search for Orders
        val searchStartTime = System.currentTimeMillis()
        val searchResults = searchOrdersByNumber(extractedInfo.orderNumber)
        val searchDuration = System.currentTimeMillis() - searchStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Order Search",
                input = extractedInfo.orderNumber,
                output = "Found ${searchResults.size} matches"
            )
        )

        Log.d(
            TAG,
            "Order search completed in ${searchDuration}ms: found ${searchResults.size} matches"
        )

        if (searchResults.isEmpty()) {
            Log.d(TAG, "No orders found for query '${extractedInfo.orderNumber}'")
            return VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.CheckOrderStatus(extractedInfo.orderNumber),
                extractedQuery = extractedInfo.orderNumber,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("order_not_found" to "No orders found matching '${extractedInfo.orderNumber}'")
            )
        }

        // Step 4: Filter by customer if provided and calculate confidence
        val filteredResults = if (extractedInfo.customerName != null) {
            filterOrdersByCustomer(searchResults, extractedInfo.customerName)
        } else {
            searchResults
        }

        if (filteredResults.isEmpty() && extractedInfo.customerName != null) {
            Log.d(TAG, "No orders found for customer '${extractedInfo.customerName}'")
            return VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.CheckOrderStatus(extractedInfo.orderNumber),
                extractedQuery = extractedInfo.orderNumber,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("customer_mismatch" to "Order found but customer '${extractedInfo.customerName}' doesn't match")
            )
        }

        val primaryMatch = filteredResults.first()
        val alternatives = filteredResults.drop(1).take(config.maxAlternatives - 1)
        val confidence = calculateOrderConfidence(extractedInfo, primaryMatch)

        Log.d(TAG, "Primary match: ${primaryMatch.number} (confidence: $confidence)")
        Log.d(TAG, "Alternative matches: ${alternatives.size}")

        // Step 5: Get Order Status Information
        val orderStatusInfo = mutableMapOf<String, OrderStatusInfo>()
        val orderLookupErrors = mutableMapOf<String, String>()

        if (confidence >= config.minimumConfidence) {
            Log.d(TAG, "Getting detailed status info for primary match")
            val statusStartTime = System.currentTimeMillis()
            loadOrderStatusInfo(primaryMatch, orderStatusInfo, orderLookupErrors)
            val statusDuration = System.currentTimeMillis() - statusStartTime
            Log.d(TAG, "Order status lookup completed in ${statusDuration}ms")
        } else {
            Log.d(
                TAG,
                "Skipping detailed status lookup: confidence=$confidence, minRequired=${config.minimumConfidence}"
            )
        }

        processingSteps.add(
            ProcessingStep(
                step = "Order Status Lookup",
                input = "Primary match: ${primaryMatch.number}",
                output = if (orderStatusInfo.isNotEmpty()) "Status: ${primaryMatch.getStatusDisplay()}" else "Skipped (low confidence)",
                confidence = confidence
            )
        )

        val result = VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.CheckOrderStatus(extractedInfo.orderNumber),
            extractedQuery = extractedInfo.orderNumber,
            matchedItem = null, // Orders don't have MasterItem - we use order info directly
            confidence = confidence,
            alternativeMatches = emptyList(), // Orders don't use MasterItem alternatives
            processingSteps = processingSteps,
            stockSummaries = emptyMap(), // Orders don't have stock summaries
            stockLookupErrors = orderLookupErrors,
            orderStatusInfo = orderStatusInfo[primaryMatch.number] // New field for order status
        )

        Log.d(TAG, "=== Order status processing completed ===")
        return result
    }

    /**
     * Extract order number and customer information from cleaned text
     */
    private fun extractOrderInfoFromCleanedText(cleanedText: String): OrderExtractionInfo {
        Log.d(TAG, "--- Starting order-specific extraction ---")
        Log.d(TAG, "Input: '$cleanedText'")

        val input = cleanedText.lowercase().trim()

        // Order extraction patterns - most specific first
        val orderPatterns = listOf(
            // MOST SPECIFIC: "show me the order status for [ID]"  
            Regex("(?:show me|get me|tell me) (?:the )?order status (?:for|of)\\s+([\\w\\d\\s-]+?)(?:[.,!?]|\\s+please)?\\s*$"),

            // "What is the status of order 12345 for customer ABC Corp"
            Regex("(?:what (?:is |are )?the |check )?(?:status|info) (?:of |for )?order\\s+([\\w\\d\\s-]+?)\\s+for\\s+customer\\s+(.+?)(?:[.,!?]|\\s+please)?\\s*$"),

            // "Check order 12345 for customer ABC Corp"
            Regex("(?:check|track|find|status of) order\\s+([\\w\\d\\s-]+?)\\s+for\\s+customer\\s+(.+?)(?:[.,!?]|\\s+please)?\\s*$"),

            // "Order 12345 customer ABC Corp status"
            Regex("order\\s+([\\w\\d\\s-]+?)\\s+customer\\s+(.+?)\\s+(?:status|info)"),

            // Standard order status without customer
            Regex("(?:what (?:is |are )?the |check |get )?(?:status|info) (?:of |for )?order\\s+([\\w\\d\\s-]+?)(?:[.,!?]|\\s+please)?\\s*$"),

            // "Check/track/find order X"
            Regex("(?:check|track|find|status of) order\\s+([\\w\\d\\s-]+?)(?:[.,!?]|\\s+please)?\\s*$"),

            // "Order X status"
            Regex("order\\s+([\\w\\d\\s-]+?)\\s+(?:status|info)(?:[.,!?]|\\s+please)?\\s*$"),

            // Generic "order X" pattern
            Regex("order\\s+([\\w\\d\\s-]+?)(?:[.,!?]|\\s+please)?\\s*$")
        )

        for ((index, pattern) in orderPatterns.withIndex()) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                val orderNumber = match.groupValues[1].trim()
                val customerName =
                    if (match.groupValues.size > 2) match.groupValues[2].trim() else null

                if (orderNumber.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "Pattern ${index + 1} matched: Order='$orderNumber', Customer='$customerName'"
                    )

                    val cleanedOrderNumber = cleanOrderNumber(orderNumber)
                    val cleanedCustomerName = customerName?.let { cleanCustomerName(it) }

                    return OrderExtractionInfo(cleanedOrderNumber, cleanedCustomerName)
                }
            }
        }

        // Fallback - look for any order-like number in the text
        val fallbackOrderNumber = extractOrderNumberFallback(input)
        val fallbackCustomer = extractCustomerNameFallback(input)

        Log.d(
            TAG,
            "Using fallback extraction: Order='$fallbackOrderNumber', Customer='$fallbackCustomer'"
        )
        Log.d(TAG, "--- Order-specific extraction completed ---")

        return OrderExtractionInfo(fallbackOrderNumber, fallbackCustomer)
    }

    /**
     * Clean and normalize order number
     */
    private fun cleanOrderNumber(orderNumber: String): String {
        var cleaned = orderNumber.trim()

        // Remove punctuation first
        cleaned = cleaned.replace(Regex("[.,!?;:]"), "")

        // Remove extra spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // Handle spoken numbers - basic patterns
        cleaned = cleaned.replace(Regex("\\b(one|1)\\b"), "1")
            .replace(Regex("\\b(two|2)\\b"), "2")
            .replace(Regex("\\b(three|3)\\b"), "3")
            .replace(Regex("\\b(four|4)\\b"), "4")
            .replace(Regex("\\b(five|5)\\b"), "5")
            .replace(Regex("\\b(six|6)\\b"), "6")
            .replace(Regex("\\b(seven|7)\\b"), "7")
            .replace(Regex("\\b(eight|8)\\b"), "8")
            .replace(Regex("\\b(nine|9)\\b"), "9")
            .replace(Regex("\\b(zero|oh)\\b"), "0")

        // Handle thousands, hundreds
        cleaned = cleaned.replace(Regex("\\b(thousand)\\b"), "000")
            .replace(Regex("\\b(hundred)\\b"), "00")

        // Final cleanup: remove extra spaces and normalize
        cleaned = cleaned.replace(Regex("\\s+"), "").uppercase()

        // Add ORD prefix if it looks like a number without prefix
        if (cleaned.matches(Regex("^\\d+$")) && cleaned.length >= 3) {
            cleaned = "ORD-$cleaned"
        }

        Log.d(TAG, "Order number cleaned: '$orderNumber' -> '$cleaned'")
        return cleaned
    }

    /**
     * Clean customer name
     */
    private fun cleanCustomerName(customerName: String): String {
        val cleaned = customerName.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()

        Log.d(TAG, "Customer name cleaned: '$customerName' -> '$cleaned'")
        return cleaned
    }

    /**
     * Fallback order number extraction
     */
    private fun extractOrderNumberFallback(input: String): String {
        // Look for ORD-12345 patterns
        val ordPattern = Regex("\\b(ord[\\s-]*\\d+[\\w\\d-]*)\\b")
        val ordMatch = ordPattern.find(input)
        if (ordMatch != null) {
            return cleanOrderNumber(ordMatch.value)
        }

        // Look for standalone numbers that might be order numbers
        val numberPattern = Regex("\\b(\\d{3,})\\b")
        val numberMatch = numberPattern.find(input)
        if (numberMatch != null) {
            return cleanOrderNumber(numberMatch.value)
        }

        // Return the whole cleaned input as fallback
        return input.replace(Regex("[^\\w\\d\\s-]"), "").trim()
    }

    /**
     * Fallback customer name extraction
     */
    private fun extractCustomerNameFallback(input: String): String? {
        val customerPattern = Regex("\\bfor\\s+customer\\s+([\\w\\s]+)")
        val match = customerPattern.find(input)
        return match?.groupValues?.get(1)?.let { cleanCustomerName(it) }
    }

    /**
     * Search for orders by number with 3-tier fuzzy matching strategy
     * Tier 1: Database search (exact + database fuzzy)
     * Tier 2: Algorithmic fuzzy matching
     * Tier 3: AI fallback (if needed)
     */
    private suspend fun searchOrdersByNumber(orderNumber: String): List<Order> {
        Log.d(TAG, "=== Starting 3-tier order search ===")
        Log.d(TAG, "Query: '$orderNumber'")

        // TIER 1: Database search (exact + database fuzzy)
        Log.d(TAG, "Tier 1: Database search")
        val databaseStartTime = System.currentTimeMillis()

        val databaseResults = try {
            withTimeoutOrNull(ORDER_LOOKUP_TIMEOUT_MS) {
                // Try exact match first
                orderUseCase.getOrderByNumber(orderNumber).fold(
                    onSuccess = { exactOrder ->
                        if (exactOrder != null) {
                            Log.d(TAG, "✅ Tier 1: Found exact database match")
                            listOf(exactOrder)
                        } else {
                            // Try database fuzzy search
                            Log.d(TAG, "No exact match, trying database fuzzy search")
                            orderUseCase.searchOrdersByNumber(orderNumber, limit = 10)
                                .getOrElse { emptyList() }
                        }
                    },
                    onFailure = {
                        Log.w(TAG, "Database exact search failed, trying database fuzzy search")
                        orderUseCase.searchOrdersByNumber(orderNumber, limit = 10)
                            .getOrElse { emptyList() }
                    }
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Tier 1 database search failed", e)
            emptyList()
        }

        val databaseDuration = System.currentTimeMillis() - databaseStartTime
        Log.d(TAG, "Tier 1 completed in ${databaseDuration}ms: ${databaseResults.size} results")

        if (databaseResults.isNotEmpty()) {
            Log.d(TAG, "=== 3-tier search completed (Tier 1 success) ===")
            return databaseResults
        }

        // TIER 2: Algorithmic fuzzy matching
        Log.d(TAG, "Tier 2: Algorithmic fuzzy matching")
        val algorithmicStartTime = System.currentTimeMillis()

        val algorithmicResults = try {
            // Get all orders for algorithmic fuzzy matching
            val allOrders = orderUseCase.getAllOrders(forceSync = false).getOrElse { emptyList() }

            if (allOrders.isEmpty()) {
                Log.w(TAG, "No orders available for algorithmic fuzzy matching")
                emptyList()
            } else {
                Log.d(TAG, "Running algorithmic fuzzy match against ${allOrders.size} orders")

                voiceProcessingRepository.findSimilarOrders(
                    orderQuery = orderNumber,
                    customerQuery = null,
                    candidates = allOrders,
                    maxResults = 5
                ).fold(
                    onSuccess = { matchesWithScores ->
                        val matches = matchesWithScores.map { it.first }
                        Log.d(TAG, "✅ Tier 2: Algorithmic fuzzy found ${matches.size} matches")
                        matches
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Tier 2 algorithmic fuzzy matching failed", exception)
                        emptyList()
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tier 2 algorithmic fuzzy matching error", e)
            emptyList()
        }

        val algorithmicDuration = System.currentTimeMillis() - algorithmicStartTime
        Log.d(
            TAG,
            "Tier 2 completed in ${algorithmicDuration}ms: ${algorithmicResults.size} results"
        )

        if (algorithmicResults.isNotEmpty()) {
            Log.d(TAG, "=== 3-tier search completed (Tier 2 success) ===")
            return algorithmicResults
        }

        // TIER 3: AI fallback (if we implement it later)
        Log.d(TAG, "Tier 3: AI fallback (not yet implemented)")
        Log.d(TAG, "Could integrate with voiceProcessingRepository.findSimilarText() if needed")

        val totalDuration = System.currentTimeMillis() - databaseStartTime
        Log.d(TAG, "=== 3-tier search completed (no matches found) in ${totalDuration}ms ===")

        return emptyList()
    }

    /**
     * Filter orders by customer name using fuzzy matching
     */
    private suspend fun filterOrdersByCustomer(
        orders: List<Order>,
        customerName: String
    ): List<Order> {
        Log.d(TAG, "=== Filtering orders by customer ===")
        Log.d(TAG, "Orders to filter: ${orders.size}")
        Log.d(TAG, "Customer query: '$customerName'")

        return try {
            // Use VoiceProcessingRepository for sophisticated customer matching
            voiceProcessingRepository.findSimilarOrders(
                orderQuery = "", // Empty order query since we're only filtering by customer
                customerQuery = customerName,
                candidates = orders,
                maxResults = orders.size // Keep all matches
            ).fold(
                onSuccess = { matchesWithScores ->
                    // Filter by customer similarity threshold (0.6 for customer names)
                    val customerFilteredResults = matchesWithScores
                        .filter { (_, score) ->
                            // Extract customer component of the score
                            // Since total score = (orderSim * 0.7) + (customerSim * 0.3)
                            // and orderSim is 0 (empty query), score represents customer similarity * 0.3
                            val customerScore = score / 0.3f // Reverse the weighting
                            customerScore >= 0.6f // 60% customer name similarity threshold
                        }
                        .map { it.first }

                    Log.d(
                        TAG,
                        "✅ Fuzzy customer filtering: ${orders.size} → ${customerFilteredResults.size} orders"
                    )

                    customerFilteredResults.forEachIndexed { index, order ->
                        if (index < 3) { // Log first 3 results
                            Log.d(
                                TAG,
                                "  Match ${index + 1}: ${order.number} - ${order.tradingPartnerDescription}"
                            )
                        }
                    }

                    customerFilteredResults
                },
                onFailure = { exception ->
                    Log.e(TAG, "Fuzzy customer filtering failed, using simple filtering", exception)
                    // Fallback to simple contains matching
                    filterOrdersByCustomerSimple(orders, customerName)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Customer filtering error, using simple fallback", e)
            filterOrdersByCustomerSimple(orders, customerName)
        }
    }

    /**
     * Simple fallback customer filtering
     */
    private fun filterOrdersByCustomerSimple(
        orders: List<Order>,
        customerName: String
    ): List<Order> {
        Log.d(TAG, "Using simple customer name filtering")

        val filtered = orders.filter { order ->
            val orderCustomer = order.tradingPartnerDescription.lowercase()
            val queryCustomer = customerName.lowercase()

            // Simple contains matching
            orderCustomer.contains(queryCustomer) ||
                    queryCustomer.contains(orderCustomer) ||
                    // Basic word overlap
                    hasCustomerNameOverlapSimple(queryCustomer, orderCustomer)
        }

        Log.d(TAG, "Simple filtering: ${orders.size} → ${filtered.size} orders")
        return filtered
    }

    /**
     * Simple customer name overlap check (fallback)
     */
    private fun hasCustomerNameOverlapSimple(
        queryCustomer: String,
        orderCustomer: String
    ): Boolean {
        val queryWords = queryCustomer.split("\\s+".toRegex()).filter { it.length > 2 }
        val orderWords = orderCustomer.split("\\s+".toRegex()).filter { it.length > 2 }

        if (queryWords.isEmpty()) return false

        val matches = queryWords.count { queryWord ->
            orderWords.any { orderWord ->
                queryWord.equals(orderWord, ignoreCase = true) ||
                        queryWord.contains(orderWord, ignoreCase = true) ||
                        orderWord.contains(queryWord, ignoreCase = true)
            }
        }

        return matches.toFloat() / queryWords.size >= 0.5f // 50% word overlap
    }


    /**
     * Calculate confidence for order matching
     */
    private fun calculateOrderConfidence(extractedInfo: OrderExtractionInfo, order: Order): Float {
        val queryNumber = extractedInfo.orderNumber.lowercase()
        val orderNumber = order.number.lowercase()

        Log.d(TAG, "Calculating confidence: '$queryNumber' vs '${order.number}'")

        val baseConfidence = when {
            orderNumber == queryNumber -> {
                Log.d(TAG, "Perfect order number match - confidence: 1.0")
                1.0f
            }

            orderNumber.contains(queryNumber) || queryNumber.contains(orderNumber) -> {
                Log.d(TAG, "Order number substring match - confidence: 0.9")
                0.9f
            }

            hasNumberSimilarity(queryNumber, orderNumber) -> {
                Log.d(TAG, "Number similarity detected - confidence: 0.85")
                0.85f
            }

            else -> {
                Log.d(TAG, "Fuzzy match found - confidence: 0.8")
                0.8f
            }
        }

        // Boost confidence if customer name also matches
        val customerBoost = if (extractedInfo.customerName != null) {
            val customerMatches = order.tradingPartnerDescription.lowercase()
                .contains(extractedInfo.customerName.lowercase())
            if (customerMatches) {
                Log.d(TAG, "Customer name also matches - boosting confidence by 0.1")
                0.1f
            } else {
                Log.d(TAG, "Customer name doesn't match - reducing confidence by 0.2")
                -0.2f
            }
        } else {
            0.0f
        }

        val finalConfidence = (baseConfidence + customerBoost).coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Final confidence: $finalConfidence")

        return finalConfidence
    }

    /**
     * Check for number similarity between order numbers
     */
    private fun hasNumberSimilarity(query: String, orderNumber: String): Boolean {
        val queryNumbers = Regex("\\d+").findAll(query).map { it.value }.toList()
        val orderNumbers = Regex("\\d+").findAll(orderNumber).map { it.value }.toList()

        return queryNumbers.any { queryNum ->
            orderNumbers.any { orderNum ->
                queryNum == orderNum ||
                        (queryNum.length >= 3 && orderNum.length >= 3 &&
                                queryNum.take(3) == orderNum.take(3))
            }
        }
    }

    /**
     * Load detailed order status information
     */
    private suspend fun loadOrderStatusInfo(
        order: Order,
        orderStatusInfo: MutableMap<String, OrderStatusInfo>,
        orderLookupErrors: MutableMap<String, String>
    ) {
        Log.d(TAG, "Loading detailed status info for order: ${order.number}")

        try {
            // Use the existing order status check from OrderUseCase
            orderUseCase.checkOrderStatus(order.number).fold(
                onSuccess = { statusInfo ->
                    orderStatusInfo[order.number] = statusInfo
                    Log.d(TAG, "Order status loaded: ${statusInfo.statusDisplay}")
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Status lookup failed"
                    orderLookupErrors[order.number] = errorMessage
                    Log.e(TAG, "Status lookup failed for ${order.number}: $errorMessage")
                }
            )
        } catch (e: Exception) {
            orderLookupErrors[order.number] = e.message ?: "Unexpected error"
            Log.e(TAG, "Unexpected error during status lookup for ${order.number}", e)
        }
    }
}

/**
 * Information extracted from order status command
 */
data class OrderExtractionInfo(
    val orderNumber: String,
    val customerName: String?
)