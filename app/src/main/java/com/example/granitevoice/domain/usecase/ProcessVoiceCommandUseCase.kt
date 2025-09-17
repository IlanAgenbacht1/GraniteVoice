package com.example.granitevoice.domain.usecase

import com.example.granitevoice.domain.model.VoiceCommandResult
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
}