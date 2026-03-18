package com.insightlenz.app.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insightlenz.app.service.AppLaunchDetector
import com.insightlenz.app.ui.theme.*

data class PermissionStep(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val actionLabel: String,
    val isGranted: (Context) -> Boolean,
    val openSettings: (Context) -> Unit
)

@Composable
fun SetupScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    // Re-check permission states every time the screen resumes
    var tick by remember { mutableIntStateOf(0) }

    val steps = remember(tick) {
        listOf(
            PermissionStep(
                id = "notifications",
                emoji = "🔔",
                title = "Notifications",
                description = "Morning briefs, evening reviews, and focus nudges are delivered as notifications.",
                actionLabel = "Enable Notifications",
                isGranted = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                },
                openSettings = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            ),
            PermissionStep(
                id = "overlay",
                emoji = "⚡",
                title = "Display Over Other Apps",
                description = "Shows your Priority #1 as a thin strip when you switch apps. This is the Jarvis layer.",
                actionLabel = "Grant Overlay Access",
                isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                openSettings = { ctx ->
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                }
            ),
            PermissionStep(
                id = "accessibility",
                emoji = "👁",
                title = "Accessibility Service",
                description = "Detects which app you're in so InsightLenz can surface context-aware overlays and track distraction patterns.",
                actionLabel = "Enable Accessibility",
                isGranted = { ctx -> isAccessibilityEnabled(ctx) },
                openSettings = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            ),
            PermissionStep(
                id = "usage",
                emoji = "📱",
                title = "Usage Access",
                description = "Reads which apps you use and for how long. Powers the screen time feed and distraction alerts.",
                actionLabel = "Grant Usage Access",
                isGranted = { ctx -> hasUsageAccess(ctx) },
                openSettings = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            )
        )
    }

    // Notification permission launcher (the one we CAN request inline)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val allGranted = steps.all { it.isGranted(context) }

    // Trigger callback as soon as everything's granted
    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    // Re-check when app comes back to foreground (user returns from Settings)
    DisposableEffect(Unit) {
        val runnable = Runnable { tick++ }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        // Poll every 500ms while this screen is visible
        val poll = object : Runnable {
            override fun run() {
                tick++
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(poll, 500)
        onDispose { handler.removeCallbacks(poll) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Text(
            text = "INSIGHTLENZ",
            color = AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Setup",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "InsightLenz needs a few special permissions to work as an OS layer, not just an app.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Permission steps
        steps.forEach { step ->
            val granted = step.isGranted(context)
            PermissionCard(
                step = step,
                granted = granted,
                onAction = {
                    if (step.id == "notifications" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        step.openSettings(context)
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue button — only active once all granted
        val grantedCount = steps.count { it.isGranted(context) }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (allGranted) AccentBlue else MidSurface,
            onClick = { if (allGranted) onAllGranted() }
        ) {
            Text(
                text = if (allGranted) "All set — Open InsightLenz" else "$grantedCount / ${steps.size} granted",
                color = if (allGranted) Color.White else TextTertiary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(
    step: PermissionStep,
    granted: Boolean,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0x2200C853) else MidSurface),
                contentAlignment = Alignment.Center
            ) {
                if (granted) {
                    Text(text = "✓", color = Color(0xFF00C853), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(text = step.emoji, fontSize = 18.sp)
                }
            }

            // Text
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = step.title,
                    color = if (granted) TextSecondary else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = step.description,
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            // Grant button
            if (!granted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                    onClick = onAction
                ) {
                    Text(
                        text = "Grant →",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

// ── Permission check helpers ───────────────────────────────────────────────────

fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
        it.resolveInfo.serviceInfo.name == AppLaunchDetector::class.java.name
    }
}

fun hasUsageAccess(context: Context): Boolean {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            now - 1000 * 60 * 2,
            now
        )
        stats != null && stats.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
