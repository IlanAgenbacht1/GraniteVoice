package com.example.granitevoice.domain.repository

import kotlinx.coroutines.flow.Flow

enum class SyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED
}

data class SyncProgress(
    val status: SyncStatus,
    val progress: Float = 0f,
    val message: String = "",
    val error: String? = null
)

interface SyncRepository {
    suspend fun startSync(): Result<Unit>
    fun observeSyncProgress(): Flow<SyncProgress>
    suspend fun cancelSync(): Result<Unit>
}