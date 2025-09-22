package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

class SearchLocalItemsUseCase @Inject constructor(
    private val masterItemRepository: MasterItemRepository,
    private val voiceProcessingRepository: VoiceProcessingRepository
) {

    companion object {
        private const val TAG = "SearchItems"
    }

    suspend operator fun invoke(query: String): List<MasterItem> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Starting item search ===")
        Log.d(TAG, "Search query: '$query'")

        // Step 1: Try simple database search first
        val simpleStartTime = System.currentTimeMillis()
        val simpleMatches = performSimpleSearch(query)
        val simpleDuration = System.currentTimeMillis() - simpleStartTime

        if (simpleMatches.isNotEmpty()) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(
                TAG,
                "✅ Simple search completed in ${simpleDuration}ms (total: ${totalDuration}ms)"
            )
            Log.d(TAG, "Found ${simpleMatches.size} simple matches:")
            simpleMatches.forEachIndexed { index, item ->
                Log.d(TAG, "  [$index] ${item.code}: ${item.description}")
            }
            Log.d(TAG, "=== Item search completed ===")
            return simpleMatches
        }

        Log.d(TAG, "Simple search completed in ${simpleDuration}ms - no matches found")
        Log.d(TAG, "Proceeding with AI fuzzy matching...")

        // Step 2: AI fuzzy matching with filtered candidates
        val aiStartTime = System.currentTimeMillis()

        val allItems = masterItemRepository.getMasterItems()
        Log.d(TAG, "Retrieved ${allItems.size} total items from database")

        val candidates = getSmartCandidates(query, allItems)
        val candidatesDuration = System.currentTimeMillis() - aiStartTime
        Log.d(TAG, "Smart candidate selection completed in ${candidatesDuration}ms")

        val aiMatches = voiceProcessingRepository.findSimilarItems(query, candidates)
            .getOrElse { exception ->
                Log.e(TAG, "AI matching failed: ${exception.message}")
                emptyList()
            }

        val aiDuration = System.currentTimeMillis() - aiStartTime
        val totalDuration = System.currentTimeMillis() - startTime

        if (aiMatches.isNotEmpty()) {
            Log.d(
                TAG,
                "✅ AI fuzzy matching completed in ${aiDuration}ms (total: ${totalDuration}ms)"
            )
            Log.d(TAG, "Found ${aiMatches.size} AI matches:")
            aiMatches.forEachIndexed { index, item ->
                Log.d(TAG, "  [$index] ${item.code}: ${item.description}")
            }
        } else {
            Log.d(
                TAG,
                "❌ AI fuzzy matching completed in ${aiDuration}ms - no matches found (total: ${totalDuration}ms)"
            )
        }

        Log.d(TAG, "=== Item search completed ===")
        return aiMatches
    }

    private suspend fun performSimpleSearch(query: String): List<MasterItem> {
        val queryLower = query.lowercase().trim()
        Log.d(TAG, "--- Starting simple database search ---")
        Log.d(TAG, "Normalized query: '$queryLower'")

        // Try exact code match first
        val exactStartTime = System.currentTimeMillis()
        val exactCodeMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase() == queryLower }
        val exactDuration = System.currentTimeMillis() - exactStartTime

        if (exactCodeMatches.isNotEmpty()) {
            Log.d(TAG, "✅ Found ${exactCodeMatches.size} exact code matches in ${exactDuration}ms:")
            exactCodeMatches.forEach { item ->
                Log.d(TAG, "  - ${item.code}: ${item.description}")
            }
            return exactCodeMatches
        }
        Log.d(TAG, "No exact code matches found (${exactDuration}ms)")

        // Try substring matches in code
        val codeStartTime = System.currentTimeMillis()
        val codeSubstringMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase().contains(queryLower) }
        val codeDuration = System.currentTimeMillis() - codeStartTime

        if (codeSubstringMatches.isNotEmpty()) {
            Log.d(
                TAG,
                "✅ Found ${codeSubstringMatches.size} code substring matches in ${codeDuration}ms:"
            )
            codeSubstringMatches.forEach { item ->
                Log.d(TAG, "  - ${item.code}: ${item.description}")
            }
            return codeSubstringMatches
        }
        Log.d(TAG, "No code substring matches found (${codeDuration}ms)")

        // Try substring matches in description
        val descStartTime = System.currentTimeMillis()
        val descriptionMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.description.lowercase().contains(queryLower) }
        val descDuration = System.currentTimeMillis() - descStartTime

        if (descriptionMatches.isNotEmpty()) {
            Log.d(
                TAG,
                "✅ Found ${descriptionMatches.size} description substring matches in ${descDuration}ms:"
            )
            descriptionMatches.take(3).forEach { item ->
                Log.d(TAG, "  - ${item.code}: ${item.description}")
            }
            Log.d(TAG, "--- Simple database search completed ---")
            return descriptionMatches.take(3)
        }
        Log.d(TAG, "No description substring matches found (${descDuration}ms)")
        Log.d(TAG, "--- Simple database search completed - no matches ---")

        return emptyList()
    }

    private fun getSmartCandidates(query: String, allItems: List<MasterItem>): List<MasterItem> {
        Log.d(TAG, "--- Starting smart candidate selection ---")

        return if (allItems.size > 100) {
            // Filter by first letter for large datasets
            val firstLetter = query.trim().lowercase().firstOrNull()
            if (firstLetter != null) {
                val candidateStartTime = System.currentTimeMillis()
                val candidates = allItems.filter { item ->
                    item.code.lowercase().startsWith(firstLetter.toString()) ||
                            item.description.lowercase().startsWith(firstLetter.toString())
                }
                val candidateDuration = System.currentTimeMillis() - candidateStartTime

                Log.d(
                    TAG,
                    "First letter filtering ('$firstLetter') completed in ${candidateDuration}ms"
                )
                Log.d(TAG, "Filtered from ${allItems.size} to ${candidates.size} candidates")

                if (candidates.isNotEmpty()) {
                    Log.d(TAG, "Sample candidates:")
                    candidates.take(5).forEach { item ->
                        Log.d(TAG, "  - ${item.code}: ${item.description}")
                    }
                    if (candidates.size > 5) {
                        Log.d(TAG, "  ... and ${candidates.size - 5} more")
                    }
                }

                candidates
            } else {
                Log.d(TAG, "No first letter found, using random sample of 50 items")
                val randomSample = allItems.shuffled().take(50)
                Log.d(TAG, "Random sample selected:")
                randomSample.take(3).forEach { item ->
                    Log.d(TAG, "  - ${item.code}: ${item.description}")
                }
                Log.d(TAG, "  ... and ${randomSample.size - 3} more")
                randomSample
            }
        } else {
            Log.d(TAG, "Dataset small enough (${allItems.size} ≤ 100), using all items")
            allItems
        }
    }
}