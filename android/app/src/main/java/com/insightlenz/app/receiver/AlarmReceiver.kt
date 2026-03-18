package com.insightlenz.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.insightlenz.app.service.BackgroundIntelligenceService

/**
 * Receives scheduled alarms from AlarmManager and routes them to
 * BackgroundIntelligenceService to fetch + deliver the brief/review.
 *
 * Android wakes the device, fires this receiver, we spin up the service,
 * service fetches from backend, posts a rich notification.
 * User wakes up at 6am to a morning brief waiting for them.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Only handle our own alarm actions
        if (action !in listOf(
                BackgroundIntelligenceService.ACTION_MORNING_BRIEF,
                BackgroundIntelligenceService.ACTION_EVENING_REVIEW,
                BackgroundIntelligenceService.ACTION_WEEKLY_REVIEW
            )
        ) return

        // For weekly review: only fire on Fridays
        if (action == BackgroundIntelligenceService.ACTION_WEEKLY_REVIEW) {
            val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            if (dayOfWeek != java.util.Calendar.FRIDAY) return
        }

        val serviceIntent = BackgroundIntelligenceService.startIntent(context).apply {
            this.action = action
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
