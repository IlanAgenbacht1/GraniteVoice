package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.MasterItem

interface MasterItemRepository {
    suspend fun getMasterItems(): List<MasterItem>
    suspend fun syncMasterItems()
}