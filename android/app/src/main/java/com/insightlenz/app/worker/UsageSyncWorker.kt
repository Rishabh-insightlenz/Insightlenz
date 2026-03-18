package com.insightlenz.app.worker

import android.content.Context
import androidx.work.*
import com.insightlenz.app.api.ApiClient
import com.insightlenz.app.api.AppUsageEntry
import com.insightlenz.app.api.AppUsageSyncRequest
import com.insightlenz.app.usage.UsageStatsHelper
import java.util.concurrent.TimeUnit

/**
 * Runs every 30 minutes in the background.
 * Reads today's app usage from UsageStatsManager and POSTs it to the backend.
 *
 * After this runs, the AI knows:
 *   "You've spent 45 min on Instagram today."
 *   "2h on Chrome, 18 min on WhatsApp."
 *
 * The morning brief, evening review, and every chat response has this data.
 * InsightLenz stops being a static profile and becomes a live mirror of your day.
 */
class UsageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val helper = UsageStatsHelper(applicationContext)

        // Don't even try if permission wasn't granted
        if (!helper.hasPermission()) {
            return Result.success()  // not a failure — just not enabled yet
        }

        val stats = helper.getTodayUsage(limit = 15)
        if (stats.isEmpty()) return Result.success()

        return try {
            val entries = stats.map { stat ->
                AppUsageEntry(
                    package_name = stat.packageName,
                    app_name = stat.appName,
                    total_time_ms = stat.totalTimeMs
                )
            }
            ApiClient.service.syncAppUsage(AppUsageSyncRequest(stats = entries))
            Result.success()
        } catch (e: Exception) {
            // Network unavailable or backend down — retry later
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "usage_sync"

        /**
         * Schedule periodic usage sync.
         * Called once from InsightLenzApp on startup.
         * WorkManager ensures it keeps running even after restarts.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // don't reset timer if already scheduled
                request
            )
        }

        /**
         * Trigger an immediate one-off sync.
         * Useful after the user opens the feed — shows fresh data right away.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
