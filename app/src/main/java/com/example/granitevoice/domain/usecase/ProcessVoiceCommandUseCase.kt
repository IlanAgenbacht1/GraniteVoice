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
        val queryLower = query.lowercase().trim()
        val codeLower = item.code.lowercase()
        val descriptionLower = item.description.lowercase()

        Log.d(
            TAG,
            "Calculating confidence for: '$queryLower' vs '${item.code}' / '${item.description}'"
        )

        // Enhanced confidence calculation that better reflects fuzzy matching quality
        return when {
            // Perfect matches
            codeLower == queryLower -> {
                Log.d(TAG, "Perfect code match - confidence: 1.0")
                1.0f
            }

            descriptionLower == queryLower -> {
                Log.d(TAG, "Perfect description match - confidence: 0.95")
                0.95f
            }

            // Very high confidence - exact code or strong word matches
            codeLower.contains(queryLower) || queryLower.contains(codeLower) -> {
                Log.d(TAG, "Code substring match - confidence: 0.9")
                0.9f
            }

            // High confidence - good description matches or word overlaps
            hasStrongWordOverlap(queryLower, descriptionLower) -> {
                val score = calculateWordOverlapScore(queryLower, descriptionLower)
                Log.d(TAG, "Strong word overlap - confidence: $score")
                score
            }

            // Medium-high confidence - partial description matches
            descriptionLower.contains(queryLower) || queryLower.contains(descriptionLower) -> {
                Log.d(TAG, "Description substring match - confidence: 0.8")
                0.8f
            }

            // Medium confidence - fuzzy matching found it with some similarity
            hasNumberSimilarity(queryLower, codeLower) || hasNumberSimilarity(
                queryLower,
                descriptionLower
            ) -> {
                Log.d(TAG, "Number similarity detected - confidence: 0.85")
                0.85f
            }

            // Default for AI/fuzzy matches - higher than before since our fuzzy matching is now better
            else -> {
                Log.d(TAG, "Fuzzy match (AI found it) - confidence: 0.8")
                0.8f
            }
        }
    }

    private fun hasStrongWordOverlap(query: String, description: String): Boolean {
        val queryWords = query.split(" ").filter { it.length > 2 }.toSet()
        val descWords = description.split(" ").filter { it.length > 2 }.toSet()

        if (queryWords.isEmpty()) return false

        val commonWords = queryWords.intersect(descWords)
        val overlapRatio = commonWords.size.toFloat() / queryWords.size

        return overlapRatio >= 0.6f // 60% or more words match
    }

    private fun calculateWordOverlapScore(query: String, description: String): Float {
        val queryWords = query.split(" ").filter { it.length > 2 }
        val descWords = description.split(" ").filter { it.length > 2 }

        if (queryWords.isEmpty()) return 0.75f

        val exactMatches = queryWords.count { queryWord ->
            descWords.any { descWord -> queryWord.equals(descWord, ignoreCase = true) }
        }

        val partialMatches = queryWords.count { queryWord ->
            descWords.any { descWord ->
                !queryWord.equals(descWord, ignoreCase = true) && (
                        queryWord.contains(descWord, ignoreCase = true) ||
                                descWord.contains(queryWord, ignoreCase = true) ||
                                (queryWord.length > 3 && descWord.length > 3 &&
                                        queryWord.take(3)
                                            .equals(descWord.take(3), ignoreCase = true))
                        )
            }
        }

        val exactRatio = exactMatches.toFloat() / queryWords.size
        val partialRatio = partialMatches.toFloat() / queryWords.size
        val combinedScore = exactRatio + (partialRatio * 0.5f)

        // Convert to confidence score (0.75 - 0.95 range for strong matches)
        return minOf(0.95f, 0.75f + (combinedScore * 0.2f))
    }

    private fun hasNumberSimilarity(query: String, text: String): Boolean {
        val queryNumbers = Regex("\\d+").findAll(query).map { it.value.toIntOrNull() ?: 0 }.toList()
        val textNumbers = Regex("\\d+").findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()

        if (queryNumbers.isEmpty() || textNumbers.isEmpty()) return false

        // Check for exact matches or close numbers
        return queryNumbers.any { queryNum ->
            textNumbers.any { textNum ->
                queryNum == textNum ||
                        kotlin.math.abs(queryNum - textNum) <= 10 ||
                        (queryNum >= 100 && textNum >= 10 &&
                                queryNum.toString().take(2) == textNum.toString().take(2))
            }
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