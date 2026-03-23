package com.sayaem.nebula.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sayaem.nebula.notifications.DeckNotificationEngine

class ReEngagementWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        // Update streak + schedule today's 10 notifications
        val engine = DeckNotificationEngine(applicationContext)
        engine.getCurrentStreak()           // updates streak counter
        engine.scheduleDailyNotifications() // schedules today's 10 alarms
        return Result.success()
    }
}
