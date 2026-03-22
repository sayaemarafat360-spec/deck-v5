package com.sayaem.nebula.ui

// Kept for any legacy references — main navigation now uses NavState in MainActivity
sealed class Screen(val route: String) {
    object Home       : Screen("home")
    object Library    : Screen("library")
    object Search     : Screen("search")
    object Settings   : Screen("settings")
    object NowPlaying : Screen("now_playing")
    object Equalizer  : Screen("equalizer")
    object Premium    : Screen("premium")
    object Stats      : Screen("stats")
}
