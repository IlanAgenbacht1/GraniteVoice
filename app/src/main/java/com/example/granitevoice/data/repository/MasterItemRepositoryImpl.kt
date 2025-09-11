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
            val masterItems = graniteDataSource.getMasterItems()
            Log.d("MasterItemSync", "Storing ${masterItems.size} master items in the database...")
            dao.insertAll(masterItems)
            Log.d("MasterItemSync", "Finished storing master items.")
        }
    }
}