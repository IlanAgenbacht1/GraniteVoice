package com.example.granitevoice.data.remote

import android.util.Log
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.model.QueryMasterItemsResponse
import com.google.gson.Gson
import javax.inject.Inject

class MasterItemsGraniteSource @Inject constructor(
    private val graniteClient: GraniteClient,
    private val gson: Gson
) {
    suspend fun getMasterItems(): Result<List<MasterItem>> {
        Log.d("MasterItemSync", "Fetching master items from remote...")

        return try {
            val result = graniteClient.getMasterItems()

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d("MasterItemSync", "Received raw JSON: $jsonResponse")

                    // Parse the JSON response
                    val response = gson.fromJson(jsonResponse, QueryMasterItemsResponse::class.java)
                    Log.d(
                        "MasterItemSync",
                        "Successfully parsed ${response.Results.size} items from JSON."
                    )

                    // Map from DTO to domain model with code as primary key
                    val masterItems = response.Results.map { dto ->
                        MasterItem(
                            code = dto.code,
                            description = dto.description,
                            remoteId = dto.id, // Keep remote ID for reference
                            unitOfMeasure = dto.unitOfMeasure ?: dto.primaryUOM
                            ?: dto.stockUOM, // Use first available UOM
                            category = dto.category,
                            itemType = dto.itemType
                        )
                    }

                    Log.d("MasterItemSync", "Mapped ${masterItems.size} items to domain models.")
                    Result.success(masterItems)
                },
                onFailure = { exception ->
                    Log.e("MasterItemSync", "Failed to fetch master items", exception)
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e("MasterItemSync", "Exception during master items fetch", e)
            Result.failure(e)
        }
    }
}