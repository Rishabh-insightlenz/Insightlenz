package com.insightlenz.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.insightlenz.app.api.ApiClient
import com.insightlenz.app.api.ChatRequest
import com.insightlenz.app.launcher.AiUsageTracker
import com.insightlenz.app.launcher.AppInfo
import com.insightlenz.app.launcher.AppRepository
import com.insightlenz.app.launcher.FeedPreferences
import com.insightlenz.app.service.AppLaunchDetector
import com.insightlenz.app.usage.AppUsageStat
import com.insightlenz.app.usage.UsageStatsHelper
import com.insightlenz.app.worker.UsageSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ── UI Models ──────────────────────────────────────────────────────────────────

data class Message(
    val content: String,
    val isUser: Boolean,
    val source: String = "chat"   // "chat" | "morning_brief" | "evening_review"
)

/**
 * A persistent Jarvis reply card that lives in the feed until the user swipes it away.
 * Unlike the old 8-second toast, these accumulate through the day.
 */
data class JarvisCard(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "reply"   // "reply" | "morning_brief" | "evening_review"
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
    val recentApps: List<AppUsageStat> = emptyList(),
    val hasUsagePermission: Boolean = false,
    val lastInsight: String? = null,

    // Persistent Jarvis conversation cards in the feed
    val jarvisCards: List<JarvisCard> = emptyList(),

    // AI usage
    val aiRequestsToday: Int = 0,
    val aiDailyBudget: Int = AiUsageTracker.PERSONAL_BUDGET,

    // Feed customisation
    val visibleCards: List<FeedPreferences.Card> = FeedPreferences.DEFAULT_ORDER,
    val activeWallpaper: FeedPreferences.WallpaperType = FeedPreferences.DEFAULT_WALLPAPER,
    val showCustomiseSheet: Boolean = false,

    // Screen state
    val currentScreen: Screen = Screen.FEED,

    // Launcher state
    val showAppDrawer: Boolean = false,
    val dockApps: List<AppInfo> = emptyList(),

    // Brief "Opening X…" toast for app launches only (true ephemeral)
    val launchToast: String? = null,
)

enum class Screen { FEED, CHAT }

// ── Session ID helpers ─────────────────────────────────────────────────────────

/**
 * Session ID strategy: one session per calendar day.
 * Format: "android_2026-03-18"
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

    val sessionId: String = todaySessionId()

    init {
        checkConnection()
        loadUsageStats()
        loadContext()
        loadChatHistory()
        loadInstalledApps()
        loadPreferences()
        refreshAiUsage()
        startLiveRefresh()  // live usage stats every 30s
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

    // ── Live refresh ───────────────────────────────────────────────────────────

    /**
     * Refreshes usage stats every 30 seconds so the screen-time card stays current.
     * Runs for the lifetime of the ViewModel (i.e. while the launcher is in memory).
     */
    private fun startLiveRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                val hasPermission = usageHelper.hasPermission()
                if (hasPermission) {
                    val stats  = usageHelper.getTodayUsage()
                    val recent = usageHelper.getRecentlyUsedApps()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            usageStats   = stats,
                            recentApps   = recent,
                            hasUsagePermission = true,
                        )
                    }
                }
            }
        }
    }

    // ── Feed data ──────────────────────────────────────────────────────────────

    fun loadUsageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasPermission = usageHelper.hasPermission()
            val stats  = if (hasPermission) usageHelper.getTodayUsage() else emptyList()
            val recent = if (hasPermission) usageHelper.getRecentlyUsedApps() else emptyList()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    hasUsagePermission = hasPermission,
                    usageStats   = stats,
                    recentApps   = recent,
                )
            }
        }
    }

    fun loadContext() {
        viewModelScope.launch {
            try {
                val context = ApiClient.service.getUserContext()
                val priority = context.priorities?.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    topPriority    = priority?.task,
                    priorityContext = priority?.why
                )
                priority?.task?.let { AppLaunchDetector.savePriority(getApplication(), it) }
            } catch (e: Exception) {
                // Context not set yet — fine
            }
        }
    }

    fun refreshFeed() {
        UsageSyncWorker.syncNow(getApplication())
        loadUsageStats()
        loadContext()
        checkConnection()
        refreshAiUsage()
    }

    fun refreshAiUsage() {
        val count = AiUsageTracker.getTodayCount(getApplication())
        _uiState.value = _uiState.value.copy(aiRequestsToday = count)
    }

    // ── Feed preferences ───────────────────────────────────────────────────────

    private fun loadPreferences() {
        val ctx = getApplication<Application>()
        val visible   = FeedPreferences.getVisibleCards(ctx)
        val wallpaper = FeedPreferences.getWallpaper(ctx)
        _uiState.value = _uiState.value.copy(
            visibleCards    = visible,
            activeWallpaper = wallpaper,
        )
    }

    fun openCustomiseSheet() {
        _uiState.value = _uiState.value.copy(showCustomiseSheet = true)
    }

    fun closeCustomiseSheet() {
        _uiState.value = _uiState.value.copy(showCustomiseSheet = false)
        loadPreferences() // re-apply any changes
    }

    fun toggleCard(card: FeedPreferences.Card, enabled: Boolean) {
        FeedPreferences.setCardEnabled(getApplication(), card, enabled)
        loadPreferences()
    }

    fun setWallpaper(type: FeedPreferences.WallpaperType) {
        FeedPreferences.setWallpaper(getApplication(), type)
        _uiState.value = _uiState.value.copy(activeWallpaper = type)
    }

    // ── Jarvis card management ─────────────────────────────────────────────────

    /** User swiped a Jarvis card away — remove it from the feed permanently */
    fun dismissJarvisCard(id: String) {
        _uiState.value = _uiState.value.copy(
            jarvisCards = _uiState.value.jarvisCards.filter { it.id != id }
        )
    }

    private fun addJarvisCard(content: String, type: String = "reply") {
        val card = JarvisCard(content = content, type = type)
        // Keep last 20 Jarvis cards in the feed
        val updated = (_uiState.value.jarvisCards + card).takeLast(20)
        _uiState.value = _uiState.value.copy(jarvisCards = updated)
    }

    // ── Chat history ───────────────────────────────────────────────────────────

    fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val history = ApiClient.service.getChatHistory(
                    sessionId = sessionId,
                    limit = 50
                )
                if (history.isNotEmpty()) {
                    val messages = history
                        .filter { !it.content.startsWith("[") }
                        .map { msg ->
                            Message(
                                content = msg.content,
                                isUser  = msg.role == "user",
                                source  = msg.source
                            )
                        }
                    if (messages.isNotEmpty()) {
                        // Reconstruct Jarvis cards from history (assistant messages)
                        val historicCards = messages
                            .filter { !it.isUser }
                            .takeLast(10)
                            .map { JarvisCard(content = it.content, type = it.source) }
                        _uiState.value = _uiState.value.copy(
                            messages    = messages,
                            jarvisCards = historicCards,
                            lastInsight = messages.lastOrNull { !it.isUser }?.content
                                ?.take(120)
                                ?.let { if (it.length == 120) "$it…" else it }
                        )
                    }
                }
            } catch (e: Exception) {
                // Silent — app works without history
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

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps     = withContext(Dispatchers.IO) { appRepo.getInstalledApps() }
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
     * 1. Detect app-launch intent → launch app + brief toast (2s then gone)
     * 2. Otherwise → send to Jarvis API
     *    → add persistent JarvisCard to feed
     *    → also append to Chat bubble list
     */
    fun sendMessage() {
        val message = _uiState.value.inputText.trim()
        if (message.isBlank()) return

        _uiState.value = _uiState.value.copy(inputText = "", error = null)

        // ── App launch intent ────────────────────────────────────────────────
        val appTarget = detectAppLaunchIntent(message)
        if (appTarget != null) {
            val match = _allApps.value.firstOrNull {
                it.appName.contains(appTarget, ignoreCase = true) ||
                it.packageName.contains(appTarget, ignoreCase = true)
            }
            if (match != null) {
                _uiState.value = _uiState.value.copy(launchToast = "Opening ${match.appName}…")
                viewModelScope.launch {
                    delay(600)
                    launchApp(match.packageName)
                    delay(2000)
                    _uiState.value = _uiState.value.copy(launchToast = null)
                }
                return
            }
        }

        // ── Regular Jarvis message ───────────────────────────────────────────
        val onFeed = _uiState.value.currentScreen == Screen.FEED

        appendMessage(Message(content = message, isUser = true))
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val response = ApiClient.service.chat(
                    ChatRequest(message = message, session_id = sessionId)
                )
                val reply = response.response

                // Track AI usage
                AiUsageTracker.increment(getApplication())
                refreshAiUsage()

                appendMessage(Message(content = reply, isUser = false))

                // Add persistent Jarvis card to the feed
                addJarvisCard(reply, type = "reply")

                _uiState.value = _uiState.value.copy(
                    isLoading   = false,
                    lastInsight = if (reply.length > 120) reply.take(120) + "…" else reply,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    private fun detectAppLaunchIntent(message: String): String? {
        val lower = message.lowercase().trim()
        val patterns = listOf("open ", "launch ", "go to ", "start ", "show me ", "take me to ")
        for (pattern in patterns) {
            if (lower.startsWith(pattern)) {
                return lower.removePrefix(pattern).trim().takeIf { it.isNotBlank() }
            }
        }
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
                val brief = "🌅 Morning Brief\n\n${response.response}"
                appendMessage(Message(content = brief, isUser = false, source = "morning_brief"))
                addJarvisCard(brief, type = "morning_brief")
                AiUsageTracker.increment(getApplication())
                refreshAiUsage()
                _uiState.value = _uiState.value.copy(
                    isLoading   = false,
                    lastInsight = response.response.take(120) + "…",
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
                val review = "🌙 Evening Review\n\n${response.response}"
                appendMessage(Message(content = review, isUser = false, source = "evening_review"))
                addJarvisCard(review, type = "evening_review")
                AiUsageTracker.increment(getApplication())
                refreshAiUsage()
                _uiState.value = _uiState.value.copy(
                    isLoading   = false,
                    lastInsight = response.response.take(120) + "…",
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
