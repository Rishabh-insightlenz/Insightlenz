package com.insightlenz.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.insightlenz.app.api.ApiClient
import com.insightlenz.app.api.ChatRequest
import com.insightlenz.app.launcher.AppInfo
import com.insightlenz.app.launcher.AppRepository
import com.insightlenz.app.service.AppLaunchDetector
import com.insightlenz.app.usage.AppUsageStat
import com.insightlenz.app.usage.UsageStatsHelper
import com.insightlenz.app.worker.UsageSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val currentScreen: Screen = Screen.FEED,

    // Launcher state
    val showAppDrawer: Boolean = false,
    val dockApps: List<AppInfo> = emptyList(),

    // Inline Jarvis reply on home screen (shown above the bar, fades after a few seconds)
    val lastJarvisReply: String? = null,
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

    // Separate flow for the full app list — not in UiState since Drawables aren't serialisable
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val usageHelper = UsageStatsHelper(application)
    private val appRepo = AppRepository(application)

    // Session ID is fixed per day — persists across app kills
    val sessionId: String = todaySessionId()

    init {
        checkConnection()
        loadUsageStats()
        loadContext()
        loadChatHistory()   // Restore today's conversation from backend
        loadInstalledApps() // Load launcher app list
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

    // ── Launcher ───────────────────────────────────────────────────────────────

    /**
     * Load all installed apps and dock on a background thread.
     * PackageManager queries are slow — must not run on the main thread.
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { appRepo.getInstalledApps() }
            val dockPkgs = withContext(Dispatchers.IO) { appRepo.getDockPackages() }
            val dockApps = withContext(Dispatchers.IO) { appRepo.resolveApps(dockPkgs) }
            _allApps.value = apps
            _uiState.value = _uiState.value.copy(dockApps = dockApps)
        }
    }

    fun openAppDrawer() {
        _uiState.value = _uiState.value.copy(showAppDrawer = true)
    }

    fun closeAppDrawer() {
        _uiState.value = _uiState.value.copy(showAppDrawer = false)
    }

    fun launchApp(packageName: String) {
        closeAppDrawer()
        viewModelScope.launch(Dispatchers.IO) {
            appRepo.launchApp(packageName)
        }
    }

    // ── Chat ───────────────────────────────────────────────────────────────────

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    /**
     * Main send handler.
     * 1. Detect app-launch intent ("open Instagram", "launch Maps", etc.)
     *    → launch the app + show brief inline confirmation
     * 2. Otherwise → send to Jarvis API
     *    → if on Feed, show response inline above the bar
     *    → if already in Chat, append to conversation
     */
    fun sendMessage() {
        val message = _uiState.value.inputText.trim()
        if (message.isBlank()) return

        _uiState.value = _uiState.value.copy(inputText = "", error = null)

        // ── Intent detection ────────────────────────────────────────────────
        val appTarget = detectAppLaunchIntent(message)
        if (appTarget != null) {
            val match = _allApps.value.firstOrNull {
                it.appName.contains(appTarget, ignoreCase = true) ||
                it.packageName.contains(appTarget, ignoreCase = true)
            }
            if (match != null) {
                // Show brief inline reply then launch
                _uiState.value = _uiState.value.copy(
                    lastJarvisReply = "Opening ${match.appName}…"
                )
                viewModelScope.launch {
                    kotlinx.coroutines.delay(600)
                    launchApp(match.packageName)
                    kotlinx.coroutines.delay(2000)
                    _uiState.value = _uiState.value.copy(lastJarvisReply = null)
                }
                return
            }
        }

        // ── Regular Jarvis message ──────────────────────────────────────────
        val onFeed = _uiState.value.currentScreen == Screen.FEED

        appendMessage(Message(content = message, isUser = true))
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            // Only navigate to chat if user explicitly went to chat screen
            currentScreen = if (onFeed) Screen.FEED else Screen.CHAT
        )

        viewModelScope.launch {
            try {
                val response = ApiClient.service.chat(ChatRequest(message = message, session_id = sessionId))
                val reply = response.response
                appendMessage(Message(content = reply, isUser = false))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastInsight = if (reply.length > 120) reply.take(120) + "…" else reply,
                    // Show inline on Feed; null in Chat (it's already in the bubble list)
                    lastJarvisReply = if (onFeed) reply.take(200).let { if (reply.length > 200) "$it…" else it } else null
                )
                // Auto-clear inline reply after 8 seconds
                if (onFeed) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(8000)
                        if (_uiState.value.currentScreen == Screen.FEED)
                            _uiState.value = _uiState.value.copy(lastJarvisReply = null)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Error: ${e.message}")
            }
        }
    }

    /**
     * Detect if a message is an app-launch intent.
     * Returns the app name/keyword to search for, or null if it's a regular message.
     * Examples: "open instagram" → "instagram"
     *           "launch maps" → "maps"
     *           "go to spotify" → "spotify"
     */
    private fun detectAppLaunchIntent(message: String): String? {
        val lower = message.lowercase().trim()
        val patterns = listOf("open ", "launch ", "go to ", "start ", "show me ", "take me to ")
        for (pattern in patterns) {
            if (lower.startsWith(pattern)) {
                return lower.removePrefix(pattern).trim().takeIf { it.isNotBlank() }
            }
        }
        // Single word that exactly matches an installed app name
        if (!lower.contains(" ")) {
            val exactMatch = _allApps.value.any { it.appName.equals(lower, ignoreCase = true) }
            if (exactMatch) return lower
        }
        return null
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
                    lastJarvisReply = response.response.take(200),
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
                    lastJarvisReply = response.response.take(200),
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
