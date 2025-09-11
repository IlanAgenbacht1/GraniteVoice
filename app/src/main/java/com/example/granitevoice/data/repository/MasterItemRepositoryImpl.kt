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

    companion object {
        private const val TAG = "MasterItemRepository"
    }

    override suspend fun getMasterItems(): List<MasterItem> = withContext(Dispatchers.IO) {
        try {
            dao.getAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get master items from database", e)
            throw e
        }
    }

    override suspend fun syncMasterItems(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting master item sync...")

            graniteDataSource.getMasterItems().fold(
                onSuccess = { masterItems ->
                    Log.d(TAG, "Storing ${masterItems.size} master items in the database...")

                    // Use atomic replace operation to prevent duplicates
                    dao.syncReplace(masterItems)

                    val finalCount = dao.getCount()
                    Log.d(TAG, "Finished storing master items. Total items: $finalCount")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch master items from remote source", exception)
                    throw exception
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Master item sync failed", e)
            throw e
        }
    }

    override suspend fun searchItems(searchTerm: String, limit: Int): List<MasterItem> =
        withContext(Dispatchers.IO) {
            try {
                dao.fuzzySearch(searchTerm, limit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search items with term '$searchTerm'", e)
                // Return empty list instead of throwing - search failure shouldn't break the app
                emptyList()
            }
    }

    override suspend fun getItemByCode(code: String): MasterItem? = withContext(Dispatchers.IO) {
        try {
            dao.getByCode(code)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get item by code '$code'", e)
            // Return null instead of throwing - missing item is a valid scenario
            null
        }
    }
}