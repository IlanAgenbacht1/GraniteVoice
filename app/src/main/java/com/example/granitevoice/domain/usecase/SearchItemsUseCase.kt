package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

class SearchItemsUseCase @Inject constructor(
    private val masterItemRepository: MasterItemRepository,
    private val voiceProcessingRepository: VoiceProcessingRepository
) {

    companion object {
        private const val TAG = "SearchItems"
    }

    suspend operator fun invoke(query: String): List<MasterItem> {
        Log.d(TAG, "Searching for items with query: '$query'")

        // Step 1: Try simple database search first
        val simpleMatches = performSimpleSearch(query)
        if (simpleMatches.isNotEmpty()) {
            Log.d(TAG, "Found ${simpleMatches.size} simple matches")
            return simpleMatches
        }

        // Step 2: AI fuzzy matching with filtered candidates
        Log.d(TAG, "No simple matches, using AI fuzzy matching")

        val allItems = masterItemRepository.getMasterItems()
        val candidates = getSmartCandidates(query, allItems)

        Log.d(TAG, "Using ${candidates.size} candidates from ${allItems.size} total items")

        return voiceProcessingRepository.findSimilarItems(query, candidates)
            .getOrElse {
                Log.e(TAG, "AI matching failed")
                emptyList()
            }
    }

    private suspend fun performSimpleSearch(query: String): List<MasterItem> {
        val queryLower = query.lowercase().trim()

        // Try exact code match first
        val exactCodeMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase() == queryLower }

        if (exactCodeMatches.isNotEmpty()) {
            Log.d(TAG, "Found exact code matches: ${exactCodeMatches.size}")
            return exactCodeMatches
        }

        // Try substring matches in code
        val codeSubstringMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.code.lowercase().contains(queryLower) }

        if (codeSubstringMatches.isNotEmpty()) {
            Log.d(TAG, "Found code substring matches: ${codeSubstringMatches.size}")
            return codeSubstringMatches
        }

        // Try substring matches in description
        val descriptionMatches = masterItemRepository.searchItems(queryLower, limit = 5)
            .filter { it.description.lowercase().contains(queryLower) }

        if (descriptionMatches.isNotEmpty()) {
            Log.d(TAG, "Found description substring matches: ${descriptionMatches.size}")
            return descriptionMatches.take(3)
        }

        return emptyList()
    }

    private fun getSmartCandidates(query: String, allItems: List<MasterItem>): List<MasterItem> {
        return if (allItems.size > 100) {
            // Filter by first letter for large datasets
            val firstLetter = query.trim().lowercase().firstOrNull()
            if (firstLetter != null) {
                allItems.filter { item ->
                    item.code.lowercase().startsWith(firstLetter.toString()) ||
                            item.description.lowercase().startsWith(firstLetter.toString())
                }.also {
                    Log.d(TAG, "Filtered by first letter '$firstLetter': ${it.size} items")
                }
            } else {
                allItems.shuffled().take(50)
            }
        } else {
            allItems
        }
    }
}