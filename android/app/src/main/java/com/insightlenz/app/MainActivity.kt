package com.insightlenz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import com.insightlenz.app.ui.screens.HomeScreen
import com.insightlenz.app.ui.screens.SetupScreen
import com.insightlenz.app.ui.screens.hasUsageAccess
import com.insightlenz.app.ui.screens.isAccessibilityEnabled
import com.insightlenz.app.ui.theme.InsightLenzTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            InsightLenzTheme {
                AppRouter()
            }
        }
    }

    // Re-check permissions every time the activity resumes
    // (user may return from Settings having just granted something)
    override fun onResume() {
        super.onResume()
        // Compose state will recompose automatically via the polling in SetupScreen
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
