package com.lingualoop.android.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.db.PendingOpDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: LinguaLoopApi,
    private val pendingOpDao: PendingOpDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val flusher = OpFlusher(api, pendingOpDao)
        val outcome = flusher.flush()
        return if (outcome.remaining == 0 || !outcome.stopped) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "lingualoop-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
