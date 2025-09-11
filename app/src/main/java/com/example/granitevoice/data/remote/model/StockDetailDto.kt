package com.example.granitevoice.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * DTO for StockDetail API response
 */
data class QueryStockDetailResponse(
    @SerializedName("Total")
    val total: Int,

    @SerializedName("ResultsCount")
    val resultsCount: Int,

    @SerializedName("Results")
    val results: List<StockDetailDto>,

    @SerializedName("SQLViewName")
    val sqlViewName: String?
)

/**
 * DTO for individual StockDetail item from the API
 * Updated to match actual API response structure
 */
data class StockDetailDto(
    @SerializedName("Code")
    val code: String,

    @SerializedName("Barcode")
    val barcode: String?,

    @SerializedName("Site")
    val site: String?,

    // Actual fields from the API response
    @SerializedName("Qty")
    val qty: Double? = null,

    @SerializedName("Status")
    val status: String? = null,

    @SerializedName("Location")
    val location: String? = null,

    @SerializedName("Description")
    val description: String? = null,

    @SerializedName("MasterItem_id")
    val masterItemId: Int? = null,

    @SerializedName("SerialNumber")
    val serialNumber: String? = null,

    @SerializedName("OnHold")
    val onHold: Boolean? = null,

    @SerializedName("StockTake")
    val stockTake: Boolean? = null,

    @SerializedName("ItemType")
    val itemType: String? = null,

    @SerializedName("LocationCategory")
    val locationCategory: String? = null,

    @SerializedName("LocationType")
    val locationType: String? = null,

    @SerializedName("Batch")
    val batch: String? = null,

    @SerializedName("CreatedDate")
    val createdDate: String? = null,

    @SerializedName("Time")
    val time: String? = null,

    @SerializedName("JulienDate")
    val julienDate: String? = null
) {
    /**
     * Check if this item is actually in stock based on status and quantity
     */
    fun isInStock(): Boolean {
        return (status?.contains("InStock", ignoreCase = true) == true) &&
                (qty ?: 0.0) > 0.0 &&
                (onHold != true)
    }

    /**
     * Get the available quantity (considering hold status)
     */
    fun getAvailableQuantity(): Double? {
        return if (isInStock()) qty else 0.0
    }
}