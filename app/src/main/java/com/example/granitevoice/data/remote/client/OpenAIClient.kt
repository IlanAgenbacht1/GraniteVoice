package com.example.granitevoice.data.remote.client

import android.content.Context
import android.util.Log
import com.example.granitevoice.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    // Warmed up state tracking
    private var isWarmedUp = false
    private val warmupJob = SupervisorJob()
    private val warmupScope = CoroutineScope(Dispatchers.IO + warmupJob)

    /**
     * Initialize and warm up the OpenAI client to reduce cold start delays
     * Call this when the app starts or when you expect to use voice commands soon
     */
    fun warmUp() {
        if (isWarmedUp) return

        Log.d(TAG, "🔥 Warming up OpenAI client...")
        warmupScope.launch {
            try {
                // Make a lightweight test request to establish connection
                val testRequest = Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("User-Agent", "GraniteVoice/1.0")
                    .build()

                val response = withTimeoutOrNull(5000L) {
                    client.newCall(testRequest).execute()
                }

                if (response != null && response.isSuccessful) {
                    Log.d(TAG, "✅ OpenAI client warmed up successfully")
                    isWarmedUp = true
                } else {
                    Log.d(TAG, "⚠️ OpenAI warmup completed (connection established)")
                    isWarmedUp = true // Still mark as warmed up since connection is established
                }

                response?.close()
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ OpenAI warmup completed with exception: ${e.message}")
                isWarmedUp = true // Mark as warmed up to avoid repeated attempts
            }
        }
    }

    suspend fun transcribeAudio(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting audio transcription ===")
        Log.d(TAG, "Audio file: $filePath")
        val startTime = System.currentTimeMillis()

        // If not warmed up, do a quick warmup
        if (!isWarmedUp) {
            Log.d(TAG, "🔥 Quick warmup before transcription...")
            warmUp()
            delay(100) // Brief pause to let warmup start
        }

        try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file not found at path: $filePath")
                return@withContext Result.failure(IOException("Audio file not found."))
            }

            Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")

            // This is where you can add context to improve transcription accuracy.
            // Consider adding prompts that help with your specific domain
            val transcriptionPrompt = "Transcribe spoken english words and sentences accurately. " +
                    "Focus on product names, item codes, inventory terms, and order-related commands."

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                // 1. CORRECTED: Use the required 'gpt-4o-transcribe' model.
                .addFormDataPart("model", "gpt-4o-transcribe")

                // 2. ADDED: Use the 'prompt' parameter for context and spelling hints.
                .addFormDataPart("prompt", transcriptionPrompt)

                // 3. (Optional) ADDED: Specify the language if known, using ISO-639-1 format.
                .addFormDataPart("language", "en")

                // 4. (Optional) ADDED: Control the output format. Default is json.
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url(OPENAI_TRANSCRIPTIONS_URL)
                .header("Authorization", "Bearer $API_KEY")
                .post(requestBody)
                .build()

            // Execute with retry logic for reliability
            val response = executeWithRetry(request)

            val duration = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Transcription API error: ${response.code} - $errorBody")
                return@withContext Result.failure(IOException("Transcription failed with code: ${response.code}"))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body."))

            val jsonResponse = JSONObject(responseBody)
            val transcription = jsonResponse.optString("text", "")

            if (transcription.isNotBlank()) {
                Log.d(TAG, "✅ Transcription completed in ${duration}ms: '$transcription'")
                Result.success(transcription)
            } else {
                Result.failure(IOException("Transcription result was empty."))
            }

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Exception during transcription after ${duration}ms: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Execute HTTP request with retry logic for better reliability
     */
    private suspend fun executeWithRetry(request: Request): Response {
        var lastException: Exception? = null

        repeat(3) { attempt ->
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code == 400) {
                    // Return successful responses or client errors (don't retry client errors)
                    return response
                }

                response.close()
                Log.d(TAG, "Attempt ${attempt + 1} failed with code ${response.code}, retrying...")

            } catch (e: Exception) {
                lastException = e
                Log.d(TAG, "Attempt ${attempt + 1} failed with exception: ${e.message}")
            }

            if (attempt < 2) {
                delay(1000L * (attempt + 1)) // Exponential backoff
            }
        }

        throw lastException ?: IOException("All retry attempts failed")
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

    fun cleanup() {
        scope.cancel()
        warmupJob.cancel()
    }
}