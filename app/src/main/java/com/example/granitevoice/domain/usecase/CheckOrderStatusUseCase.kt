package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.OrderRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

/**
 * Use case responsible for checking order status
 */
class CheckOrderStatusUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val voiceProcessingRepository: VoiceProcessingRepository
) {

    companion object {
        private const val TAG = "CheckOrderStatusUseCase"
    }

    suspend operator fun invoke(command: CommandIntent.CheckOrderStatus): VoiceCommandResult {
        Log.d(TAG, "=== Starting order status check ===")
        Log.d(TAG, "Order ID: '${command.orderId}'")

        val processingSteps = mutableListOf<ProcessingStep>()

        try {
            // Step 1: Clean the order ID using the generic voice processing repository
            val cleanOrderIdStartTime = System.currentTimeMillis()
            val cleanedOrderId = voiceProcessingRepository.cleanTranscription(command.orderId)
                .getOrDefault(command.orderId)
            val cleanOrderIdDuration = System.currentTimeMillis() - cleanOrderIdStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Order ID Cleaning",
                    input = command.orderId,
                    output = cleanedOrderId
                )
            )

            Log.d(
                TAG,
                "Order ID cleaning completed in ${cleanOrderIdDuration}ms: '${command.orderId}' -> '$cleanedOrderId'"
            )

            // Step 2: Look up the order
            val lookupStartTime = System.currentTimeMillis()

            val orderResult = orderRepository.getOrderById(cleanedOrderId)

            val lookupDuration = System.currentTimeMillis() - lookupStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Order Lookup",
                    input = cleanedOrderId,
                    output = if (orderResult.isSuccess) {
                        val order = orderResult.getOrNull()
                        if (order != null) "Found order: ${order.status.name}" else "Order not found"
                    } else {
                        "Lookup failed: ${orderResult.exceptionOrNull()?.message}"
                    },
                    confidence = if (orderResult.isSuccess && orderResult.getOrNull() != null) 1.0f else 0.0f
                )
            )

            Log.d(TAG, "Order lookup completed in ${lookupDuration}ms")

            return orderResult.fold(
                onSuccess = { order ->
                    if (order == null) {
                        Log.w(TAG, "Order '$cleanedOrderId' not found")
                        VoiceCommandResult(
                            originalTranscription = "Check status of order ${command.orderId}",
                            intent = command,
                            extractedQuery = cleanedOrderId,
                            matchedItem = null,
                            confidence = 0.0f,
                            alternativeMatches = emptyList(),
                            processingSteps = processingSteps,
                            stockSummaries = emptyMap(),
                            stockLookupErrors = mapOf("order_not_found" to "Order '$cleanedOrderId' not found.")
                        )
                    } else {
                        Log.d(TAG, "Order found: ${order.id}, Status: ${order.status}")

                        // Step 3: Format status information
                        processingSteps.add(
                            ProcessingStep(
                                step = "Status Formatting",
                                input = "Order: ${order.id}, Status: ${order.status}",
                                output = "Status: ${order.getStatusDisplay()}, Items: ${order.getTotalItems()}"
                            )
                        )

                        VoiceCommandResult(
                            originalTranscription = "Check status of order ${command.orderId}",
                            intent = command,
                            extractedQuery = cleanedOrderId,
                            matchedItem = null, // Order status doesn't have a specific matched item
                            confidence = 1.0f,
                            alternativeMatches = emptyList(),
                            processingSteps = processingSteps,
                            stockSummaries = emptyMap(),
                            stockLookupErrors = emptyMap()
                        )
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Order lookup failed for ID: $cleanedOrderId", exception)
                    VoiceCommandResult(
                        originalTranscription = "Check status of order ${command.orderId}",
                        intent = command,
                        extractedQuery = cleanedOrderId,
                        matchedItem = null,
                        confidence = 0.0f,
                        alternativeMatches = emptyList(),
                        processingSteps = processingSteps,
                        stockSummaries = emptyMap(),
                        stockLookupErrors = mapOf(
                            "order_lookup" to (exception.message ?: "Order lookup failed")
                        )
                    )
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during order status check", e)

            processingSteps.add(
                ProcessingStep(
                    step = "Error Handling",
                    input = "Exception occurred",
                    output = e.message ?: "Unexpected error"
                )
            )

            return VoiceCommandResult(
                originalTranscription = "Check status of order ${command.orderId}",
                intent = command,
                extractedQuery = command.orderId,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("unexpected_error" to (e.message ?: "Unexpected error"))
            )
        } finally {
            Log.d(TAG, "=== Order status check completed ===")
        }
    }
}