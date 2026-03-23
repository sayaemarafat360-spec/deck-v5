package com.sayaem.nebula.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Re-schedule daily notifications after the device reboots
// (AlarmManager alarms are cleared on reboot — this brings them back)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Force reschedule by clearing the "already scheduled today" flag
        context.getSharedPreferences("deck_notif_engine", Context.MODE_PRIVATE)
            .edit().remove("last_sched_day").apply()
        // Schedule today's notifications
        DeckNotificationEngine(context).scheduleDailyNotifications()
    }
}
