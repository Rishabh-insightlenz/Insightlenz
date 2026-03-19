package com.insightlenz.app.ui.screens

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insightlenz.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * InsightLenz lock screen.
 *
 * Shown when the screen wakes up (via ScreenOnReceiver).
 * Uses FLAG_SHOW_WHEN_LOCKED so it appears before or alongside
 * the system keyguard — on devices with no PIN it IS the lock screen;
 * on devices with PIN the user sees Jarvis greeting then system PIN.
 *
 * Tap anywhere to dismiss and proceed to the launcher.
 */
class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the keyguard, turn the screen on, dismiss keyguard if no PIN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContent {
            InsightLenzLockScreen(onUnlock = { finish() })
        }
    }

    override fun onBackPressed() {
        // Block back — lock screen should not be dismissible via back
    }
}

@Composable
private fun InsightLenzLockScreen(onUnlock: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val amPmFmt = remember { SimpleDateFormat("a", Locale.getDefault()) }

    var time by remember { mutableStateOf(timeFmt.format(Date())) }
    var date by remember { mutableStateOf(dateFmt.format(Date())) }
    var amPm by remember { mutableStateOf(amPmFmt.format(Date())) }

    // Greeting based on time of day
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11  -> "Good morning."
        in 12..16 -> "Good afternoon."
        in 17..20 -> "Good evening."
        else      -> "Welcome back."
    }

    // Ambient tint
    val ambientTint = when (hour) {
        in 5..9   -> TintMorning
        in 10..16 -> TintDay
        in 17..20 -> TintEvening
        else      -> TintNight
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = timeFmt.format(now)
            date = dateFmt.format(now)
            amPm = amPmFmt.format(now)
            kotlinx.coroutines.delay(15_000)
        }
    }

    // Pulsing "tap to unlock" animation
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by inf.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onUnlock,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ambientTint.copy(alpha = 0.6f), Color.Transparent),
                        radius = 800f,
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Jarvis logo mark
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimaryGlow),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = Primary, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Big clock
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time,
                    color = OnSurface,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp,
                    lineHeight = 84.sp,
                )
                Text(
                    " $amPm",
                    color = OnSurfaceVar,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(date, color = OnSurfaceVar, fontSize = 16.sp, fontWeight = FontWeight.Light)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                greeting,
                color = OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.3).sp,
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Tap to unlock pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    "Tap anywhere to unlock",
                    color = OnSurfaceVar.copy(alpha = pulseAlpha),
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
