package com.insightlenz.app.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Reads all installed, launchable apps from the system.
 * Also manages the dock — the pinned apps at the bottom of the home screen.
 *
 * The dock is persisted in SharedPreferences so it survives reboots.
 * Default dock: Phone, Messages, Camera, Chrome (or whatever is actually installed).
 */
class AppRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private val pm: PackageManager = context.packageManager

    // ── Installed apps ──────────────────────────────────────────────────────────

    /**
     * Returns all installed apps that have a launcher intent.
     * Sorted alphabetically. Excludes InsightLenz itself.
     */
    fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
            .filter { it.activityInfo.packageName != context.packageName } // exclude self
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm),
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Launch an app by package name.
     * If the app isn't installed or can't be launched, silently fails.
     */
    fun launchApp(packageName: String) {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ── Dock management ─────────────────────────────────────────────────────────

    companion object {
        private const val PREF_DOCK = "dock_packages"

        // Default dock app candidates — we pick whichever are actually installed
        private val DOCK_CANDIDATES = listOf(
            // Phone
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            // Messages / SMS
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            // Camera
            "com.google.android.GoogleCamera",
            "com.android.camera2",
            "com.samsung.android.camera",
            // Chrome / Browser
            "com.android.chrome",
            "org.mozilla.firefox",
            // Instagram (common enough to be a dock app)
            "com.instagram.android",
        )
    }

    /**
     * Returns the dock apps in order.
     * If no custom dock has been saved, returns the first 5 installed defaults.
     */
    fun getDockPackages(): List<String> {
        val saved = prefs.getString(PREF_DOCK, null)
        if (saved != null) {
            return saved.split(",").filter { it.isNotBlank() }
        }
        // Build default dock from candidates that are actually installed
        return DOCK_CANDIDATES
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .take(5)
    }

    /**
     * Resolve a list of package names to AppInfo objects (with icons).
     * Filters out any packages that are no longer installed.
     */
    fun resolveApps(packages: List<String>): List<AppInfo> {
        return packages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(pkg),
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null // app was uninstalled
            }
        }
    }

    /**
     * Save a new dock configuration.
     * @param packages ordered list of package names for the dock (max 5)
     */
    fun saveDock(packages: List<String>) {
        prefs.edit().putString(PREF_DOCK, packages.take(5).joinToString(",")).apply()
    }
}
