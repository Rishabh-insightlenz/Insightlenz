package com.insightlenz.app

import android.app.Application
import android.content.Intent
import android.os.Build
import com.insightlenz.app.service.BackgroundIntelligenceService
import com.insightlenz.app.worker.UsageSyncWorker

class InsightLenzApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startBackgroundService()
        scheduleUsageSync()
    }

    private fun startBackgroundService() {
        val intent = BackgroundIntelligenceService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun scheduleUsageSync() {
        // Schedule periodic sync (every 30 min) — WorkManager handles restarts
        UsageSyncWorker.schedule(this)
        // Fire an immediate sync so the backend has data right away
        UsageSyncWorker.syncNow(this)
    }
}
