package com.sayaem.nebula.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sayaem.nebula.data.models.PlaybackState
import com.sayaem.nebula.data.models.RepeatMode
import com.sayaem.nebula.data.models.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class PlayerController(private val context: Context) {

    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var currentQueue: List<Song> = emptyList()
    private var _service: DeckPlaybackService? = null

    // Safe accessor — throws if not ready (use isReady to check first)
    val player: ExoPlayer get() = _service?.exoPlayer
        ?: throw IllegalStateException("Service not bound yet")

    // Null-safe accessor for VideoPlayerScreen
    val playerOrNull: ExoPlayer? get() = _service?.exoPlayer

    val isReady: Boolean get() = _service?.exoPlayer != null

    // Callback when audio session is ready for EQ
    var onAudioSessionReady: ((Int) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            _service = (binder as? DeckPlaybackService.LocalBinder)?.getService()
            _service?.exoPlayer?.addListener(playerListener)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _service = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(s: Int) {
            syncState()
            if (s == Player.STATE_READY) {
                val sid = _service?.exoPlayer?.audioSessionId ?: 0
                if (sid != 0) onAudioSessionReady?.invoke(sid)
            }
        }
        override fun onIsPlayingChanged(p: Boolean) {
            syncState()
            if (p) startPositionUpdates() else stopPositionUpdates()
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            syncState()
            // Real crossfade: duck out old track, fade in new one
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && crossfadeSeconds > 0) {
                scope.launch { applyCrossfade() }
            }
        }
        override fun onShuffleModeEnabledChanged(e: Boolean) { syncState() }
        override fun onRepeatModeChanged(m: Int) { syncState() }
    }

    init {
        // Start + bind to service
        val intent = Intent(context, DeckPlaybackService::class.java).apply {
            action = DeckPlaybackService.ACTION_BIND
        }
        context.startService(Intent(context, DeckPlaybackService::class.java))
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val p = _service?.exoPlayer ?: return
        currentQueue = songs
        p.clearMediaItems()
        p.addMediaItems(songs.map { MediaItem.fromUri(it.uri) })
        p.seekToDefaultPosition(startIndex)
        p.prepare()
        p.play()
        startPositionUpdates()
    }

    fun togglePlay()            { _service?.exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next()                  { _service?.exoPlayer?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous()              { _service?.exoPlayer?.let { if (it.currentPosition > 3000) it.seekTo(0) else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
    fun seekTo(ms: Long)        { _service?.exoPlayer?.seekTo(ms) }
    fun seekToFraction(f: Float){ _service?.exoPlayer?.let { it.seekTo((it.duration * f).toLong().coerceAtLeast(0)) } }
    fun seekToIndex(idx: Int)   { _service?.exoPlayer?.seekTo(idx, 0) }
    fun setSpeed(s: Float)      { _service?.exoPlayer?.setPlaybackSpeed(s) }
    fun setVolume(v: Float)     { _service?.exoPlayer?.let { it.volume = v.coerceIn(0f, 1f) } }
    fun toggleShuffle()         { _service?.exoPlayer?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        _service?.exoPlayer?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else                   -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // Crossfade duration in seconds (0 = disabled)
    var crossfadeSeconds: Float = 0f

    private suspend fun applyCrossfade() {
        val p = _service?.exoPlayer ?: return
        val steps = 20
        val stepMs = (crossfadeSeconds * 1000 / steps).toLong().coerceAtLeast(50L)
        // Fade in from 0 to 1 over the crossfade duration
        for (i in 0..steps) {
            val vol = i.toFloat() / steps
            p.volume = vol
            delay(stepMs)
        }
        p.volume = 1f
    }

    fun getQueue(): List<Song> = currentQueue

    // Play song immediately next in queue
    fun playNext(song: Song) {
        val p   = _service?.exoPlayer ?: return
        val idx = p.currentMediaItemIndex + 1
        currentQueue = currentQueue.toMutableList().also {
            it.add(idx.coerceAtMost(it.size), song)
        }
        p.addMediaItem(idx, MediaItem.fromUri(song.uri))
        syncState()
    }

    // Append song to end of queue
    fun addToQueue(song: Song) {
        val p = _service?.exoPlayer ?: return
        currentQueue = currentQueue + song
        p.addMediaItem(MediaItem.fromUri(song.uri))
        syncState()
    }

    private fun syncState() {
        val p = _service?.exoPlayer ?: return
        val idx  = p.currentMediaItemIndex.coerceAtLeast(0)
        val song = currentQueue.getOrNull(idx)
        val repeat = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else                   -> RepeatMode.NONE
        }
        _state.value = PlaybackState(
            currentSong = song,
            isPlaying   = p.isPlaying,
            position    = p.currentPosition.coerceAtLeast(0),
            duration    = p.duration.coerceAtLeast(0),
            isShuffled  = p.shuffleModeEnabled,
            repeatMode  = repeat,
            queue       = currentQueue,
            queueIndex  = idx,
        )
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                delay(500)
                val p = _service?.exoPlayer ?: break
                if (!p.isPlaying) break
                _state.value = _state.value.copy(
                    position  = p.currentPosition.coerceAtLeast(0),
                    duration  = p.duration.coerceAtLeast(0),
                    isPlaying = p.isPlaying,
                )
            }
        }
    }

    private fun stopPositionUpdates() { positionJob?.cancel() }

    fun release() {
        positionJob?.cancel()
        scope.cancel()
        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
    }
}
