package com.insightlenz.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Base surfaces ──────────────────────────────────────────────────────────────
val Background       = Color(0xFF080808)   // true near-black
val SurfaceDim       = Color(0xFF0F0F0F)   // barely lifted
val Surface          = Color(0xFF1C1C1E)   // iOS dark surface energy
val SurfaceBright    = Color(0xFF2C2C2E)   // for inputs / chips
val SurfaceOverlay   = Color(0x18FFFFFF)   // white 9.4% — card backgrounds

// ── Text ───────────────────────────────────────────────────────────────────────
val OnSurface        = Color(0xFFFFFFFF)
val OnSurfaceVar     = Color(0xFF9A9A9F)   // secondary text — iOS gray
val OnSurfaceFaint   = Color(0xFF48484A)   // hint / disabled

// ── Brand ─────────────────────────────────────────────────────────────────────
val Primary          = Color(0xFF4285F4)   // Google Blue
val PrimaryDim       = Color(0xFF1A3A6B)   // deep tint for containers
val PrimaryGlow      = Color(0x334285F4)   // glow / border tint

// ── Semantic ──────────────────────────────────────────────────────────────────
val SemanticGreen    = Color(0xFF34A853)   // Google Green — live / success
val SemanticRed      = Color(0xFFEA4335)   // Google Red — warning / reactive
val SemanticYellow   = Color(0xFFFBBC05)   // Google Yellow — morning / caution
val SemanticPurple   = Color(0xFFAB47BC)   // memory / insight

// ── Card & borders ────────────────────────────────────────────────────────────
val BorderSubtle     = Color(0x14FFFFFF)   // white 8%
val BorderNormal     = Color(0x1FFFFFFF)   // white 12%

// ── Time-of-day ambient tints (overlaid on background) ────────────────────────
val TintMorning      = Color(0x0C4285F4)   // cool blue, 5–10am
val TintDay          = Color(0x00000000)   // none, 10–17pm
val TintEvening      = Color(0x0CFBBC05)   // warm amber, 17–21pm
val TintNight        = Color(0x0C3D3087)   // deep indigo, 21–5am

// ── Legacy aliases — keeps existing code compiling ────────────────────────────
val Black            = Background
val NearBlack        = SurfaceDim
val CardSurface      = Surface
val MidSurface       = SurfaceBright
val DarkSurface      = SurfaceDim
val TextPrimary      = OnSurface
val TextSecondary    = OnSurfaceVar
val TextTertiary     = OnSurfaceFaint
val AccentBlue       = Primary
val AccentBlueLight  = Color(0xFF6BB2FF)
val UserBubble       = PrimaryDim
val BorderColor      = BorderNormal
val StatusGreen      = SemanticGreen
val StatusRed        = SemanticRed
