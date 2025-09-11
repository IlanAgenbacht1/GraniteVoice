package com.example.granitevoice.data.remote.model

import com.google.gson.annotations.SerializedName

data class QueryMasterItemsResponse(
    @SerializedName("Results")
    val Results: List<MasterItemDto>
)