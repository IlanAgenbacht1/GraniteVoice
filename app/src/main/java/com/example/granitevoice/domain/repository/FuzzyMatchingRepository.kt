package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.MasterItem

interface FuzzyMatchingRepository {
    suspend fun findSimilarOrders(
        orderQuery: String,
        customerQuery: String?,
        candidates: List<Order>,
        maxResults: Int = 5
    ): Result<List<Pair<Order, Float>>>

    suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>,
        maxResults: Int = 5
    ): Result<List<MasterItem>>
}
