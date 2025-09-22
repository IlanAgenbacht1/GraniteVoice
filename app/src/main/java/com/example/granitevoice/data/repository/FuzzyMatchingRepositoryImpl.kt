package com.example.granitevoice.data.repository

import android.util.Log
import com.example.granitevoice.data.repository.helper.OrderFuzzyMatchLogic
import com.example.granitevoice.data.repository.helper.StockFuzzyMatchLogic
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.FuzzyMatchingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FuzzyMatchingRepositoryImpl : FuzzyMatchingRepository {
    private val TAG = "FuzzyMatchingRepositoryImpl"
    private val orderMatcher = OrderFuzzyMatchLogic()
    private val stockMatcher = StockFuzzyMatchLogic()

    override suspend fun findSimilarOrders(
        orderQuery: String,
        customerQuery: String?,
        candidates: List<Order>,
        maxResults: Int
    ): Result<List<Pair<Order, Float>>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting order fuzzy matching ===")
        Log.d(TAG, "Order query: '$orderQuery'")
        Log.d(TAG, "Customer query: '$customerQuery'")
        Log.d(TAG, "Candidates: ${candidates.size} orders")
        val startTime = System.currentTimeMillis()
        try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "\u274c No order candidates provided")
                return@withContext Result.success(emptyList())
            }
            val result =
                orderMatcher.findSimilarOrders(orderQuery, customerQuery, candidates, maxResults)
            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "\u2705 Order fuzzy matching completed in ${totalDuration}ms")
            Log.d(
                TAG,
                "Found ${result.getOrNull()?.size} matches above threshold ${OrderFuzzyMatchLogic.ORDER_SIMILARITY_THRESHOLD}"
            )
            result.getOrNull()?.forEachIndexed { index, (order, similarity) ->
                Log.d(
                    TAG,
                    "  ${index + 1}. ${order.number} (${order.tradingPartnerDescription}) - score: ${
                        String.format(
                            "%.3f",
                            similarity
                        )
                    }"
                )
            }
            Log.d(TAG, "=== Order fuzzy matching completed ===")
            result
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "\u274c Order fuzzy matching failed after ${totalDuration}ms", e)
            Result.failure(e)
        }
    }

    override suspend fun findSimilarItems(
        query: String,
        candidates: List<MasterItem>,
        maxResults: Int
    ): Result<List<MasterItem>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting fast AI-based fuzzy matching ===")
        Log.d(TAG, "Query: '$query'")
        Log.d(TAG, "Candidates: ${candidates.size} items")
        val startTime = System.currentTimeMillis()
        try {
            if (candidates.isEmpty()) {
                Log.w(TAG, "\u274c No candidates provided")
                return@withContext Result.success(emptyList())
            }
            val result = stockMatcher.findSimilarItems(query, candidates, maxResults)
            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "\u2705 Fast AI fuzzy matching completed in ${totalDuration}ms")
            Log.d(
                TAG,
                "Found ${result.getOrNull()?.size} matches above threshold ${StockFuzzyMatchLogic.ITEM_SIMILARITY_THRESHOLD}"
            )
            result.getOrNull()?.forEachIndexed { index, item ->
                Log.d(
                    TAG,
                    "  ${index + 1}. ${item.code}: ${item.description}"
                )
            }
            Log.d(TAG, "=== Fast AI fuzzy matching completed ===")
            result
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "\u274c Fast AI fuzzy matching failed after ${totalDuration}ms", e)
            Result.failure(e)
        }
    }
}
