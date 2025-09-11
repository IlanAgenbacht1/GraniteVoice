package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.repository.AudioTranscriptionRepository
import java.io.File
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val transcriptionRepository: AudioTranscriptionRepository
) {

    companion object {
        private const val TAG = "TranscribeAudio"
    }

    suspend operator fun invoke(audioFile: File): Result<String> {
        Log.d(TAG, "=== Starting audio transcription ===")
        Log.d(TAG, "Audio file: ${audioFile.name} (${audioFile.length()} bytes)")
        val startTime = System.currentTimeMillis()

        return try {
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Audio file does not exist: ${audioFile.absolutePath}")
                return Result.failure(Exception("Audio file not found"))
            }

            if (audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file is empty: ${audioFile.absolutePath}")
                return Result.failure(Exception("Audio file is empty"))
            }

            val result = transcriptionRepository.transcribeAudio(audioFile)
            val duration = System.currentTimeMillis() - startTime

            result.fold(
                onSuccess = { transcription ->
                    Log.d(TAG, "✅ Transcription completed in ${duration}ms")
                    Log.d(TAG, "Transcription result: '$transcription'")
                    Log.d(TAG, "=== Audio transcription completed ===")
                    Result.success(transcription)
                },
                onFailure = { exception ->
                    Log.e(TAG, "❌ Transcription failed after ${duration}ms: ${exception.message}")
                    Log.d(TAG, "=== Audio transcription completed (with error) ===")
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Transcription use case failed after ${duration}ms", e)
            Log.d(TAG, "=== Audio transcription completed (with exception) ===")
            Result.failure(e)
        }
    }
}