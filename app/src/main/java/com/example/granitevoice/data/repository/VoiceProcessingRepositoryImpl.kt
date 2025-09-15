package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.remote.client.FastFuzzyMatchClient
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

class VoiceProcessingRepositoryImpl @Inject constructor(
    private val openAIClient: OpenAIClient,
    private val fastFuzzyMatchClient: FastFuzzyMatchClient
) : VoiceProcessingRepository {

    companion object {
        private const val TAG = "VoiceProcessingRepo"
    }

    override suspend fun extractItemQuery(transcription: String): Result<String> {
        Log.d(TAG, "=== Starting fast rule-based extraction ===")
        Log.d(TAG, "Input transcription: '$transcription'")
        val startTime = System.currentTimeMillis()

        return try {
            // Fast rule-based extraction instead of AI
            val extracted = fastExtractItemQuery(transcription)
            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "✅ Fast extraction completed in ${totalDuration}ms")
            Log.d(TAG, "Original: '$transcription'")
            Log.d(TAG, "Extracted: '$extracted'")
            Log.d(TAG, "=== Fast rule-based extraction completed ===")

            Result.success(extracted)
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Fast extraction failed after ${totalDuration}ms", e)

            // Fallback to simple cleaning if fast extraction fails
            Log.d(TAG, "Using cleaning fallback")
            val fallback = cleanTranscriptionForMatching(transcription)
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

    private fun cleanTranscriptionForMatching(transcription: String): String {
        Log.d(TAG, "--- Starting fallback transcription cleaning ---")

        // Remove command phrases but keep all product-related information
        val commandPhrases = listOf(
            "do a stock lookup for",
            "find stock for",
            "check inventory for",
            "look up",
            "search for",
            "do we have",
            "find",
            "check",
            "stock lookup for",
            "inventory lookup for",
            "what is the stock for",
            "show me",
            "get me"
        )

        var cleaned = transcription.lowercase().trim()

        // Remove command phrases from the beginning
        for (phrase in commandPhrases.sortedByDescending { it.length }) {
            if (cleaned.startsWith(phrase)) {
                cleaned = cleaned.removePrefix(phrase).trim()
                Log.d(TAG, "Removed command phrase: '$phrase'")
                break
            }
        }

        // Clean up extra words that don't help matching
        val fillerWords =
            setOf("the", "a", "an", "some", "any", "please", "can", "you", "what", "is")
        val words = cleaned.split("\\s+".toRegex())
            .filter { word ->
            word.isNotBlank() && !fillerWords.contains(word.lowercase())
            }

        val finalCleaned = words.joinToString(" ")

        Log.d(TAG, "Fallback cleaning: '$transcription' → '$finalCleaned'")
        Log.d(TAG, "--- Fallback transcription cleaning completed ---")

        return finalCleaned
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

    private fun fastExtractItemQuery(transcription: String): String {
        Log.d(TAG, "--- Starting fast rule-based extraction ---")

        // Step 0: Correct common speech recognition errors before processing
        val corrected = correctCommonErrors(transcription)
        if (corrected != transcription) {
            Log.d(TAG, "Applied speech corrections: '$transcription' → '$corrected'")
        }

        val input = corrected.lowercase().trim()

        // Step 1: Try extraction patterns (most specific first)
        val extractionPatterns = listOf(
            // Most specific patterns first - handle "show me stock/inventory/levels for X"
            Regex("(?:can you |please )?(?:show me|get me) (?:the )?(?:stock|inventory|quantity) (?:levels? )?(?:for|of) (.+?)(?:\\s+please)?$"),
            Regex("(?:what (?:is |are )?the |show me the )?(?:stock|inventory|quantity) (?:levels? )?(?:for|of) (.+?)(?:\\s+please)?$"),

            // Standard lookup patterns
            Regex("(?:do a |make a |perform a )?(?:stock lookup|inventory (?:check|lookup)) for (.+?)(?:\\s+please)?$"),
            Regex("(?:find|check|look up|search for) (?:stock|inventory) (?:for|of) (.+?)(?:\\s+please)?$"),

            // "Do we have X" patterns
            Regex("(?:do we have|have we got) (?:any |some )?(.+?)(?:\\s+(?:in stock|available|stock|inventory|please))*$"),

            // "Find/check X stock" patterns (X before stock/inventory)
            Regex("(?:find|get|check) (.+?) (?:stock|inventory|quantity|levels?)(?:\\s+please)?$"),

            // General lookup patterns
            Regex("(?:look up|find|search for|check) (.+?)(?:\\s+(?:stock|inventory|please))*$"),

            // Fallback patterns
            Regex("(.+?) (?:stock levels?|stock|inventory|quantity|available)(?:\\s+please)?$"),
            Regex("(?:find|check|show|get) (.+?)(?:\\s+please)?$"),
            Regex("(.+?)(?:\\s+please)?$") // Last resort - everything after cleaning
        )

        var extracted: String? = null

        for ((index, pattern) in extractionPatterns.withIndex()) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty() && candidate.length >= 2) {
                    extracted = candidate
                    Log.d(TAG, "Pattern ${index + 1} matched: '$candidate'")
                    break
                }
            }
        }

        // Step 2: If no pattern matched, fall back to command word removal
        if (extracted == null) {
            extracted = removeCommandWords(input)
            Log.d(TAG, "Using command word removal fallback")
        }

        // Step 3: Clean and enhance the extracted query
        val cleaned = cleanExtractedQuery(extracted)

        Log.d(TAG, "Extraction chain: '$transcription' → '$extracted' → '$cleaned'")
        Log.d(TAG, "--- Fast rule-based extraction completed ---")

        return cleaned
    }

    private fun removeCommandWords(input: String): String {
        val commandPhrases = listOf(
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
        for (phrase in commandPhrases.sortedByDescending { it.length }) {
            if (cleaned.startsWith(phrase)) {
                cleaned = cleaned.removePrefix(phrase).trim()
                break
            }
        }

        return cleaned
    }

    private fun cleanExtractedQuery(query: String): String {
        Log.d(TAG, "Cleaning extracted query: '$query'")

        // Remove trailing words that don't help with matching
        val trailingWords = listOf(
            "please", "stock", "inventory", "available", "in stock",
            "quantity", "level", "levels", "thanks", "thank you"
        )

        var cleaned = query.trim()

        // Remove trailing unhelpful words
        for (word in trailingWords) {
            if (cleaned.endsWith(" $word")) {
                cleaned = cleaned.removeSuffix(" $word").trim()
            }
        }

        // Remove unhelpful leading preposition phrases (speech recognition errors)
        val leadingPhrases = listOf(
            "from the ", "from ", "of the ", "of ", "at the ", "at ",
            "in the ", "in ", "on the ", "on ", "with the ", "with ", "for ", "for the ", "the "
        )

        for (phrase in leadingPhrases) {
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

        // Handle speech patterns and clean up words
        val originalWords = cleaned.split("\\s+")
        val words = originalWords
            .mapIndexed { index, word ->
                val lowerWord = word.lowercase()

                // Remove speech fillers and articles, but be smart about it
                when {
                    // Always remove these speech fillers
                    lowerWord in speechFillers -> null

                    // Remove articles at the beginning or when followed by adjectives/descriptors
                    lowerWord in articles && shouldRemoveArticle(originalWords, index) -> null

                    // Keep everything else that's meaningful
                    word.isNotBlank() && isImportantWord(lowerWord) -> lowerWord

                    else -> null
                }
            }
            .filterNotNull()

        val finalCleaned = words.joinToString(" ")

        Log.d(TAG, "Query cleaning: '$query' → '$finalCleaned'")

        return if (finalCleaned.isNotEmpty()) finalCleaned else query.lowercase().trim()
    }

    private val speechFillers = setOf(
        "ah", "uh", "um", "er", "mm", "hmm", "well", "so", "like", "you know"
    )

    private val articles = setOf("a", "an", "the")

    private fun shouldRemoveArticle(words: List<String>, articleIndex: Int): Boolean {
        // Always remove articles at the start
        if (articleIndex == 0) return true

        // Remove articles before common descriptive words
        if (articleIndex < words.size - 1) {
            val nextWord = words[articleIndex + 1].lowercase()
            // Keep articles before very specific technical terms, remove before generic terms
            return nextWord !in technicalWords
        }

        return true
    }

    private val technicalWords = setOf(
        // Specific product types where article might be important
        "type", "model", "grade", "series", "version", "size", "style"
    )

    private fun isImportantWord(word: String): Boolean {
        return word.length > 2 || // Keep longer words
                word.any { it.isDigit() } || // Keep words with numbers
                word.contains('x') || // Keep dimensions like 92x92
                word.contains('.') || // Keep decimals like 2.5
                word.matches(Regex("[a-z]+\\d+")) || // Keep alphanumeric codes
                importantShortWords.contains(word) // Keep important short words
    }

    private val importantShortWords = setOf(
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

    private fun correctCommonErrors(transcription: String): String {
        Log.d(TAG, "Checking for common speech recognition errors...")

        var corrected = transcription

        // Common command word corrections (case insensitive)
        val corrections = listOf(
            // "stock lookup" variations
            "start lookup" to "stock lookup",
            "start look up" to "stock lookup",
            "stark lookup" to "stock lookup",
            "stop lookup" to "stock lookup",
            "stuck lookup" to "stock lookup",
            "stuff lookup" to "stock lookup",
            "stalk lookup" to "stock lookup",
            "stock look up" to "stock lookup",

            // "find stock" variations
            "fine stock" to "find stock",
            "fund stock" to "find stock",
            "bind stock" to "find stock",
            "find stuck" to "find stock",

            // "inventory" variations
            "in pantry" to "inventory",
            "inventary" to "inventory",
            "inventory" to "inventory", // Keep as-is but normalize

            // "do we have" variations  
            "do we half" to "do we have",
            "do we hab" to "do we have",
            "due we have" to "do we have",

            // "show me" variations
            "show may" to "show me",
            "show my" to "show me",
            "showed me" to "show me",

            // Other common corrections
            "check stuck" to "check stock",
            "look up stuck" to "look up stock",
            "search for stuck" to "search for stock"
        )

        // Apply corrections (case insensitive)
        for ((error, correction) in corrections) {
            if (corrected.contains(error, ignoreCase = true)) {
                // Use regex to replace whole words only (avoid partial matches)
                val regex = Regex("\\b${Regex.escape(error)}\\b", RegexOption.IGNORE_CASE)
                corrected = regex.replace(corrected, correction)
                Log.d(TAG, "Corrected: '$error' → '$correction'")
            }
        }

        return corrected
    }
}