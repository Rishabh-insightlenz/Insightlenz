package com.insightlenz.app

import android.content.IntentFilter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import com.insightlenz.app.receiver.ScreenOnReceiver
import com.insightlenz.app.ui.screens.HomeScreen
import com.insightlenz.app.ui.screens.SetupScreen
import com.insightlenz.app.ui.screens.hasUsageAccess
import com.insightlenz.app.ui.screens.isAccessibilityEnabled
import com.insightlenz.app.ui.theme.InsightLenzTheme

class MainActivity : ComponentActivity() {

    // Dynamic receiver — ACTION_SCREEN_ON can't be registered in the manifest
    private val screenOnReceiver = ScreenOnReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Register screen-on receiver
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)

        setContent {
            InsightLenzTheme {
                AppRouter()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Compose state recomposes automatically via SetupScreen polling
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenOnReceiver) } catch (_: Exception) {}
    }
}

@Composable
private fun AppRouter() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // We're set up once all 4 special permissions are granted.
    // Check live — SetupScreen polls every 500ms so this updates immediately
    // when the user returns from Settings.
    var setupComplete by remember {
        mutableStateOf(isSetupComplete(context))
    }

    if (setupComplete) {
        HomeScreen()
    } else {
        SetupScreen(
            onAllGranted = { setupComplete = true }
        )
    }
}

private fun isSetupComplete(context: android.content.Context): Boolean {
    val overlayGranted   = android.provider.Settings.canDrawOverlays(context)
    val accessibilityOn  = isAccessibilityEnabled(context)
    val usageGranted     = hasUsageAccess(context)
    // Notifications: optional but not blocking — don't gate on it
    return overlayGranted && accessibilityOn && usageGranted
}
