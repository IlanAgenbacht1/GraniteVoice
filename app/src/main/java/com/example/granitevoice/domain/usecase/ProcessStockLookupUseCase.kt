package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.StockRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Use case responsible for processing stock lookup commands
 */
class ProcessStockLookupUseCase @Inject constructor(
    private val searchLocalItemsUseCase: SearchLocalItemsUseCase,
    private val voiceProcessingRepository: VoiceProcessingRepository,
    private val stockRepository: StockRepository,
    private val config: VoiceProcessingConfig
) {

    companion object {
        private const val TAG = "StockLookupUseCase"
    }

    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        Log.d(TAG, "=== Starting stock lookup processing ===")
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

        // Step 2: The Use Case now contains the SPECIFIC logic to extract the item query
        val extractionStartTime = System.currentTimeMillis()
        val extractedQuery = extractItemQueryFromCleanedText(cleanedTranscription)
        val extractionDuration = System.currentTimeMillis() - extractionStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Stock-Specific Query Extraction",
                input = cleanedTranscription,
                output = extractedQuery
            )
        )

        Log.d(
            TAG,
            "Stock-specific extraction completed in ${extractionDuration}ms: '$cleanedTranscription' -> '$extractedQuery'"
        )

        // Step 3: Search for Items
        val searchStartTime = System.currentTimeMillis()
        val searchResults = searchLocalItemsUseCase(extractedQuery)
        val searchDuration = System.currentTimeMillis() - searchStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Item Search",
                input = extractedQuery,
                output = "Found ${searchResults.size} matches"
            )
        )

        Log.d(
            TAG,
            "Item search completed in ${searchDuration}ms: found ${searchResults.size} matches"
        )

        if (searchResults.isEmpty()) {
            Log.d(TAG, "No items found for query '$extractedQuery'")
            return VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.StockLookup,
                extractedQuery = extractedQuery,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = emptyMap()
            )
        }

        // Step 4: Calculate Confidence and Determine Primary Match
        val primaryMatch = searchResults.first()
        val alternatives = searchResults.drop(1).take(config.maxAlternatives - 1)
        val confidence = calculateConfidence(extractedQuery, primaryMatch)

        Log.d(TAG, "Primary match: ${primaryMatch.code} (confidence: $confidence)")
        Log.d(TAG, "Alternative matches: ${alternatives.size}")

        // Step 5: Load Stock Information
        val stockSummaries = mutableMapOf<String, ItemStockSummary>()
        val stockLookupErrors = mutableMapOf<String, String>()

        if (confidence >= config.minimumConfidence && config.enableStockLookup) {
            Log.d(TAG, "Loading stock info for primary match")
            val stockStartTime = System.currentTimeMillis()
            loadStockInfo(primaryMatch, stockSummaries, stockLookupErrors)
            val stockDuration = System.currentTimeMillis() - stockStartTime
            Log.d(TAG, "Stock lookup completed in ${stockDuration}ms")
        } else {
            Log.d(
                TAG,
                "Skipping stock lookup: confidence=$confidence, minRequired=${config.minimumConfidence}"
            )
        }

        processingSteps.add(
            ProcessingStep(
                step = "Stock Lookup",
                input = "Primary match: ${primaryMatch.code}",
                output = if (stockSummaries.isNotEmpty()) "Success" else "Skipped (low confidence)"
            )
        )

        val result = VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.StockLookup,
            extractedQuery = extractedQuery,
            matchedItem = if (confidence >= config.minimumConfidence) primaryMatch else null,
            confidence = confidence,
            alternativeMatches = alternatives,
            processingSteps = processingSteps,
            stockSummaries = stockSummaries,
            stockLookupErrors = stockLookupErrors,
            // Legacy compatibility
            stockSummary = stockSummaries[primaryMatch.code],
            stockLookupError = stockLookupErrors[primaryMatch.code]
        )

        Log.d(TAG, "=== Stock lookup processing completed ===")
        return result
    }

    /**
     * Domain-specific function for extracting item queries from cleaned transcription text.
     * This logic belongs in the stock lookup use case, NOT in the generic voice processing repository.
     * This contains the comprehensive stock-specific extraction logic that was moved from the repository.
     */
    private fun extractItemQueryFromCleanedText(cleanedText: String): String {
        Log.d(TAG, "--- Starting comprehensive stock-specific extraction ---")
        Log.d(TAG, "Input: '$cleanedText'")

        val input = cleanedText.lowercase().trim()

        // Step 1: Try sophisticated extraction patterns (most specific first)
        val extractionPatterns = listOf(
            // Most specific patterns first - handle "show me stock/inventory/levels for X"
            Regex("(?:can you |please )?(?:show me|get me) (?:the )?(?:stock|inventory|quantity) (?:levels? )?(?:for|of) (.+?)(?:\\s+please)?$"),
            Regex("(?:what (?:is |are )?the |show me the )?(?:stock|inventory|quantity) (?:levels? )?(?:for|of) (.+?)(?:\\s+please)?$"),

            // Standard lookup patterns - FIXED to properly capture item names
            Regex("(?:do a |make a |perform a )?(?:stock )?lookup (?:for|of) (.+?)(?:\\s+please)?$"),
            Regex("(?:do a |make a |perform a )?(?:stock lookup|inventory (?:check|lookup)) for (.+?)(?:\\s+please)?$"),
            Regex("(?:find|check|look up|search for) (?:stock|inventory) (?:for|of) (.+?)(?:\\s+please)?$"),

            // "Do we have X" patterns
            Regex("(?:do we have|have we got) (?:any |some )?(.+?)(?:\\s+(?:in stock|available|stock|inventory|please))*$"),

            // "Find/check X stock" patterns (X before stock/inventory)
            Regex("(?:find|get|check) (.+?) (?:stock|inventory|quantity|levels?)(?:\\s+please)?$"),

            // General lookup patterns - IMPROVED
            Regex("(?:look up|find|search for|check|lookup) (.+?)(?:\\s+(?:stock|inventory|please))*$"),
            Regex("(?:get|show) (?:me )?(.+?)(?:\\s+(?:stock|inventory|please))*$"),

            // Fallback patterns - FIXED to avoid capturing command words
            Regex("(.+?) (?:stock levels?|stock|inventory|quantity|available)(?:\\s+please)?$"),

            // REMOVED the overly broad fallback pattern that was capturing everything
            // Regex("(.+?)(?:\\s+please)?$") // This was causing the issue!
        )

        var extracted: String? = null

        for ((index, pattern) in extractionPatterns.withIndex()) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty() && candidate.length >= 2) {
                    extracted = candidate
                    Log.d(TAG, "Pattern ${index + 1} (${pattern.pattern}) matched: '$candidate'")
                    Log.d(TAG, "Full match: '${match.value}' -> extracted: '$candidate'")
                    break
                }
            }
        }

        // Step 2: If no pattern matched, fall back to stock-specific command word removal
        if (extracted == null) {
            extracted = removeStockCommandWords(input)
            Log.d(TAG, "Using stock command word removal fallback")
        }

        // Step 3: Clean and enhance the extracted query using stock-specific knowledge
        val cleaned = cleanExtractedStockQuery(extracted)

        Log.d(TAG, "Stock extraction chain: '$cleanedText' → '$extracted' → '$cleaned'")
        Log.d(TAG, "--- Comprehensive stock-specific extraction completed ---")

        return cleaned
    }

    /**
     * Remove stock-specific command words and phrases
     */
    private fun removeStockCommandWords(input: String): String {
        val stockCommandPhrases = listOf(
            "do a stock lookup for",
            "make a stock lookup for",
            "perform a stock lookup for",
            "find stock for",
            "check inventory for",
            "look up stock for",
            "search for stock of",
            "inventory lookup for",
            "stock lookup for",
            "what is the stock for",
            "show me stock for",
            "get me stock for",
            "do a lookup for",
            "lookup for",
            "do we have",
            "have we got",
            "look up",
            "search for",
            "find",
            "check",
            "show me",
            "get me"
        )

        var cleaned = input.trim()

        // Remove command phrases (longest first to avoid partial matches)
        for (phrase in stockCommandPhrases.sortedByDescending { it.length }) {
            if (cleaned.startsWith(phrase)) {
                cleaned = cleaned.removePrefix(phrase).trim()
                Log.d(TAG, "Removed stock command phrase: '$phrase'")
                break
            }
        }

        return cleaned
    }

    /**
     * Apply stock-specific cleaning and enhancement to extracted queries
     */
    private fun cleanExtractedStockQuery(query: String): String {
        Log.d(TAG, "Cleaning extracted stock query: '$query'")

        // Remove trailing words that don't help with stock matching
        val stockTrailingWords = listOf(
            "please", "stock", "inventory", "available", "in stock",
            "quantity", "level", "levels", "thanks", "thank you"
        )

        var cleaned = query.trim()

        // Remove trailing unhelpful words
        for (word in stockTrailingWords) {
            if (cleaned.endsWith(" $word")) {
                cleaned = cleaned.removeSuffix(" $word").trim()
            }
        }

        // Remove unhelpful leading preposition phrases (speech recognition errors)
        val stockLeadingPhrases = listOf(
            "from the ", "from ", "of the ", "of ", "at the ", "at ",
            "in the ", "in ", "on the ", "on ", "with the ", "with ", "for ", "for the ", "the "
        )

        for (phrase in stockLeadingPhrases) {
            if (cleaned.startsWith(phrase)) {
                cleaned = cleaned.removePrefix(phrase).trim()
                Log.d(TAG, "Removed leading phrase: '$phrase'")
                break
            }
        }

        // Clean up extra spaces and basic punctuation
        cleaned = cleaned.replace(Regex("\\s+"), " ")
            .replace(Regex("[,;.!?]+$"), "") // Remove trailing punctuation
            .trim()

        // Handle speech patterns and clean up words with stock-specific knowledge
        val originalWords = cleaned.split("\\s+")
        val words = originalWords
            .mapIndexed { index, word ->
                val lowerWord = word.lowercase()

                // Remove stock-specific speech fillers and articles, but be smart about it
                when {
                    // Always remove these stock-specific speech fillers
                    lowerWord in stockSpeechFillers -> null

                    // Remove articles at the beginning or when followed by stock descriptors
                    lowerWord in stockArticles && shouldRemoveStockArticle(
                        originalWords,
                        index
                    ) -> null

                    // Keep everything else that's meaningful for stock lookup
                    word.isNotBlank() && isImportantStockWord(lowerWord) -> lowerWord

                    else -> null
                }
            }
            .filterNotNull()

        val finalCleaned = words.joinToString(" ")

        Log.d(TAG, "Stock query cleaning: '$query' → '$finalCleaned'")

        return if (finalCleaned.isNotEmpty()) finalCleaned else query.lowercase().trim()
    }

    /**
     * Stock-specific speech fillers that should be removed
     */
    private val stockSpeechFillers = setOf(
        "ah", "uh", "um", "er", "mm", "hmm", "well", "so", "like", "you know"
    )

    /**
     * Articles that might not be important for stock lookups
     */
    private val stockArticles = setOf("a", "an", "the")

    /**
     * Determine if an article should be removed in stock context
     */
    private fun shouldRemoveStockArticle(words: List<String>, articleIndex: Int): Boolean {
        // Always remove articles at the start
        if (articleIndex == 0) return true

        // Remove articles before common descriptive words
        if (articleIndex < words.size - 1) {
            val nextWord = words[articleIndex + 1].lowercase()
            // Keep articles before very specific technical terms, remove before generic terms
            return nextWord !in stockTechnicalWords
        }

        return true
    }

    /**
     * Technical words where articles might be important for stock lookups
     */
    private val stockTechnicalWords = setOf(
        // Specific product types where article might be important
        "type", "model", "grade", "series", "version", "size", "style"
    )

    /**
     * Check if a word is important for stock lookup purposes
     */
    private fun isImportantStockWord(word: String): Boolean {
        return word.length > 2 || // Keep longer words
                word.any { it.isDigit() } || // Keep words with numbers
                word.contains('x') || // Keep dimensions like 92x92
                word.contains('.') || // Keep decimals like 2.5
                word.matches(Regex("[a-z]+\\d+")) || // Keep alphanumeric codes
                importantStockShortWords.contains(word) // Keep important short words
    }

    /**
     * Important short words for stock lookups
     */
    private val importantStockShortWords = setOf(
        // Colors
        "red", "blue", "green", "black", "white", "brown", "gray", "grey",
        // Materials  
        "wood", "metal", "steel", "iron", "pvc", "abs",
        // Sizes
        "sm", "md", "lg", "xl", "xs",
        // Common product words
        "box", "bag", "can", "jar", "tube", "roll", "set", "kit",
        // Numbers as words
        "one", "two", "six", "ten"
    )

    private suspend fun loadStockInfo(
        item: MasterItem,
        stockSummaries: MutableMap<String, ItemStockSummary>,
        stockLookupErrors: MutableMap<String, String>
    ) {
        Log.d(TAG, "Loading stock info for item: ${item.code}")

        try {
            withTimeoutOrNull(config.stockLookupTimeoutMs) {
                stockRepository.getItemStockSummary(item)
            }?.fold(
                onSuccess = { summary ->
                    stockSummaries[item.code] = summary
                    Log.d(
                        TAG,
                        "Stock lookup successful for ${item.code}: ${summary.getTotalQuantityDisplay()} in ${summary.locationCount} locations"
                    )
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Stock lookup failed"
                    stockLookupErrors[item.code] = errorMessage
                    Log.e(TAG, "Stock lookup failed for ${item.code}: $errorMessage")
                }
            ) ?: run {
                stockLookupErrors[item.code] = "Stock lookup timed out"
                Log.w(TAG, "Stock lookup timed out for ${item.code}")
            }
        } catch (e: Exception) {
            stockLookupErrors[item.code] = e.message ?: "Unexpected error"
            Log.e(TAG, "Unexpected error during stock lookup for ${item.code}", e)
        }
    }

    private fun calculateConfidence(query: String, item: MasterItem): Float {
        val queryLower = query.lowercase().trim()
        val codeLower = item.code.lowercase()
        val descriptionLower = item.description.lowercase()

        Log.d(
            TAG,
            "Calculating confidence for: '$queryLower' vs '${item.code}' / '${item.description}'"
        )

        return when {
            // Perfect matches
            codeLower == queryLower -> {
                Log.d(TAG, "Perfect code match - confidence: 1.0")
                1.0f
            }

            descriptionLower == queryLower -> {
                Log.d(TAG, "Perfect description match - confidence: 0.95")
                0.95f
            }

            // Very high confidence - exact code or strong word matches
            codeLower.contains(queryLower) || queryLower.contains(codeLower) -> {
                Log.d(TAG, "Code substring match - confidence: 0.9")
                0.9f
            }

            // High confidence - good description matches or word overlaps
            hasStrongWordOverlap(queryLower, descriptionLower) -> {
                val score = calculateWordOverlapScore(queryLower, descriptionLower)
                Log.d(TAG, "Strong word overlap - confidence: $score")
                score
            }

            // Medium-high confidence - partial description matches
            descriptionLower.contains(queryLower) || queryLower.contains(descriptionLower) -> {
                Log.d(TAG, "Description substring match - confidence: 0.8")
                0.8f
            }

            // Medium confidence - fuzzy matching found it with some similarity
            hasNumberSimilarity(queryLower, codeLower) || hasNumberSimilarity(
                queryLower,
                descriptionLower
            ) -> {
                Log.d(TAG, "Number similarity detected - confidence: 0.85")
                0.85f
            }

            // Default for AI/fuzzy matches
            else -> {
                Log.d(TAG, "Fuzzy match (AI found it) - confidence: 0.8")
                0.8f
            }
        }
    }

    private fun hasStrongWordOverlap(query: String, description: String): Boolean {
        val queryWords = query.split(" ").filter { it.length > 2 }.toSet()
        val descWords = description.split(" ").filter { it.length > 2 }.toSet()

        if (queryWords.isEmpty()) return false

        val commonWords = queryWords.intersect(descWords)
        val overlapRatio = commonWords.size.toFloat() / queryWords.size

        return overlapRatio >= 0.6f // 60% or more words match
    }

    private fun calculateWordOverlapScore(query: String, description: String): Float {
        val queryWords = query.split(" ").filter { it.length > 2 }
        val descWords = description.split(" ").filter { it.length > 2 }

        if (queryWords.isEmpty()) return 0.75f

        val exactMatches = queryWords.count { queryWord ->
            descWords.any { descWord -> queryWord.equals(descWord, ignoreCase = true) }
        }

        val partialMatches = queryWords.count { queryWord ->
            descWords.any { descWord ->
                !queryWord.equals(descWord, ignoreCase = true) && (
                        queryWord.contains(descWord, ignoreCase = true) ||
                                descWord.contains(queryWord, ignoreCase = true) ||
                                (queryWord.length > 3 && descWord.length > 3 &&
                                        queryWord.take(3)
                                            .equals(descWord.take(3), ignoreCase = true))
                        )
            }
        }

        val exactRatio = exactMatches.toFloat() / queryWords.size
        val partialRatio = partialMatches.toFloat() / queryWords.size
        val combinedScore = exactRatio + (partialRatio * 0.5f)

        // Convert to confidence score (0.75 - 0.95 range for strong matches)
        return minOf(0.95f, 0.75f + (combinedScore * 0.2f))
    }

    private fun hasNumberSimilarity(query: String, text: String): Boolean {
        val queryNumbers = Regex("\\d+").findAll(query).map { it.value.toIntOrNull() ?: 0 }.toList()
        val textNumbers = Regex("\\d+").findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()

        if (queryNumbers.isEmpty() || textNumbers.isEmpty()) return false

        // Check for exact matches or close numbers
        return queryNumbers.any { queryNum ->
            textNumbers.any { textNum ->
                queryNum == textNum ||
                        kotlin.math.abs(queryNum - textNum) <= 10 ||
                        (queryNum >= 100 && textNum >= 10 &&
                                queryNum.toString().take(2) == textNum.toString().take(2))
            }
        }
    }
}