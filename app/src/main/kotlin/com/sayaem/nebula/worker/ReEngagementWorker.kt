package com.sayaem.nebula.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sayaem.nebula.data.local.LocalDataStore
import com.sayaem.nebula.player.DeckNotificationManager


class ReEngagementWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val store = LocalDataStore(applicationContext)
        val notif = DeckNotificationManager(applicationContext)

        val recentIds  = store.getRecentIds()
        val stats      = store.getPlayStats()
        val lastPlayed = stats.values.maxOfOrNull { it.lastPlayed } ?: 0L
        val hoursSince = (System.currentTimeMillis() - lastPlayed) / 3_600_000

        if (hoursSince >= 24 && recentIds.isNotEmpty()) {
            // Show come-back notification
            notif.showComeBackNotification("your favorite songs")
        }

        // Weekly stats (every 7th call roughly)
        val callCount = store.prefs.getInt("worker_calls", 0) + 1
        store.prefs.edit().putInt("worker_calls", callCount).apply()
        if (callCount % 7 == 0) {
            val totalPlays  = stats.values.sumOf { it.playCount }
            val minutesEst  = (totalPlays * 3.5).toInt()
            val topArtist   = "your top artist" // would need song map for real name
            notif.showWeeklyStats(minutesEst, topArtist)
        }

        return Result.success()
    }
}
