package com.insightlenz.app.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long
) {
    /** Human-readable duration: "1h 23m" or "45m" */
    val displayTime: String get() {
        val totalMin = (totalTimeMs / 60_000).toInt()
        val hours = totalMin / 60
        val mins = totalMin % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0             -> "${hours}h"
            else                  -> "${mins}m"
        }
    }
}

class UsageStatsHelper(private val context: Context) {

    /**
     * Returns top apps used today, sorted by time descending.
     * Excludes system/launcher packages and InsightLenz itself.
     * Returns empty list if permission not granted.
     */
    fun getTodayUsage(limit: Int = 8): List<AppUsageStat> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            System.currentTimeMillis()
        ) ?: return emptyList()

        val pm = context.packageManager

        return stats
            .filter { it.totalTimeInForeground > 60_000 }   // at least 1 min
            .filter { it.packageName != context.packageName }
            .filter { !it.packageName.startsWith("com.android.systemui") }
            .filter { !it.packageName.startsWith("com.samsung.android.launcher") }
            .filter { !it.packageName.startsWith("com.google.android.inputmethod") }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .mapNotNull { stat ->
                val appName = resolveAppName(pm, stat.packageName) ?: return@mapNotNull null
                AppUsageStat(
                    packageName = stat.packageName,
                    appName = appName,
                    totalTimeMs = stat.totalTimeInForeground
                )
            }
    }

    /**
     * Returns total screen time today in milliseconds.
     */
    fun getTotalScreenTimeMs(): Long {
        return getTodayUsage(limit = 50).sumOf { it.totalTimeMs }
    }

    /**
     * Check if PACKAGE_USAGE_STATS permission is granted.
     * This permission requires user to manually enable it in:
     * Settings → Apps → Special app access → Usage access
     */
    fun hasPermission(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 1000 * 60 * 2,
            now
        )
        // If stats is null or empty AND it's not 2am (when all stats are empty), permission is denied
        return stats != null && stats.isNotEmpty()
    }

    private fun resolveAppName(pm: PackageManager, packageName: String): String? {
        return try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // App uninstalled mid-query, skip it
            null
        }
    }

    companion object {
        /** Apps we consider "reactive" / distraction-prone */
        val REACTIVE_APPS = setOf(
            "com.instagram.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.google.android.youtube"
        )
    }
}
