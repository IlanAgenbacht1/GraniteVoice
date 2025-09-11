package com.example.granitevoice.ui.main_screen

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.granitevoice.AudioRecorderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private val audioRecorderManager = AudioRecorderManager(application)

    sealed class UiState {
        data object Idle : UiState()
        data object Listening : UiState()
        data object Processing : UiState()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        _uiState.update { UiState.Listening }
        audioRecorderManager.startRecording()
    }

    fun stopRecording() {
        _uiState.update { UiState.Processing }
        audioRecorderManager.stopRecording(object : AudioRecorderManager.RecordingCallback {
            override fun onTranscriptionComplete(transcription: String?) {
                viewModelScope.launch {
                    _snackbarMessage.value = "Transcription: $transcription"
                    _uiState.update { UiState.Idle }
                }
            }

            override fun onError(error: String) {
                viewModelScope.launch {
                    _snackbarMessage.value = "Error: $error"
                    _uiState.update { UiState.Idle }
                }
            }
        })
    }

    fun cancelRecording() {
        audioRecorderManager.cancelRecording()
        _uiState.update { UiState.Idle }
    }

    fun snackbarShown() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.cleanup()
    }
}