package com.sayaem.nebula.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.MoreExecutors


class MediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = SessionToken(context, ComponentName(context, DeckPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val ctrl = future.get()
                when (intent.action) {
                    DeckNotificationManager.ACTION_PLAY_PAUSE ->
                        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                    DeckNotificationManager.ACTION_NEXT -> ctrl.seekToNextMediaItem()
                    DeckNotificationManager.ACTION_PREV -> ctrl.seekToPreviousMediaItem()
                }
                // Release after command
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    MediaController.releaseFuture(future)
                }, 500)
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }
}
