package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.MasterItem

/**
 * Repository interface for generic voice/natural language processing tasks.
 *
 * This repository provides reusable language processing tools that are NOT tied
 * to any specific business domain (like stock lookups or orders). It focuses on:
 * - Cleaning and normalizing voice transcriptions
 * - Generic fuzzy matching capabilities
 * - Language processing utilities
 *
 * Domain-specific logic (like extracting item names or order details) belongs
 * in the respective use cases, NOT in this repository.
 */
interface VoiceProcessingRepository {
    /**
     * Cleans a voice transcription by removing common filler words, command phrases,
     * and correcting speech recognition errors. This makes the text easier to process
     * for specific entity extraction in use cases.
     *
     * This is a generic text cleaning operation that can be used by any voice command type.
     */
    suspend fun cleanTranscription(transcription: String): Result<String>

    /**
     * Find similar items using fuzzy matching techniques.
     * This is a generic language processing task that compares text similarity
     * against a list of candidates.
     */
    suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>
    ): Result<List<MasterItem>>
}