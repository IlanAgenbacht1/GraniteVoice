package com.example.granitevoice.domain.usecase

import com.example.granitevoice.domain.model.*
import javax.inject.Inject

/**
 * Legacy wrapper around the new RouteVoiceCommandUseCase for backward compatibility.
 * This maintains the existing API while implementing the new Strategy/Factory pattern architecture.
 *
 * @deprecated Consider using RouteVoiceCommandUseCase directly for new code.
 */
class ProcessVoiceCommandUseCase @Inject constructor(
    private val routeVoiceCommandUseCase: RouteVoiceCommandUseCase
) {

    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        return routeVoiceCommandUseCase(transcription)
    }

    /**
     * Load stock information for a specific item on-demand (for lazy loading)
     * @deprecated This method will be moved to a dedicated stock service in future versions
     */
    @Deprecated("Use StockRepository.getItemStockSummary directly")
    suspend fun loadStockForItem(item: MasterItem): Result<ItemStockSummary> {
        // This functionality should be moved to a dedicated stock service
        // For now, we'll throw an UnsupportedOperationException to encourage migration
        throw UnsupportedOperationException(
            "loadStockForItem is deprecated. Use StockRepository.getItemStockSummary directly."
        )
    }
}