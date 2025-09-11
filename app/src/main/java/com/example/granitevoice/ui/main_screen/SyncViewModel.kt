package com.example.granitevoice.ui.main_screen

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.granitevoice.sync.GraniteDatabaseWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    // This state is specific to the sync process
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    fun startSync() {
        val workRequest = OneTimeWorkRequestBuilder<GraniteDatabaseWorker>().build()
        WorkManager.getInstance(application).enqueue(workRequest)
        _snackbarMessage.value = "Database sync started..."
    }

    fun snackbarShown() {
        _snackbarMessage.value = null
    }
}