package com.example.granitevoice.ui.main_screen

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.granitevoice.AudioRecorderManager
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.domain.model.CommandIntent
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.VoiceCommandResult
import com.example.granitevoice.domain.usecase.ProcessVoiceCommandUseCase
import com.example.granitevoice.domain.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val processVoiceCommandUseCase: ProcessVoiceCommandUseCase,
    private val openAIClient: OpenAIClient,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private val _commandResult = MutableStateFlow<VoiceCommandResult?>(null)
    val commandResult = _commandResult.asStateFlow()

    private val audioRecorderManager = AudioRecorderManager(application)

    sealed class UiState {
        data object Idle : UiState()
        data object Listening : UiState()
        data object Processing : UiState()
        data class ShowingResult(val result: VoiceCommandResult) : UiState()
        data class ShowingError(val message: String) : UiState()
    }

    init {
        // Warm up OpenAI client and audio system to reduce first-time transcription delays
        viewModelScope.launch {
            try {
                // Start both warmups in parallel for better performance
                launch { openAIClient.warmUp() }
                launch { audioRecorderManager.preWarmAudioSystem() }

                android.util.Log.d("MainViewModel", "🔥 Voice system warmup initiated")
            } catch (e: Exception) {
                // Warmup failure is not critical, just log it
                android.util.Log.d("MainViewModel", "Voice system warmup failed: ${e.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        _uiState.update { UiState.Listening }
        _commandResult.value = null
        audioRecorderManager.startRecording()
    }

    fun stopRecording() {
        _uiState.update { UiState.Processing }
        audioRecorderManager.stopRecording(object : AudioRecorderManager.RecordingCallback {
            override fun onTranscriptionComplete(transcription: String?) {
                viewModelScope.launch {
                    if (!transcription.isNullOrBlank()) {
                        processVoiceCommand(transcription)
                    } else {
                        _snackbarMessage.value = "No speech detected. Please try again."
                        _uiState.update { UiState.Idle }
                    }
                }
            }

            override fun onError(error: String) {
                viewModelScope.launch {
                    _snackbarMessage.value = "Recording error: $error"
                    _uiState.update { UiState.ShowingError(error) }
                }
            }
        })
    }

    private suspend fun processVoiceCommand(transcription: String) {
        try {
            val result = processVoiceCommandUseCase(transcription)
            _commandResult.value = result

            when (result.intent) {
                is CommandIntent.StockLookup -> {
                    handleStockLookupResult(result)
                }
                is CommandIntent.CheckOrderStatus -> {
                    handleOrderStatusResult(result)
                }
                is CommandIntent.Unknown -> {
                    _snackbarMessage.value =
                        "I didn't understand that command. Try: 'Find stock for [item name]' or 'Check order [number]'"
                    _uiState.update { UiState.Idle }
                }
                else -> {
                    _snackbarMessage.value = "Command not supported"
                    _uiState.update { UiState.Idle }
                }
            }
        } catch (exception: Exception) {
            _snackbarMessage.value = "Processing error: ${exception.message}"
            _uiState.update { UiState.ShowingError(exception.message ?: "Unknown error") }
        }
    }

    private fun handleStockLookupResult(result: VoiceCommandResult) {
        _uiState.update { UiState.ShowingResult(result) }

        when {
            result.hasCompleteStockInfo() -> {
                // We have both item match and stock information
                val stockSummary = result.stockSummary!!
                val status = if (stockSummary.isInStock()) "✅" else "❌"
                _snackbarMessage.value = "$status ${result.getResultSummary()}"
            }

            result.matchedItem != null && result.stockLookupError != null -> {
                // Item found but stock lookup failed
                _snackbarMessage.value =
                    "Found ${result.matchedItem.description}, but couldn't get stock info: ${result.stockLookupError}"
            }

            result.matchedItem != null -> {
                // Item found, stock lookup might be in progress or not enabled
                _snackbarMessage.value =
                    "Found: ${result.matchedItem.code} - ${result.matchedItem.description}"
            }

            result.alternativeMatches.isNotEmpty() -> {
                // No exact match but we have alternatives
                _snackbarMessage.value =
                    "No exact match. Found ${result.alternativeMatches.size} similar items."
            }

            else -> {
                // No match found
                _snackbarMessage.value =
                    "No match found for '${result.extractedQuery}'. Try being more specific."
            }
        }
    }

    private fun handleOrderStatusResult(result: VoiceCommandResult) {
        _uiState.update { UiState.ShowingResult(result) }

        when {
            result.orderStatusInfo != null -> {
                // We have detailed order status information
                val orderInfo = result.orderStatusInfo
                val progressText = if (orderInfo.orderProgress != null) {
                    orderInfo.orderProgress.getProgressSummary()
                } else {
                    "Order ${orderInfo.orderNumber}: ${orderInfo.statusDisplay}"
                }
                _snackbarMessage.value = "📋 $progressText"
            }

            result.stockLookupErrors.isNotEmpty() -> {
                // Order lookup failed
                val errorMessages = result.stockLookupErrors.values.joinToString(", ")
                _snackbarMessage.value = "❌ $errorMessages"
            }

            else -> {
                // Generic fallback
                _snackbarMessage.value = "Order status command processed"
            }
        }
    }

    fun cancelRecording() {
        audioRecorderManager.cancelRecording()
        _uiState.update { UiState.Idle }
    }

    fun snackbarShown() {
        _snackbarMessage.value = null
    }

    fun clearResult() {
        _commandResult.value = null
        _uiState.update { UiState.Idle }
    }

    fun retryWithAlternative(alternative: MasterItem) {
        viewModelScope.launch {
            // Show the alternative as the selected result with correct stock info
            val currentResult = _commandResult.value
            if (currentResult != null) {
                // Check if we already have stock info for this alternative
                val alternativeStockSummary = currentResult.getStockSummary(alternative.code)
                val alternativeStockError = currentResult.getStockLookupError(alternative.code)

                if (alternativeStockSummary == null && alternativeStockError == null) {
                    // Need to lazy load stock info - show loading state
                    val loadingResult = currentResult.copy(
                        matchedItem = alternative,
                        confidence = 1.0f,
                        stockSummary = null,
                        stockLookupError = null
                    )
                    _commandResult.value = loadingResult
                    _snackbarMessage.value = "Loading stock info for ${alternative.description}..."

                    // Load stock info in background
                    try {
                        val result = withTimeoutOrNull(5000L) {
                            stockRepository.getItemStockSummary(alternative)
                        }

                        result?.fold(
                            onSuccess = { summary ->
                                // Update with loaded stock info
                                val updatedStockSummaries =
                                    currentResult.stockSummaries.toMutableMap()
                                updatedStockSummaries[alternative.code] = summary

                                val finalResult = currentResult.copy(
                                    matchedItem = alternative,
                                    confidence = 1.0f,
                                    stockSummaries = updatedStockSummaries,
                                    stockSummary = summary,
                                    stockLookupError = null,
                                    alternativeMatches = if (currentResult.matchedItem != null) {
                                        listOf(currentResult.matchedItem) + currentResult.alternativeMatches.filter { it.code != alternative.code }
                                    } else {
                                        currentResult.alternativeMatches.filter { it.code != alternative.code }
                                    }
                                )
                                _commandResult.value = finalResult

                                val status = if (summary.isInStock()) "✅" else "❌"
                                _snackbarMessage.value =
                                    "$status Selected: ${alternative.description} - ${summary.getTotalQuantityDisplay()}"
                            },
                            onFailure = { exception ->
                                // Update with error
                                val updatedErrors = currentResult.stockLookupErrors.toMutableMap()
                                updatedErrors[alternative.code] =
                                    exception.message ?: "Stock lookup failed"

                                val finalResult = currentResult.copy(
                                    matchedItem = alternative,
                                    confidence = 1.0f,
                                    stockLookupErrors = updatedErrors,
                                    stockSummary = null,
                                    stockLookupError = exception.message ?: "Stock lookup failed",
                                    alternativeMatches = if (currentResult.matchedItem != null) {
                                        listOf(currentResult.matchedItem) + currentResult.alternativeMatches.filter { it.code != alternative.code }
                                    } else {
                                        currentResult.alternativeMatches.filter { it.code != alternative.code }
                                    }
                                )
                                _commandResult.value = finalResult
                                _snackbarMessage.value =
                                    "Selected: ${alternative.description} (Stock lookup failed)"
                            }
                        ) ?: run {
                            // Timeout occurred
                            val updatedErrors = currentResult.stockLookupErrors.toMutableMap()
                            updatedErrors[alternative.code] = "Stock lookup timed out"

                            val finalResult = currentResult.copy(
                                matchedItem = alternative,
                                confidence = 1.0f,
                                stockLookupErrors = updatedErrors,
                                stockSummary = null,
                                stockLookupError = "Stock lookup timed out",
                                alternativeMatches = if (currentResult.matchedItem != null) {
                                    listOf(currentResult.matchedItem) + currentResult.alternativeMatches.filter { it.code != alternative.code }
                                } else {
                                    currentResult.alternativeMatches.filter { it.code != alternative.code }
                                }
                            )
                            _commandResult.value = finalResult
                            _snackbarMessage.value =
                                "Selected: ${alternative.description} (Stock lookup timed out)"
                        }
                    } catch (e: Exception) {
                        // Handle unexpected exceptions
                        val updatedErrors = currentResult.stockLookupErrors.toMutableMap()
                        updatedErrors[alternative.code] = e.message ?: "Unexpected error"

                        val finalResult = currentResult.copy(
                            matchedItem = alternative,
                            confidence = 1.0f,
                            stockLookupErrors = updatedErrors,
                            stockSummary = null,
                            stockLookupError = e.message ?: "Unexpected error",
                            alternativeMatches = if (currentResult.matchedItem != null) {
                                listOf(currentResult.matchedItem) + currentResult.alternativeMatches.filter { it.code != alternative.code }
                            } else {
                                currentResult.alternativeMatches.filter { it.code != alternative.code }
                            }
                        )
                        _commandResult.value = finalResult
                        _snackbarMessage.value =
                            "Selected: ${alternative.description} (Stock lookup error)"
                    }
                } else {
                    // Already have stock info, use it immediately
                    val updatedResult = currentResult.copy(
                        matchedItem = alternative,
                        confidence = 1.0f,
                        stockSummary = alternativeStockSummary,
                        stockLookupError = alternativeStockError,
                        alternativeMatches = if (currentResult.matchedItem != null) {
                            listOf(currentResult.matchedItem) + currentResult.alternativeMatches.filter { it.code != alternative.code }
                        } else {
                            currentResult.alternativeMatches.filter { it.code != alternative.code }
                        }
                    )
                    _commandResult.value = updatedResult

                    val message = when {
                        alternativeStockSummary != null -> {
                            val status = if (alternativeStockSummary.isInStock()) "✅" else "❌"
                            "$status Selected: ${alternative.description} - ${alternativeStockSummary.getTotalQuantityDisplay()}"
                        }

                        alternativeStockError != null -> {
                            "Selected: ${alternative.description} (Stock lookup failed: $alternativeStockError)"
                        }

                        else -> {
                            "Selected: ${alternative.code} - ${alternative.description}"
                        }
                    }
                    _snackbarMessage.value = message
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.cleanup()
        openAIClient.cleanup()
    }
}