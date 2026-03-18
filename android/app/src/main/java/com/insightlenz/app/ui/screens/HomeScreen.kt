package com.insightlenz.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.insightlenz.app.launcher.AppInfo
import com.insightlenz.app.ui.theme.*
import com.insightlenz.app.usage.AppUsageStat
import com.insightlenz.app.usage.UsageStatsHelper
import com.insightlenz.app.viewmodel.ChatUiState
import com.insightlenz.app.viewmodel.ChatViewModel
import com.insightlenz.app.viewmodel.Message
import com.insightlenz.app.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════════════
// ROOT
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    var swipeDx by remember { mutableFloatStateOf(0f) }
    var swipeDy by remember { mutableFloatStateOf(0f) }

    // Time-of-day ambient background tint
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val ambientTint = when (hour) {
        in 5..9   -> TintMorning
        in 10..16 -> TintDay
        in 17..20 -> TintEvening
        else      -> TintNight
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Ambient gradient overlay — changes with time of day
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ambientTint, Color.Transparent),
                        radius = 900f,
                    )
                )
        )

        // ── Main swipe-gesture wrapper ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.currentScreen, uiState.showAppDrawer) {
                    if (uiState.showAppDrawer) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            val xDom = abs(swipeDx) > abs(swipeDy)
                            when {
                                !xDom && swipeDy < -120f -> viewModel.openAppDrawer()
                                xDom && swipeDx > 120f && uiState.currentScreen == Screen.FEED -> viewModel.openChat()
                                xDom && swipeDx < -120f && uiState.currentScreen == Screen.CHAT -> viewModel.openFeed()
                            }
                            swipeDx = 0f; swipeDy = 0f
                        },
                        onDragCancel = { swipeDx = 0f; swipeDy = 0f },
                        onDrag = { _, d -> swipeDx += d.x; swipeDy += d.y }
                    )
                }
        ) {
            AnimatedContent(
                targetState = uiState.currentScreen,
                transitionSpec = {
                    if (targetState == Screen.CHAT)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.FEED -> FeedScreen(uiState, viewModel)
                    Screen.CHAT -> ChatScreen(uiState, viewModel)
                }
            }
        }

        // ── Jarvis bar — always anchored to the bottom ─────────────────────
        AnimatedVisibility(
            visible = !uiState.showAppDrawer,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            JarvisBar(
                uiState = uiState,
                onTextChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() },
                onOpenApps = { viewModel.openAppDrawer() },
            )
        }

        // ── Spotlight app search ───────────────────────────────────────────
        SpotlightSearch(
            apps = allApps,
            visible = uiState.showAppDrawer,
            onDismiss = { viewModel.closeAppDrawer() },
            onAppLaunch = { viewModel.launchApp(it) },
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// JARVIS BAR
// The command surface. Lives at the bottom of every screen.
// Handles both chat messages and app-launch intents.
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JarvisBar(
    uiState: ChatUiState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = uiState.isConnected && !uiState.isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Background.copy(alpha = 0.97f))
                )
            )
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp),
    ) {
        // ── Jarvis response — shown inline above the bar ───────────────────
        AnimatedVisibility(
            visible = uiState.isLoading || (uiState.currentScreen == Screen.FEED && uiState.lastJarvisReply != null),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceOverlay)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                if (uiState.isLoading) {
                    JarvisThinking()
                } else {
                    uiState.lastJarvisReply?.let { reply ->
                        Text(
                            text = reply,
                            color = OnSurface,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }

        // ── Input pill ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceBright)
                .border(1.dp, BorderSubtle, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Grid icon to open app spotlight
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SurfaceOverlay)
                    .clickable { onOpenApps() },
                contentAlignment = Alignment.Center,
            ) {
                Text("⊞", color = OnSurfaceVar, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text input
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.inputText.isEmpty()) {
                    Text(
                        text = if (!uiState.isConnected) "Backend offline…"
                               else if (uiState.isLoading) "Thinking…"
                               else "Ask Jarvis or open an app…",
                        color = OnSurfaceFaint,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = uiState.inputText,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    textStyle = TextStyle(color = OnSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Send / loading button
            AnimatedContent(targetState = uiState.isLoading, label = "send") { loading ->
                if (loading) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (uiState.inputText.isNotBlank()) Primary else Color.Transparent)
                            .clickable(enabled = enabled && uiState.inputText.isNotBlank()) { onSend() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "↑",
                            color = if (uiState.inputText.isNotBlank()) Color.White else OnSurfaceFaint,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── Swipe up hint ─────────────────────────────────────────────────
        if (uiState.currentScreen == Screen.FEED) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "↑  swipe up for apps",
                color = OnSurfaceFaint,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun JarvisThinking() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha"
    )
    Text(
        text = "Jarvis is thinking…",
        color = OnSurfaceVar.copy(alpha = alpha),
        fontSize = 14.sp,
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// FEED SCREEN — the home screen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeedScreen(uiState: ChatUiState, viewModel: ChatViewModel) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Hero: Clock + greeting ─────────────────────────────────────────
        item { ClockHero() }

        // ── Priority ──────────────────────────────────────────────────────
        item {
            FeedCard(accentColor = Primary, label = "FOCUS NOW", emoji = "◎") {
                if (uiState.topPriority != null) {
                    Text(
                        text = uiState.topPriority,
                        color = OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 25.sp,
                    )
                    if (uiState.priorityContext != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.priorityContext,
                            color = OnSurfaceVar,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                } else {
                    Text(
                        text = "Tell Jarvis what matters most today.",
                        color = OnSurfaceFaint,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // ── Last Jarvis insight ───────────────────────────────────────────
        uiState.lastInsight?.let { insight ->
            item {
                FeedCard(accentColor = SemanticPurple, label = "JARVIS NOTED", emoji = "✦") {
                    Text(
                        text = "\"$insight\"",
                        color = OnSurfaceVar,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }

        // ── Screen time ───────────────────────────────────────────────────
        item {
            FeedCard(accentColor = SemanticYellow, label = "TODAY'S SCREEN TIME", emoji = "◷") {
                if (!uiState.hasUsagePermission) {
                    Text(
                        "Grant usage access so Jarvis can understand your attention.",
                        color = OnSurfaceVar,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GrantButton(label = "Grant Access") {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                } else if (uiState.usageStats.isEmpty()) {
                    Text("No usage data yet today.", color = OnSurfaceFaint, fontSize = 13.sp)
                } else {
                    val maxTime = uiState.usageStats.maxOf { it.totalTimeMs }.toFloat().coerceAtLeast(1f)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.usageStats.take(6).forEach { stat ->
                            UsageRow(stat = stat, maxTime = maxTime)
                        }
                    }
                }
            }
        }

        // ── Quick actions ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAction(
                    label = "🌅 Morning Brief",
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected && !uiState.isLoading,
                    onClick = { viewModel.getMorningBrief() },
                )
                QuickAction(
                    label = "🌙 Evening Review",
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected && !uiState.isLoading,
                    onClick = { viewModel.getEveningReview() },
                )
            }
        }
    }
}

// ── Clock hero ────────────────────────────────────────────────────────────────

@Composable
private fun ClockHero() {
    val timeFmt  = remember { SimpleDateFormat("h:mm",          Locale.getDefault()) }
    val dateFmt  = remember { SimpleDateFormat("EEEE, MMMM d",  Locale.getDefault()) }
    val amPmFmt  = remember { SimpleDateFormat("a",             Locale.getDefault()) }

    var time  by remember { mutableStateOf(timeFmt.format(Date())) }
    var date  by remember { mutableStateOf(dateFmt.format(Date())) }
    var amPm  by remember { mutableStateOf(amPmFmt.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = timeFmt.format(now)
            date = dateFmt.format(now)
            amPm = amPmFmt.format(now)
            kotlinx.coroutines.delay(15_000) // update every 15s
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
    ) {
        // Date
        Text(
            text = date,
            color = OnSurfaceVar,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Big clock
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = time,
                color = OnSurface,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                lineHeight = 76.sp,
            )
            Text(
                text = " $amPm",
                color = OnSurfaceVar,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Version badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(PrimaryGlow)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "INSIGHTLENZ v0.4",
                    color = Primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

// ── Feed card shell ───────────────────────────────────────────────────────────

@Composable
private fun FeedCard(
    accentColor: Color,
    label: String,
    emoji: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceOverlay)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        // Label row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = emoji,
                color = accentColor,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// ── Usage row ─────────────────────────────────────────────────────────────────

@Composable
private fun UsageRow(stat: AppUsageStat, maxTime: Float) {
    val isReactive = stat.packageName in UsageStatsHelper.REACTIVE_APPS
    val barColor   = if (isReactive) SemanticRed else Primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stat.appName,
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                if (isReactive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SemanticRed.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("reactive", color = SemanticRed, fontSize = 9.sp)
                    }
                }
            }
            Text(text = stat.displayTime, color = OnSurfaceVar, fontSize = 12.sp)
        }
        // Thin progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(BorderNormal)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.totalTimeMs / maxTime)
                    .fillMaxHeight()
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────

@Composable
private fun QuickAction(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceOverlay,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = if (enabled) OnSurfaceVar else OnSurfaceFaint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GrantButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = PrimaryGlow,
    ) {
        Text(
            text = label,
            color = Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDim)
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.openFeed() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home", tint = OnSurfaceVar)
            }
            Column {
                Text("Jarvis", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (uiState.isConnected) SemanticGreen else SemanticRed)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(uiState.backendStatus, color = OnSurfaceFaint, fontSize = 11.sp)
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // ── Messages ───────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            if (uiState.messages.isEmpty()) {
                item { ChatEmptyState() }
            } else {
                items(uiState.messages.size) { i ->
                    MessageBubble(message = uiState.messages[i])
                }
            }
            if (uiState.isLoading) {
                item { ThinkingBubble() }
            }
        }

        uiState.error?.let {
            Text(it, color = SemanticRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 4.dp))
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 4.dp,
                    bottomEnd   = if (message.isUser) 4.dp  else 18.dp,
                ))
                .background(if (message.isUser) PrimaryDim else SurfaceOverlay)
                .border(
                    1.dp,
                    if (message.isUser) PrimaryGlow else BorderSubtle,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (message.isUser) 18.dp else 4.dp,
                        bottomEnd   = if (message.isUser) 4.dp  else 18.dp,
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = message.content, color = OnSurface, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ChatEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", color = Primary, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Jarvis is ready.", color = OnSurfaceVar, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ask anything. Open any app.\nJarvis knows your context.",
            color = OnSurfaceFaint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ThinkingBubble() {
    val inf = rememberInfiniteTransition(label = "dots")
    val alpha by inf.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Row {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                .background(SurfaceOverlay)
                .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("● ● ●", color = OnSurfaceVar.copy(alpha = alpha), fontSize = 12.sp, letterSpacing = 3.sp)
        }
    }
}

