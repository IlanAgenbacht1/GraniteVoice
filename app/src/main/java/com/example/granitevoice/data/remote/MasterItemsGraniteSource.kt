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
    fun getMasterItems(): List<MasterItem> {
        Log.d("MasterItemSync", "Fetching master items from remote...")
        // Step 1: Get the raw JSON string.
        val jsonResponse = graniteClient.getMasterItems()
        Log.d("MasterItemSync", "Received raw JSON: $jsonResponse")

        // Step 2: Gson automatically parses the string into our DTO classes.
        // This is where the automatic mapping from JSON -> DTO happens.
        val response = gson.fromJson(jsonResponse, QueryMasterItemsResponse::class.java)
        Log.d("MasterItemSync", "Successfully parsed ${response.Results.size} items from JSON.")

        // Step 3: We manually map from the DTO to our internal domain model.
        // This is where we pick only the fields we need.
        return response.Results.map { dto ->
            MasterItem(
                // We let Room handle the 'id', so we only take what we need.
                code = dto.code,
                description = dto.description
            )
        }
    }
}