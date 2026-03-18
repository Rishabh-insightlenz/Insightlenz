package com.insightlenz.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.insightlenz.app.ui.theme.*
import com.insightlenz.app.usage.AppUsageStat
import com.insightlenz.app.usage.UsageStatsHelper
import com.insightlenz.app.viewmodel.ChatUiState
import com.insightlenz.app.viewmodel.ChatViewModel
import com.insightlenz.app.viewmodel.Message
import com.insightlenz.app.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding()
            .imePadding()
    ) {
        AnimatedContent(
            targetState = uiState.currentScreen,
            transitionSpec = {
                if (targetState == Screen.CHAT) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                    slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.FEED -> FeedScreen(uiState, viewModel)
                Screen.CHAT -> ChatScreen(uiState, viewModel)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FEED SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeedScreen(uiState: ChatUiState, viewModel: ChatViewModel) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item { FeedHeader(uiState, onRefresh = { viewModel.refreshFeed() }) }

        // ── Priority card ──────────────────────────────────────────────────────
        item {
            PriorityCard(
                priority = uiState.topPriority,
                context = uiState.priorityContext
            )
        }

        // ── Last insight ───────────────────────────────────────────────────────
        uiState.lastInsight?.let { insight ->
            item { InsightCard(insight = insight) }
        }

        // ── Screen time ────────────────────────────────────────────────────────
        item {
            ScreenTimeCard(
                stats = uiState.usageStats,
                hasPermission = uiState.hasUsagePermission,
                onGrantPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            )
        }

        // ── Morning brief / Evening review quick access ────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedButton(
                    label = "🌅 Morning Brief",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.getMorningBrief() },
                    enabled = uiState.isConnected && !uiState.isLoading
                )
                FeedButton(
                    label = "🌙 Evening Review",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.getEveningReview() },
                    enabled = uiState.isConnected && !uiState.isLoading
                )
            }
        }
    }

    // ── Floating "Talk to Jarvis" button ───────────────────────────────────────
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = AccentBlue,
            onClick = { viewModel.openChat() }
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thinking...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                } else {
                    Text("Talk to Jarvis", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Feed Header ────────────────────────────────────────────────────────────────

@Composable
private fun FeedHeader(uiState: ChatUiState, onRefresh: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }
    val currentTime = remember { mutableStateOf(timeFormat.format(Date())) }

    // Refresh time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = timeFormat.format(Date())
            kotlinx.coroutines.delay(60_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NearBlack)
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "INSIGHTLENZ",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "v0.3",
                    color = AccentBlue.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = currentTime.value,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Backend status dot
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isConnected) StatusGreen else StatusRed)
                )
                Text(
                    text = if (uiState.isConnected) "Live" else "Offline",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Priority Card ──────────────────────────────────────────────────────────────

@Composable
private fun PriorityCard(priority: String?, context: String?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🎯  FOCUS NOW",
                color = AccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (priority != null) {
                Text(
                    text = priority,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp
                )
                if (context != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = context,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            } else {
                Text(
                    text = "No priority set yet.",
                    color = TextTertiary,
                    fontSize = 15.sp
                )
                Text(
                    text = "Ask InsightLenz to set your priorities.",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ── Insight Card ───────────────────────────────────────────────────────────────

@Composable
private fun InsightCard(insight: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🧠  LAST INSIGHT",
                color = Color(0xFFBB86FC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "\"$insight\"",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

// ── Screen Time Card ───────────────────────────────────────────────────────────

@Composable
private fun ScreenTimeCard(
    stats: List<AppUsageStat>,
    hasPermission: Boolean,
    onGrantPermission: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "📱  TODAY'S SCREEN TIME",
                color = Color(0xFFFF9800),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!hasPermission) {
                Text(
                    text = "Grant usage access to see which apps you're actually spending time in.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FF9800),
                    onClick = onGrantPermission
                ) {
                    Text(
                        text = "Grant Access →",
                        color = Color(0xFFFF9800),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else if (stats.isEmpty()) {
                Text(text = "No app usage recorded yet today.", color = TextTertiary, fontSize = 13.sp)
            } else {
                val maxTime = stats.maxOf { it.totalTimeMs }.toFloat().coerceAtLeast(1f)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { stat ->
                        UsageRow(stat = stat, maxTime = maxTime)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(stat: AppUsageStat, maxTime: Float) {
    val isReactive = stat.packageName in UsageStatsHelper.REACTIVE_APPS
    val barColor = if (isReactive) Color(0xFFFF5252) else AccentBlue

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stat.appName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (isReactive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FF5252))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(text = "reactive", color = Color(0xFFFF5252), fontSize = 9.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
            Text(text = stat.displayTime, color = TextSecondary, fontSize = 12.sp)
        }
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BorderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.totalTimeMs / maxTime)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

// ── Feed Button ────────────────────────────────────────────────────────────────

@Composable
private fun FeedButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MidSurface,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = if (enabled) TextSecondary else TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CHAT SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatScreen(uiState: ChatUiState, viewModel: ChatViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Chat header with back button ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NearBlack)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.openFeed() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column {
                Text("InsightLenz", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(uiState.backendStatus, color = TextTertiary, fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

        // ── Messages ───────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.messages.isEmpty()) {
                item { ChatEmptyState() }
            } else {
                items(uiState.messages) { message ->
                    MessageBubble(message = message)
                }
            }
            if (uiState.isLoading) {
                item { ThinkingIndicator() }
            }
        }

        // ── Error ──────────────────────────────────────────────────────────────
        uiState.error?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── Input ──────────────────────────────────────────────────────────────
        ChatInput(
            text = uiState.inputText,
            onTextChange = { viewModel.updateInput(it) },
            onSend = { viewModel.sendMessage() },
            enabled = !uiState.isLoading && uiState.isConnected
        )
    }
}

// ── Message Bubble ─────────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(if (message.isUser) UserBubble else CardSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = message.content, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ChatEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Ask anything.", color = TextSecondary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Grounded in your priorities, values, and what you\nactually told InsightLenz matters.",
            color = TextTertiary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = "Thinking...", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChatInput(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = if (enabled) "Ask InsightLenz..." else "Connecting...",
                    color = TextTertiary,
                    fontSize = 15.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MidSurface,
                unfocusedContainerColor = MidSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledContainerColor = MidSurface,
                disabledTextColor = TextSecondary,
                disabledIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
            maxLines = 4,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (enabled && text.isNotBlank()) AccentBlue else TextTertiary
            )
        }
    }
}
