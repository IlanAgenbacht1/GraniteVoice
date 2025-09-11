package com.example.granitevoice.data.remote

import android.util.Log
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.model.QueryStockDetailResponse
import com.example.granitevoice.domain.model.StockDetail
import com.example.granitevoice.domain.model.ItemStockSummary
import com.example.granitevoice.domain.model.MasterItem
import com.google.gson.Gson
import javax.inject.Inject

class StockDetailGraniteSource @Inject constructor(
    private val graniteClient: GraniteClient,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "StockDetailSource"
    }

    suspend fun getStockDetailByMasterItemId(masterItemId: Int): Result<List<StockDetail>> {
        Log.d(TAG, "Fetching stock detail for MasterItemID: $masterItemId")

        return try {
            val result = graniteClient.getStockDetailByMasterItemId(masterItemId)

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received stock detail JSON: $jsonResponse")
                    parseStockDetailResponse(jsonResponse)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch stock detail by master item ID", exception)
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stock detail fetch by master item ID", e)
            Result.failure(e)
        }
    }

    suspend fun getStockDetailByCode(code: String): Result<List<StockDetail>> {
        Log.d(TAG, "Fetching stock detail for Code: $code")

        return try {
            val result = graniteClient.getStockDetailByCode(code)

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received stock detail JSON: $jsonResponse")
                    parseStockDetailResponse(jsonResponse)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch stock detail by code", exception)
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stock detail fetch by code", e)
            Result.failure(e)
        }
    }

    suspend fun getStockDetail(
        site: String? = null,
        location: String? = null
    ): Result<List<StockDetail>> {
        Log.d(TAG, "Fetching stock detail - Site: $site, Location: $location")

        return try {
            val result = graniteClient.getStockDetail(site, location)

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received stock detail JSON: $jsonResponse")
                    parseStockDetailResponse(jsonResponse)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch stock detail", exception)
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stock detail fetch", e)
            Result.failure(e)
        }
    }

    suspend fun getItemStockSummary(masterItem: MasterItem): Result<ItemStockSummary> {
        Log.d(TAG, "Getting stock summary for item: ${masterItem.code}")

        return try {
            // Try by remote ID first, then by code as fallback
            val stockDetailResult = if (masterItem.remoteId > 0) {
                getStockDetailByMasterItemId(masterItem.remoteId)
            } else {
                getStockDetailByCode(masterItem.code)
            }

            stockDetailResult.fold(
                onSuccess = { stockDetails ->
                    val summary = createStockSummary(masterItem, stockDetails)
                    Log.d(
                        TAG,
                        "Created stock summary: ${summary.getTotalQuantityDisplay()} in ${summary.getLocationSummary()}"
                    )
                    Result.success(summary)
                },
                onFailure = { exception ->
                    // If remote ID fails, try code as fallback
                    if (masterItem.remoteId > 0) {
                        Log.d(TAG, "Master item ID lookup failed, trying code fallback")
                        val fallbackResult = getStockDetailByCode(masterItem.code)

                        fallbackResult.fold(
                            onSuccess = { stockDetails ->
                                val summary = createStockSummary(masterItem, stockDetails)
                                Result.success(summary)
                            },
                            onFailure = {
                                Log.e(TAG, "Both master item ID and code lookups failed", exception)
                                Result.failure(exception)
                            }
                        )
                    } else {
                        Result.failure(exception)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stock summary creation", e)
            Result.failure(e)
        }
    }

    private fun parseStockDetailResponse(jsonResponse: String): Result<List<StockDetail>> {
        return try {
            val response = gson.fromJson(jsonResponse, QueryStockDetailResponse::class.java)
            Log.d(TAG, "Successfully parsed ${response.results.size} stock detail items from JSON.")

            val stockDetails = response.results.map { dto ->
                StockDetail(
                    code = dto.code,
                    barcode = dto.barcode,
                    site = dto.site,
                    quantity = dto.qty, // Use the actual API field
                    availableQuantity = dto.getAvailableQuantity(), // Use calculated available quantity
                    location = dto.location,
                    description = dto.description,
                    unitOfMeasure = null, // Not provided by this API
                    lastUpdated = if (dto.createdDate != null && dto.time != null) {
                        "${dto.createdDate} ${dto.time}"
                    } else null
                )
            }

            Result.success(stockDetails)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stock detail response", e)
            Result.failure(e)
        }
    }

    private fun createStockSummary(
        masterItem: MasterItem,
        stockDetails: List<StockDetail>
    ): ItemStockSummary {
        Log.d(TAG, "Creating stock summary from ${stockDetails.size} raw records")

        // Group by location and consolidate quantities
        val consolidatedDetails = stockDetails
            .groupBy { detail ->
                // Create a unique key for location + site combination
                val locationKey = when {
                    !detail.location.isNullOrBlank() && !detail.site.isNullOrBlank() ->
                        "${detail.location} (${detail.site})"

                    !detail.location.isNullOrBlank() -> detail.location
                    !detail.site.isNullOrBlank() -> detail.site
                    else -> "Unknown Location"
                }
                locationKey
            }
            .map { (locationKey, records) ->
                Log.d(TAG, "Consolidating ${records.size} records for location: $locationKey")

                // Sum quantities for this location
                val totalQuantity = records.mapNotNull { it.quantity }.sum()
                val totalAvailable = records.mapNotNull { it.availableQuantity }.sum()

                // Use the first record as template and update quantities
                val firstRecord = records.first()
                StockDetail(
                    code = firstRecord.code,
                    barcode = firstRecord.barcode,
                    site = firstRecord.site,
                    quantity = totalQuantity,
                    availableQuantity = totalAvailable,
                    location = firstRecord.location,
                    description = firstRecord.description,
                    unitOfMeasure = firstRecord.unitOfMeasure,
                    lastUpdated = records.mapNotNull { it.lastUpdated }
                        .maxOrNull() // Use most recent date
                )
            }
            .sortedBy { it.getLocationDisplay() } // Sort for consistent display order

        val totalQuantity = consolidatedDetails.mapNotNull { it.quantity }.sum()
        val totalAvailable = consolidatedDetails.mapNotNull { it.availableQuantity }.sum()
        val locationCount = consolidatedDetails.size

        Log.d(
            TAG,
            "Consolidated ${stockDetails.size} raw records into ${consolidatedDetails.size} location entries"
        )
        Log.d(
            TAG,
            "Total quantity: $totalQuantity, Total available: $totalAvailable, Locations: $locationCount"
        )

        return ItemStockSummary(
            masterItem = masterItem,
            stockDetails = consolidatedDetails,
            totalQuantity = totalQuantity,
            totalAvailable = totalAvailable,
            locationCount = locationCount,
            unitOfMeasure = masterItem.unitOfMeasure ?: "EA" // Use UOM from master item
        )
    }
}