package com.example.granitevoice.data.remote.model

import com.example.granitevoice.domain.model.Order
import com.google.gson.annotations.SerializedName

/**
 * DTO for individual order from QueryDocuments API
 */
data class OrderDto(
    @SerializedName("ID")
    val id: Int,

    @SerializedName("Number")
    val number: String,

    @SerializedName("TradingPartnerCode")
    val tradingPartnerCode: String,

    @SerializedName("TradingPartnerDescription")
    val tradingPartnerDescription: String,

    @SerializedName("CreateDate")
    val createDate: String,

    @SerializedName("Site")
    val site: String?,

    @SerializedName("Status")
    val status: String,

    @SerializedName("AuditDate")
    val auditDate: String,

    @SerializedName("AuditUser")
    val auditUser: String,

    @SerializedName("Type")
    val type: String,

    @SerializedName("Description")
    val description: String? = null,

    @SerializedName("Priority")
    val priority: Int? = null,

    @SerializedName("ERPLocation")
    val erpLocation: String? = null
) {
    /**
     * Convert DTO to domain model
     */
    fun toDomainModel(): Order {
        return Order(
            id = id,
            number = number,
            tradingPartnerCode = tradingPartnerCode,
            tradingPartnerDescription = tradingPartnerDescription,
            createDate = createDate,
            site = site,
            status = status,
            auditDate = auditDate,
            auditUser = auditUser,
            type = type,
            description = description,
            priority = priority,
            erpLocation = erpLocation,
            lastSyncAt = System.currentTimeMillis()
        )
    }
}