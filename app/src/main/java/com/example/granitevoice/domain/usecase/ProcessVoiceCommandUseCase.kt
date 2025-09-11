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
        val startTime = System.currentTimeMillis()
        val processingSteps = mutableListOf<ProcessingStep>()

        return try {
            Log.d(TAG, "=== Starting voice command processing ===")
            Log.d(TAG, "Transcription: '$transcription'")
            Log.d(
                TAG,
                "Config: maxAlternatives=${config.maxAlternatives}, minConfidence=${config.minimumConfidence}"
            )

            // Step 1: Recognize Intent
            val intentStartTime = System.currentTimeMillis()
            val intent = recognizeIntentUseCase(transcription)
            val intentDuration = System.currentTimeMillis() - intentStartTime

            processingSteps.add(
                ProcessingStep(
                    step = "Intent Recognition",
                    input = transcription,
                    output = intent.toString()
                )
            )

            Log.d(TAG, "Intent recognition completed in ${intentDuration}ms: $intent")

            val result = when (intent) {
                is CommandIntent.StockLookup -> {
                    Log.d(TAG, "Processing stock lookup command")
                    processStockLookup(transcription, processingSteps)
                }

                is CommandIntent.Unknown -> {
                    Log.d(TAG, "Processing unknown command")
                    handleUnknownCommand(transcription, processingSteps)
                }

                else -> {
                    Log.d(TAG, "Processing unsupported command")
                    handleUnknownCommand(transcription, processingSteps)
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "=== Voice command processing completed in ${totalDuration}ms ===")
            Log.d(TAG, "Result summary: ${result.getResultSummary()}")

            if (config.enableProcessingLogging) {
                logProcessingSteps(result.processingSteps)
            }

            result

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Voice command processing failed after ${totalDuration}ms", e)

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
        Log.d(TAG, "--- Starting stock lookup workflow ---")

        // Step 2: Extract Query
        val queryStartTime = System.currentTimeMillis()
        val extractedQuery = voiceProcessingRepository.extractItemQuery(transcription)
            .getOrDefault(transcription)
        val queryDuration = System.currentTimeMillis() - queryStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Query Extraction",
                input = transcription,
                output = extractedQuery
            )
        )

        Log.d(
            TAG,
            "Query extraction completed in ${queryDuration}ms: '$transcription' -> '$extractedQuery'"
        )

        // Step 3: Search for Items
        val searchStartTime = System.currentTimeMillis()
        val searchResults = searchItemsUseCase(extractedQuery)
        val searchDuration = System.currentTimeMillis() - searchStartTime

        processingSteps.add(
            ProcessingStep(
                step = "Item Search",
                input = extractedQuery,
                output = "Found ${searchResults.size} matches"
            )
        )

        Log.d(
            TAG,
            "Item search completed in ${searchDuration}ms: found ${searchResults.size} matches"
        )

        if (searchResults.isNotEmpty()) {
            Log.d(TAG, "Search results:")
            searchResults.forEachIndexed { index, item ->
                Log.d(TAG, "  [$index] ${item.code}: ${item.description}")
            }
        }

        if (searchResults.isEmpty()) {
            Log.d(TAG, "No items found for query '$extractedQuery'")
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

        Log.d(TAG, "Primary match: ${primaryMatch.code} (confidence: $confidence)")
        Log.d(TAG, "Alternative matches: ${alternatives.size}")
        alternatives.forEach { alt ->
            Log.d(TAG, "  - ${alt.code}: ${alt.description}")
        }

        // Step 5: Load Stock Information (Primary Only for Performance)
        val stockSummaries = mutableMapOf<String, ItemStockSummary>()
        val stockLookupErrors = mutableMapOf<String, String>()

        if (confidence >= config.minimumConfidence && config.enableStockLookup) {
            Log.d(
                TAG,
                "Loading stock info for primary match (confidence $confidence >= ${config.minimumConfidence})"
            )
            val stockStartTime = System.currentTimeMillis()
            loadStockInfo(primaryMatch, stockSummaries, stockLookupErrors)
            val stockDuration = System.currentTimeMillis() - stockStartTime
            Log.d(TAG, "Stock lookup completed in ${stockDuration}ms")
        } else {
            Log.d(
                TAG,
                "Skipping stock lookup: confidence=$confidence, minRequired=${config.minimumConfidence}, enabled=${config.enableStockLookup}"
            )
        }

        processingSteps.add(
            ProcessingStep(
                step = "Stock Lookup",
                input = "Primary match: ${primaryMatch.code}",
                output = if (stockSummaries.isNotEmpty()) "Success" else "Skipped (low confidence)"
            )
        )

        val result = VoiceCommandResult(
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

        Log.d(TAG, "--- Stock lookup workflow completed ---")
        return result
    }

    private suspend fun handleUnknownCommand(
        transcription: String,
        processingSteps: MutableList<ProcessingStep>
    ): VoiceCommandResult {
        Log.d(TAG, "Handling unknown command: '$transcription'")

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
        Log.d(TAG, "Loading stock info for item: ${item.code}")

        try {
            withTimeoutOrNull(config.stockLookupTimeoutMs) {
                stockRepository.getItemStockSummary(item)
            }?.fold(
                onSuccess = { summary ->
                    stockSummaries[item.code] = summary
                    Log.d(
                        TAG,
                        "Stock lookup successful for ${item.code}: ${summary.getTotalQuantityDisplay()} in ${summary.locationCount} locations"
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

    private fun logProcessingSteps(steps: List<ProcessingStep>) {
        Log.d(TAG, "=== Processing Steps Summary ===")
        steps.forEachIndexed { index, step ->
            Log.d(TAG, "Step ${index + 1}: ${step.step}")
            Log.d(TAG, "  Input: ${step.input}")
            Log.d(TAG, "  Output: ${step.output}")
        }
        Log.d(TAG, "=== End Processing Steps ===")
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