package com.example.granitevoice.data.remote.client

import android.content.Context
import android.util.Log
import com.example.granitevoice.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class OpenAIClient(private val context: Context) {
    private val client: OkHttpClient = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "OpenAIClient"

    private companion object {
        private const val OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_TRANSCRIPTIONS_URL =
            "https://api.openai.com/v1/audio/transcriptions"
        private val API_KEY = BuildConfig.OPENAI_API_KEY
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun transcribeAudio(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file not found at path: $filePath")
                return@withContext Result.failure(IOException("Audio file not found."))
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.Companion.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "gpt-4o-transcribe")
                .build()

            val request = Request.Builder()
                .url(OPENAI_TRANSCRIPTIONS_URL)
                .header("Authorization", "Bearer $API_KEY")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Transcription API error: ${response.code} - $errorBody")
                    return@withContext Result.failure(IOException("Transcription failed with code: ${response.code}"))
                }
                val responseBody = response.body!!.string()
                val jsonResponse = JSONObject(responseBody)
                val transcription = jsonResponse.optString("text", "")
                if (transcription.isNotBlank()) {
                    Result.success(transcription)
                } else {
                    Result.failure(IOException("Transcription result was empty."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during transcription: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun makeChatRequest(prompt: String): JSONObject = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var attempt = 0
        while (attempt < maxRetries) {
            var response: Response? = null
            try {
                val jsonBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }))
                }

                val body = jsonBody.toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url(OPENAI_CHAT_URL)
                    .header("Authorization", "Bearer $API_KEY")
                    .post(body)
                    .build()

                response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body!!.string()
                    val responseJson = JSONObject(responseBody)
                    val content = responseJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return@withContext JSONObject(content)
                }

                // If not successful, throw an exception to trigger the retry logic in the catch block.
                throw IOException("Chat request failed with code: ${response.code}")

            } catch (e: Exception) {
                Log.e(TAG, "Chat request attempt ${attempt + 1} failed: ${e.message}")
                attempt++
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                } else {
                    // All retries failed, throw an exception to be caught by the caller.
                    throw IOException("Chat request failed after $maxRetries attempts")
                }
            } finally {
                response?.close()
            }
        }
        // This line is unreachable but required by the compiler.
        throw IOException("Chat request failed after max retries.")
    }

    fun cleanup() {
        scope.cancel()
    }

    internal suspend fun makeChatRequestRaw(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }))
            }

            val body = jsonBody.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url(OPENAI_CHAT_URL)
                .header("Authorization", "Bearer $API_KEY")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body!!.string()
                    val responseJson = JSONObject(responseBody)
                    return@withContext responseJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }

                // If not successful, throw an exception to trigger the retry logic in the catch block.
                throw IOException("Chat request failed with code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat error: ${e.message}")
            return@withContext "" // Return empty string on failure
        }
    }

    /*suspend fun fuzzyMatchName(target: String, candidates: List<String>): String? =
        withContext(Dispatchers.IO) {
            if (candidates.isEmpty()) return@withContext null
            val prompt = UtilityPrompts.fuzzyMatchPrompt.replace("{target}", target)
                .replace("{candidates}", candidates.toString())
            try {
                val response = makeChatRequest(prompt)
                Log.d(TAG, "Fuzzy Match Response: $response")
                val cleanedResponse = response.toString()
                    .replace(Regex("(?s)^\\s*```\\w*\\s*\\n?(.*?)\\n?\\s*```\\s*$"), "$1").trim()
                val json = JSONObject(cleanedResponse)
                val match =
                    if (json.has("match") && !json.isNull("match")) json.optString("match") else null
                Log.d(TAG, "Fuzzy Match Result: $match")
                match
            } catch (e: Exception) {
                Log.e(TAG, "Fuzzy match error: ${e.message}")
                // Fallback to simple case-insensitive match on error
                candidates.find { it.trim().equals(target.trim(), ignoreCase = true) }
            }
        }

    suspend fun parseCardDescriptionWithAI(fullDesc: String): Pair<String, JSONObject> {
        if (fullDesc.isBlank()) {
            return "" to JSONObject()
        }

        return try {
            val prompt =
                UtilityPrompts.descriptionParsingPrompt.replace("{full_description}", fullDesc)
            val response = makeChatRequest(prompt)
            val mainDesc = response.optString("main_description", fullDesc)
            val fields = response.optJSONObject("fields") ?: JSONObject()
            mainDesc to fields
        } catch (e: Exception) {
            Log.e(TAG, "AI parsing of description failed: ${e.message}", e)
            // Fallback to treating the whole description as the main description
            fullDesc to JSONObject()
        }
    }*/
}