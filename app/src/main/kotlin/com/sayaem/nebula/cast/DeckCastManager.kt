package com.sayaem.nebula.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.sayaem.nebula.data.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CastState {
    DISCONNECTED,   // No cast device connected
    CONNECTING,     // Connecting to device
    CONNECTED,      // Connected, idle
    PLAYING,        // Actively casting video
    PAUSED,         // Casting but paused
    ERROR           // Connection failed
}

class DeckCastManager(private val context: Context) {

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState = _castState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var remoteClient: RemoteMediaClient? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentSession = session
            remoteClient = session.remoteMediaClient
            _castState.value = CastState.CONNECTED
            _connectedDeviceName.value = session.castDevice?.friendlyName
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            currentSession = null; remoteClient = null
            _castState.value = CastState.DISCONNECTED
            _connectedDeviceName.value = null
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentSession = session
            remoteClient = session.remoteMediaClient
            _castState.value = CastState.CONNECTED
            _connectedDeviceName.value = session.castDevice?.friendlyName
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _castState.value = CastState.ERROR
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _castState.value = CastState.ERROR
        }
        override fun onSessionStarting(session: CastSession) { _castState.value = CastState.CONNECTING }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) { _castState.value = CastState.CONNECTING }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    fun init() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            // Sync initial state
            currentSession = castContext?.sessionManager?.currentCastSession
            remoteClient = currentSession?.remoteMediaClient
            _castState.value = if (currentSession != null) CastState.CONNECTED else CastState.DISCONNECTED
            _connectedDeviceName.value = currentSession?.castDevice?.friendlyName
        } catch (_: Exception) {
            _castState.value = CastState.ERROR
        }
    }

    fun release() {
        try { castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java) }
        catch (_: Exception) {}
    }

    // Load and play a video on the connected Cast device
    fun castVideo(video: Song, position: Long = 0L): Boolean {
        val client = remoteClient ?: return false
        try {
            val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, video.title)
            }
            // Build content URL from file URI — Cast requires HTTP/HTTPS
            // For local files we use the file URI directly (works on same-network devices)
            val mediaInfo = MediaInfo.Builder(video.uri.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("video/*")
                .setMetadata(meta)
                .build()
            val loadRequest = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(position.toDouble() / 1000.0)
                .build()
            client.load(loadRequest)
            _castState.value = CastState.PLAYING
            return true
        } catch (_: Exception) {
            _castState.value = CastState.ERROR
            return false
        }
    }

    fun togglePlayPause() {
        val client = remoteClient ?: return
        try {
            if (client.isPlaying) {
                client.pause()
                _castState.value = CastState.PAUSED
            } else {
                client.play()
                _castState.value = CastState.PLAYING
            }
        } catch (_: Exception) {}
    }

    fun seekTo(ms: Long) {
        try {
            val options = com.google.android.gms.cast.MediaSeekOptions.Builder()
                .setPosition(ms)
                .build()
            remoteClient?.seek(options)
        } catch (_: Exception) {}
    }

    fun stop() {
        try { remoteClient?.stop() } catch (_: Exception) {}
        _castState.value = CastState.CONNECTED
    }

    fun endSession() {
        try { castContext?.sessionManager?.endCurrentSession(true) } catch (_: Exception) {}
    }

    val isConnected get() = _castState.value in listOf(CastState.CONNECTED, CastState.PLAYING, CastState.PAUSED)
    val isPlaying   get() = _castState.value == CastState.PLAYING
}
