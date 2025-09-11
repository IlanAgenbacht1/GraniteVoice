package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.data.remote.MasterItemsGraniteSource
import com.example.granitevoice.domain.repository.MasterItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MasterItemRepositoryImpl @Inject constructor(
    private val dao: MasterItemDao,
    private val graniteDataSource: MasterItemsGraniteSource
) : MasterItemRepository {

    override suspend fun getMasterItems(): List<MasterItem> {
        return dao.getAll()
    }

    override suspend fun syncMasterItems() {
        withContext(Dispatchers.IO) {
            Log.d("MasterItemSync", "Starting master item sync...")

            graniteDataSource.getMasterItems().fold(
                onSuccess = { masterItems ->
                    Log.d(
                        "MasterItemSync",
                        "Storing ${masterItems.size} master items in the database..."
                    )
                    // Use atomic replace operation to prevent duplicates
                    dao.syncReplace(masterItems)
                    Log.d(
                        "MasterItemSync",
                        "Finished storing master items. Total items: ${dao.getCount()}"
                    )
                },
                onFailure = { exception ->
                    Log.e("MasterItemSync", "Failed to sync master items", exception)
                    throw exception
                }
            )
        }
    }

    // New methods for voice command processing

    suspend fun searchItems(searchTerm: String, limit: Int = 10): List<MasterItem> {
        return dao.fuzzySearch(searchTerm, limit)
    }

    suspend fun getItemByCode(code: String): MasterItem? {
        return dao.getByCode(code)
    }

    suspend fun getAllCodes(): List<String> {
        return dao.getAllCodes()
    }

    suspend fun getAllDescriptions(): List<String> {
        return dao.getAllDescriptions()
    }

    suspend fun getItemCount(): Int {
        return dao.getCount()
    }
}