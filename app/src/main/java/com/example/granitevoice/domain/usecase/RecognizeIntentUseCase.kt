package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.CommandIntent
import javax.inject.Inject

class RecognizeIntentUseCase @Inject constructor() {

    companion object {
        private const val TAG = "IntentRecognition"
    }

    operator fun invoke(transcription: String): CommandIntent {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Starting intent recognition ===")
        Log.d(TAG, "Input: '$transcription'")

        val lowerTranscription = transcription.lowercase()
        Log.d(TAG, "Normalized: '$lowerTranscription'")

        val stockKeywords = listOf(
            "stock", "inventory", "item", "product", "part", "component",
            "lookup", "look up", "find", "search", "get", "show", "check",
            "what is", "where is", "do we have", "how much", "how many",
            "quantity", "available", "in stock", "locate", "tell me about"
        )

        Log.d(TAG, "Checking against ${stockKeywords.size} stock keywords")

        val matchedKeywords = mutableListOf<String>()
        val hasStockIntent = stockKeywords.any { keyword ->
            val matches = lowerTranscription.contains(keyword)
            if (matches) {
                matchedKeywords.add(keyword)
                Log.d(TAG, "  Matched keyword: '$keyword'")
            }
            matches
        }

        val intent = if (hasStockIntent) {
            CommandIntent.StockLookup
        } else {
            CommandIntent.Unknown
        }

        val duration = System.currentTimeMillis() - startTime

        if (hasStockIntent) {
            Log.d(TAG, " Intent: StockLookup (${duration}ms)")
            Log.d(
                TAG,
                "Matched ${matchedKeywords.size} keywords: ${matchedKeywords.joinToString(", ")}"
            )
        } else {
            Log.d(TAG, " Intent: Unknown (${duration}ms)")
            Log.d(TAG, "No stock keywords found in transcription")

            // Log for debugging - what words were in the transcription?
            val words = lowerTranscription.split(" ").filter { it.isNotBlank() }
            Log.d(TAG, "Transcription contained ${words.size} words: ${words.joinToString(", ")}")
        }

        Log.d(TAG, "=== Intent recognition completed ===")
        return intent
    }
}