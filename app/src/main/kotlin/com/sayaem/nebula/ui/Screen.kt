package com.sayaem.nebula.ui

sealed class Screen(val route: String) {
    // ── Primary bottom nav (Playit-style) ────────────────────────────
    object Home     : Screen("home")
    object Videos   : Screen("videos")
    object Music    : Screen("music")
    object More     : Screen("more")

    // ── Secondary / overlay screens ──────────────────────────────────
    object Equalizer : Screen("equalizer")
    object Premium   : Screen("premium")
    object Stats     : Screen("stats")
    object Search    : Screen("search")   // overlay, not a tab
}
