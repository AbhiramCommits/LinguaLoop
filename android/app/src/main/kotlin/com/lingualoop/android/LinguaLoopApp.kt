package com.lingualoop.android

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lingualoop.android.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LinguaLoopApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Flush anything queued while offline on the next connectivity window.
        SyncWorker.enqueue(this)
    }

    companion object {
        fun syncPending(context: Context) = SyncWorker.enqueue(context)
    }
}
