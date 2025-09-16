package com.example.granitevoice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.granitevoice.data.remote.client.OpenAIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var openAIClient: OpenAIClient = OpenAIClient(context)
    private var recorderThread: HandlerThread? = null
    private var recorderHandler: Handler? = null
    private val isRecording = AtomicBoolean(false)
    private var bufferSize = 0
    private var rawFile: File? = null
    private var rawOutputStream: FileOutputStream? = null

    // Pre-warm flag to avoid repeated initialization
    private var isPreWarmed = AtomicBoolean(false)

    private val TAG = "AudioRecorderManager"

    interface RecordingCallback {
        fun onTranscriptionComplete(transcription: String?)
        fun onError(error: String)
    }

    init {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 3
        recorderThread = HandlerThread("RecorderThread")
        recorderThread!!.start()
        recorderHandler = Handler(recorderThread!!.looper)

        // Pre-warm the OpenAI client
        openAIClient.warmUp()
    }

    /**
     * Pre-warm the audio recording system to reduce cold start delays
     * Call this when the app starts or when you expect to use voice commands soon
     */
    fun preWarmAudioSystem() {
        if (isPreWarmed.get()) return

        try {
            // Pre-create the recording directory if it doesn't exist
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (musicDir != null && !musicDir.exists()) {
                musicDir.mkdirs()
            }

            isPreWarmed.set(true)
            Log.d(TAG, "🔥 Audio system pre-warmed")
        } catch (e: Exception) {
            Log.w(TAG, "Pre-warm failed: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (!isRecording.compareAndSet(false, true)) {
            Log.w(TAG, "Recording is already in progress.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed.")
                isRecording.set(false)
                return
            }

            rawFile =
                File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recording.raw")
            rawOutputStream = FileOutputStream(rawFile)

            audioRecord!!.startRecording()
            Log.d(TAG, "Recording started.")

            recorderHandler?.post {
                val buffer = ByteArray(bufferSize)
                while (isRecording.get()) {
                    val read = audioRecord!!.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        try {
                            rawOutputStream?.write(buffer, 0, read)
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing to raw file: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Audio permission not granted.", e)
            isRecording.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            isRecording.set(false)
        }
    }

    fun stopRecording(callback: RecordingCallback?) {
        if (!isRecording.compareAndSet(true, false)) {
            Log.w(TAG, "Recording was not in progress.")
            if (callback == null) return
        }

        Log.d(TAG, "Stopping recording.")
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            rawOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        if (callback == null) {
            rawFile?.delete()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val wavFile =
                File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recording.wav")
            try {
                if (rawFile?.exists() != true) {
                    Log.e(TAG, "Raw recording file not found, cannot process.")
                    callback.onError("Recording file was not created or is missing.")
                    return@launch
                }
                convertPcmToWav(rawFile!!, wavFile)

                val transcriptionResult = openAIClient.transcribeAudio(wavFile.absolutePath)
                transcriptionResult.fold(
                    onSuccess = {
                        callback.onTranscriptionComplete(it)
                    },
                    onFailure = {
                        callback.onError(it.message ?: "Unknown transcription error")
                    }
                )
            } catch (e: IOException) {
                Log.e(TAG, "Error during WAV conversion or transcription: ${e.message}", e)
                callback.onError("Failed to process audio file.")
            } finally {
                rawFile?.delete()
                if (wavFile.exists()) {
                    wavFile.delete()
                }
            }
        }
    }

    fun cancelRecording() {
        stopRecording(null)
    }

    @Throws(IOException::class)
    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        FileOutputStream(wavFile).use { fos ->
            val sampleRate = SAMPLE_RATE
            val channels = 1
            val bitsPerSample = 16
            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
            val dataSize = pcmFile.length()
            val totalSize = dataSize + 36

            fos.write(
                byteArrayOf(
                    'R'.code.toByte(),
                    'I'.code.toByte(),
                    'F'.code.toByte(),
                    'F'.code.toByte()
                )
            )
            fos.write(intToByteArray(totalSize.toInt()))
            fos.write(
                byteArrayOf(
                    'W'.code.toByte(),
                    'A'.code.toByte(),
                    'V'.code.toByte(),
                    'E'.code.toByte()
                )
            )
            fos.write(
                byteArrayOf(
                    'f'.code.toByte(),
                    'm'.code.toByte(),
                    't'.code.toByte(),
                    ' '.code.toByte()
                )
            )
            fos.write(intToByteArray(16))
            fos.write(shortToByteArray(1.toShort()))
            fos.write(shortToByteArray(channels.toShort()))
            fos.write(intToByteArray(sampleRate))
            fos.write(intToByteArray(byteRate.toInt()))
            fos.write(shortToByteArray((channels * bitsPerSample / 8).toShort()))
            fos.write(shortToByteArray(bitsPerSample.toShort()))
            fos.write(
                byteArrayOf(
                    'd'.code.toByte(),
                    'a'.code.toByte(),
                    't'.code.toByte(),
                    'a'.code.toByte()
                )
            )
            fos.write(intToByteArray(dataSize.toInt()))
            FileInputStream(pcmFile).use { fis ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while ((fis.read(buffer).also { bytesRead = it }) != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 0).toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() shr 0).toByte(),
            (value.toInt() shr 8).toByte()
        )
    }

    fun cleanup() {
        recorderThread?.quitSafely()
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}