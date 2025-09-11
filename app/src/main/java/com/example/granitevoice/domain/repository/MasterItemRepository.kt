package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.MasterItem

interface MasterItemRepository {
    suspend fun getMasterItems(): List<MasterItem>
    suspend fun syncMasterItems()
    suspend fun searchItems(searchTerm: String, limit: Int = 10): List<MasterItem>
    suspend fun getItemByCode(code: String): MasterItem?
}