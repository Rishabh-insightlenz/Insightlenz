package com.insightlenz.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insightlenz.app.launcher.AppInfo
import com.insightlenz.app.ui.theme.*

/**
 * Spotlight-style app search — text only, no icon grid.
 *
 * Design philosophy: the user shouldn't be browsing icons.
 * They know what they want. Type it, tap it, done.
 * This feels like cmd+Space on macOS or iOS spotlight — fast and intentional.
 *
 * Swipe down to dismiss.
 */
@Composable
fun SpotlightSearch(
    apps: List<AppInfo>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onAppLaunch: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { it },
        exit  = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it },
    ) {
        var query by remember { mutableStateOf("") }
        var dragY by remember { mutableFloatStateOf(0f) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(visible) {
            if (visible) {
                query = ""
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
        }

        val filtered = remember(apps, query) {
            if (query.isBlank()) apps
            else apps.filter { it.appName.contains(query, ignoreCase = true) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xF5080808), Color(0xFF080808))
                    )
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd   = { if (dragY > 100f) { onDismiss(); dragY = 0f } },
                        onDragCancel = { dragY = 0f },
                        onVerticalDrag = { _, dy -> dragY = (dragY + dy).coerceAtLeast(0f) }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ── Drag handle ───────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OnSurfaceFaint)
                            .clickable { onDismiss() }
                    )
                }

                // ── Search bar ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceBright)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", color = OnSurfaceVar, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search apps…", color = OnSurfaceFaint, fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = TextStyle(color = OnSurface, fontSize = 16.sp),
                            cursorBrush = SolidColor(Primary),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "✕",
                            color = OnSurfaceFaint,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { query = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Count hint ────────────────────────────────────────────
                Text(
                    text = if (query.isBlank()) "${apps.size} apps installed"
                           else "${filtered.size} match",
                    color = OnSurfaceFaint,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )

                // ── App list ──────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            query = query,
                            onClick = {
                                onAppLaunch(app.packageName)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Small icon — optional decorative element, not the focus
        AppIcon(
            drawable = app.icon,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        // App name — this is what the user is reading
        Text(
            text = app.appName,
            color = OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Open arrow
        Text("›", color = OnSurfaceFaint, fontSize = 18.sp)
    }

    HorizontalDivider(
        color = BorderSubtle,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
