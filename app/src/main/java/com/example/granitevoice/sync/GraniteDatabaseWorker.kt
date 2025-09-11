package com.example.granitevoice.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.granitevoice.domain.repository.MasterItemRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class GraniteDatabaseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MasterItemRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("MasterItemSync", "GraniteDatabaseWorker started.")
        try {
            repository.syncMasterItems()
            Log.d("MasterItemSync", "GraniteDatabaseWorker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MasterItemSync", "GraniteDatabaseWorker failed.", e)
            Result.failure()
        }
    }
}