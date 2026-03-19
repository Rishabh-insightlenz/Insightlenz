package com.insightlenz.app.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user's feed customisation choices.
 *
 * Cards can be toggled on/off and reordered.  The order is stored as a comma-
 * separated list of card IDs so that additions survive upgrades gracefully.
 */
object FeedPreferences {

    private const val PREFS_NAME   = "insightlenz_feed_prefs"
    private const val KEY_ORDER    = "card_order"
    private const val KEY_DISABLED = "cards_disabled"
    private const val KEY_WALLPAPER = "wallpaper_type"

    // ── Card catalogue ──────────────────────────────────────────────────────

    enum class Card(val id: String, val label: String) {
        CLOCK        ("clock",         "Clock"),
        RECENT_APPS  ("recent_apps",   "Recent Apps"),
        PRIORITY     ("priority",      "Today's Priority"),
        JARVIS_HISTORY("jarvis",       "Jarvis Conversation"),
        SCREEN_TIME  ("screen_time",   "Screen Time"),
        AI_QUOTA     ("ai_quota",      "AI Usage"),
    }

    /** All cards in their default order */
    val DEFAULT_ORDER = listOf(
        Card.CLOCK,
        Card.RECENT_APPS,
        Card.PRIORITY,
        Card.JARVIS_HISTORY,
        Card.SCREEN_TIME,
        Card.AI_QUOTA,
    )

    // ── Wallpaper catalogue ─────────────────────────────────────────────────

    enum class WallpaperType(
        val id: String,
        val label: String,
        /** Pair of ARGB hex strings for a vertical gradient.  Single means flat colour. */
        val gradientTop: Long,
        val gradientBottom: Long,
    ) {
        VOID       ("void",        "Deep Void",       0xFF080808, 0xFF080808),
        MIDNIGHT   ("midnight",    "Midnight Blue",   0xFF0A0E1F, 0xFF050810),
        AURORA     ("aurora",      "Aurora",          0xFF041A24, 0xFF0D1F0A),
        DUSK       ("dusk",        "Warm Dusk",       0xFF1A0A0F, 0xFF0A0515),
        SLATE      ("slate",       "Graphite Slate",  0xFF111318, 0xFF080A0F),
        FOREST     ("forest",      "Forest Dark",     0xFF071410, 0xFF030A07),
    }

    val DEFAULT_WALLPAPER = WallpaperType.VOID

    // ── Prefs helpers ───────────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Card order ──────────────────────────────────────────────────────────

    fun getCardOrder(ctx: Context): List<Card> {
        val raw = prefs(ctx).getString(KEY_ORDER, null) ?: return DEFAULT_ORDER
        val savedIds = raw.split(",").filter { it.isNotBlank() }
        val byId = Card.values().associateBy { it.id }
        // Keep saved order, append any new cards not yet in the list
        val saved = savedIds.mapNotNull { byId[it] }
        val unseen = DEFAULT_ORDER.filter { it !in saved }
        return saved + unseen
    }

    fun saveCardOrder(ctx: Context, order: List<Card>) {
        prefs(ctx).edit().putString(KEY_ORDER, order.joinToString(",") { it.id }).apply()
    }

    // ── Disabled cards ──────────────────────────────────────────────────────

    fun getDisabledCards(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()
    }

    fun setCardEnabled(ctx: Context, card: Card, enabled: Boolean) {
        val disabled = getDisabledCards(ctx).toMutableSet()
        if (enabled) disabled.remove(card.id) else disabled.add(card.id)
        prefs(ctx).edit().putStringSet(KEY_DISABLED, disabled).apply()
    }

    fun isCardEnabled(ctx: Context, card: Card): Boolean =
        card.id !in getDisabledCards(ctx)

    // ── Visible card list (order + enabled filter) ──────────────────────────

    fun getVisibleCards(ctx: Context): List<Card> {
        val disabled = getDisabledCards(ctx)
        return getCardOrder(ctx).filter { it.id !in disabled }
    }

    // ── Wallpaper ───────────────────────────────────────────────────────────

    fun getWallpaper(ctx: Context): WallpaperType {
        val id = prefs(ctx).getString(KEY_WALLPAPER, DEFAULT_WALLPAPER.id)
        return WallpaperType.values().firstOrNull { it.id == id } ?: DEFAULT_WALLPAPER
    }

    fun setWallpaper(ctx: Context, type: WallpaperType) {
        prefs(ctx).edit().putString(KEY_WALLPAPER, type.id).apply()
    }
}
