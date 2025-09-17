package com.example.granitevoice.data.remote.model

import com.example.granitevoice.domain.model.OrderProgress
import com.google.gson.annotations.SerializedName

/**
 * DTO for QueryDocumentProgress API response
 */
data class QueryDocumentProgressResponse(
    @SerializedName("TradingPartner")
    val tradingPartner: Map<String, String>? = null,

    @SerializedName("Total")
    val total: Int,

    @SerializedName("ResultsCount")
    val resultsCount: Int,

    @SerializedName("Results")
    val results: List<OrderProgressDto>,

    @SerializedName("_links")
    val links: LinksDto? = null,

    @SerializedName("SQLViewName")
    val sqlViewName: String? = null
)

/**
 * DTO for individual order progress from QueryDocumentProgress API
 */
data class OrderProgressDto(
    @SerializedName("ID")
    val id: Int,

    @SerializedName("Number")
    val number: String,

    @SerializedName("Type")
    val type: String,

    @SerializedName("Site")
    val site: String?,

    @SerializedName("Status")
    val status: String,

    @SerializedName("Progress")
    val progress: Float? = null, // Actual field name from API

    @SerializedName("ActionQty")
    val actionQty: Double? = null,

    @SerializedName("DocumentQty")
    val documentQty: Double? = null, // Actual field name from API (not "Qty")

    @SerializedName("TradingPartnerCode")
    val tradingPartnerCode: String? = null,

    @SerializedName("TradingPartnerDescription")
    val tradingPartnerDescription: String? = null,

    @SerializedName("ActionDate")
    val actionDate: String? = null,

    @SerializedName("Started")
    val started: String? = null,

    @SerializedName("Stopped")
    val stopped: String? = null,

    @SerializedName("Duration")
    val duration: Int? = null,

    @SerializedName("User")
    val user: String? = null
) {
    /**
     * Convert DTO to domain model
     */
    fun toDomainModel(): OrderProgress {
        return OrderProgress(
            number = number,
            type = type,
            site = site,
            status = status,
            percentComplete = progress ?: 0f, // Use "Progress" field
            actionQty = actionQty ?: 0.0,
            qty = documentQty ?: 0.0, // Use "DocumentQty" field
            tradingPartnerCode = tradingPartnerCode,
            tradingPartnerDescription = tradingPartnerDescription,
            createDate = started, // Use "Started" as create date
            description = null // Not in this API
        )
    }
}