package com.example.drivertracking.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.drivertracking.data.repository.TrackingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: TrackingRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val success = repository.syncOfflineLocations()
        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
