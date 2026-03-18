package com.insightlenz.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.insightlenz.app.service.BackgroundIntelligenceService

/**
 * Restarts BackgroundIntelligenceService after a phone reboot.
 *
 * Without this, the service and all its alarms disappear when the user
 * restarts their phone. With this, InsightLenz comes back alive automatically.
 *
 * Requires: RECEIVE_BOOT_COMPLETED permission in manifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"  // HTC devices
        ) return

        val serviceIntent = BackgroundIntelligenceService.startIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
