package com.insightlenz.app.launcher

import android.graphics.drawable.Drawable

/**
 * Represents a single installed, launchable app.
 * Lightweight — only what the launcher UI needs.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
)
