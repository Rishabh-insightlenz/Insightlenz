package com.insightlenz.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.insightlenz.app.ui.screens.LockActivity

/**
 * Listens for the screen turning on and launches the Jarvis lock screen.
 *
 * Registered dynamically in MainActivity (not in the manifest) because
 * ACTION_SCREEN_ON cannot be received by manifest-registered receivers
 * on Android 3.1+.
 *
 * Registration lifecycle:
 *   onCreate  → register
 *   onDestroy → unregister
 */
class ScreenOnReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            val lock = Intent(context, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(lock)
        }
    }
}
