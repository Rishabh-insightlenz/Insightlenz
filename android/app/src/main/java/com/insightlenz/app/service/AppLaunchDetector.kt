package com.insightlenz.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.insightlenz.app.MainActivity

/**
 * The Jarvis layer — an ambient overlay on every app.
 *
 * When you switch to any app, a thin strip appears at the top showing:
 * - Your current Priority #1
 * - How long you've been on this app (if reactive)
 * - A nudge if you've been on a distraction app too long
 *
 * Auto-dismisses after 4 seconds. Tap to open InsightLenz.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY — no SYSTEM_ALERT_WINDOW permission needed,
 * but requires the Accessibility service to be enabled by the user.
 */
class AppLaunchDetector : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentPackage = ""
    private var packageOpenTime = 0L
    private val nudgeCooldown = mutableMapOf<String, Long>()

    private val reactiveApps = mapOf(
        "com.instagram.android"       to "Instagram",
        "com.twitter.android"         to "X (Twitter)",
        "com.zhiliaoapp.musically"    to "TikTok",
        "com.ss.android.ugc.trill"    to "TikTok",
        "com.facebook.katana"         to "Facebook",
        "com.snapchat.android"        to "Snapchat",
        "com.reddit.frontpage"        to "Reddit",
        "com.linkedin.android"        to "LinkedIn",
        "com.google.android.youtube"  to "YouTube"
    )

    private val nudgeThresholdMs = 10 * 60 * 1000L
    private val cooldownMs = 45 * 60 * 1000L
    private val overlayDismissMs = 5_000L     // auto-hide after 5 seconds

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPackage = event.packageName?.toString() ?: return
        if (newPackage == currentPackage) return
        if (newPackage.startsWith("com.android.systemui")) return
        if (newPackage == "com.insightlenz.app") {
            removeOverlay()
            return
        }

        val now = System.currentTimeMillis()

        // Check time on the app we're leaving
        if (currentPackage in reactiveApps && packageOpenTime > 0) {
            val timeSpentMs = now - packageOpenTime
            if (timeSpentMs >= nudgeThresholdMs) {
                maybeSendNudge(currentPackage, timeSpentMs)
            }
        }

        currentPackage = newPackage
        packageOpenTime = now

        // Show ambient overlay on every app switch (not just reactive apps)
        val appName = reactiveApps[newPackage]
        showOverlay(newPackage, appName)
    }

    override fun onInterrupt() {
        handler.post {
            removeOverlay()
            currentPackage = ""
            packageOpenTime = 0L
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.post { removeOverlay() }
    }

    // ── Overlay ────────────────────────────────────────────────────────────────

    private fun showOverlay(packageName: String, appDisplayName: String?) {
        handler.post {
            removeOverlay()

            val priority = getSavedPriority()
            val isReactive = packageName in reactiveApps

            // Always show on reactive apps; on others only if we have a priority
            if (priority.isBlank() && !isReactive) return@post

            val label = buildOverlayText(
                priority = priority.ifBlank { "Open InsightLenz to set priorities" },
                appName = appDisplayName,
                isReactive = isReactive
            )

            val layout = buildOverlayLayout(label, isReactive)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            layout.setOnClickListener {
                removeOverlay()
                val intent = Intent(this@AppLaunchDetector, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }

            try {
                windowManager?.addView(layout, params)
                overlayView = layout
                // Auto-dismiss
                handler.postDelayed({ removeOverlay() }, overlayDismissMs)
            } catch (e: Exception) {
                // WindowManager may reject in rare cases (e.g. locked screen)
            }
        }
    }

    private fun buildOverlayLayout(label: String, isReactive: Boolean): LinearLayout {
        val bgColor = if (isReactive)
            Color.argb(220, 40, 10, 10)
        else
            Color.argb(220, 10, 14, 26)

        val accentColor = if (isReactive)
            Color.parseColor("#FF5252")
        else
            Color.parseColor("#4FC3F7")

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Accent strip on left
            addView(View(context).apply {
                setBackgroundColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dpToPx(12)
                }
            })

            // Text
            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#E0E0E0"))
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Dismiss hint
            addView(TextView(context).apply {
                text = "✕"
                textSize = 12f
                setTextColor(Color.parseColor("#555555"))
                setPadding(dpToPx(8), 0, 0, 0)
            })
        }
    }

    private fun buildOverlayText(priority: String, appName: String?, isReactive: Boolean): String {
        return if (isReactive && appName != null) {
            "⚠  $appName  ·  Priority: $priority"
        } else {
            "⚡  $priority"
        }
    }

    private fun removeOverlay() {
        // Synchronous — must only be called from the main thread (handler.post context).
        // Do NOT wrap in handler.post here; callers are already on the main thread,
        // and nesting a post would queue the removal AFTER the new view is added,
        // making the overlay vanish within one frame.
        handler.removeCallbacksAndMessages(null)
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    // ── Nudge (notification fallback) ─────────────────────────────────────────

    private fun maybeSendNudge(packageName: String, timeSpentMs: Long) {
        val now = System.currentTimeMillis()
        val lastNudge = nudgeCooldown[packageName] ?: 0L
        if (now - lastNudge < cooldownMs) return
        nudgeCooldown[packageName] = now

        val appName = reactiveApps[packageName] ?: return
        val minutes = (timeSpentMs / 60_000).toInt().coerceAtLeast(1)

        // Show overlay nudge — already handled above, plus a longer overlay
        handler.post {
            showOverlay(packageName, "$appName  ·  ${minutes}m so far")
        }
    }

    // ── SharedPreferences bridge ───────────────────────────────────────────────

    /** Priority is stored by the main app whenever context is loaded */
    private fun getSavedPriority(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRIORITY, "") ?: ""
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val PREFS_NAME = "insightlenz_prefs"
        const val KEY_PRIORITY = "top_priority"

        /** Call this from ViewModel after loading context so the overlay can read it */
        fun savePriority(context: Context, priority: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PRIORITY, priority)
                .apply()
        }
    }
}
