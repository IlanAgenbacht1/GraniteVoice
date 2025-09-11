package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

class VoiceProcessingRepositoryImpl @Inject constructor(
    private val openAIClient: OpenAIClient
) : VoiceProcessingRepository {

    companion object {
        private const val TAG = "VoiceProcessingRepo"
    }

    override suspend fun extractItemQuery(transcription: String): Result<String> {
        Log.d(TAG, "=== Starting query extraction ===")
        Log.d(TAG, "Input transcription: '$transcription'")
        val startTime = System.currentTimeMillis()

        return try {
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

            Log.d(TAG, "Sending extraction request to AI (${prompt.length} chars)")

            val aiStartTime = System.currentTimeMillis()
            val response = openAIClient.makeChatRequestRaw(prompt)
            val aiDuration = System.currentTimeMillis() - aiStartTime

            val extracted = response.trim().removePrefix("\"").removeSuffix("\"")
            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "✅ AI extraction completed in ${aiDuration}ms (total: ${totalDuration}ms)")
            Log.d(TAG, "AI response: '$response'")
            Log.d(TAG, "Cleaned extraction: '$extracted'")

            if (extracted.length < 3 || extracted.equals(transcription, ignoreCase = true)) {
                Log.d(
                    TAG,
                    "⚠️ AI extraction seems insufficient (too short or identical), using fallback"
                )
                val fallback = simpleQueryExtraction(transcription)
                Log.d(TAG, "Fallback extraction: '$fallback'")
                Log.d(TAG, "=== Query extraction completed (fallback) ===")
                Result.success(fallback)
            } else {
                Log.d(TAG, "=== Query extraction completed (AI) ===")
                Result.success(extracted)
            }
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ AI query extraction failed after ${totalDuration}ms", e)
            Log.d(TAG, "Using fallback extraction method")

            val fallback = simpleQueryExtraction(transcription)
            Log.d(TAG, "Fallback extraction: '$fallback'")
            Log.d(TAG, "=== Query extraction completed (fallback after error) ===")
            Result.success(fallback)
        }
    }

    override suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>
    ): Result<List<MasterItem>> {
        Log.d(TAG, "=== Starting AI fuzzy matching ===")
        Log.d(TAG, "Query: '$query'")
        Log.d(TAG, "Candidates: ${candidates.size} items")
        val startTime = System.currentTimeMillis()

        return try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "❌ No candidates provided for AI matching")
                return Result.success(emptyList())
            }

            val descriptions = candidates.map { it.description }.distinct()
            Log.d(TAG, "Unique descriptions: ${descriptions.size} (from ${candidates.size} items)")

            // Log sample descriptions
            Log.d(TAG, "Sample descriptions:")
            descriptions.take(5).forEach { desc ->
                Log.d(TAG, "  - $desc")
            }
            if (descriptions.size > 5) {
                Log.d(TAG, "  ... and ${descriptions.size - 5} more")
            }

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

            Log.d(TAG, "Sending matching request to AI (${prompt.length} chars)")

            val aiStartTime = System.currentTimeMillis()
            val response = openAIClient.makeChatRequestRaw(prompt).trim()
            val aiDuration = System.currentTimeMillis() - aiStartTime

            Log.d(TAG, "✅ AI matching completed in ${aiDuration}ms")
            Log.d(TAG, "AI response: '$response'")

            if (response.contains("NO_MATCHES_FOUND", ignoreCase = true)) {
                val totalDuration = System.currentTimeMillis() - startTime
                Log.d(TAG, "🤷 AI found no matches (total: ${totalDuration}ms)")
                Log.d(TAG, "=== AI fuzzy matching completed ===")
                return Result.success(emptyList())
            }

            val parseStartTime = System.currentTimeMillis()
            val matchedItems = parseAIMatches(response, candidates)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Parsing completed in ${parseDuration}ms")
            Log.d(TAG, "✅ AI fuzzy matching completed in ${totalDuration}ms")
            Log.d(TAG, "Found ${matchedItems.size} matched items")

            matchedItems.forEach { item ->
                Log.d(TAG, "  ✓ ${item.code}: ${item.description}")
            }

            Log.d(TAG, "=== AI fuzzy matching completed ===")
            Result.success(matchedItems)

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ AI matching failed after ${totalDuration}ms", e)
            Log.d(TAG, "=== AI fuzzy matching completed (with error) ===")
            Result.success(emptyList())
        }
    }

    private fun simpleQueryExtraction(transcription: String): String {
        Log.d(TAG, "--- Starting simple query extraction ---")

        val stopWords = setOf(
            "find", "stock", "for", "inventory", "lookup", "search", "get", "show", "check",
            "what", "is", "where", "do", "we", "have", "any", "the", "a", "an", "some",
            "of", "in", "how", "much", "many", "quantity", "available", "level", "levels",
            "me", "information", "about"
        )

        val words = transcription.split(" ")
            .map { it.lowercase().replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.isNotEmpty() && !stopWords.contains(it) }

        val extracted = words.joinToString(" ")

        Log.d(TAG, "Original words: ${transcription.split(" ")}")
        Log.d(TAG, "Filtered words: $words")
        Log.d(TAG, "Extracted query: '$extracted'")
        Log.d(TAG, "--- Simple query extraction completed ---")

        return extracted
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