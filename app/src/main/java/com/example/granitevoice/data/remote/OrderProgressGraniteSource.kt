package com.example.granitevoice.data.remote

import android.util.Log
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.model.QueryDocumentProgressResponse
import com.example.granitevoice.domain.model.OrderProgress
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderProgressGraniteSource @Inject constructor(
    private val graniteClient: GraniteClient,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "OrderProgressSync"
    }

    /**
     * Fetch progress information for a specific order
     */
    suspend fun fetchOrderProgress(
        orderNumber: String,
        site: String? = null
    ): Result<OrderProgress?> {
        Log.d(TAG, "Fetching progress for order: '$orderNumber'")
        Log.d(TAG, "Parameters - Site: $site")

        return try {
            val result = graniteClient.getOrderProgress(orderNumber, site)

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(
                        TAG,
                        "Received order progress response (${jsonResponse.length} characters)"
                    )
                    Log.v(TAG, "Raw JSON: $jsonResponse")
                    parseOrderProgressResponse(jsonResponse, orderNumber)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch order progress from API", exception)

                    // Handle 404 as "no progress info available" rather than error
                    if (exception.message?.contains("Order progress not found") == true) {
                        Log.w(TAG, "No progress information available for order: $orderNumber")
                        Result.success(null)
                    } else {
                        Result.failure(exception)
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching order progress", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch progress information for multiple orders
     */
    suspend fun fetchOrdersProgress(
        site: String? = null,
        filter: String? = null,
        limit: Int = 100
    ): Result<List<OrderProgress>> {
        Log.d(TAG, "Fetching progress for multiple orders")
        Log.d(TAG, "Parameters - Site: $site, Filter: $filter, Limit: $limit")

        return try {
            val result = graniteClient.getDocumentProgress(
                documentType = "ORDER",
                site = site,
                filter = filter,
                limit = limit,
                orderBy = "Number DESC"
            )

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(
                        TAG,
                        "Received orders progress response (${jsonResponse.length} characters)"
                    )
                    parseOrdersProgressResponse(jsonResponse)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch orders progress from API", exception)
                    Result.failure(exception)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching orders progress", e)
            Result.failure(e)
        }
    }

    /**
     * Parse single order progress response
     */
    private fun parseOrderProgressResponse(
        jsonResponse: String,
        orderNumber: String
    ): Result<OrderProgress?> {
        return try {
            Log.d(TAG, "Parsing order progress JSON response...")

            val response = gson.fromJson(jsonResponse, QueryDocumentProgressResponse::class.java)

            Log.d(TAG, "Successfully parsed QueryDocumentProgressResponse")
            Log.d(TAG, "Response Summary:")
            Log.d(TAG, "  - Total: ${response.total}")
            Log.d(TAG, "  - Results Count: ${response.resultsCount}")
            Log.d(TAG, "  - Actual Results: ${response.results.size}")

            if (response.results.isEmpty()) {
                Log.d(TAG, "No progress information found for order: $orderNumber")
                return Result.success(null)
            }

            // Find the matching order (should be only one with the filter)
            val matchingProgress =
                response.results.find { it.number.equals(orderNumber, ignoreCase = true) }

            if (matchingProgress == null) {
                Log.w(TAG, "Order progress response didn't contain expected order: $orderNumber")
                return Result.success(null)
            }

            val orderProgress = matchingProgress.toDomainModel()

            Log.d(TAG, "Successfully mapped order progress:")
            Log.d(TAG, "  - Number: ${orderProgress.number}")
            Log.d(TAG, "  - Status: ${orderProgress.status}")
            Log.d(TAG, "  - Raw Progress Value: ${matchingProgress.progress}")
            Log.d(TAG, "  - Mapped Progress: ${orderProgress.percentComplete}")
            Log.d(TAG, "  - Progress Display: ${orderProgress.getProgressDisplay()}")
            Log.d(TAG, "  - Progress Percent: ${orderProgress.getProgressPercent()}")
            Log.d(TAG, "  - Quantity: ${orderProgress.getQuantityDisplay()}")
            Log.d(TAG, "  - Raw ActionQty: ${matchingProgress.actionQty}")
            Log.d(TAG, "  - Raw DocumentQty: ${matchingProgress.documentQty}")

            Result.success(orderProgress)

        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse order progress JSON response - Invalid JSON format", e)
            Log.e(TAG, "Problematic JSON snippet: ${jsonResponse.take(500)}...")
            Result.failure(
                IllegalStateException(
                    "Invalid JSON response from order progress API",
                    e
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing order progress response", e)
            Log.e(TAG, "Response length: ${jsonResponse.length}")
            Result.failure(e)
        }
    }

    /**
     * Parse multiple orders progress response
     */
    private fun parseOrdersProgressResponse(jsonResponse: String): Result<List<OrderProgress>> {
        return try {
            Log.d(TAG, "Parsing orders progress JSON response...")

            val response = gson.fromJson(jsonResponse, QueryDocumentProgressResponse::class.java)

            Log.d(TAG, "Successfully parsed QueryDocumentProgressResponse")
            Log.d(TAG, "Response Summary:")
            Log.d(TAG, "  - Total: ${response.total}")
            Log.d(TAG, "  - Results Count: ${response.resultsCount}")
            Log.d(TAG, "  - Actual Results: ${response.results.size}")

            if (response.results.isEmpty()) {
                Log.d(TAG, "No progress information found")
                return Result.success(emptyList())
            }

            val ordersProgress = response.results.map { dto ->
                dto.toDomainModel()
            }

            Log.d(TAG, "Successfully mapped ${ordersProgress.size} orders progress")

            // Log some statistics
            val statusCounts = ordersProgress.groupingBy { it.status }.eachCount()
            Log.d(TAG, "Progress status distribution: $statusCounts")

            val avgProgress = ordersProgress.map { it.percentComplete }.average()
            Log.d(TAG, "Average completion: ${String.format("%.1f", avgProgress)}%")

            Result.success(ordersProgress)

        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse orders progress JSON response - Invalid JSON format", e)
            Log.e(TAG, "Problematic JSON snippet: ${jsonResponse.take(500)}...")
            Result.failure(
                IllegalStateException(
                    "Invalid JSON response from orders progress API",
                    e
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing orders progress response", e)
            Log.e(TAG, "Response length: ${jsonResponse.length}")
            Result.failure(e)
        }
    }
}