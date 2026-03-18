package com.insightlenz.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.insightlenz.app.api.ApiClient
import com.insightlenz.app.api.ChatRequest
import com.insightlenz.app.service.AppLaunchDetector
import com.insightlenz.app.usage.AppUsageStat
import com.insightlenz.app.usage.UsageStatsHelper
import com.insightlenz.app.worker.UsageSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── UI Models ──────────────────────────────────────────────────────────────────

data class Message(
    val content: String,
    val isUser: Boolean,
    val source: String = "chat"   // "chat" | "morning_brief" | "evening_review"
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
    val backendStatus: String = "Connecting...",
    val isConnected: Boolean = false,

    // Feed data
    val topPriority: String? = null,
    val priorityContext: String? = null,
    val usageStats: List<AppUsageStat> = emptyList(),
    val hasUsagePermission: Boolean = false,
    val lastInsight: String? = null,

    // Screen state
    val currentScreen: Screen = Screen.FEED
)

enum class Screen { FEED, CHAT }

// ── Session ID helpers ─────────────────────────────────────────────────────────

/**
 * Session ID strategy: one session per calendar day.
 * Format: "android_2026-03-18"
 * This means:
 *   - History persists across app kills within the same day
 *   - Each new day starts fresh (but old sessions remain searchable in history)
 *   - Backend groups conversations cleanly by day
 */
private fun todaySessionId(): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return "android_$today"
}

// ── ViewModel ──────────────────────────────────────────────────────────────────

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val usageHelper = UsageStatsHelper(application)

    // Session ID is fixed per day — persists across app kills
    val sessionId: String = todaySessionId()

    init {
        checkConnection()
        loadUsageStats()
        loadContext()
        loadChatHistory()   // Restore today's conversation from backend
    }

    // ── Connection ─────────────────────────────────────────────────────────────

    fun checkConnection() {
        viewModelScope.launch {
            try {
                val health = ApiClient.service.health()
                _uiState.value = _uiState.value.copy(
                    isConnected = true,
                    backendStatus = "${health.ai_model} · DB ${health.database}",
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    backendStatus = "Backend offline"
                )
            }
        }
    }

    // ── Feed data ──────────────────────────────────────────────────────────────

    fun loadUsageStats() {
        viewModelScope.launch {
            val hasPermission = usageHelper.hasPermission()
            val stats = if (hasPermission) usageHelper.getTodayUsage() else emptyList()
            _uiState.value = _uiState.value.copy(
                hasUsagePermission = hasPermission,
                usageStats = stats
            )
        }
    }

    fun loadContext() {
        viewModelScope.launch {
            try {
                val context = ApiClient.service.getUserContext()
                val priority = context.priorities?.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    topPriority = priority?.task,
                    priorityContext = priority?.why
                )
                // Persist for the accessibility overlay to read
                priority?.task?.let { AppLaunchDetector.savePriority(getApplication(), it) }
            } catch (e: Exception) {
                // Context not set yet — that's fine
            }
        }
    }

    fun refreshFeed() {
        // Trigger an immediate sync to backend so AI has latest phone data
        UsageSyncWorker.syncNow(getApplication())
        loadUsageStats()
        loadContext()
        checkConnection()
    }

    /**
     * Load today's chat history from backend on startup.
     * This means the conversation is fully restored even after the app is killed.
     * Only loads the current day's session — older history is in the history screen.
     */
    fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val history = ApiClient.service.getChatHistory(
                    sessionId = sessionId,
                    limit = 50
                )
                if (history.isNotEmpty()) {
                    val messages = history
                        // Filter out system triggers (morning brief, etc. shown inline already)
                        .filter { !it.content.startsWith("[") }
                        .map { msg ->
                            Message(
                                content = msg.content,
                                isUser = msg.role == "user",
                                source = msg.source
                            )
                        }
                    if (messages.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            // Last assistant message becomes the feed insight
                            lastInsight = messages.lastOrNull { !it.isUser }?.content
                                ?.take(120)
                                ?.let { if (it.length == 120) "$it…" else it }
                        )
                    }
                }
            } catch (e: Exception) {
                // History load failure is silent — app still works, just no restored history
            }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun openChat() {
        _uiState.value = _uiState.value.copy(currentScreen = Screen.CHAT)
    }

    fun openFeed() {
        _uiState.value = _uiState.value.copy(currentScreen = Screen.FEED)
    }

    // ── Chat ───────────────────────────────────────────────────────────────────

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val message = _uiState.value.inputText.trim()
        if (message.isBlank()) return

        appendMessage(Message(content = message, isUser = true))
        _uiState.value = _uiState.value.copy(
            inputText = "",
            isLoading = true,
            error = null,
            currentScreen = Screen.CHAT
        )

        viewModelScope.launch {
            try {
                val response = ApiClient.service.chat(ChatRequest(message = message, session_id = sessionId))
                val reply = response.response
                appendMessage(Message(content = reply, isUser = false))
                // Surface last AI response in the feed as an insight
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastInsight = if (reply.length > 120) reply.take(120) + "…" else reply
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun getMorningBrief() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val response = ApiClient.service.morningBrief()
                val brief = "🌅  Morning Brief\n\n${response.response}"
                appendMessage(Message(content = brief, isUser = false, source = "morning_brief"))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastInsight = response.response.take(120) + "…",
                    currentScreen = Screen.CHAT
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Error: ${e.message}")
            }
        }
    }

    fun getEveningReview() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val response = ApiClient.service.eveningReview()
                val review = "🌙  Evening Review\n\n${response.response}"
                appendMessage(Message(content = review, isUser = false, source = "evening_review"))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastInsight = response.response.take(120) + "…",
                    currentScreen = Screen.CHAT
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Error: ${e.message}")
            }
        }
    }

    private fun appendMessage(message: Message) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }
}
