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
    private var crossfadeJob: Job? = null
    private var currentQueue: List<Song> = emptyList()
    private var _service: DeckPlaybackService? = null

    // ── Two-player crossfade architecture ────────────────────────────
    // playerA = primary (currently playing)
    // playerB = secondary (fading in during crossfade)
    private var playerA: ExoPlayer? = null  // bound from service
    private var playerB: ExoPlayer? = null  // created locally for crossfade

    val player: ExoPlayer get() = playerA
        ?: throw IllegalStateException("Service not bound yet")
    val playerOrNull: ExoPlayer? get() = playerA
    val isReady: Boolean get() = playerA != null

    var onAudioSessionReady: ((Int) -> Unit)? = null

    // Settings
    var crossfadeSeconds: Float = 0f
    var fadeOnPause: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            _service = (binder as? DeckPlaybackService.LocalBinder)?.getService()
            playerA  = _service?.exoPlayer
            playerA?.addListener(playerListener)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _service = null; playerA = null
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(s: Int) {
            syncState()
            if (s == Player.STATE_READY) {
                val sid = playerA?.audioSessionId ?: 0
                if (sid != 0) onAudioSessionReady?.invoke(sid)
            }
        }
        override fun onIsPlayingChanged(p: Boolean) {
            syncState()
            if (p) startPositionUpdates() else stopPositionUpdates()
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            syncState()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && crossfadeSeconds > 0f) {
                // Auto-transition means ExoPlayer moved to next track — fade was handled
                // in position-based crossfade trigger. Nothing extra needed here.
            }
        }
        override fun onShuffleModeEnabledChanged(e: Boolean) { syncState() }
        override fun onRepeatModeChanged(m: Int) { syncState() }
    }

    init {
        val intent = Intent(context, DeckPlaybackService::class.java).apply {
            action = DeckPlaybackService.ACTION_BIND
        }
        context.startService(Intent(context, DeckPlaybackService::class.java))
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val p = playerA ?: return
        crossfadeJob?.cancel()
        releasePlayerB()
        currentQueue = songs
        p.volume = 1f
        p.clearMediaItems()
        p.addMediaItems(songs.map { MediaItem.fromUri(it.uri) })
        p.seekToDefaultPosition(startIndex)
        p.prepare()
        p.play()
        startPositionUpdates()
    }

    // ── Playback controls ────────────────────────────────────────────
    fun togglePlay() {
        val p = playerA ?: return
        if (p.isPlaying) {
            if (fadeOnPause) {
                scope.launch {
                    // Fade out over 300ms then pause
                    val steps = 15
                    repeat(steps) { i ->
                        p.volume = 1f - (i + 1).toFloat() / steps
                        delay(20)
                    }
                    p.pause()
                    p.volume = 1f  // reset so resume sounds normal
                }
            } else {
                p.pause()
            }
        } else {
            p.play()
            if (fadeOnPause) {
                scope.launch {
                    // Fade in over 300ms after resume
                    p.volume = 0f
                    val steps = 15
                    repeat(steps) { i ->
                        p.volume = (i + 1).toFloat() / steps
                        delay(20)
                    }
                    p.volume = 1f
                }
            }
        }
    }

    fun next() {
        val p = playerA ?: return
        crossfadeJob?.cancel()
        releasePlayerB()
        p.volume = 1f
        if (p.hasNextMediaItem()) p.seekToNextMediaItem()
    }

    fun previous() {
        val p = playerA ?: return
        if (p.currentPosition > 3000) p.seekTo(0)
        else if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long)          { playerA?.seekTo(ms) }
    fun seekToFraction(f: Float)  { playerA?.let { it.seekTo((it.duration * f).toLong().coerceAtLeast(0)) } }
    fun seekToIndex(idx: Int)     { playerA?.seekTo(idx, 0) }
    fun setSpeed(s: Float)        { playerA?.setPlaybackSpeed(s) }
    fun setVolume(v: Float)       { playerA?.volume = v.coerceIn(0f, 1f) }
    fun toggleShuffle()           { playerA?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        playerA?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else                   -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun getQueue(): List<Song> = currentQueue

    fun playNext(song: Song) {
        val p = playerA ?: return
        val idx = p.currentMediaItemIndex + 1
        currentQueue = currentQueue.toMutableList().also {
            it.add(idx.coerceAtMost(it.size), song)
        }
        p.addMediaItem(idx, MediaItem.fromUri(song.uri))
        syncState()
    }

    fun addToQueue(song: Song) {
        val p = playerA ?: return
        if (currentQueue.isEmpty()) { playQueue(listOf(song), 0); return }
        currentQueue = currentQueue + song
        p.addMediaItem(MediaItem.fromUri(song.uri))
        syncState()
    }

    // ── Real crossfade ─────────────────────────────────────────────
    // Called from position update loop when approaching track end.
    // playerA fades 1→0 while playerB fades 0→1. When playerB reaches
    // full volume, playerA is released and playerB becomes playerA.
    private fun startCrossfade() {
        val pA    = playerA ?: return
        val idx   = pA.currentMediaItemIndex
        val nextIdx = idx + 1
        val nextSong = currentQueue.getOrNull(nextIdx) ?: return

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            // Build playerB on main thread
            val pB = ExoPlayer.Builder(context).build()
            playerB = pB
            pB.volume = 0f
            pB.setMediaItem(MediaItem.fromUri(nextSong.uri))
            pB.prepare()
            pB.play()

            val totalMs  = (crossfadeSeconds * 1000).toLong()
            val stepMs   = 50L
            val steps    = (totalMs / stepMs).toInt().coerceAtLeast(1)

            // Simultaneous fade: A out, B in
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                pA.volume = 1f - t   // A: 1 → 0
                pB.volume = t         // B: 0 → 1
                delay(stepMs)
            }

            // Swap: pB becomes primary
            pA.pause()
            pA.volume = 1f
            playerA = pB
            playerB = null
            playerA?.addListener(playerListener)
            currentQueue = currentQueue.drop(nextIdx) + currentQueue.take(nextIdx)
            syncState()
            startPositionUpdates()
            pA.removeListener(playerListener)
            pA.release()
        }
    }

    private fun releasePlayerB() {
        playerB?.release()
        playerB = null
    }

    // ── Position updates + crossfade trigger ─────────────────────────
    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                delay(300)
                val p = playerA ?: break
                if (!p.isPlaying) break
                val pos = p.currentPosition.coerceAtLeast(0)
                val dur = p.duration.coerceAtLeast(0)
                _state.value = _state.value.copy(position = pos, duration = dur, isPlaying = true)

                // Trigger crossfade when within crossfadeSeconds of track end
                if (crossfadeSeconds > 0f && dur > 0 && crossfadeJob == null &&
                    (dur - pos) < (crossfadeSeconds * 1000).toLong() &&
                    p.hasNextMediaItem()) {
                    startCrossfade()
                }
            }
        }
    }

    private fun stopPositionUpdates() { positionJob?.cancel() }

    private fun syncState() {
        val p   = playerA ?: return
        val idx = p.currentMediaItemIndex.coerceAtLeast(0)
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

    fun release() {
        positionJob?.cancel()
        crossfadeJob?.cancel()
        releasePlayerB()
        scope.cancel()
        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
    }
}
