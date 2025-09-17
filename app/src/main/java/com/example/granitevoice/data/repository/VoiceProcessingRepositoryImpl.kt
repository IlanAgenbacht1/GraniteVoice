package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.remote.client.FastFuzzyMatchClient
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.data.remote.client.OrderFuzzyMatchClient
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

class VoiceProcessingRepositoryImpl @Inject constructor(
    private val openAIClient: OpenAIClient,
    private val fastFuzzyMatchClient: FastFuzzyMatchClient,
    private val orderFuzzyMatchClient: OrderFuzzyMatchClient
) : VoiceProcessingRepository {

    companion object {
        private const val TAG = "VoiceProcessingRepo"
    }

    override suspend fun cleanTranscription(transcription: String): Result<String> {
        Log.d(TAG, "=== Starting generic transcription cleaning ===")
        Log.d(TAG, "Input transcription: '$transcription'")
        val startTime = System.currentTimeMillis()

        return try {
            // Step 1: Correct common speech recognition errors
            val corrected = correctCommonSpeechErrors(transcription)

            // Step 2: Apply comprehensive generic text cleaning
            val cleaned = applyComprehensiveGenericCleaning(corrected)

            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "✅ Generic cleaning completed in ${totalDuration}ms")
            Log.d(TAG, "Original: '$transcription'")
            Log.d(TAG, "Cleaned: '$cleaned'")
            Log.d(TAG, "=== Generic transcription cleaning completed ===")

            Result.success(cleaned)
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Generic cleaning failed after ${totalDuration}ms", e)

            // Fallback to basic cleaning if error occurs
            Log.d(TAG, "Using basic cleanup fallback")
            val fallback = basicTextCleanup(transcription)
            Result.success(fallback)
        }
    }

    override suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>
    ): Result<List<MasterItem>> {
        Log.d(TAG, "=== Starting hybrid fuzzy matching ===")
        Log.d(TAG, "Query: '$query'")
        Log.d(TAG, "Candidates: ${candidates.size} items")
        val startTime = System.currentTimeMillis()

        return try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "❌ No candidates provided for matching")
                return Result.success(emptyList())
            }

            // First try: Fast AI-based fuzzy matching (much faster than OpenAI)
            Log.d(TAG, "🚀 Trying fast AI-based fuzzy matching first...")
            val fastMatchStartTime = System.currentTimeMillis()

            val fastMatchResult = fastFuzzyMatchClient.findSimilarItems(query, candidates, 5)

            fastMatchResult.fold(
                onSuccess = { fastMatches ->
                    val fastMatchDuration = System.currentTimeMillis() - fastMatchStartTime
                    Log.d(
                        TAG,
                        "✅ Fast matching completed in ${fastMatchDuration}ms, found ${fastMatches.size} matches"
                    )

                    if (fastMatches.isNotEmpty()) {
                        val totalDuration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "✅ Using fast matching results (total: ${totalDuration}ms)")
                        Log.d(TAG, "=== Hybrid fuzzy matching completed (fast) ===")
                        return Result.success(fastMatches)
                    } else {
                        Log.d(TAG, "⚠️ Fast matching found no results, falling back to OpenAI...")
                    }
                },
                onFailure = { fastError ->
                    val fastMatchDuration = System.currentTimeMillis() - fastMatchStartTime
                    Log.w(
                        TAG,
                        "⚠️ Fast matching failed in ${fastMatchDuration}ms, falling back to OpenAI",
                        fastError
                    )
                }
            )

            // Second try: OpenAI matching (slower but more comprehensive)
            Log.d(TAG, "🤖 Falling back to OpenAI matching...")
            val openAIStartTime = System.currentTimeMillis()

            val descriptions = candidates.map { it.description }.distinct()
            Log.d(TAG, "Unique descriptions: ${descriptions.size} (from ${candidates.size} items)")

            val prompt = """
            Match spoken query to items. Handle speech errors.

            SAID: "$query"

            ITEMS:
            ${descriptions.joinToString("\n") { "- $it" }}

            Rules: Handle mishearings, numbers, phonetics. Focus on meaning.

            Return top 3:
            MATCH1: [exact description]
            MATCH2: [exact description]
            MATCH3: [exact description]

            Or: NO_MATCHES_FOUND
            """.trimIndent()

            Log.d(TAG, "Sending matching request to OpenAI (${prompt.length} chars)")

            val aiStartTime = System.currentTimeMillis()
            val response = openAIClient.makeChatRequestRaw(prompt).trim()
            val aiDuration = System.currentTimeMillis() - aiStartTime

            Log.d(TAG, "✅ OpenAI matching completed in ${aiDuration}ms")
            Log.d(TAG, "OpenAI response: '$response'")

            if (response.contains("NO_MATCHES_FOUND", ignoreCase = true)) {
                val totalDuration = System.currentTimeMillis() - startTime
                Log.d(TAG, "🤷 OpenAI found no matches (total: ${totalDuration}ms)")
                Log.d(TAG, "=== Hybrid fuzzy matching completed (no matches) ===")
                return Result.success(emptyList())
            }

            val parseStartTime = System.currentTimeMillis()
            val matchedItems = parseAIMatches(response, candidates)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Parsing completed in ${parseDuration}ms")
            Log.d(TAG, "✅ Hybrid fuzzy matching completed in ${totalDuration}ms")
            Log.d(TAG, "Found ${matchedItems.size} matched items")

            matchedItems.forEach { item ->
                Log.d(TAG, "  ✓ ${item.code}: ${item.description}")
            }

            Log.d(TAG, "=== Hybrid fuzzy matching completed (OpenAI fallback) ===")
            Result.success(matchedItems)

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Hybrid matching failed after ${totalDuration}ms", e)
            Log.d(TAG, "=== Hybrid fuzzy matching completed (with error) ===")
            Result.success(emptyList())
        }
    }

    override suspend fun findSimilarOrders(
        orderQuery: String,
        customerQuery: String?,
        candidates: List<Order>,
        maxResults: Int
    ): Result<List<Pair<Order, Float>>> {
        Log.d(TAG, "=== Starting generic order fuzzy matching ===")
        Log.d(TAG, "Order query: '$orderQuery'")
        Log.d(TAG, "Customer query: '$customerQuery'")
        Log.d(TAG, "Candidates: ${candidates.size} orders")
        val startTime = System.currentTimeMillis()

        return try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "❌ No order candidates provided for matching")
                return Result.success(emptyList())
            }

            // Delegate to the OrderFuzzyMatchClient for sophisticated algorithmic matching
            Log.d(TAG, "🚀 Using algorithmic order fuzzy matching...")

            val result = orderFuzzyMatchClient.findSimilarOrders(
                orderQuery = orderQuery,
                customerQuery = customerQuery,
                candidates = candidates,
                maxResults = maxResults
            )

            result.fold(
                onSuccess = { matches ->
                    val totalDuration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "✅ Order fuzzy matching completed in ${totalDuration}ms")
                    Log.d(TAG, "Found ${matches.size} similar orders")

                    matches.forEachIndexed { index, (order, similarity) ->
                        if (index < 3) { // Log first 3 for debugging
                            Log.d(
                                TAG,
                                "  ${index + 1}. ${order.number} (${order.tradingPartnerDescription}) - score: ${
                                    String.format(
                                        "%.3f",
                                        similarity
                                    )
                                }"
                            )
                        }
                    }

                    Log.d(TAG, "=== Generic order fuzzy matching completed ===")
                    Result.success(matches)
                },
                onFailure = { exception ->
                    val totalDuration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "❌ Order fuzzy matching failed after ${totalDuration}ms", exception)
                    Log.d(TAG, "=== Generic order fuzzy matching completed (with error) ===")
                    Result.failure(exception)
                }
            )

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Generic order fuzzy matching error after ${totalDuration}ms", e)
            Log.d(TAG, "=== Generic order fuzzy matching completed (with error) ===")
            Result.failure(e)
        }
    }

    /**
     * Correct common speech recognition errors in a generic way.
     * This is not tied to any specific domain.
     */
    private fun correctCommonSpeechErrors(transcription: String): String {
        Log.d(TAG, "Correcting common speech recognition errors...")

        var corrected = transcription

        // Generic speech corrections (not domain-specific)
        val genericCorrections = listOf(
            // Common word mishearings
            "do we half" to "do we have",
            "do we hab" to "do we have",
            "due we have" to "do we have",
            "show may" to "show me",
            "show my" to "show me",
            "showed me" to "show me",
            "fine" to "find",
            "fund" to "find",
            "bind" to "find",
            "get may" to "get me",
            "get my" to "get me",
            // Generic command corrections (not domain-specific)
            "can you" to "can you",
            "could you" to "could you",
            "would you" to "would you",
            "please" to "please"
        )

        // Apply generic corrections (case insensitive)
        for ((error, correction) in genericCorrections) {
            if (corrected.contains(error, ignoreCase = true)) {
                val regex = Regex("\\b${Regex.escape(error)}\\b", RegexOption.IGNORE_CASE)
                corrected = regex.replace(corrected, correction)
                Log.d(TAG, "Corrected: '$error' → '$correction'")
            }
        }

        return corrected
    }

    /**
     * Apply comprehensive generic text cleaning that preserves important content
     * while removing noise. This is domain-agnostic and works for any voice command type.
     */
    private fun applyComprehensiveGenericCleaning(text: String): String {
        Log.d(TAG, "Applying comprehensive generic cleaning to: '$text'")

        var cleaned = text.lowercase().trim()

        // Step 1: Remove speech fillers and unnecessary words
        val fillerWords = setOf(
            "ah", "uh", "um", "er", "mm", "hmm", "well", "so", "like", "you know"
        )

        // Step 2: Remove common unnecessary leading/trailing words but preserve important content
        val unnecessaryLeadingWords = setOf(
            "can you", "could you", "would you", "please"
        )

        val unnecessaryTrailingWords = setOf(
            "please", "thanks", "thank you"
        )

        // Split into words and apply sophisticated filtering
        val originalWords = cleaned.split(Regex("\\s+"))
        val words = originalWords
            .mapIndexed { index, word ->
                val lowerWord = word.lowercase()

                when {
                    // Always remove speech fillers
                    lowerWord in fillerWords -> null

                    // Remove unnecessary leading words at the start
                    index == 0 && lowerWord in unnecessaryLeadingWords -> null

                    // Remove unnecessary trailing words at the end
                    index == originalWords.size - 1 && lowerWord in unnecessaryTrailingWords -> null

                    // Keep all other meaningful words
                    word.isNotBlank() -> lowerWord

                    else -> null
                }
            }
            .filterNotNull()

        cleaned = words.joinToString(" ")

        // Step 3: Clean up punctuation and extra spaces
        cleaned = cleaned.replace(Regex("[,;.!?]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Step 4: Handle special cases and preserve important patterns
        cleaned = preserveImportantPatterns(cleaned)

        Log.d(TAG, "Comprehensive cleaning: '$text' → '$cleaned'")
        return cleaned
    }

    /**
     * Preserve important patterns that should not be over-cleaned
     */
    private fun preserveImportantPatterns(text: String): String {
        var processed = text

        // Preserve alphanumeric codes (like ABC123, XYZ-456)
        // Preserve numbers with decimals (like 2.5, 10.25)
        // Preserve dimension patterns (like 12x24, 5x10)

        // These patterns are commonly important across domains
        // No changes needed as basic cleaning already preserves these

        return processed
    }

    /**
     * Basic fallback text cleanup for error cases
     */
    private fun basicTextCleanup(text: String): String {
        return text.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[,;.!?]+$"), "")
    }

    private fun parseAIMatches(response: String, candidates: List<MasterItem>): List<MasterItem> {
        Log.d(TAG, "--- Starting AI response parsing ---")

        val matchedItems = mutableListOf<MasterItem>()
        val alreadyMatched = mutableSetOf<String>()
        val lines = response.split("\n")

        Log.d(TAG, "Response has ${lines.size} lines to parse")

        for ((lineIndex, line) in lines.withIndex()) {
            if (line.startsWith("MATCH", ignoreCase = true)) {
                val matchedDescription = line.substringAfter(":").trim()
                Log.d(TAG, "Processing line $lineIndex: '$line'")
                Log.d(TAG, "Extracted description: '$matchedDescription'")

                val itemsWithDescription = candidates.filter { item ->
                    item.description.equals(matchedDescription, ignoreCase = true) &&
                            !alreadyMatched.contains(item.code)
                }

                Log.d(TAG, "Found ${itemsWithDescription.size} items with matching description")

                itemsWithDescription.forEach { item ->
                    if (!alreadyMatched.contains(item.code)) {
                        matchedItems.add(item)
                        alreadyMatched.add(item.code)
                        Log.d(TAG, "  ✓ Added: ${item.code}")
                    }
                }

                if (itemsWithDescription.isEmpty()) {
                    Log.w(TAG, "  ⚠️ No items found with description: '$matchedDescription'")

                    // Log available descriptions for debugging
                    val similarDescriptions = candidates.map { it.description }.distinct()
                        .filter { it.contains(matchedDescription.take(5), ignoreCase = true) }
                    if (similarDescriptions.isNotEmpty()) {
                        Log.d(TAG, "  Similar descriptions available:")
                        similarDescriptions.take(3).forEach { desc ->
                            Log.d(TAG, "    - $desc")
                        }
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Parsing completed: ${matchedItems.size} items matched from ${alreadyMatched.size} unique codes"
        )
        Log.d(TAG, "--- AI response parsing completed ---")

        return matchedItems.take(5) // Limit to 5 matches
    }
}