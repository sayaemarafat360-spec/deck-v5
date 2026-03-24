package com.sayaem.nebula.ui

sealed class Screen(val route: String) {
    // ── Primary bottom nav ────────────────────────────────────────────
    object Home     : Screen("home")
    object Music    : Screen("music")
    object Discover : Screen("discover")
    object More     : Screen("more")

    // ── Secondary / overlay screens ──────────────────────────────────
    object Equalizer : Screen("equalizer")
    object Premium   : Screen("premium")
    object Stats     : Screen("stats")
    object Search    : Screen("search")
    object Videos    : Screen("videos")   // kept for folder drill-down back-compat
}
