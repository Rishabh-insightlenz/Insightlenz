package com.insightlenz.app.api

// ── Requests ─────────────────────────────────────────────────────────────────

data class ChatRequest(
    val message: String,
    val session_id: String = "android"
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class ChatResponse(
    val response: String,
    val provider: String,
    val model: String
)

data class HealthResponse(
    val status: String,
    val service: String,
    val ai_provider: String,
    val ai_model: String,
    val database: String
)

// ── Context ────────────────────────────────────────────────────────────────────

data class PriorityResponse(
    val task: String,
    val why: String?,
    val week_of: String?
)

data class UserContextResponse(
    val name: String?,
    val current_priorities: List<PriorityResponse>?,  // matches backend field name
    val values: List<Any>?,
    val goals: List<Any>?
) {
    // Convenience alias
    val priorities: List<PriorityResponse>? get() = current_priorities
}

// ── App Usage Sync ─────────────────────────────────────────────────────────────

data class AppUsageEntry(
    val package_name: String,
    val app_name: String,
    val total_time_ms: Long
)

data class AppUsageSyncRequest(
    val stats: List<AppUsageEntry>
)

data class AppUsageSyncResponse(
    val synced: Int
)
