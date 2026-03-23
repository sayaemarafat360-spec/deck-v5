package com.sayaem.nebula.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sayaem.nebula.MainActivity
import com.sayaem.nebula.data.local.LocalDataStore
import java.util.Calendar
import kotlin.random.Random

class DeckNotificationEngine(private val context: Context) {

    companion object {
        const val CH_PLAYBACK  = "deck_playback"
        const val CH_ENGAGE    = "deck_engage"
        const val CH_ACTIVITY  = "deck_activity"
        const val CH_TIPS      = "deck_tips"

        const val ID_PLAYBACK  = 1
        const val ID_BASE      = 200  // engagement IDs start here

        const val ACTION_DAILY = "com.sayaem.nebula.DAILY_NOTIF"
        const val EXTRA_TYPE   = "notif_type"

        private const val PREFS              = "deck_notif_engine"
        private const val KEY_LAST_SCHEDULE  = "last_sched_day"
        private const val KEY_STREAK         = "listening_streak"
        private const val KEY_STREAK_DAY     = "streak_last_day"
        private const val KEY_FIRST_RUN      = "first_run_done"
        private const val KEY_LAST_MILESTONE = "last_milestone"

        // ── 20+ notification type constants ──────────────────────────────
        const val T_COMEBACK_1    =  1  // "Your music misses you"
        const val T_COMEBACK_2    =  2  // "Time for some music?"
        const val T_COMEBACK_3    =  3  // "Still there? Music waiting"
        const val T_STREAK        =  4
        const val T_TOP_SONG      =  5
        const val T_WEEKLY_STATS  =  6
        const val T_MOOD_MORNING  =  7
        const val T_MOOD_NOON     =  8  // new
        const val T_MOOD_EVENING  =  9
        const val T_MOOD_NIGHT    = 10  // new
        const val T_DISCOVERY     = 11
        const val T_MILESTONE     = 12
        const val T_NEW_SONGS     = 13
        const val T_QUEUE_HINT    = 14
        const val T_TIP_GESTURE   = 15  // gesture tip
        const val T_TIP_EQ        = 16  // EQ tip
        const val T_TIP_LYRICS    = 17  // lyrics tip
        const val T_TIP_SLEEP     = 18  // sleep timer tip
        const val T_TIP_SEARCH    = 19  // search tip
        const val T_TIP_GENERAL   = 20  // general tip
        const val T_INVITE        = 21  // "Share Deck with friends"
        const val T_PLAYLIST_HINT = 22  // "Create a playlist"
        const val T_VIDEO_HINT    = 23  // "Try the video player"
        const val T_FAVORITE_HINT = 24  // "Favorite your best songs"
    }

    private val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store = LocalDataStore(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init { createChannels() }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(NotificationChannel(
            CH_PLAYBACK, "Now Playing", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_ENGAGE, "Daily Music", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Daily listening reminders and your music stats"
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_ACTIVITY, "Achievements", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Streaks, milestones, achievements"
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_TIPS, "Tips & Tricks", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Hidden features and discovery"
        })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIRST INSTALL — fire immediately + schedule first 20
    // ═══════════════════════════════════════════════════════════════════════
    fun onFirstInstall() {
        val done = prefs.getBoolean(KEY_FIRST_RUN, false)
        if (done) return
        prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply()

        // Fire a welcome notification RIGHT NOW (3 min delay)
        scheduleAt(System.currentTimeMillis() + 3 * 60_000L, T_MOOD_MORNING, 9999)
        // Another one in 15 minutes
        scheduleAt(System.currentTimeMillis() + 15 * 60_000L, T_TIP_GENERAL, 9998)
        // And one at the 30-minute mark
        scheduleAt(System.currentTimeMillis() + 30 * 60_000L, T_FAVORITE_HINT, 9997)

        // Force reschedule today
        prefs.edit().remove(KEY_LAST_SCHEDULE).apply()
        scheduleDailyNotifications()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCHEDULE 20 notifications spread across the day
    // ═══════════════════════════════════════════════════════════════════════
    fun scheduleDailyNotifications() {
        val today = todayKey()
        if (prefs.getString(KEY_LAST_SCHEDULE, "") == today) return
        prefs.edit().putString(KEY_LAST_SCHEDULE, today).apply()

        val types = pickTwentyTypes()
        val slots = generateSlots(types.size)
        types.forEachIndexed { i, type ->
            scheduleAt(slots[i], type, i * 100)
        }
    }

    // ── Pick 20 types weighted by user context ────────────────────────────
    private fun pickTwentyTypes(): List<Int> {
        val stats      = store.getPlayStats()
        val recentIds  = store.getRecentIds()
        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: 0L
        val hoursAgo   = (System.currentTimeMillis() - lastPlayed) / 3_600_000L
        val totalPlays = stats.values.sumOf { it.playCount }
        val favCount   = store.getFavorites().size
        val streak     = getCurrentStreak()
        val hour       = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val pool = mutableListOf<Int>()

        // Time-of-day mood — always include relevant ones
        if (hour in 5..10)   { repeat(2) { pool += T_MOOD_MORNING } }
        if (hour in 11..14)  { repeat(2) { pool += T_MOOD_NOON    } }
        if (hour in 15..19)  { repeat(2) { pool += T_MOOD_EVENING } }
        if (hour in 20..23)  { repeat(2) { pool += T_MOOD_NIGHT   } }
        // Always include morning + evening regardless
        pool += T_MOOD_MORNING
        pool += T_MOOD_EVENING

        // Comeback — weighted by inactivity
        when {
            hoursAgo >= 24  -> repeat(4) { pool += T_COMEBACK_1 }
            hoursAgo >= 6   -> repeat(3) { pool += T_COMEBACK_2 }
            hoursAgo >= 2   -> repeat(2) { pool += T_COMEBACK_3 }
            else            -> pool += T_COMEBACK_1
        }

        // Streak
        if (streak >= 2) repeat(2) { pool += T_STREAK }

        // Stats (real data)
        if (totalPlays >= 10) pool += T_WEEKLY_STATS
        if (totalPlays >= 3)  pool += T_TOP_SONG

        // Discovery
        if (recentIds.size >= 5) pool += T_DISCOVERY

        // Milestone
        if (isMilestone(totalPlays)) repeat(2) { pool += T_MILESTONE }

        // Onboarding hints (for new users)
        if (totalPlays < 10) {
            pool += T_FAVORITE_HINT
            pool += T_PLAYLIST_HINT
            pool += T_VIDEO_HINT
        }

        // Favorites
        if (favCount >= 3) pool += T_QUEUE_HINT
        else pool += T_FAVORITE_HINT

        // Tips — always several
        pool += T_TIP_GESTURE
        pool += T_TIP_EQ
        pool += T_TIP_LYRICS
        pool += T_TIP_SLEEP
        pool += T_TIP_SEARCH
        pool += T_TIP_GENERAL

        // New songs
        pool += T_NEW_SONGS

        // Social
        if (totalPlays >= 20) pool += T_INVITE

        pool.shuffle()
        // Ensure variety — deduplicate same type appearing > 3 times
        val result = mutableListOf<Int>()
        val counts = mutableMapOf<Int, Int>()
        for (t in pool) {
            val c = counts.getOrDefault(t, 0)
            if (c < 3) { result += t; counts[t] = c + 1 }
            if (result.size == 20) break
        }
        // Pad to 20 with tips if needed
        while (result.size < 20) {
            result += listOf(T_TIP_GENERAL, T_TIP_GESTURE, T_TIP_EQ, T_MOOD_EVENING,
                T_COMEBACK_1, T_FAVORITE_HINT, T_TIP_SEARCH).random()
        }
        return result.take(20)
    }

    // ── Generate 20 time slots spread across the waking day ──────────────
    private fun generateSlots(count: Int): List<Long> {
        val now  = System.currentTimeMillis()
        val cal  = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Distribute across windows, skipping windows that have fully passed
        data class Window(val range: IntRange, val slots: Int)
        val windows = listOf(
            Window(6..9,   3),   // early morning
            Window(10..12, 3),   // late morning
            Window(13..14, 2),   // early afternoon
            Window(15..17, 3),   // afternoon
            Window(18..19, 3),   // early evening
            Window(20..21, 3),   // evening
            Window(22..23, 3),   // night
        )

        val result = mutableListOf<Long>()

        // First: schedule a few SOON (next 1-4 hours) if none would fire today
        val soonHour = (hour + 1).coerceAtMost(23)
        if (hour < 22) {
            repeat(3) {
                val h = (hour + 1 + it).coerceAtMost(23)
                result += timeToday(h, Random.nextInt(5, 55))
            }
        }

        // Then spread rest across day
        for (w in windows) {
            if (w.range.last < hour) continue  // window fully passed
            repeat(w.slots) {
                val h = w.range.random()
                val m = Random.nextInt(0, 59)
                val ms = timeToday(h, m)
                // If this time has passed, push to tomorrow
                val adjusted = if (ms <= now) ms + 24 * 3_600_000L else ms
                result += adjusted
            }
        }

        // Deduplicate (within 5 min of each other)
        val deduped = mutableListOf<Long>()
        for (t in result.sorted()) {
            if (deduped.none { abs(it - t) < 5 * 60_000L }) deduped += t
        }

        return deduped.sorted().take(count)
    }

    private fun timeToday(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, Random.nextInt(0, 59))
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun abs(x: Long) = if (x < 0) -x else x

    private fun scheduleAt(triggerMs: Long, type: Int, requestCode: Int) {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_DAILY
            putExtra(EXTRA_TYPE, type)
        }
        val pi = PendingIntent.getBroadcast(context, requestCode + type, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (_: Exception) {
            try { alarm.set(AlarmManager.RTC_WAKEUP, triggerMs, pi) } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIRE — build and show notification
    // ═══════════════════════════════════════════════════════════════════════
    fun fireNotification(type: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        when (type) {
            T_COMEBACK_1, T_COMEBACK_2, T_COMEBACK_3 -> fireComeback(type)
            T_STREAK       -> fireStreak()
            T_TOP_SONG     -> fireTopSong()
            T_WEEKLY_STATS -> fireWeeklyStats()
            T_MOOD_MORNING -> fireMood(0)
            T_MOOD_NOON    -> fireMood(1)
            T_MOOD_EVENING -> fireMood(2)
            T_MOOD_NIGHT   -> fireMood(3)
            T_DISCOVERY    -> fireDiscovery()
            T_MILESTONE    -> fireMilestone()
            T_NEW_SONGS    -> fireNewSongs()
            T_QUEUE_HINT   -> fireQueueHint()
            T_INVITE       -> fireInvite()
            T_PLAYLIST_HINT -> firePlaylistHint()
            T_VIDEO_HINT   -> fireVideoHint()
            T_FAVORITE_HINT -> fireFavoriteHint()
            T_TIP_GESTURE  -> fireTip(0)
            T_TIP_EQ       -> fireTip(1)
            T_TIP_LYRICS   -> fireTip(2)
            T_TIP_SLEEP    -> fireTip(3)
            T_TIP_SEARCH   -> fireTip(4)
            T_TIP_GENERAL  -> fireTip(5)
            else           -> fireTip(Random.nextInt(0, 6))
        }
    }

    // ── Comeback (3 variants) ─────────────────────────────────────────────
    private fun fireComeback(variant: Int) {
        val stats = store.getPlayStats()
        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: return
        val hoursAgo = (System.currentTimeMillis() - lastPlayed) / 3_600_000L
        if (hoursAgo < 1) return  // user is active

        val (title, body) = when (variant) {
            T_COMEBACK_1 -> listOf(
                "Your music misses you 🎵" to "It's been ${hoursAgo}h. Time to listen?",
                "Missing the beat? 🎧" to "Your playlist is waiting. Come back!",
                "Silence is overrated 🎶" to "Open Deck and pick up where you left off.",
            ).random()
            T_COMEBACK_2 -> listOf(
                "Time for some music? ⏰" to "You haven't listened in a while. Let's go.",
                "Your daily soundtrack awaits 🎵" to "What are we listening to today?",
                "A little music goes a long way 🎸" to "Open Deck for an instant mood boost.",
            ).random()
            else -> listOf(
                "Still there? 👀" to "Your music library is ready whenever you are.",
                "Great music is waiting 🎧" to "Tap to open Deck and start listening.",
                "Deck misses you 🎶" to "Come back and listen to something great.",
            ).random()
        }
        post(ID_BASE + variant, CH_ENGAGE, title, body)
    }

    // ── Streak ───────────────────────────────────────────────────────────
    private fun fireStreak() {
        val streak = getCurrentStreak()
        if (streak < 2) return
        val (title, body) = when {
            streak >= 30 -> "🔥 ${streak}-day streak!" to "Unstoppable. You are a true music lover."
            streak >= 14 -> "⚡ ${streak} days straight!" to "Two weeks! Your dedication is inspiring."
            streak >= 7  -> "🎯 ${streak}-day streak!" to "A full week of great music. Keep going!"
            streak >= 3  -> "🎵 ${streak} days in a row!" to "Building momentum. Don't break it!"
            else         -> "🎶 Day $streak" to "You're on a roll. Keep the streak alive!"
        }
        post(ID_BASE + 10, CH_ACTIVITY, title, body)
    }

    // ── Top Song ─────────────────────────────────────────────────────────
    private fun fireTopSong() {
        val top = store.getPlayStats().maxByOrNull { it.value.playCount } ?: return
        if (top.value.playCount < 3) return
        val messages = listOf(
            "Your most played track 🎵" to "You've played it ${top.value.playCount} times. A real favourite.",
            "On repeat mode 🔁" to "${top.value.playCount} plays and counting. You love this one.",
            "Your anthem this week 🏆" to "Played ${top.value.playCount} times. Tap to play it now.",
        )
        val (title, body) = messages.random()
        post(ID_BASE + 11, CH_ENGAGE, title, body)
    }

    // ── Weekly Stats ─────────────────────────────────────────────────────
    private fun fireWeeklyStats() {
        val totalMs = store.getTotalPlaytimeMs()
        val hours   = totalMs / 3_600_000L
        val mins    = (totalMs % 3_600_000L) / 60_000L
        if (hours == 0L && mins < 5L) return
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val messages = listOf(
            "Your Deck stats 📊" to "You've listened for $timeStr total. Impressive!",
            "Listening summary 🎧" to "$timeStr of music. That's dedication!",
            "Your music journey 🎵" to "$timeStr listened. Check your full history in the app.",
        )
        val (title, body) = messages.random()
        post(ID_BASE + 12, CH_ENGAGE, title, body, screen = "stats")
    }

    // ── Mood (4 time slots) ───────────────────────────────────────────────
    private fun fireMood(slot: Int) {
        val (title, body) = when (slot) {
            0 -> listOf(  // morning
                "Good morning! 🌅" to "Start the day right with your music.",
                "Rise and vibe ☀️" to "Your morning playlist is ready in Deck.",
                "Morning energy 🎶" to "The best mornings start with great music.",
            ).random()
            1 -> listOf(  // noon
                "Lunchtime playlist? 🍽️" to "Take a break and listen to something good.",
                "Afternoon pick-me-up 🎵" to "Music is the best midday reset.",
                "Midday vibes 🎧" to "Your music is ready for the afternoon.",
            ).random()
            2 -> listOf(  // evening
                "Wind down time 🌆" to "Relax with your favourite tracks in Deck.",
                "Evening session 🌇" to "The perfect time to lose yourself in music.",
                "End the day right 🎵" to "Great music for a great evening.",
            ).random()
            else -> listOf(  // night
                "Late night listening 🌙" to "Best music hits different at night.",
                "Night owl playlist 🦉" to "The city sleeps. Your music doesn't.",
                "Midnight vibes 🌃" to "Perfect time for your favourite tracks.",
            ).random()
        }
        post(ID_BASE + 13 + slot, CH_ENGAGE, title, body)
    }

    // ── Discovery ─────────────────────────────────────────────────────────
    private fun fireDiscovery() {
        val stats    = store.getPlayStats()
        val recent   = store.getRecentIds().take(5).toSet()
        val forgotten = stats.entries
            .filter { it.key !in recent && it.value.playCount >= 2 }
            .sortedBy { it.value.lastPlayed }
            .firstOrNull() ?: return
        val daysAgo = (System.currentTimeMillis() - forgotten.value.lastPlayed) / 86_400_000L
        if (daysAgo < 7) return
        val messages = listOf(
            "Remember this one? 🎵" to "Haven't heard it in $daysAgo days. Time to revisit.",
            "Old favourite rediscovered 🎶" to "A song you loved is waiting to be played again.",
            "Blast from the past 🎧" to "One of your songs hasn't been played in $daysAgo days.",
        )
        val (title, body) = messages.random()
        post(ID_BASE + 17, CH_TIPS, title, body)
    }

    // ── Milestone ────────────────────────────────────────────────────────
    private fun fireMilestone() {
        val total = store.getPlayStats().values.sumOf { it.playCount }
        val ms    = getMilestone(total) ?: return
        val prev  = prefs.getInt(KEY_LAST_MILESTONE, 0)
        if (ms <= prev) return
        prefs.edit().putInt(KEY_LAST_MILESTONE, ms).apply()
        val (title, body) = when {
            ms >= 1000 -> "🏆 1,000 songs played!" to "Legendary listener. You are the real deal."
            ms >= 500  -> "🌟 500 plays!" to "Half a thousand songs. Incredible dedication."
            ms >= 100  -> "⭐ 100 songs played!" to "You're building a serious listening history!"
            ms >= 50   -> "🎯 50 plays!" to "50 songs in! You're finding your groove with Deck."
            ms >= 25   -> "🎵 25 plays!" to "Getting started. Your music story is just beginning."
            else       -> "🎶 $ms plays!" to "Every song counts. Keep listening!"
        }
        post(ID_BASE + 18, CH_ACTIVITY, title, body)
    }

    // ── New Songs ────────────────────────────────────────────────────────
    private fun fireNewSongs() {
        val count = prefs.getInt("recently_added_count", 0)
        if (count < 2) {
            // Fire a generic library tip instead
            post(ID_BASE + 19, CH_ENGAGE,
                "Your music library 📚",
                "Tap Refresh in the app to scan for new music files on your device.")
            return
        }
        val messages = listOf(
            "New music this week 🎵" to "$count new songs added to your library. Ready to explore?",
            "Fresh additions 🎶" to "$count songs are new in your library. Tap to discover them.",
            "Library update 📚" to "You have $count new tracks. Check them out in Recently Added.",
        )
        val (title, body) = messages.random()
        post(ID_BASE + 19, CH_ENGAGE, title, body)
    }

    // ── Queue Hint ───────────────────────────────────────────────────────
    private fun fireQueueHint() {
        val favCount = store.getFavorites().size
        if (favCount < 3) { fireFavoriteHint(); return }
        val messages = listOf(
            "Your favourites are ready 💜" to "You have $favCount favourite songs. Play them all now.",
            "Shuffle your best $favCount ❤️" to "Tap Favourites in the app for your personal playlist.",
            "Queue up your top picks 🎵" to "$favCount great songs waiting in your Favourites.",
        )
        val (title, body) = messages.random()
        post(ID_BASE + 20, CH_ENGAGE, title, body, screen = "music")
    }

    // ── Onboarding / Discovery hints ─────────────────────────────────────
    private fun fireFavoriteHint() {
        post(ID_BASE + 21, CH_TIPS,
            "Heart your favourites ❤️",
            "Tap the heart on any song to add it to Favourites for quick access.")
    }

    private fun firePlaylistHint() {
        post(ID_BASE + 22, CH_TIPS,
            "Create a playlist 📋",
            "Long-press any song and tap 'Add to Playlist' to start building your collection.")
    }

    private fun fireVideoHint() {
        post(ID_BASE + 23, CH_TIPS,
            "Deck plays videos too 🎬",
            "Open the Videos tab to play all your local video files with advanced controls.")
    }

    private fun fireInvite() {
        post(ID_BASE + 24, CH_ENGAGE,
            "Share Deck with friends 🤝",
            "Know someone who loves music? Tell them about Deck — it's free!")
    }

    // ── Tips (6 variants) ────────────────────────────────────────────────
    private fun fireTip(variant: Int) {
        val tips = listOf(
            "💡 Gesture tip" to "Swipe left/right on the album art in Now Playing to skip tracks.",
            "🎚️ Equalizer" to "Long-press a song and tap EQ to set a custom equalizer profile just for it.",
            "🎤 Synced lyrics" to "Add a .lrc file with the same name as your song for real-time lyrics.",
            "⏱️ Sleep timer" to "Tap the moon icon in Now Playing to set a sleep timer. Falls asleep to music!",
            "🔍 Quick search" to "Tap the search icon on any tab to instantly find songs, videos, and artists.",
            "⚡ Speed control" to "Long-press any video to play at 2× speed. Or tap the speed button for options.",
        )
        val (title, body) = tips[variant.coerceIn(0, tips.size - 1)]
        post(ID_BASE + 30 + variant, CH_TIPS, title, body)
    }

    // ── Post notification ─────────────────────────────────────────────────
    private fun post(id: Int, channel: String, title: String, body: String, screen: String? = null) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                screen?.let { putExtra("open_screen", it) }
            }
            val pi = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(if (channel == CH_ACTIVITY) NotificationCompat.PRIORITY_HIGH
                             else NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: Exception) {}
    }

    // ── Streak calculation ────────────────────────────────────────────────
    fun getCurrentStreak(): Int {
        val stats = store.getPlayStats()
        if (stats.isEmpty()) return 0
        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: return 0
        val today      = todayKey()
        val lastDay    = msToDay(lastPlayed)
        val prevDay    = prefs.getString(KEY_STREAK_DAY, "") ?: ""
        var streak     = prefs.getInt(KEY_STREAK, 0)
        return when {
            lastDay == today -> {
                if (prevDay != today) {
                    val yesterday = msToDay(System.currentTimeMillis() - 86_400_000L)
                    streak = if (prevDay == yesterday) streak + 1 else 1
                    prefs.edit().putInt(KEY_STREAK, streak).putString(KEY_STREAK_DAY, today).apply()
                }
                streak
            }
            lastDay == msToDay(System.currentTimeMillis() - 86_400_000L) -> streak
            else -> 0
        }
    }

    private fun todayKey() = msToDay(System.currentTimeMillis())
    private fun msToDay(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }
    private fun isMilestone(p: Int) = p in listOf(5, 10, 25, 50, 100, 250, 500, 1000)
    private fun getMilestone(p: Int) = listOf(5, 10, 25, 50, 100, 250, 500, 1000).lastOrNull { p >= it }
}

// ── Alarm Receiver ────────────────────────────────────────────────────────
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(DeckNotificationEngine.EXTRA_TYPE, -1)
        if (type == -1) return
        DeckNotificationEngine(context).fireNotification(type)
    }
}
