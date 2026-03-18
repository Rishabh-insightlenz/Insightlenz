package com.insightlenz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary          = AccentBlue,
    secondary        = AccentBlueLight,
    background       = Black,
    surface          = DarkSurface,
    surfaceVariant   = MidSurface,
    onPrimary        = TextPrimary,
    onSecondary      = TextPrimary,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = BorderColor,
)

private val InsightLenzTypography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 21.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun InsightLenzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = InsightLenzTypography,
        content     = content
    )
}
