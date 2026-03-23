package com.sayaem.nebula.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sayaem.nebula.MainActivity
import com.sayaem.nebula.data.local.LocalDataStore
import java.util.Calendar
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════
// DECK NOTIFICATION ENGINE
// Handles all local push notifications — 10 daily engagement types.
// Each type uses real user data from LocalDataStore so messages are personal.
// ═══════════════════════════════════════════════════════════════════════════
class DeckNotificationEngine(private val context: Context) {

    companion object {
        // ── Channels ──────────────────────────────────────────────────
        const val CH_PLAYBACK   = "deck_playback"
        const val CH_ENGAGE     = "deck_engage"     // daily engagement
        const val CH_ACTIVITY   = "deck_activity"   // in-session (milestone, streak)
        const val CH_TIPS       = "deck_tips"        // tips & discovery

        // ── Notification IDs ──────────────────────────────────────────
        const val ID_PLAYBACK   = 1
        const val ID_COMEBACK   = 100
        const val ID_STREAK     = 101
        const val ID_TOP_SONG   = 102
        const val ID_WEEKLY     = 103
        const val ID_MOOD       = 104
        const val ID_DISCOVERY  = 105
        const val ID_MILESTONE  = 106
        const val ID_NEW_SONGS  = 107
        const val ID_QUEUE_HINT = 108
        const val ID_TIP        = 109

        // ── Alarm actions ─────────────────────────────────────────────
        const val ACTION_DAILY_NOTIF = "com.sayaem.nebula.DAILY_NOTIF"
        const val EXTRA_NOTIF_TYPE   = "notif_type"

        // ── Prefs keys ────────────────────────────────────────────────
        private const val PREFS = "deck_notif_engine"
        private const val KEY_LAST_SCHEDULE  = "last_sched_day"
        private const val KEY_SONG_COUNT_PREV = "prev_song_count"
        private const val KEY_STREAK          = "listening_streak"
        private const val KEY_STREAK_LAST_DAY = "streak_last_day"
    }

    private val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store = LocalDataStore(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init { createChannels() }

    // ── Channel setup ─────────────────────────────────────────────────────
    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(NotificationChannel(
            CH_PLAYBACK, "Now Playing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Playback controls in notification shade"; setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_ENGAGE, "Daily Music", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Daily listening reminders and stats"
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_ACTIVITY, "Activity", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Streaks, milestones, and achievements"
        })
        nm.createNotificationChannel(NotificationChannel(
            CH_TIPS, "Tips & Discovery", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Tips, hidden features, and music discovery"
        })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCHEDULE: 10 notifications at random times throughout the day
    // Called once per day (from DailyNotificationWorker or on app open)
    // ═══════════════════════════════════════════════════════════════════════
    fun scheduleDailyNotifications() {
        val today = todayKey()
        if (prefs.getString(KEY_LAST_SCHEDULE, "") == today) return  // already scheduled today

        prefs.edit().putString(KEY_LAST_SCHEDULE, today).apply()

        val notifTypes = pickNotificationTypesForToday()
        val timeSlots  = generateRandomTimeSlots(notifTypes.size)

        notifTypes.forEachIndexed { i, type ->
            scheduleAt(timeSlots[i], type, baseRequestCode = i * 100)
        }
    }

    // ── Pick which 10 notification types fire today ───────────────────────
    // Uses real user data to decide relevance
    private fun pickNotificationTypesForToday(): List<Int> {
        val stats       = store.getPlayStats()
        val recentIds   = store.getRecentIds()
        val lastPlayed  = stats.values.maxOfOrNull { it.lastPlayed } ?: 0L
        val hoursSince  = (System.currentTimeMillis() - lastPlayed) / 3_600_000L
        val totalPlays  = stats.values.sumOf { it.playCount }
        val favCount    = store.getFavorites().size
        val streak      = getCurrentStreak()

        // Pool of all types with weights based on user context
        val pool = mutableListOf<Int>()

        // Always include mood-based (morning/evening) — 2 slots
        pool += NOTIF_MOOD_MORNING
        pool += NOTIF_MOOD_EVENING

        // Comeback if not active recently
        if (hoursSince >= 3) repeat(2) { pool += NOTIF_COMEBACK }

        // Streak if active
        if (streak >= 2) pool += NOTIF_STREAK

        // Stats if enough data
        if (totalPlays >= 10) pool += NOTIF_WEEKLY_STATS

        // Top song
        if (recentIds.isNotEmpty()) pool += NOTIF_TOP_SONG

        // Discovery — random song from library
        if (recentIds.size >= 5) pool += NOTIF_DISCOVERY

        // Milestone check
        if (isMilestone(totalPlays)) pool += NOTIF_MILESTONE

        // Tips — always helpful
        repeat(2) { pool += NOTIF_TIP }

        // Favorites queue hint
        if (favCount >= 3) pool += NOTIF_QUEUE_HINT

        // New songs added this week
        pool += NOTIF_NEW_SONGS

        // Shuffle and take exactly 10
        pool.shuffle()
        return pool.take(10).ifEmpty {
            // Fallback: 10 tips if no data available
            List(10) { NOTIF_TIP }
        }
    }

    // ── Generate 10 non-overlapping random time slots ─────────────────────
    // Distributes across the day: morning (7-11), afternoon (12-17), evening (18-22)
    private fun generateRandomTimeSlots(count: Int): List<Long> {
        val now   = System.currentTimeMillis()
        val slots = mutableSetOf<Long>()

        // Distribute into time windows
        val windows = listOf(
            7..11,    // morning — 3 slots
            12..17,   // afternoon — 4 slots
            18..22,   // evening — 3 slots
        )
        val distribution = listOf(3, 4, 3)

        var idx = 0
        windows.forEachIndexed { wi, window ->
            repeat(distribution[wi]) {
                if (idx < count) {
                    val hour   = window.random()
                    val minute = (0..59).random()
                    val cal    = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, Random.nextInt(0, 59))
                        set(Calendar.MILLISECOND, 0)
                        // If this time has already passed today, skip to next day
                        if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                    }
                    slots += cal.timeInMillis
                    idx++
                }
            }
        }

        // Fill remaining with random times if needed
        while (slots.size < count) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, (7..22).random())
                set(Calendar.MINUTE, (0..59).random())
                set(Calendar.SECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            slots += cal.timeInMillis
        }

        return slots.sorted().take(count)
    }

    private fun scheduleAt(triggerMs: Long, notifType: Int, baseRequestCode: Int) {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_NOTIF
            putExtra(EXTRA_NOTIF_TYPE, notifType)
        }
        val pi = PendingIntent.getBroadcast(
            context, baseRequestCode + notifType,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                // Can't schedule exact — use inexact
                alarm.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (_: Exception) {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIRE: Build and show the actual notification with real content
    // ═══════════════════════════════════════════════════════════════════════
    fun fireNotification(type: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        when (type) {
            NOTIF_COMEBACK       -> fireComeback()
            NOTIF_STREAK         -> fireStreak()
            NOTIF_TOP_SONG       -> fireTopSong()
            NOTIF_WEEKLY_STATS   -> fireWeeklyStats()
            NOTIF_MOOD_MORNING   -> fireMood(morning = true)
            NOTIF_MOOD_EVENING   -> fireMood(morning = false)
            NOTIF_DISCOVERY      -> fireDiscovery()
            NOTIF_MILESTONE      -> fireMilestone()
            NOTIF_NEW_SONGS      -> fireNewSongs()
            NOTIF_QUEUE_HINT     -> fireQueueHint()
            NOTIF_TIP            -> fireTip()
        }
    }

    // ── 1. COMEBACK — fires after inactivity ─────────────────────────────
    private fun fireComeback() {
        val stats   = store.getPlayStats()
        val recentIds = store.getRecentIds()
        if (recentIds.isEmpty()) return

        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: return
        val hoursAgo   = (System.currentTimeMillis() - lastPlayed) / 3_600_000L
        if (hoursAgo < 2) return  // too soon — user is active

        val messages = listOf(
            "Your music is waiting 🎵" to "Pick up where you left off",
            "Time for a break? 🎧" to "Your playlist is ready when you are",
            "Missing the beat?" to "Come back and listen to something great",
            "Your ears deserve it 🎶" to "Deck is ready with your music",
        )
        val (title, body) = messages.random()

        post(ID_COMEBACK, CH_ENGAGE, title, body, autoCancel = true)
    }

    // ── 2. STREAK — consecutive days listening ───────────────────────────
    private fun fireStreak() {
        val streak = getCurrentStreak()
        if (streak < 2) return

        val (title, body) = when {
            streak >= 30 -> "🔥 ${streak}-day streak!" to "You're on fire! Unreal dedication."
            streak >= 14 -> "🎯 ${streak} days straight!" to "Two weeks of great music. Keep it up!"
            streak >= 7  -> "⚡ ${streak}-day streak!" to "A full week of listening. Legend."
            streak >= 3  -> "🎵 ${streak} days in a row!" to "The streak continues. Don't break it!"
            else         -> "Day $streak 🎶" to "You listened yesterday and today. Keep going!"
        }
        post(ID_STREAK, CH_ACTIVITY, title, body, autoCancel = true)
    }

    // ── 3. TOP SONG — your most played ──────────────────────────────────
    private fun fireTopSong() {
        val stats   = store.getPlayStats()
        val top     = stats.maxByOrNull { it.value.playCount } ?: return
        val plays   = top.value.playCount
        if (plays < 3) return

        val title = "Your top track this week 🎵"
        val body  = "You've played it $plays times. What a hit."
        post(ID_TOP_SONG, CH_ENGAGE, title, body, autoCancel = true)
    }

    // ── 4. WEEKLY STATS ──────────────────────────────────────────────────
    private fun fireWeeklyStats() {
        val totalMs  = store.getTotalPlaytimeMs()
        val hours    = totalMs / 3_600_000L
        val minutes  = (totalMs % 3_600_000L) / 60_000L
        if (hours == 0L && minutes < 5L) return

        val timeStr  = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        val title    = "Your Deck stats 📊"
        val body     = "You've listened for $timeStr total. Tap to see your full history."
        post(ID_WEEKLY, CH_ENGAGE, title, body, screen = "stats", autoCancel = true)
    }

    // ── 5. MOOD — time-of-day based ──────────────────────────────────────
    private fun fireMood(morning: Boolean) {
        val (title, body) = if (morning) {
            val greetings = listOf(
                "Good morning! 🌅" to "Start the day right with your music",
                "Rise and shine 🎵" to "Your morning playlist is ready",
                "Morning vibes 🌤" to "Set the tone for today with Deck",
                "New day, great music ☀️" to "What are we listening to today?",
            )
            greetings.random()
        } else {
            val evenings = listOf(
                "Wind down time 🌙" to "Relax with your favorite tracks",
                "Evening session 🎧" to "The perfect time to lose yourself in music",
                "End the day right 🌆" to "Your playlist is waiting",
                "Night vibes 🌃" to "Great music for a great evening",
            )
            evenings.random()
        }
        val id = if (morning) ID_MOOD else ID_MOOD + 1
        post(id, CH_ENGAGE, title, body, autoCancel = true)
    }

    // ── 6. DISCOVERY — song you haven't heard in a while ─────────────────
    private fun fireDiscovery() {
        val stats    = store.getPlayStats()
        val recentIds = store.getRecentIds().take(5).toSet()

        // Find a song played before but not recently
        val forgotten = stats.entries
            .filter { it.key !in recentIds && it.value.playCount >= 2 }
            .sortedBy { it.value.lastPlayed }
            .firstOrNull() ?: return

        val daysAgo = (System.currentTimeMillis() - forgotten.value.lastPlayed) /
                        (24L * 3_600_000L)
        if (daysAgo < 7) return

        val title = "Remember this? 🎵"
        val body  = "You haven't played one of your songs in $daysAgo days. Time to revisit."
        post(ID_DISCOVERY, CH_TIPS, title, body, autoCancel = true)
    }

    // ── 7. MILESTONE — play count achievements ───────────────────────────
    private fun fireMilestone() {
        val totalPlays = store.getPlayStats().values.sumOf { it.playCount }
        val milestone  = getMilestone(totalPlays) ?: return
        val prev       = prefs.getInt("last_milestone", 0)
        if (milestone <= prev) return
        prefs.edit().putInt("last_milestone", milestone).apply()

        val (title, body) = when {
            milestone >= 1000 -> "🏆 1,000 songs played!" to "You are a true music lover. Legendary."
            milestone >= 500  -> "🌟 500 songs played!" to "Half a thousand plays. You're dedicated."
            milestone >= 100  -> "⭐ 100 songs played!" to "You're building a real listening history!"
            milestone >= 50   -> "🎯 50 songs played!" to "You're finding your groove with Deck."
            else               -> "🎵 $milestone songs played!" to "Your music journey is growing!"
        }
        post(ID_MILESTONE, CH_ACTIVITY, title, body, autoCancel = true)
    }

    // ── 8. NEW SONGS — songs added this week ─────────────────────────────
    private fun fireNewSongs() {
        val weekAgo     = System.currentTimeMillis() - 7L * 24 * 3_600_000
        val recentCount = store.prefs.getInt("recently_added_count", 0)
        if (recentCount < 2) return

        val title = "New music this week 🎵"
        val body  = "$recentCount new songs are in your library. Ready to explore?"
        post(ID_NEW_SONGS, CH_ENGAGE, title, body, autoCancel = true)
    }

    // ── 9. QUEUE HINT — favorites playlist ───────────────────────────────
    private fun fireQueueHint() {
        val favCount = store.getFavorites().size
        if (favCount < 3) return

        val hints = listOf(
            "Your favorites are calling 💜" to "You have $favCount favorited songs waiting.",
            "Queue up your best $favCount ❤️" to "Your favorites playlist is stacked.",
            "Shuffle your favorites 🎲" to "$favCount great songs. Tap to start.",
        )
        val (title, body) = hints.random()
        post(ID_QUEUE_HINT, CH_ENGAGE, title, body, screen = "music", autoCancel = true)
    }

    // ── 10. TIPS — feature discovery ─────────────────────────────────────
    private fun fireTip() {
        val tips = listOf(
            "💡 Did you know?" to "Swipe left/right on album art to skip tracks",
            "🎚 Pro tip" to "Long-press any song to set a custom EQ profile for it",
            "⏱ Sleep timer" to "Use the sleep timer in Now Playing to fall asleep to music",
            "🎤 Lyrics" to "Add a .lrc file next to your music for synced lyrics",
            "📁 Folders" to "The Videos tab shows your files organized by folder",
            "🔁 Crossfade" to "Enable crossfade in Settings for smooth transitions",
            "📊 Stats" to "Check your listening history in the More tab",
            "❤️ Favorites" to "Tap the heart on any song to add it to Favorites",
            "🔍 Search" to "Tap the search icon on any tab to find songs instantly",
            "⚡ Speed" to "Change playback speed from Now Playing for podcasts & audiobooks",
            "📱 Lock screen" to "Control playback from your lock screen — swipe down to see controls",
        )
        val (title, body) = tips[Random.nextInt(tips.size)]
        post(ID_TIP, CH_TIPS, title, body, autoCancel = true)
    }

    // ── Streak calculation ────────────────────────────────────────────────
    fun getCurrentStreak(): Int {
        val stats      = store.getPlayStats()
        if (stats.isEmpty()) return 0
        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: return 0
        val today      = todayKey()
        val lastDay    = msToDateKey(lastPlayed)
        val streakLastDay = prefs.getString(KEY_STREAK_LAST_DAY, "") ?: ""
        var streak     = prefs.getInt(KEY_STREAK, 0)

        return when {
            lastDay == today -> {
                // Played today — update streak if this is a new day
                if (streakLastDay != today) {
                    val yesterday = yesterdayKey()
                    streak = if (streakLastDay == yesterday) streak + 1 else 1
                    prefs.edit()
                        .putInt(KEY_STREAK, streak)
                        .putString(KEY_STREAK_LAST_DAY, today)
                        .apply()
                }
                streak
            }
            lastDay == yesterdayKey() -> streak  // played yesterday — streak intact
            else -> 0  // gap — streak broken
        }
    }

    // ── Post a notification ───────────────────────────────────────────────
    private fun post(
        id: Int, channel: String, title: String, body: String,
        screen: String? = null, autoCancel: Boolean = true, art: Bitmap? = null,
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                screen?.let { putExtra("open_screen", it) }
            }
            val pi = PendingIntent.getActivity(context, id,
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(autoCancel)
                .setPriority(
                    if (channel == CH_ACTIVITY) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )

            if (art != null) builder.setLargeIcon(art)
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: Exception) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun todayKey()     = msToDateKey(System.currentTimeMillis())
    private fun yesterdayKey() = msToDateKey(System.currentTimeMillis() - 24 * 3_600_000L)

    private fun msToDateKey(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun isMilestone(plays: Int) = plays in listOf(10, 25, 50, 100, 250, 500, 1000)
    private fun getMilestone(plays: Int) = listOf(10, 25, 50, 100, 250, 500, 1000)
        .lastOrNull { plays >= it }

    // ── Notification type constants ───────────────────────────────────────
    companion object {
        const val NOTIF_COMEBACK      = 1
        const val NOTIF_STREAK        = 2
        const val NOTIF_TOP_SONG      = 3
        const val NOTIF_WEEKLY_STATS  = 4
        const val NOTIF_MOOD_MORNING  = 5
        const val NOTIF_MOOD_EVENING  = 6
        const val NOTIF_DISCOVERY     = 7
        const val NOTIF_MILESTONE     = 8
        const val NOTIF_NEW_SONGS     = 9
        const val NOTIF_QUEUE_HINT    = 10
        const val NOTIF_TIP           = 11
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ALARM RECEIVER — wakes up at the scheduled times and fires the notification
// ═══════════════════════════════════════════════════════════════════════════
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(DeckNotificationEngine.EXTRA_NOTIF_TYPE, -1)
        if (type == -1) return
        DeckNotificationEngine(context).fireNotification(type)
    }
}
