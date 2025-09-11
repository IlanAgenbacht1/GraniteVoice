package com.example.granitevoice.domain.repository

import java.io.File

interface AudioTranscriptionRepository {
    suspend fun transcribeAudio(audioFile: File): Result<String>
}