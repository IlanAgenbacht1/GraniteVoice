package com.example.granitevoice.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * DTO for QueryDocuments API response when querying ORDER type
 * Represents the complete API response structure
 */
data class QueryOrdersResponse(
    @SerializedName("Sites")
    val sites: List<String>? = null,

    @SerializedName("ERPLocations")
    val erpLocations: List<String>? = null,

    @SerializedName("Status")
    val statusList: List<String>? = null,

    @SerializedName("TradingPartners")
    val tradingPartners: List<TradingPartnerDto>? = null,

    @SerializedName("canDelete")
    val canDelete: Boolean = false,

    @SerializedName("Total")
    val total: Int,

    @SerializedName("ResultsCount")
    val resultsCount: Int,

    @SerializedName("Results")
    val results: List<OrderDto>,

    @SerializedName("_links")
    val links: LinksDto? = null,

    @SerializedName("SQLViewName")
    val sqlViewName: String? = null
)

/**
 * DTO for trading partner information
 */
data class TradingPartnerDto(
    @SerializedName("Key")
    val key: String,

    @SerializedName("Value")
    val value: String
)

/**
 * DTO for API links
 */
data class LinksDto(
    @SerializedName("Export")
    val export: LinkDto? = null,

    @SerializedName("self")
    val self: LinkDto? = null,

    @SerializedName("Copy")
    val copy: LinkDto? = null,

    @SerializedName("Save")
    val save: LinkDto? = null,

    @SerializedName("Get")
    val get: LinkDto? = null,

    @SerializedName("UpdateDocumentStatus")
    val updateDocumentStatus: LinkDto? = null
)

/**
 * DTO for individual link
 */
data class LinkDto(
    @SerializedName("href")
    val href: String,

    @SerializedName("verb")
    val verb: String,

    @SerializedName("templated")
    val templated: Boolean? = null
)