package com.example.granitevoice.data.remote

import android.util.Log
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.model.QueryOrdersResponse
import com.example.granitevoice.domain.model.Order
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersGraniteSource @Inject constructor(
    private val graniteClient: GraniteClient,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "OrderSync"
        private const val DEFAULT_LIMIT = 1000 // Get all orders by default
    }

    /**
     * Fetch all orders from the Granite API
     */
    suspend fun fetchOrders(
        site: String? = null,
        filter: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): Result<List<Order>> {
        Log.d(TAG, "Fetching orders from remote...")
        Log.d(TAG, "Parameters - Site: $site, Filter: $filter, Limit: $limit")

        return try {
            val result = graniteClient.getOrders(
                site = site,
                filter = filter,
                limit = limit,
                orderBy = "ID DESC" // Most recent first
            )

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received raw JSON response (${jsonResponse.length} characters)")
                    Log.v(TAG, "Raw JSON: $jsonResponse") // Verbose level for full JSON
                    parseOrdersResponse(jsonResponse)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch orders from API", exception)

                    // Check if this is a "no orders found" case which we can treat as success
                    val isNoOrdersFound = exception.message?.contains("Orders not found") == true ||
                            exception.message?.contains("404") == true

                    if (isNoOrdersFound) {
                        Log.w(TAG, "API returned 404 - treating as empty orders dataset")
                        Log.d(TAG, "This likely means no orders exist in the system yet")
                        return@fold Result.success(emptyList()) // Return empty list as success
                    }

                    // Provide more specific error information for real errors
                    val errorMsg = when {
                        exception.message?.contains("Authentication required") == true -> {
                            "Authentication failed - check username/password"
                        }
                        exception.message?.contains("Connection") == true -> {
                            "Network connection issue - check API endpoint URL"
                        }
                        else -> {
                            exception.message ?: "Unknown error"
                        }
                    }

                    Log.w(TAG, "Detailed error analysis: $errorMsg")
                    Result.failure(RuntimeException(errorMsg, exception))
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching orders", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch orders with specific status filter
     */
    suspend fun fetchOrdersByStatus(
        status: String,
        site: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): Result<List<Order>> {
        Log.d(TAG, "Fetching orders with status: $status")
        Log.d(TAG, "Parameters - Site: $site, Limit: $limit")

        val filter = "Status[eq]='$status'"
        return fetchOrders(site = site, filter = filter, limit = limit)
    }

    /**
     * Fetch active orders (not cancelled or complete)
     */
    suspend fun fetchActiveOrders(
        site: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): Result<List<Order>> {
        Log.d(TAG, "Fetching active orders")
        Log.d(TAG, "Parameters - Site: $site, Limit: $limit")

        // Filter out completed and cancelled orders
        val filter = "Status[neq]='COMPLETE' AND Status[neq]='CANCELLED'"
        return fetchOrders(site = site, filter = filter, limit = limit)
    }

    /**
     * Fetch orders by number pattern (fuzzy search)
     */
    suspend fun fetchOrdersByNumber(
        numberPattern: String,
        site: String? = null,
        limit: Int = 50
    ): Result<List<Order>> {
        Log.d(TAG, "Fetching orders matching number pattern: '$numberPattern'")
        Log.d(TAG, "Parameters - Site: $site, Limit: $limit")

        val filter = "Number[lk]='$numberPattern'"
        return fetchOrders(site = site, filter = filter, limit = limit)
    }

    /**
     * Parse the JSON response from the QueryDocuments API
     */
    private fun parseOrdersResponse(jsonResponse: String): Result<List<Order>> {
        return try {
            Log.d(TAG, "Parsing orders JSON response...")

            val response = gson.fromJson(jsonResponse, QueryOrdersResponse::class.java)

            Log.d(TAG, "Successfully parsed QueryOrdersResponse")
            Log.d(TAG, "API Response Summary:")
            Log.d(TAG, "  - Total: ${response.total}")
            Log.d(TAG, "  - Results Count: ${response.resultsCount}")
            Log.d(TAG, "  - Actual Results: ${response.results.size}")

            if (response.statusList != null) {
                Log.d(TAG, "  - Available Statuses: ${response.statusList}")
            }

            if (response.sites != null) {
                Log.d(TAG, "  - Available Sites: ${response.sites}")
            }

            if (response.erpLocations != null) {
                Log.d(TAG, "  - Available ERP Locations: ${response.erpLocations}")
            }

            if (response.results.isEmpty()) {
                Log.d(TAG, "No orders found in response")
                return Result.success(emptyList())
            }

            Log.d(TAG, "Mapping ${response.results.size} orders from DTOs to domain models...")

            val orders = response.results.mapIndexed { index, orderDto ->
                val order = orderDto.toDomainModel()
                if (index < 3) { // Log first 3 orders for debugging
                    Log.d(
                        TAG,
                        "  Order ${index + 1}: ${order.number} (${order.status}) - ${order.tradingPartnerDescription}"
                    )
                }
                order
            }

            Log.d(TAG, "Successfully mapped ${orders.size} orders to domain models")

            // Log some statistics
            val statusCounts = orders.groupingBy { it.status }.eachCount()
            Log.d(TAG, "Order status distribution: $statusCounts")

            val activeCounts = orders.count { it.isActive() }
            Log.d(TAG, "Active orders: $activeCounts/${orders.size}")

            Result.success(orders)

        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse orders JSON response - Invalid JSON format", e)
            Log.e(TAG, "Problematic JSON snippet: ${jsonResponse.take(500)}...")
            Result.failure(IllegalStateException("Invalid JSON response from orders API", e))

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing orders response", e)
            Log.e(TAG, "Response length: ${jsonResponse.length}")
            Result.failure(e)
        }
    }

    /**
     * Get available statuses from the last API response
     * This could be cached for filtering UI
     */
    suspend fun fetchAvailableStatuses(site: String? = null): Result<List<String>> {
        Log.d(TAG, "Fetching available order statuses")
        Log.d(TAG, "Parameters - Site: $site")

        return try {
            val result = graniteClient.getOrders(site = site, limit = 1) // Just get metadata

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received metadata response for statuses")
                    val response = gson.fromJson(jsonResponse, QueryOrdersResponse::class.java)
                    val statuses = response.statusList ?: emptyList()
                    Log.d(TAG, "Available statuses retrieved: $statuses")
                    Result.success(statuses)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch available statuses", exception)
                    Result.failure(exception)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching statuses", e)
            Result.failure(e)
        }
    }

    /**
     * Get available sites from the last API response
     */
    suspend fun fetchAvailableSites(): Result<List<String>> {
        Log.d(TAG, "Fetching available sites")

        return try {
            val result = graniteClient.getOrders(limit = 1) // Just get metadata

            result.fold(
                onSuccess = { jsonResponse ->
                    Log.d(TAG, "Received metadata response for sites")
                    val response = gson.fromJson(jsonResponse, QueryOrdersResponse::class.java)
                    val sites = response.sites?.filter { it.isNotBlank() } ?: emptyList()
                    Log.d(TAG, "Available sites retrieved: $sites")
                    Result.success(sites)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to fetch available sites", exception)
                    Result.failure(exception)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching sites", e)
            Result.failure(e)
        }
    }
}