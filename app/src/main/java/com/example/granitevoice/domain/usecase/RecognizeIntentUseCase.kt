package com.example.granitevoice.domain.usecase

import android.util.Log
import com.example.granitevoice.domain.model.CommandIntent
import javax.inject.Inject

class RecognizeIntentUseCase @Inject constructor() {

    companion object {
        private const val TAG = "IntentRecognition"
    }

    operator fun invoke(transcription: String): CommandIntent {
        val lowerTranscription = transcription.lowercase()

        val stockKeywords = listOf(
            "stock", "inventory", "item", "product", "part", "component",
            "lookup", "look up", "find", "search", "get", "show", "check",
            "what is", "where is", "do we have", "how much", "how many",
            "quantity", "available", "in stock", "locate", "tell me about"
        )

        Log.d(TAG, "Intent recognition for: '$lowerTranscription'")

        val hasStockIntent = stockKeywords.any { keyword ->
            lowerTranscription.contains(keyword)
        }

        val intent = if (hasStockIntent) {
            CommandIntent.StockLookup
        } else {
            CommandIntent.Unknown
        }

        Log.d(TAG, "Intent recognized as: $intent")
        return intent
    }
}