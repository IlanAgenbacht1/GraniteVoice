package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.MasterItem

interface VoiceProcessingRepository {
    suspend fun extractItemQuery(transcription: String): Result<String>
    suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>
    ): Result<List<MasterItem>>
}