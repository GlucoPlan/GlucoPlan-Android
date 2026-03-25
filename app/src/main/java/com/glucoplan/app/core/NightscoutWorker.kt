package com.glucoplan.app.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.glucoplan.app.data.repository.GlucoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class NightscoutWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: GlucoRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = repo.getSettings()
            if (!settings.nsEnabled || settings.nsUrl.isBlank()) return Result.success()
            val client = NightscoutClient(settings.nsUrl, settings.nsApiSecret)
            client.getLatestReading() // Just fetch to verify connection; UI reads via foreground polling
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "nightscout_poll"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NightscoutWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
