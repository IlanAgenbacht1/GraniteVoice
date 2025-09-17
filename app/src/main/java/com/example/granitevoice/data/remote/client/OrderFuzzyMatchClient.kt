package com.example.granitevoice.data.remote.client

import android.util.Log
import com.example.granitevoice.domain.model.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Fuzzy matching client specifically designed for order numbers and customer names
 * Uses sophisticated algorithmic similarity scoring like FastFuzzyMatchClient
 */
class OrderFuzzyMatchClient {
    private val TAG = "OrderFuzzyMatchClient"

    companion object {
        private const val SIMILARITY_THRESHOLD = 0.4f // Slightly lower for orders
        private const val ORDER_NUMBER_WEIGHT = 0.7f  // Order number is most important
        private const val CUSTOMER_NAME_WEIGHT = 0.3f // Customer name is secondary
        private const val PHONETIC_WEIGHT = 0.4f
        private const val STRUCTURAL_WEIGHT = 0.3f
        private const val CONTEXTUAL_WEIGHT = 0.3f
    }

    /**
     * Find orders that fuzzy match the given order number and optional customer name
     */
    suspend fun findSimilarOrders(
        orderQuery: String,
        customerQuery: String? = null,
        candidates: List<Order>,
        maxResults: Int = 5
    ): Result<List<Pair<Order, Float>>> = withContext(Dispatchers.IO) {

        Log.d(TAG, "=== Starting order fuzzy matching ===")
        Log.d(TAG, "Order query: '$orderQuery'")
        Log.d(TAG, "Customer query: '$customerQuery'")
        Log.d(TAG, "Candidates: ${candidates.size} orders")
        val startTime = System.currentTimeMillis()

        try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "❌ No order candidates provided")
                return@withContext Result.success(emptyList())
            }

            val processedOrderQuery = preprocessOrderNumber(orderQuery)
            val processedCustomerQuery = customerQuery?.let { preprocessCustomerName(it) }

            Log.d(TAG, "Preprocessed order query: '$processedOrderQuery'")
            Log.d(TAG, "Preprocessed customer query: '$processedCustomerQuery'")

            // Calculate similarities for all candidates
            val similarities = mutableListOf<Pair<Order, Float>>()

            for (order in candidates) {
                val orderSimilarity =
                    calculateOrderNumberSimilarity(processedOrderQuery, order.number)

                val customerSimilarity = if (processedCustomerQuery != null) {
                    calculateCustomerNameSimilarity(
                        processedCustomerQuery,
                        order.tradingPartnerDescription
                    )
                } else {
                    0.5f // Neutral score when no customer specified
                }

                // Weighted combination - order number is more important
                val totalSimilarity = (orderSimilarity * ORDER_NUMBER_WEIGHT) +
                        (customerSimilarity * CUSTOMER_NAME_WEIGHT)

                if (totalSimilarity > SIMILARITY_THRESHOLD) {
                    similarities.add(Pair(order, totalSimilarity))
                    Log.v(
                        TAG,
                        "Order ${order.number}: orderSim=${
                            String.format(
                                "%.3f",
                                orderSimilarity
                            )
                        }, " +
                                "customerSim=${String.format("%.3f", customerSimilarity)}, " +
                                "total=${String.format("%.3f", totalSimilarity)}"
                    )
                }
            }

            // Sort by similarity and take top results
            val topMatches = similarities
                .sortedByDescending { it.second }
                .take(maxResults)

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Order fuzzy matching completed in ${totalDuration}ms")
            Log.d(TAG, "Found ${topMatches.size} matches above threshold $SIMILARITY_THRESHOLD")

            topMatches.forEachIndexed { index, (order, similarity) ->
                Log.d(
                    TAG, "  ${index + 1}. ${order.number} (${order.tradingPartnerDescription}) " +
                            "- score: ${String.format("%.3f", similarity)}"
                )
            }

            Log.d(TAG, "=== Order fuzzy matching completed ===")
            Result.success(topMatches)

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Order fuzzy matching failed after ${totalDuration}ms", e)
            Result.failure(e)
        }
    }

    /**
     * Calculate sophisticated similarity between order numbers
     */
    private fun calculateOrderNumberSimilarity(query: String, candidate: String): Float {
        val processedCandidate = preprocessOrderNumber(candidate)

        // Exact match gets perfect score
        if (query.equals(processedCandidate, ignoreCase = true)) {
            return 1.0f
        }

        // Multi-faceted order number similarity
        val phoneticSim = calculateOrderPhoneticSimilarity(query, processedCandidate)
        val structuralSim = calculateOrderStructuralSimilarity(query, processedCandidate)
        val contextualSim = calculateOrderContextualSimilarity(query, processedCandidate)

        // Weighted combination
        val totalScore = (phoneticSim * PHONETIC_WEIGHT) +
                (structuralSim * STRUCTURAL_WEIGHT) +
                (contextualSim * CONTEXTUAL_WEIGHT)

        // Apply boost for very similar order numbers
        val boostFactor = calculateOrderBoostFactor(query, processedCandidate)
        val finalScore = minOf(1.0f, totalScore * boostFactor)

        return finalScore
    }

    /**
     * Calculate similarity between customer names
     */
    private fun calculateCustomerNameSimilarity(query: String, candidate: String): Float {
        val processedCandidate = preprocessCustomerName(candidate)

        // Exact match gets perfect score
        if (query.equals(processedCandidate, ignoreCase = true)) {
            return 1.0f
        }

        // Check for substring matches (common with customer names)
        if (query.contains(processedCandidate, ignoreCase = true) ||
            processedCandidate.contains(query, ignoreCase = true)
        ) {
            return 0.9f
        }

        // Word-based similarity for customer names
        return calculateCustomerWordSimilarity(query, processedCandidate)
    }

    /**
     * Preprocess order number for matching
     */
    private fun preprocessOrderNumber(orderNumber: String): String {
        return orderNumber.uppercase()
            .replace(Regex("[^A-Z0-9]"), "") // Remove all non-alphanumeric
            .trim()
    }

    /**
     * Preprocess customer name for matching
     */
    private fun preprocessCustomerName(customerName: String): String {
        return customerName.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Keep letters, numbers, spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Phonetic similarity for order numbers (handles spoken number errors)
     */
    private fun calculateOrderPhoneticSimilarity(query: String, candidate: String): Float {
        // Extract numeric parts
        val queryNumbers = extractNumbers(query)
        val candidateNumbers = extractNumbers(candidate)

        // Extract alpha parts
        val queryAlpha = extractAlpha(query)
        val candidateAlpha = extractAlpha(candidate)

        // Number phonetic similarity (handles "five four five four" vs "5454")
        val numberSim = if (queryNumbers.isNotEmpty() && candidateNumbers.isNotEmpty()) {
            calculateNumberPhoneticSimilarity(queryNumbers, candidateNumbers)
        } else {
            0.5f
        }

        // Alpha phonetic similarity (handles "ORD" vs "ORDER")
        val alphaSim = if (queryAlpha.isNotEmpty() && candidateAlpha.isNotEmpty()) {
            calculateAlphaPhoneticSimilarity(queryAlpha, candidateAlpha)
        } else {
            0.5f
        }

        return (numberSim * 0.7f) + (alphaSim * 0.3f) // Numbers more important for orders
    }

    /**
     * Structural similarity for order numbers (length, patterns)
     */
    private fun calculateOrderStructuralSimilarity(query: String, candidate: String): Float {
        val lengthSim = 1f - abs(query.length - candidate.length).toFloat() / maxOf(
            query.length,
            candidate.length
        )

        // Pattern similarity (ORD-12345 vs ORD12345 vs 12345)
        val patternSim = calculateOrderPatternSimilarity(query, candidate)

        // Character overlap
        val charSim = calculateCharacterOverlap(query, candidate)

        return (lengthSim * 0.2f) + (patternSim * 0.5f) + (charSim * 0.3f)
    }

    /**
     * Contextual similarity for order numbers (number ranges, sequences)
     */
    private fun calculateOrderContextualSimilarity(query: String, candidate: String): Float {
        val queryNumbers = extractNumbers(query)
        val candidateNumbers = extractNumbers(candidate)

        if (queryNumbers.isEmpty() || candidateNumbers.isEmpty()) return 0.3f

        // Check for number range similarity (12345 vs 12346)
        val rangeSim = calculateNumberRangeSimilarity(queryNumbers, candidateNumbers)

        // Check for digit sequence similarity (1234 vs 1243)
        val sequenceSim = calculateDigitSequenceSimilarity(query, candidate)

        return (rangeSim * 0.6f) + (sequenceSim * 0.4f)
    }

    /**
     * Calculate boost factor for very similar order numbers
     */
    private fun calculateOrderBoostFactor(query: String, candidate: String): Float {
        // Exact number match with different prefix/suffix
        val queryNumbers = extractNumbers(query)
        val candidateNumbers = extractNumbers(candidate)

        if (queryNumbers.isNotEmpty() && candidateNumbers.isNotEmpty()) {
            val exactNumberMatch = queryNumbers.intersect(candidateNumbers.toSet()).isNotEmpty()
            if (exactNumberMatch) {
                return 1.3f // 30% boost for exact number matches
            }
        }

        // Levenshtein distance boost
        val editDistance = calculateLevenshteinDistance(query, candidate)
        val maxLength = maxOf(query.length, candidate.length)
        val editSimilarity = 1f - (editDistance.toFloat() / maxLength)

        return when {
            editSimilarity >= 0.8f -> 1.2f // 20% boost for very similar strings
            editSimilarity >= 0.6f -> 1.1f // 10% boost for somewhat similar strings
            else -> 1.0f // No boost
        }
    }

    /**
     * Calculate word-based similarity for customer names
     */
    private fun calculateCustomerWordSimilarity(query: String, candidate: String): Float {
        val queryWords = query.split("\\s+".toRegex()).filter { it.length > 1 }
        val candidateWords = candidate.split("\\s+".toRegex()).filter { it.length > 1 }

        if (queryWords.isEmpty() || candidateWords.isEmpty()) return 0f

        // Exact word matches
        val exactMatches = queryWords.count { queryWord ->
            candidateWords.any { candidateWord ->
                queryWord.equals(candidateWord, ignoreCase = true)
            }
        }

        // Partial word matches (for abbreviations, typos)
        val partialMatches = queryWords.count { queryWord ->
            candidateWords.any { candidateWord ->
                !queryWord.equals(candidateWord, ignoreCase = true) && (
                        candidateWord.startsWith(queryWord, ignoreCase = true) ||
                                queryWord.startsWith(candidateWord, ignoreCase = true) ||
                                calculateLevenshteinDistance(queryWord, candidateWord) <= 2
                        )
            }
        }

        val exactRatio = exactMatches.toFloat() / queryWords.size
        val partialRatio = (partialMatches.toFloat() / queryWords.size) * 0.7f // Partial worth 70%

        return exactRatio + partialRatio
    }

    // Helper functions for number and pattern analysis

    private fun extractNumbers(text: String): List<String> {
        return Regex("\\d+").findAll(text).map { it.value }.toList()
    }

    private fun extractAlpha(text: String): String {
        return text.replace(Regex("\\d"), "")
    }

    private fun calculateNumberPhoneticSimilarity(
        queryNumbers: List<String>,
        candidateNumbers: List<String>
    ): Float {
        // Check for matches between spoken and written numbers
        var bestMatch = 0f

        for (queryNum in queryNumbers) {
            for (candidateNum in candidateNumbers) {
                val similarity = when {
                    queryNum == candidateNum -> 1.0f
                    queryNum.startsWith(candidateNum) || candidateNum.startsWith(queryNum) -> 0.8f
                    calculateDigitSimilarity(queryNum, candidateNum) > 0.5f -> 0.7f
                    else -> 0f
                }
                bestMatch = maxOf(bestMatch, similarity)
            }
        }

        return bestMatch
    }

    private fun calculateAlphaPhoneticSimilarity(
        queryAlpha: String,
        candidateAlpha: String
    ): Float {
        return when {
            queryAlpha == candidateAlpha -> 1.0f
            queryAlpha.startsWith(candidateAlpha) || candidateAlpha.startsWith(queryAlpha) -> 0.8f
            calculateLevenshteinDistance(queryAlpha, candidateAlpha) <= 2 -> 0.6f
            else -> 0.3f
        }
    }

    private fun calculateOrderPatternSimilarity(query: String, candidate: String): Float {
        // Check if both follow similar patterns (letters + numbers, numbers only, etc.)
        val queryPattern = getOrderPattern(query)
        val candidatePattern = getOrderPattern(candidate)

        return if (queryPattern == candidatePattern) 1.0f else 0.5f
    }

    private fun getOrderPattern(orderNumber: String): String {
        return orderNumber.map { char ->
            when {
                char.isLetter() -> 'A'
                char.isDigit() -> 'N'
                else -> 'X'
            }
        }.joinToString("")
    }

    private fun calculateCharacterOverlap(query: String, candidate: String): Float {
        val queryChars = query.groupingBy { it }.eachCount()
        val candidateChars = candidate.groupingBy { it }.eachCount()

        val commonChars = queryChars.keys.intersect(candidateChars.keys)
        if (commonChars.isEmpty()) return 0f

        var overlap = 0
        for (char in commonChars) {
            overlap += minOf(queryChars[char]!!, candidateChars[char]!!)
        }

        val totalChars = maxOf(query.length, candidate.length)
        return overlap.toFloat() / totalChars
    }

    private fun calculateNumberRangeSimilarity(
        queryNumbers: List<String>,
        candidateNumbers: List<String>
    ): Float {
        var bestSimilarity = 0f

        for (queryNum in queryNumbers) {
            for (candidateNum in candidateNumbers) {
                val qNum = queryNum.toIntOrNull() ?: continue
                val cNum = candidateNum.toIntOrNull() ?: continue

                val similarity = when {
                    qNum == cNum -> 1.0f
                    abs(qNum - cNum) <= 1 -> 0.9f // Adjacent numbers
                    abs(qNum - cNum) <= 10 -> 0.7f // Close numbers
                    abs(qNum - cNum) <= 100 -> 0.5f // Same order of magnitude
                    else -> 0f
                }

                bestSimilarity = maxOf(bestSimilarity, similarity)
            }
        }

        return bestSimilarity
    }

    private fun calculateDigitSequenceSimilarity(query: String, candidate: String): Float {
        val queryDigits = query.filter { it.isDigit() }
        val candidateDigits = candidate.filter { it.isDigit() }

        if (queryDigits.isEmpty() || candidateDigits.isEmpty()) return 0f

        return calculateLevenshteinSimilarity(queryDigits, candidateDigits)
    }

    private fun calculateDigitSimilarity(num1: String, num2: String): Float {
        return calculateLevenshteinSimilarity(num1, num2)
    }

    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Float {
        val distance = calculateLevenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        return if (maxLength == 0) 1f else 1f - (distance.toFloat() / maxLength)
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }

        return dp[s1.length][s2.length]
    }
}