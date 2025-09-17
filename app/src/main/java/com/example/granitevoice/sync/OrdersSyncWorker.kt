package com.example.granitevoice.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.granitevoice.data.repository.OrderRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker for syncing orders from the Granite API in the background
 */
@HiltWorker
class OrdersSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderRepository: OrderRepositoryImpl
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "OrdersSyncWorker"
        const val WORK_NAME = "orders_sync"

        /**
         * Schedule periodic orders sync
         */
        fun schedulePeriodicSync(workManager: WorkManager) {
            val syncRequest = PeriodicWorkRequestBuilder<OrdersSyncWorker>(
                repeatInterval = 15, // Every 15 minutes
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5, // Allow 5 minutes flexibility
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("orders")
                .addTag("sync")
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )

            Log.d(TAG, "Scheduled periodic orders sync")
        }

        /**
         * Schedule immediate orders sync
         */
        fun scheduleImmediateSync(workManager: WorkManager) {
            val syncRequest = OneTimeWorkRequestBuilder<OrdersSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("orders")
                .addTag("sync")
                .addTag("immediate")
                .build()

            workManager.enqueueUniqueWork(
                "orders_sync_immediate",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )

            Log.d(TAG, "Scheduled immediate orders sync")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "OrdersSyncWorker started")
        Log.d(TAG, "Worker run attempt: ${runAttemptCount + 1}")

        val startTime = System.currentTimeMillis()

        return try {
            // Perform the sync
            val syncResult = orderRepository.syncOrders(forceSync = false)

            syncResult.fold(
                onSuccess = {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "OrdersSyncWorker completed successfully in ${duration}ms")
                    Result.success()
                },
                onFailure = { exception ->
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "OrdersSyncWorker failed after ${duration}ms", exception)

                    // Determine if we should retry
                    when (exception) {
                        is java.net.UnknownHostException,
                        is java.net.SocketTimeoutException,
                        is java.net.ConnectException -> {
                            Log.w(
                                TAG,
                                "Network error during sync - will retry: ${exception.message}"
                            )
                            Result.retry()
                        }
                        else -> {
                            Log.e(TAG, "Non-recoverable sync error: ${exception.message}")
                            Result.failure()
                        }
                    }
                }
            )

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "OrdersSyncWorker encountered unexpected error after ${duration}ms", e)
            Result.failure()
        }
    }
}