package com.sayaem.nebula.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sayaem.nebula.MainActivity


class DeckPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // Expose the real ExoPlayer so ViewModel can access audioSessionId
    var exoPlayer: ExoPlayer? = null
        private set

    inner class LocalBinder : Binder() {
        fun getService() = this@DeckPlaybackService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        // Return our binder when the app binds (not the session binder)
        if (intent?.action == ACTION_BIND) return binder
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer ?: return)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_BIND = "com.sayaem.nebula.BIND_PLAYER"
    }
}
