package com.charles.livecaptionn.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that asks the [UpdateChecker] whether a newer
 * release exists and, if so, surfaces a system notification via
 * [UpdateNotifier]. Designed to run roughly twice a day, only when the device
 * has network. Idempotent — [schedule] uses KEEP so re-enqueueing on every app
 * start is a no-op after the first run.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val checker = UpdateChecker()
        val info = checker.check() ?: return Result.success()
        UpdateNotifier(applicationContext).notifyIfNew(info)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "livecaption_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
