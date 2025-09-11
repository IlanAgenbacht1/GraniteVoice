package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.data.remote.StockDetailGraniteSource
import com.example.granitevoice.data.repository.MasterItemRepositoryImpl
import com.example.granitevoice.domain.model.CommandIntent
import com.example.granitevoice.domain.model.ProcessingStep
import com.example.granitevoice.domain.model.VoiceCommandResult
import com.example.granitevoice.domain.model.VoiceProcessingConfig
import com.example.granitevoice.domain.model.MasterItem
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessVoiceCommandUseCase @Inject constructor(
    private val masterItemRepository: MasterItemRepositoryImpl,
    private val stockDetailSource: StockDetailGraniteSource,
    private val openAIClient: OpenAIClient,
    private val config: VoiceProcessingConfig = VoiceProcessingConfig()
) {

    companion object {
        private const val TAG = "VoiceCommandProcessor"
    }

    suspend fun execute(transcription: String): Result<VoiceCommandResult> {
        return try {
            if (config.enableProcessingLogging) {
                Log.d(TAG, "Processing voice command: '$transcription'")
            }

            val processingSteps = mutableListOf<ProcessingStep>()

            // Step 1: Intent Recognition
            val intent = recognizeIntent(transcription)
            processingSteps.add(
                ProcessingStep(
                    step = "Intent Recognition",
                    input = transcription,
                    output = intent.toString()
                )
            )

            if (intent !is CommandIntent.StockLookup) {
                return Result.success(
                    VoiceCommandResult(
                        originalTranscription = transcription,
                        intent = intent,
                        extractedQuery = "",
                        matchedItem = null,
                        confidence = 0.0f,
                        processingSteps = processingSteps
                    )
                )
            }

            // Step 2: Extract Item Query
            val extractedQuery = extractItemQuery(transcription)
            processingSteps.add(
                ProcessingStep(
                    step = "Query Extraction",
                    input = transcription,
                    output = extractedQuery
                )
            )

            // Step 3: Find Matches
            val searchResults = performSearch(extractedQuery)
            processingSteps.add(
                ProcessingStep(
                    step = "Database Search",
                    input = extractedQuery,
                    output = "${searchResults.size} results found"
                )
            )

            // Step 4: AI-Enhanced Fuzzy Matching (if enabled and no exact match)
            val finalResult = if (config.useAiFuzzyMatching && searchResults.isEmpty()) {
                performAiFuzzyMatching(extractedQuery, processingSteps)
            } else {
                searchResults
            }

            val bestMatch = finalResult.firstOrNull()
            val alternatives = if (finalResult.size > 1) {
                finalResult.drop(1).take(config.maxAlternatives - 1)
            } else {
                emptyList()
            }

            // Calculate confidence based on match source and ranking
            val confidence = calculateConfidence(
                extractedQuery,
                bestMatch,
                finalResult.size,
                searchResults.isNotEmpty()
            )

            // Step 5: Stock Detail Lookup (PRIMARY MATCH ONLY for speed)
            val stockSummaries =
                mutableMapOf<String, com.example.granitevoice.domain.model.ItemStockSummary>()
            val stockLookupErrors = mutableMapOf<String, String>()

            // Only lookup stock for primary match initially to improve speed
            if (bestMatch != null && confidence >= config.minimumConfidence && config.enableStockLookup) {
                Log.d(TAG, "Performing stock lookup for primary match only: ${bestMatch.code}")

                val stockLookupResult = performStockLookup(bestMatch, processingSteps)
                stockLookupResult.fold(
                    onSuccess = { summary ->
                        stockSummaries[bestMatch.code] = summary
                        Log.d(
                            TAG,
                            "Primary stock lookup successful for ${bestMatch.code}: ${summary.getTotalQuantityDisplay()}"
                        )
                    },
                    onFailure = { exception ->
                        val errorMessage = exception.message ?: "Stock lookup failed"
                        stockLookupErrors[bestMatch.code] = errorMessage
                        Log.e(
                            TAG,
                            "Primary stock lookup failed for ${bestMatch.code}: $errorMessage"
                        )
                    }
                )

                processingSteps.add(
                    ProcessingStep(
                        step = "Stock Lookup",
                        input = "Primary item: ${bestMatch.code}",
                        output = if (stockSummaries.containsKey(bestMatch.code)) "Success" else "Failed"
                    )
                )
            } else if (bestMatch != null) {
                Log.d(
                    TAG,
                    "Skipping stock lookup - confidence ${confidence} below threshold ${config.minimumConfidence}"
                )
            }

            val result = VoiceCommandResult(
                originalTranscription = transcription,
                intent = intent,
                extractedQuery = extractedQuery,
                matchedItem = if (confidence >= config.minimumConfidence) bestMatch else null,
                confidence = confidence,
                alternativeMatches = alternatives,
                processingSteps = processingSteps,
                stockSummaries = stockSummaries,
                stockLookupErrors = stockLookupErrors,
                // Legacy fields for backward compatibility
                stockSummary = if (bestMatch != null) stockSummaries[bestMatch.code] else null,
                stockLookupError = if (bestMatch != null) stockLookupErrors[bestMatch.code] else null
            )

            if (config.enableProcessingLogging) {
                Log.d(TAG, "Voice command processed: ${result.getResultSummary()}")
            }

            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing voice command", e)
            Result.failure(e)
        }
    }

    private suspend fun performStockLookup(
        masterItem: MasterItem,
        processingSteps: MutableList<ProcessingStep>
    ): Result<com.example.granitevoice.domain.model.ItemStockSummary> {
        return try {
            // Use timeout to prevent hanging
            val result = withTimeoutOrNull(config.stockLookupTimeoutMs) {
                stockDetailSource.getItemStockSummary(masterItem)
            }

            when {
                result == null -> {
                    Result.failure(Exception("Stock lookup timed out after ${config.stockLookupTimeoutMs}ms"))
                }
                result.isSuccess -> {
                    result
                }
                else -> {
                    result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stock lookup failed for item: ${masterItem.code}", e)
            Result.failure(e)
        }
    }

    private fun recognizeIntent(transcription: String): CommandIntent {
        val lowerTranscription = transcription.lowercase()

        // Enhanced stock lookup keywords including natural phrases
        val stockKeywords = listOf(
            "stock", "inventory", "item", "product", "part", "component",
            "lookup", "look up", "find", "search", "get", "show", "check",
            "what is", "where is", "do we have", "how much", "how many",
            "quantity", "available", "in stock", "locate", "tell me about"
        )

        Log.d(TAG, "Intent recognition for: '$lowerTranscription'")

        val hasStockIntent = stockKeywords.any { keyword ->
            lowerTranscription.contains(keyword)
        }

        val intent = if (hasStockIntent) {
            CommandIntent.StockLookup
        } else {
            CommandIntent.Unknown
        }

        Log.d(TAG, "Intent recognized as: $intent")
        return intent
    }

    private suspend fun extractItemQuery(transcription: String): String {
        // Use AI to extract the item query from natural language - optimized for speed
        val prompt = """
        Extract just the item/product name from this command. Keep descriptors.
        
        Command: "$transcription"
        
        Examples:
        "Find stock for hammer" -> "hammer"
        "Do we have screws" -> "screws"
        "Box type 1" -> "box type 1"
        "Restricted cage free organic eggs" -> "restricted cage free organic eggs"
        
        Item:
        """.trimIndent()

        return try {
            val response = openAIClient.makeChatRequestRaw(prompt)
            val extracted = response.trim().removePrefix("\"").removeSuffix("\"")

            Log.d(TAG, "AI extracted query: '$transcription' -> '$extracted'")

            // If AI extraction seems too short or failed, use fallback
            if (extracted.length < 3 || extracted.equals(transcription, ignoreCase = true)) {
                Log.d(TAG, "AI extraction seems insufficient, using fallback")
                val fallback = simpleQueryExtraction(transcription)
                Log.d(TAG, "Fallback extraction: '$fallback'")
                return fallback
            }

            extracted
        } catch (e: Exception) {
            Log.e(TAG, "AI query extraction failed, falling back to simple extraction", e)
            // Fallback to simple extraction
            simpleQueryExtraction(transcription)
        }
    }

    private fun simpleQueryExtraction(transcription: String): String {
        // More conservative fallback extraction - preserve more words
        val stopWords = setOf(
            "find",
            "stock",
            "for",
            "inventory",
            "lookup",
            "search",
            "get",
            "show",
            "check",
            "what",
            "is",
            "where",
            "do",
            "we",
            "have",
            "any",
            "the",
            "a",
            "an",
            "some",
            "of",
            "in",
            "how",
            "much",
            "many",
            "quantity",
            "available",
            "level",
            "levels",
            "me",
            "information",
            "about"
        )

        val extracted = transcription.split(" ")
            .map { it.lowercase().replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.isNotEmpty() && !stopWords.contains(it) }
            .joinToString(" ")

        Log.d(TAG, "Simple extraction: '$transcription' -> '$extracted'")
        return extracted
    }

    private suspend fun performSearch(query: String): List<MasterItem> {
        return try {
            // Step 1: Simple programmatic matching first
            val simpleMatches = performSimpleProgrammaticSearch(query)
            if (simpleMatches.isNotEmpty()) {
                Log.d(TAG, "Found ${simpleMatches.size} simple matches for '$query'")
                return simpleMatches
            }

            Log.d(TAG, "No simple matches found, will use AI matching")
            return emptyList() // Trigger AI fuzzy matching

        } catch (e: Exception) {
            Log.e(TAG, "Database search failed", e)
            emptyList()
        }
    }

    /**
     * Simple programmatic search for exact/obvious matches
     */
    private suspend fun performSimpleProgrammaticSearch(query: String): List<MasterItem> {
        val queryLower = query.lowercase().trim()

        // Try exact code match first
        val exactCodeMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase() == queryLower }

        if (exactCodeMatches.isNotEmpty()) {
            Log.d(TAG, "Found exact code matches: ${exactCodeMatches.size}")
            return exactCodeMatches
        }

        // Try simple substring matches in code (prioritized)
        val codeSubstringMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase().contains(queryLower) }

        if (codeSubstringMatches.isNotEmpty()) {
            Log.d(TAG, "Found code substring matches: ${codeSubstringMatches.size}")
            return codeSubstringMatches
        }

        // Try simple substring matches in description
        val descriptionMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.description.lowercase().contains(queryLower) }

        if (descriptionMatches.isNotEmpty()) {
            Log.d(TAG, "Found description substring matches: ${descriptionMatches.size}")
            return descriptionMatches.take(3) // Limit description matches
        }

        return emptyList()
    }

    private suspend fun performAiFuzzyMatching(
        query: String,
        processingSteps: MutableList<ProcessingStep>
    ): List<MasterItem> {
        return try {
            val allItems = masterItemRepository.getMasterItems()
            if (allItems.isEmpty()) {
                Log.w(TAG, "No master items available for AI fuzzy matching")
                return emptyList()
            }

            Log.d(TAG, "Total master items available: ${allItems.size}")

            // Step 2: If too many items, filter by first letter only
            val candidates = if (allItems.size > 100) {
                val firstLetter = query.trim().lowercase().firstOrNull()
                if (firstLetter != null) {
                    val firstLetterItems = allItems.filter { item ->
                        item.code.lowercase().startsWith(firstLetter.toString()) ||
                                item.description.lowercase().startsWith(firstLetter.toString())
                    }
                    Log.d(
                        TAG,
                        "Filtered by first letter '$firstLetter': ${firstLetterItems.size} items"
                    )

                    // Log the actual candidates (simple format for large lists)
                    firstLetterItems.forEachIndexed { index, item ->
                        Log.d(TAG, "Candidate[$index]: ${item.code} | ${item.description}")
                    }

                    firstLetterItems // Use whatever the query returns
                } else {
                    Log.d(TAG, "No first letter found, using random sample")
                    val randomSample = allItems.shuffled().take(50)

                    // Log random sample candidates
                    randomSample.forEachIndexed { index, item ->
                        Log.d(TAG, "RandomCandidate[$index]: ${item.code} | ${item.description}")
                    }

                    randomSample // Reasonable fallback
                }
            } else {
                Log.d(TAG, "Using all ${allItems.size} items")

                // Log all candidates
                allItems.forEachIndexed { index, item ->
                    Log.d(TAG, "AllCandidate[$index]: ${item.code} | ${item.description}")
                }

                allItems // Use all items if manageable
            }

            Log.d(TAG, "Sending ${candidates.size} candidates to AI for matching")

            // Step 3: Simple AI matching
            val fuzzyMatchResults = performSimpleAiFuzzyMatch(query, candidates)
            processingSteps.add(
                ProcessingStep(
                    step = "AI Fuzzy Matching",
                    input = "Query: '$query', ${candidates.size} candidates from ${allItems.size} total",
                    output = "Found ${fuzzyMatchResults.size} matches"
                )
            )

            fuzzyMatchResults

        } catch (e: Exception) {
            Log.e(TAG, "AI fuzzy matching failed", e)
            emptyList()
        }
    }

    private suspend fun performSimpleAiFuzzyMatch(
        query: String,
        candidates: List<MasterItem>
    ): List<MasterItem> {
        if (candidates.isEmpty()) return emptyList()

        // Send only descriptions to AI - codes are internal data layer concern
        val descriptions = candidates.map { it.description }.distinct()

        Log.d(TAG, "AI matching '$query' against ${descriptions.size} descriptions")

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

        return try {
            val response = openAIClient.makeChatRequestRaw(prompt).trim()
            Log.d(TAG, "AI response: $response")

            if (response.contains("NO_MATCHES_FOUND", ignoreCase = true)) {
                Log.d(TAG, "AI found no matches")
                return emptyList()
            }

            // Parse matched descriptions and map back to MasterItems
            val matchedItems = mutableListOf<MasterItem>()
            val alreadyMatched = mutableSetOf<String>()
            val lines = response.split("\n")

            for (line in lines) {
                if (line.startsWith("MATCH", ignoreCase = true)) {
                    val matchedDescription = line.substringAfter(":").trim()
                    Log.d(TAG, "Processing AI match: '$matchedDescription'")

                    // Find ALL MasterItems with this description
                    val itemsWithDescription = candidates.filter { item ->
                        item.description.equals(matchedDescription, ignoreCase = true) &&
                                !alreadyMatched.contains(item.code) // Don't include already matched items
                    }

                    if (itemsWithDescription.isNotEmpty()) {
                        // Add ALL different items with this description (up to available space)
                        val remainingSlots = config.maxAlternatives - matchedItems.size
                        val itemsToAdd = itemsWithDescription.take(remainingSlots)

                        itemsToAdd.forEach { item ->
                            matchedItems.add(item)
                            alreadyMatched.add(item.code)
                            Log.d(
                                TAG,
                                "Successfully mapped description '${matchedDescription}' to item: ${item.code}"
                            )
                        }

                        if (itemsWithDescription.size > itemsToAdd.size) {
                            Log.d(
                                TAG,
                                "Found ${itemsWithDescription.size} items with description '$matchedDescription', added ${itemsToAdd.size}"
                            )
                        }
                    } else {
                        Log.w(TAG, "Could not find item with description: '$matchedDescription'")
                    }

                    // Stop if we've filled all available slots
                    if (matchedItems.size >= config.maxAlternatives) {
                        break
                    }
                }
            }

            Log.d(
                TAG,
                "AI fuzzy matching found ${matchedItems.size} unique items for '$query' (searched ${descriptions.size} descriptions)"
            )

            // Return up to config.maxAlternatives matches
            matchedItems.take(config.maxAlternatives)

        } catch (e: Exception) {
            Log.e(TAG, "AI matching failed", e)
            emptyList()
        }
    }

    private fun calculateConfidence(
        query: String,
        bestMatch: MasterItem?,
        resultCount: Int,
        isExactMatch: Boolean
    ): Float {
        if (bestMatch == null) return 0.0f

        val queryLower = query.lowercase()
        val codeLower = bestMatch.code.lowercase()
        val descriptionLower = bestMatch.description.lowercase()

        var confidence = 0.0f

        // For AI-found matches, be more generous with confidence
        // Check for word matches in the description
        val queryWords = queryLower.split(" ").filter { it.length > 2 }
        val descriptionWords = descriptionLower.split(" ").filter { it.length > 2 }
        val codeWords = codeLower.split(Regex("[-_\\s]")).filter { it.length > 1 }

        val matchingDescWords = queryWords.intersect(descriptionWords.toSet()).size
        val matchingCodeWords = queryWords.intersect(codeWords.toSet()).size

        // Enhanced phonetic similarity check for speech recognition errors
        val hasPhoneticSimilarity = checkPhoneticSimilarity(queryLower, codeLower, descriptionLower)

        Log.d(
            TAG,
            "Analyzing '${bestMatch.code}': query='$queryLower', code='$codeLower', desc='$descriptionLower'"
        )
        Log.d(
            TAG,
            "Word matches - desc: $matchingDescWords, code: $matchingCodeWords, isExact: $isExactMatch, phonetic: $hasPhoneticSimilarity"
        )

        // Calculate base confidence based on match quality
        when {
            // Perfect matches
            codeLower == queryLower -> confidence = 1.0f
            descriptionLower == queryLower -> confidence = 0.95f

            // Strong phonetic similarity (AI found it, likely correct despite speech errors)
            hasPhoneticSimilarity -> confidence = 0.8f

            // Strong word matches
            matchingDescWords >= queryWords.size * 0.8 -> confidence =
                0.9f  // Most words match in description
            matchingCodeWords >= queryWords.size * 0.7 -> confidence = 0.85f // Good code match
            matchingDescWords >= queryWords.size * 0.6 -> confidence =
                0.8f  // More than half words match

            // Partial matches
            codeLower.contains(queryLower) || queryLower.contains(codeLower) -> confidence = 0.75f
            descriptionLower.contains(queryLower) || queryLower.contains(descriptionLower) -> confidence =
                0.7f

            // Some word matches
            matchingDescWords > 0 || matchingCodeWords > 0 -> confidence = 0.65f

            // AI found it, but weak match (fuzzy matching result)
            else -> confidence = 0.6f
        }

        // Adjust confidence based on match source and ranking
        if (isExactMatch) {
            // Boost confidence for exact database matches
            confidence *= 1.15f
            Log.d(TAG, "Boosted confidence for exact match: $confidence")
        } else {
            // This is an AI fuzzy match - apply ranking penalty
            val rankingPenalty = when (resultCount) {
                1 -> 1.0f      // Single match - no penalty
                2 -> 0.95f     // First of two matches - small penalty
                3 -> 0.9f      // First of three matches - moderate penalty
                else -> 0.85f  // First of many matches - larger penalty
            }
            confidence *= rankingPenalty
            Log.d(TAG, "Applied AI ranking penalty (${rankingPenalty}): $confidence")
        }

        val finalConfidence = confidence.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Final confidence for '${bestMatch.code}': $finalConfidence")

        return finalConfidence
    }

    /**
     * Enhanced phonetic similarity check for speech recognition errors
     */
    private fun checkPhoneticSimilarity(query: String, code: String, description: String): Boolean {
        // Remove spaces and special characters for comparison
        val queryClean = query.replace(Regex("[^a-z0-9]"), "")
        val codeClean = code.replace(Regex("[^a-z0-9]"), "")
        val descClean = description.replace(Regex("[^a-z0-9]"), "")

        // Common speech recognition error mappings
        val phoneticMappings = mapOf(
            // Common phrase mishearings
            "nestrun" to listOf("nestron", "nest run", "nesrun"),
            "nestron" to listOf("nestrun", "nest run"),
            "boxtype" to listOf("bot type", "bottle type"),
            "cagefree" to listOf("cage free", "cadge free"),

            // Number variations
            "5052" to listOf("fifty fifty two", "50 52"),
            "4042" to listOf("forty forty two", "40 42"),
            "2444" to listOf("twenty four forty four", "24 44"),

            // Common word substitutions
            "type" to listOf("tight", "tip"),
            "free" to listOf("three"),
            "run" to listOf("ron", "ran")
        )

        // Check if cleaned query has phonetic similarity to code or description
        for ((canonical, variations) in phoneticMappings) {
            if (variations.any { variation ->
                    queryClean.contains(variation.replace(" ", "")) ||
                            variation.replace(" ", "").contains(queryClean)
                }) {
                if (codeClean.contains(canonical) || descClean.contains(canonical)) {
                    Log.d(TAG, "Found phonetic match: '$query' matches via '$canonical'")
                    return true
                }
            }

            if (queryClean.contains(canonical)) {
                if (variations.any { variation ->
                        codeClean.contains(variation.replace(" ", "")) ||
                                descClean.contains(variation.replace(" ", ""))
                    }) {
                    Log.d(TAG, "Found reverse phonetic match: '$query' contains '$canonical'")
                    return true
                }
            }
        }

        // Check for similar character sequences (Levenshtein-like)
        val similarity = calculateStringSimilarity(queryClean, codeClean)
        val descSimilarity = calculateStringSimilarity(queryClean, descClean)
        val maxSimilarity = maxOf(similarity, descSimilarity)

        if (maxSimilarity > 0.7f) {
            Log.d(TAG, "Found high string similarity: $maxSimilarity")
            return true
        }

        return false
    }

    /**
     * Calculate string similarity ratio (0.0 to 1.0)
     */
    private fun calculateStringSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1 == s2) return 1.0f

        val maxLength = maxOf(s1.length, s2.length)
        val editDistance = calculateLevenshteinDistance(s1, s2)

        return (maxLength - editDistance).toFloat() / maxLength
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Load stock information for a specific item on-demand
     * Used for lazy loading of alternative items
     */
    suspend fun loadStockForItem(item: MasterItem): Result<com.example.granitevoice.domain.model.ItemStockSummary> {
        Log.d(TAG, "Lazy loading stock for item: ${item.code}")
        return performStockLookup(item, mutableListOf())
    }
}