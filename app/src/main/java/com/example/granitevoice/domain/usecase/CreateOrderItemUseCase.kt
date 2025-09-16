package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.example.granitevoice.domain.repository.OrderRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import javax.inject.Inject

/**
 * Use case responsible for creating order items
 */
class CreateOrderItemUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val masterItemRepository: MasterItemRepository,
    private val voiceProcessingRepository: VoiceProcessingRepository
) {

    companion object {
        private const val TAG = "CreateOrderUseCase"
    }

    suspend operator fun invoke(command: CommandIntent.CreateOrderItem): VoiceCommandResult {
        Log.d(TAG, "=== Starting order creation ===")
        Log.d(TAG, "Item name: '${command.itemName}', Quantity: ${command.quantity}")

        val processingSteps = mutableListOf<ProcessingStep>()

        try {
            // Step 1: Clean the item name using the generic voice processing repository
            val cleanItemNameStartTime = System.currentTimeMillis()
            val cleanedItemName = voiceProcessingRepository.cleanTranscription(command.itemName)
                .getOrDefault(command.itemName)
            val cleanItemNameDuration = System.currentTimeMillis() - cleanItemNameStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Item Name Cleaning",
                    input = command.itemName,
                    output = cleanedItemName
                )
            )

            Log.d(
                TAG,
                "Item name cleaning completed in ${cleanItemNameDuration}ms: '${command.itemName}' -> '$cleanedItemName'"
            )

            // Step 2: Find the item to ensure it exists
            val findItemStartTime = System.currentTimeMillis()

            val item = masterItemRepository.getItemByCode(cleanedItemName)
                ?: masterItemRepository.searchItems(cleanedItemName, limit = 1).firstOrNull()

            val findItemDuration = System.currentTimeMillis() - findItemStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Item Lookup",
                    input = cleanedItemName,
                    output = if (item != null) "Found: ${item.description}" else "Item not found",
                    confidence = if (item != null) 1.0f else 0.0f
                )
            )

            Log.d(TAG, "Item lookup completed in ${findItemDuration}ms")

            if (item == null) {
                Log.w(TAG, "Item '$cleanedItemName' not found")
                return VoiceCommandResult(
                    originalTranscription = "Create order for ${command.itemName} quantity ${command.quantity}",
                    intent = command,
                    extractedQuery = cleanedItemName,
                    matchedItem = null,
                    confidence = 0.0f,
                    alternativeMatches = emptyList(),
                    processingSteps = processingSteps,
                    stockSummaries = emptyMap(),
                    stockLookupErrors = mapOf("item_not_found" to "Item '$cleanedItemName' not found.")
                )
            }

            // Step 3: Execute the business logic to create an order
            val createOrderStartTime = System.currentTimeMillis()

            val orderResult = orderRepository.createOrder(item, command.quantity)

            val createOrderDuration = System.currentTimeMillis() - createOrderStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Order Creation",
                    input = "Item: ${item.code}, Quantity: ${command.quantity}",
                    output = if (orderResult.isSuccess)
                        "Order created: ${orderResult.getOrNull()?.id}"
                    else
                        "Order creation failed: ${orderResult.exceptionOrNull()?.message}"
                )
            )

            Log.d(TAG, "Order creation completed in ${createOrderDuration}ms")

            return orderResult.fold(
                onSuccess = { order ->
                    Log.d(TAG, "Order created successfully: ${order.id}")
                    VoiceCommandResult(
                        originalTranscription = "Create order for ${command.itemName} quantity ${command.quantity}",
                        intent = command,
                        extractedQuery = cleanedItemName,
                        matchedItem = item,
                        confidence = 1.0f,
                        alternativeMatches = emptyList(),
                        processingSteps = processingSteps,
                        stockSummaries = emptyMap(),
                        stockLookupErrors = emptyMap()
                    )
                },
                onFailure = { exception ->
                    Log.e(TAG, "Order creation failed", exception)
                    VoiceCommandResult(
                        originalTranscription = "Create order for ${command.itemName} quantity ${command.quantity}",
                        intent = command,
                        extractedQuery = cleanedItemName,
                        matchedItem = item,
                        confidence = 1.0f,
                        alternativeMatches = emptyList(),
                        processingSteps = processingSteps,
                        stockSummaries = emptyMap(),
                        stockLookupErrors = mapOf(
                            "order_creation" to (exception.message ?: "Order creation failed")
                        )
                    )
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during order creation", e)

            processingSteps.add(
                ProcessingStep(
                    step = "Error Handling",
                    input = "Exception occurred",
                    output = e.message ?: "Unexpected error"
                )
            )

            return VoiceCommandResult(
                originalTranscription = "Create order for ${command.itemName} quantity ${command.quantity}",
                intent = command,
                extractedQuery = command.itemName,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("unexpected_error" to (e.message ?: "Unexpected error"))
            )
        } finally {
            Log.d(TAG, "=== Order creation processing completed ===")
        }
    }
}