package com.insightlenz.app.launcher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks daily Groq API request usage in SharedPreferences.
 *
 * Groq free-tier limits (llama-3.3-70b-versatile as of early 2026):
 *   - 30 requests / minute
 *   - 14,400 requests / day
 *   - 500,000 tokens / day
 *
 * We track request count only (token counting would require parsing API responses).
 * Reset automatically at midnight.
 */
object AiUsageTracker {

    private const val PREFS = "insightlenz_ai_usage"
    private const val KEY_COUNT = "requests_today"
    private const val KEY_DATE  = "usage_date"

    // Groq free tier daily request limit for llama-3.3-70b-versatile
    const val DAILY_LIMIT = 14_400
    // Show a softer "personal budget" so users know when to be careful
    const val PERSONAL_BUDGET = 200

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun increment(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, "")
        val todayStr  = today()

        val current = if (savedDate == todayStr) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_DATE, todayStr)
            .putInt(KEY_COUNT, current + 1)
            .apply()
    }

    fun getTodayCount(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, "")
        return if (savedDate == today()) prefs.getInt(KEY_COUNT, 0) else 0
    }

    /** 0.0 … 1.0 fraction of PERSONAL_BUDGET consumed today */
    fun getBudgetFraction(ctx: Context): Float =
        (getTodayCount(ctx).toFloat() / PERSONAL_BUDGET).coerceAtMost(1f)

    /** Human-readable summary: "12 / 200 requests today" */
    fun getSummary(ctx: Context): String {
        val count = getTodayCount(ctx)
        return "$count / $PERSONAL_BUDGET requests today"
    }
}
