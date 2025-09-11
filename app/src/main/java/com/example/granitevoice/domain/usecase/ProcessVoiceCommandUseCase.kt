package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.*
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.example.granitevoice.domain.repository.StockRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Main orchestrator use case that coordinates the voice command processing workflow.
 * Follows Clean Architecture by depending only on domain repository interfaces.
 */
class ProcessVoiceCommandUseCase @Inject constructor(
    private val recognizeIntentUseCase: RecognizeIntentUseCase,
    private val searchItemsUseCase: SearchItemsUseCase,
    private val voiceProcessingRepository: VoiceProcessingRepository,
    private val stockRepository: StockRepository,
    private val config: VoiceProcessingConfig
) {

    companion object {
        private const val TAG = "VoiceCommandProcessor"
    }

    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        val processingSteps = mutableListOf<ProcessingStep>()

        return try {
            Log.d(TAG, "Processing voice command: '$transcription'")

            // Step 1: Recognize Intent
            val intent = recognizeIntentUseCase(transcription)
            processingSteps.add(
                ProcessingStep(
                    step = "Intent Recognition",
                    input = transcription,
                    output = intent.toString()
                )
            )

            when (intent) {
                is CommandIntent.StockLookup -> processStockLookup(transcription, processingSteps)
                is CommandIntent.Unknown -> handleUnknownCommand(transcription, processingSteps)
                else -> handleUnknownCommand(transcription, processingSteps)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Voice command processing failed", e)
            VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.Unknown,
                extractedQuery = transcription,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = mapOf("processing_error" to (e.message ?: "Unknown error"))
            )
        }
    }

    private suspend fun processStockLookup(
        transcription: String,
        processingSteps: MutableList<ProcessingStep>
    ): VoiceCommandResult {

        // Step 2: Extract Query
        val extractedQuery = voiceProcessingRepository.extractItemQuery(transcription)
            .getOrDefault(transcription)

        processingSteps.add(
            ProcessingStep(
                step = "Query Extraction",
                input = transcription,
                output = extractedQuery
            )
        )

        // Step 3: Search for Items
        val searchResults = searchItemsUseCase(extractedQuery)
        processingSteps.add(
            ProcessingStep(
                step = "Item Search",
                input = extractedQuery,
                output = "Found ${searchResults.size} matches"
            )
        )

        if (searchResults.isEmpty()) {
            return VoiceCommandResult(
                originalTranscription = transcription,
                intent = CommandIntent.StockLookup,
                extractedQuery = extractedQuery,
                matchedItem = null,
                confidence = 0.0f,
                alternativeMatches = emptyList(),
                processingSteps = processingSteps,
                stockSummaries = emptyMap(),
                stockLookupErrors = emptyMap()
            )
        }

        // Step 4: Calculate Confidence and Determine Primary Match
        val primaryMatch = searchResults.first()
        val alternatives = searchResults.drop(1).take(config.maxAlternatives - 1)
        val confidence = calculateConfidence(extractedQuery, primaryMatch)

        // Step 5: Load Stock Information (Primary Only for Performance)
        val stockSummaries = mutableMapOf<String, ItemStockSummary>()
        val stockLookupErrors = mutableMapOf<String, String>()

        if (confidence >= config.minimumConfidence && config.enableStockLookup) {
            loadStockInfo(primaryMatch, stockSummaries, stockLookupErrors)
        }

        processingSteps.add(
            ProcessingStep(
                step = "Stock Lookup",
                input = "Primary match: ${primaryMatch.code}",
                output = if (stockSummaries.isNotEmpty()) "Success" else "Skipped (low confidence)"
            )
        )

        return VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.StockLookup,
            extractedQuery = extractedQuery,
            matchedItem = if (confidence >= config.minimumConfidence) primaryMatch else null,
            confidence = confidence,
            alternativeMatches = alternatives,
            processingSteps = processingSteps,
            stockSummaries = stockSummaries,
            stockLookupErrors = stockLookupErrors,
            // Legacy compatibility
            stockSummary = stockSummaries[primaryMatch.code],
            stockLookupError = stockLookupErrors[primaryMatch.code]
        )
    }

    private suspend fun handleUnknownCommand(
        transcription: String,
        processingSteps: MutableList<ProcessingStep>
    ): VoiceCommandResult {
        processingSteps.add(
            ProcessingStep(
                step = "Command Processing",
                input = transcription,
                output = "Intent not recognized"
            )
        )

        return VoiceCommandResult(
            originalTranscription = transcription,
            intent = CommandIntent.Unknown,
            extractedQuery = transcription,
            matchedItem = null,
            confidence = 0.0f,
            alternativeMatches = emptyList(),
            processingSteps = processingSteps,
            stockSummaries = emptyMap(),
            stockLookupErrors = emptyMap()
        )
    }

    private suspend fun loadStockInfo(
        item: MasterItem,
        stockSummaries: MutableMap<String, ItemStockSummary>,
        stockLookupErrors: MutableMap<String, String>
    ) {
        try {
            withTimeoutOrNull(config.stockLookupTimeoutMs) {
                stockRepository.getItemStockSummary(item)
            }?.fold(
                onSuccess = { summary ->
                    stockSummaries[item.code] = summary
                    Log.d(
                        TAG,
                        "Stock lookup successful for ${item.code}: ${summary.getTotalQuantityDisplay()}"
                    )
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Stock lookup failed"
                    stockLookupErrors[item.code] = errorMessage
                    Log.e(TAG, "Stock lookup failed for ${item.code}: $errorMessage")
                }
            ) ?: run {
                stockLookupErrors[item.code] = "Stock lookup timed out"
                Log.w(TAG, "Stock lookup timed out for ${item.code}")
            }
        } catch (e: Exception) {
            stockLookupErrors[item.code] = e.message ?: "Unexpected error"
            Log.e(TAG, "Unexpected error during stock lookup for ${item.code}", e)
        }
    }

    private fun calculateConfidence(query: String, item: MasterItem): Float {
        val queryLower = query.lowercase()
        val codeLower = item.code.lowercase()
        val descriptionLower = item.description.lowercase()

        // Simple confidence calculation - can be enhanced
        return when {
            codeLower == queryLower -> 1.0f
            descriptionLower == queryLower -> 0.95f
            codeLower.contains(queryLower) || queryLower.contains(codeLower) -> 0.8f
            descriptionLower.contains(queryLower) || queryLower.contains(descriptionLower) -> 0.75f
            else -> 0.6f // AI found it, give it reasonable credit
        }
    }

    /**
     * Load stock information for a specific item on-demand (for lazy loading)
     */
    suspend fun loadStockForItem(item: MasterItem): Result<ItemStockSummary> {
        Log.d(TAG, "Lazy loading stock for item: ${item.code}")
        return try {
            withTimeoutOrNull(config.stockLookupTimeoutMs) {
                stockRepository.getItemStockSummary(item)
            } ?: Result.failure(Exception("Stock lookup timed out"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}