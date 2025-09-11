package com.example.granitevoice.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * DTO for a single MasterItem from the API.
 * This class perfectly matches the JSON structure inside the "Results" array.
 */
data class MasterItemDto(
    @SerializedName("ID")
    val id: Int,

    @SerializedName("Code")
    val code: String,

    @SerializedName("Description")
    val description: String,

    @SerializedName("UOM")
    val unitOfMeasure: String? = null,

    @SerializedName("PrimaryUOM")
    val primaryUOM: String? = null,

    @SerializedName("StockUOM")
    val stockUOM: String? = null,

    @SerializedName("Category")
    val category: String? = null,

    @SerializedName("ItemType")
    val itemType: String? = null
)