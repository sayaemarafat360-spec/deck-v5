package com.sayaem.nebula.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

class MediaReceiver : BroadcastReceiver() {

    // Track double/triple press timing for headphone buttons
    companion object {
        private var lastPressTime = 0L
        private var pressCount    = 0
        private const val MULTI_PRESS_WINDOW = 500L  // ms window for multi-press
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Handle headphone/hardware media buttons
        if (action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            if (keyEvent.action != KeyEvent.ACTION_UP) return  // fire only on key-up

            val now = SystemClock.elapsedRealtime()
            if (now - lastPressTime < MULTI_PRESS_WINDOW) {
                pressCount++
            } else {
                pressCount = 1
            }
            lastPressTime = now

            // Wait briefly to distinguish single vs double vs triple press
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val count = pressCount
                pressCount = 0
                withController(context) { ctrl ->
                    when (count) {
                        1    -> if (ctrl.isPlaying) ctrl.pause() else ctrl.play()   // single = play/pause
                        2    -> ctrl.seekToNextMediaItem()                           // double = next
                        else -> ctrl.seekToPreviousMediaItem()                       // triple = prev
                    }
                }
            }, MULTI_PRESS_WINDOW)
            return
        }

        // Handle notification action buttons
        withController(context) { ctrl ->
            when (action) {
                DeckNotificationManager.ACTION_PLAY_PAUSE ->
                    if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                DeckNotificationManager.ACTION_NEXT -> ctrl.seekToNextMediaItem()
                DeckNotificationManager.ACTION_PREV -> ctrl.seekToPreviousMediaItem()
            }
        }
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        val token  = SessionToken(context, ComponentName(context, DeckPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val ctrl = future.get()
                block(ctrl)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    MediaController.releaseFuture(future)
                }, 500)
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }
}
