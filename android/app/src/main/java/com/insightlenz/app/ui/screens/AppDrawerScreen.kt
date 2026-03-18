package com.insightlenz.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.insightlenz.app.launcher.AppInfo
import com.insightlenz.app.ui.theme.NearBlack
import com.insightlenz.app.ui.theme.TextSecondary
import com.insightlenz.app.ui.theme.AccentBlue

/**
 * Full-screen app drawer — slides up from the bottom.
 *
 * - Swipe down anywhere to dismiss
 * - Search bar filters apps in real-time
 * - 4-column grid with app icons and names
 */
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onAppLaunch: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(250),
            targetOffsetY = { it }
        ),
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var dragOffset by remember { mutableStateOf(0f) }

        val filteredApps = remember(apps, searchQuery) {
            if (searchQuery.isBlank()) apps
            else apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0080810))  // near-black with slight transparency
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 120f) onDismiss()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {

                // ── Drag handle ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }

                // ── Search bar ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search apps…",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(AccentBlue),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            TextButton(
                                onClick = { searchQuery = "" },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("✕", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ── App count hint ──────────────────────────────────────────
                Text(
                    text = if (searchQuery.isBlank()) "${apps.size} apps"
                    else "${filteredApps.size} of ${apps.size}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // ── App grid ────────────────────────────────────────────────
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppGridItem(
                            app = app,
                            onClick = {
                                searchQuery = ""
                                onAppLaunch(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        AppIcon(
            drawable = app.icon,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.appName,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Renders an Android Drawable as a Compose Image.
 * Converts Drawable → Bitmap → ImageBitmap.
 * Cached by `remember` so the conversion only happens once per icon.
 */
@Composable
fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val imageBitmap: ImageBitmap = remember(drawable) {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = modifier,
    )
}
