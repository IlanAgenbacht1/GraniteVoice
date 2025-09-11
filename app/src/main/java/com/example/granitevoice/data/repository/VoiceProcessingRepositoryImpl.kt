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

            val response = openAIClient.makeChatRequestRaw(prompt)
            val extracted = response.trim().removePrefix("\"").removeSuffix("\"")

            Log.d(TAG, "AI extracted query: '$transcription' -> '$extracted'")

            if (extracted.length < 3 || extracted.equals(transcription, ignoreCase = true)) {
                Log.d(TAG, "AI extraction seems insufficient, using fallback")
                val fallback = simpleQueryExtraction(transcription)
                Result.success(fallback)
            } else {
                Result.success(extracted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI query extraction failed", e)
            Result.success(simpleQueryExtraction(transcription))
        }
    }

    override suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>
    ): Result<List<MasterItem>> {
        return try {
            val descriptions = candidates.map { it.description }.distinct()

            Log.d(TAG, "AI matching '$query' against ${descriptions.size} descriptions")

            val prompt = """
            You're an expert fuzzy matcher. Match spoken query to items. Handle speech errors.

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

            val response = openAIClient.makeChatRequestRaw(prompt).trim()
            Log.d(TAG, "AI response: $response")

            if (response.contains("NO_MATCHES_FOUND", ignoreCase = true)) {
                Log.d(TAG, "AI found no matches")
                return Result.success(emptyList())
            }

            val matchedItems = parseAIMatches(response, candidates)
            Result.success(matchedItems)

        } catch (e: Exception) {
            Log.e(TAG, "AI matching failed", e)
            Result.success(emptyList())
        }
    }

    private fun simpleQueryExtraction(transcription: String): String {
        val stopWords = setOf(
            "find", "stock", "for", "inventory", "lookup", "search", "get", "show", "check",
            "what", "is", "where", "do", "we", "have", "any", "the", "a", "an", "some",
            "of", "in", "how", "much", "many", "quantity", "available", "level", "levels",
            "me", "information", "about"
        )

        return transcription.split(" ")
            .map { it.lowercase().replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.isNotEmpty() && !stopWords.contains(it) }
            .joinToString(" ")
    }

    private fun parseAIMatches(response: String, candidates: List<MasterItem>): List<MasterItem> {
        val matchedItems = mutableListOf<MasterItem>()
        val alreadyMatched = mutableSetOf<String>()
        val lines = response.split("\n")

        for (line in lines) {
            if (line.startsWith("MATCH", ignoreCase = true)) {
                val matchedDescription = line.substringAfter(":").trim()
                Log.d(TAG, "Processing AI match: '$matchedDescription'")

                val itemsWithDescription = candidates.filter { item ->
                    item.description.equals(matchedDescription, ignoreCase = true) &&
                            !alreadyMatched.contains(item.code)
                }

                itemsWithDescription.forEach { item ->
                    if (!alreadyMatched.contains(item.code)) {
                        matchedItems.add(item)
                        alreadyMatched.add(item.code)
                        Log.d(
                            TAG,
                            "Successfully mapped description '${matchedDescription}' to item: ${item.code}"
                        )
                    }
                }
            }
        }

        Log.d(TAG, "AI fuzzy matching found ${matchedItems.size} unique items")
        return matchedItems.take(5) // Limit to 5 matches
    }
}