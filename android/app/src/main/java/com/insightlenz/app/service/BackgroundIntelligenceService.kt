package com.insightlenz.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.insightlenz.app.MainActivity
import com.insightlenz.app.api.ApiClient
import com.insightlenz.app.receiver.AlarmReceiver
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * The OS heartbeat. Runs as a ForegroundService 24/7.
 *
 * This is what separates InsightLenz from a chatbot — it stays alive,
 * watches the clock, and reaches out to the user unprompted.
 *
 * Responsibilities:
 *   - Maintain a persistent (silent) notification so Android doesn't kill us
 *   - Schedule morning brief (6am) and evening review (9pm) alarms
 *   - Execute brief/review fetches when triggered by AlarmReceiver
 *   - Survive phone restarts via BootReceiver
 */
class BackgroundIntelligenceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIF_ID_STATUS, buildStatusNotification("Active — watching your day"))
        scheduleRecurringAlarms()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MORNING_BRIEF   -> fetchAndNotify(BriefType.MORNING)
            ACTION_EVENING_REVIEW  -> fetchAndNotify(BriefType.EVENING)
            ACTION_WEEKLY_REVIEW   -> fetchAndNotify(BriefType.WEEKLY)
        }
        // START_STICKY: if Android kills us, restart with the last intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Brief / Review fetching ────────────────────────────────────────────────

    private enum class BriefType { MORNING, EVENING, WEEKLY }

    private fun fetchAndNotify(type: BriefType) {
        serviceScope.launch {
            try {
                val response = when (type) {
                    BriefType.MORNING -> ApiClient.service.morningBrief()
                    BriefType.EVENING -> ApiClient.service.eveningReview()
                    BriefType.WEEKLY  -> ApiClient.service.weeklyReview()
                }
                val (title, notifId) = when (type) {
                    BriefType.MORNING -> Pair("🌅 Morning Brief", NOTIF_ID_MORNING)
                    BriefType.EVENING -> Pair("🌙 Evening Review", NOTIF_ID_EVENING)
                    BriefType.WEEKLY  -> Pair("📊 Weekly Review", NOTIF_ID_WEEKLY)
                }
                showIntelligenceNotification(title, response.response, notifId)
            } catch (e: Exception) {
                // Backend unreachable — silent fail, retry on next alarm cycle
                // Don't crash the service over a network hiccup
            }
        }
    }

    // ── Alarm scheduling ──────────────────────────────────────────────────────

    private fun scheduleRecurringAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDaily(alarmManager, hour = 6,  minute = 0, action = ACTION_MORNING_BRIEF,  requestCode = REQ_MORNING)
        scheduleDaily(alarmManager, hour = 21, minute = 0, action = ACTION_EVENING_REVIEW, requestCode = REQ_EVENING)
        // Weekly review fires every Friday at 5pm — handled via day-of-week check in receiver
        scheduleDaily(alarmManager, hour = 17, minute = 0, action = ACTION_WEEKLY_REVIEW,  requestCode = REQ_WEEKLY)
    }

    private fun scheduleDaily(
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        action: String,
        requestCode: Int
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, AlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use inexactRepeating for battery efficiency — Android may shift by ~15 min
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    // ── Notification builders ─────────────────────────────────────────────────

    private fun buildStatusNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle("InsightLenz")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun showIntelligenceNotification(title: String, content: String, notifId: Int) {
        val intent = PendingIntent.getActivity(
            this, notifId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val preview = if (content.length > 120) content.take(120) + "…" else content

        val notification = NotificationCompat.Builder(this, CHANNEL_INTELLIGENCE)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
    }

    // ── Notification channels ─────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Silent status channel — just keeps the foreground service alive
        NotificationChannel(
            CHANNEL_STATUS,
            "InsightLenz Status",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Background service indicator — always silent"
            setShowBadge(false)
            manager.createNotificationChannel(this)
        }

        // High-priority channel for briefs, reviews, and nudges
        NotificationChannel(
            CHANNEL_INTELLIGENCE,
            "Intelligence Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Morning briefs, evening reviews, and focus nudges"
            enableVibration(true)
            manager.createNotificationChannel(this)
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        // Notification IDs
        const val NOTIF_ID_STATUS   = 1
        const val NOTIF_ID_MORNING  = 2
        const val NOTIF_ID_EVENING  = 3
        const val NOTIF_ID_WEEKLY   = 4
        const val NOTIF_ID_NUDGE    = 10

        // Notification channels
        const val CHANNEL_STATUS       = "insightlenz_status"
        const val CHANNEL_INTELLIGENCE = "insightlenz_intelligence"

        // Intent actions
        const val ACTION_MORNING_BRIEF  = "com.insightlenz.ACTION_MORNING_BRIEF"
        const val ACTION_EVENING_REVIEW = "com.insightlenz.ACTION_EVENING_REVIEW"
        const val ACTION_WEEKLY_REVIEW  = "com.insightlenz.ACTION_WEEKLY_REVIEW"

        // Alarm request codes
        const val REQ_MORNING = 100
        const val REQ_EVENING = 101
        const val REQ_WEEKLY  = 102

        fun startIntent(context: Context) =
            Intent(context, BackgroundIntelligenceService::class.java)
    }
}
