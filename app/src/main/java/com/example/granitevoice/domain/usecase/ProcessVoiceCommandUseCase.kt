package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.*
import javax.inject.Inject

/**
 * Router/Orchestrator use case that determines the intent and delegates to the appropriate specific use case.
 * Implements the Strategy pattern by routing to different use cases based on command intent.
 */
class ProcessVoiceCommandUseCase @Inject constructor(
    private val recognizeIntentUseCase: RecognizeIntentUseCase,
    private val processStockLookupUseCase: ProcessStockLookupUseCase,
    private val processOrderStatusUseCase: ProcessOrderStatusUseCase
) {
    companion object {
        private const val TAG = "VoiceCommandRouter"
    }

    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Starting voice command routing ===")
        Log.d(TAG, "Transcription: '$transcription'")

        return try {
            // Step 1: Recognize the intent
            val intentStartTime = System.currentTimeMillis()
            val intent = recognizeIntentUseCase(transcription)
            val intentDuration = System.currentTimeMillis() - intentStartTime

            Log.d(TAG, "Intent recognition completed in ${intentDuration}ms: $intent")

            // Step 2: Route to the correct use case (Strategy Pattern)
            val routingStartTime = System.currentTimeMillis()
            val result = when (intent) {
                is CommandIntent.StockLookup -> {
                    Log.d(TAG, "Routing to stock lookup use case")
                    processStockLookupUseCase(transcription)
                }

                is CommandIntent.CheckOrderStatus -> {
                    Log.d(TAG, "Routing to order status use case")
                    Log.d(TAG, "Order ID: '${intent.orderId}'")
                    processOrderStatusUseCase(transcription)
                }

                is CommandIntent.Unknown -> {
                    Log.d(TAG, "Unknown command - returning error result")
                    handleUnknownCommand(transcription)
                }

                is CommandIntent.Other -> {
                    Log.d(TAG, "Unsupported command type: ${intent.description}")
                    handleUnsupportedCommand(transcription, intent.description)
                }
            }

            val routingDuration = System.currentTimeMillis() - routingStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(
                TAG,
                "Command processing completed in ${routingDuration}ms (total: ${totalDuration}ms)"
            )
            Log.d(TAG, "Result summary: ${result.getResultSummary()}")
            Log.d(TAG, "=== Voice command routing completed ===")
            result
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Voice command routing failed after ${totalDuration}ms", e)
            VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.Unknown,
                extractedQuery = transcription,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = listOf(
                    ProcessingStep(
                        step = "Router Error",
                        input = transcription,
                        output = e.message ?: "Routing failed"
                    )
                ),
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("routing_error" to (e.message ?: "Unknown routing error"))
            )
        }
    }

    /**
     * Handle unknown commands that couldn't be recognized
     */
    private fun handleUnknownCommand(transcription: String): VoiceCommandResult {
        Log.d(TAG, "Handling unknown command: '$transcription'")
        return VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.Unknown,
            extractedQuery = transcription,
            matchedItem = null,
            confidence = 0.0f,
            alternativeMatches = emptyList(),
            processingSteps = listOf(
                ProcessingStep(
                    step = "Intent Recognition",
                    input = transcription,
                    output = "Command not understood"
                )
            ),
            stockSummaries = emptyMap(),
            stockLookupErrors = mapOf(
                "intent_not_recognized" to "Command not understood. Try: 'Find stock for [item]', 'Check order [ID]'"
            )
        )
    }

    /**
     * Handle unsupported command types
     */
    private fun handleUnsupportedCommand(
        transcription: String,
        description: String
    ): VoiceCommandResult {
        Log.d(TAG, "Handling unsupported command: '$description'")
        return VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.Other(description),
            extractedQuery = transcription,
            matchedItem = null,
            confidence = 0.0f,
            alternativeMatches = emptyList(),
            processingSteps = listOf(
                ProcessingStep(
                    step = "Intent Recognition",
                    input = transcription,
                    output = "Unsupported command type: $description"
                )
            ),
            stockSummaries = emptyMap(),
            stockLookupErrors = mapOf(
                "unsupported_command" to "Command type '$description' is not yet supported"
            )
        )
    }
}