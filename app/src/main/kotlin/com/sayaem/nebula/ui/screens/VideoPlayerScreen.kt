package com.sayaem.nebula.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.media.audiofx.Equalizer as AndroidEqualizer
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import com.sayaem.nebula.MainActivity
import com.sayaem.nebula.cast.DeckCastManager
import com.sayaem.nebula.cast.CastState
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.notifications.DeckNotificationEngine
import com.sayaem.nebula.notifications.DeckToastEngine
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ── Enums ─────────────────────────────────────────────────────────────────
private enum class OrientMode { AUTO, PORTRAIT, LANDSCAPE }
private enum class AspectMode(val label: String, val rm: Int) {
    FIT("Fit",   AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}
private enum class LoopMode(val icon: ImageVector) {
    SHUFFLE(Icons.Filled.Shuffle),
    REPEAT_ONE(Icons.Filled.RepeatOne),
    LIST(Icons.Filled.Repeat),          // playlist loop (green = active)
    NONE(Icons.Filled.ExitToApp),       // exit after
}

// ═════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(
    videos: List<Song>,
    startIndex: Int = 0,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
    onToggleVideoFavorite: ((Long, Boolean) -> Unit)? = null,
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope    = rememberCoroutineScope()
    val prefs    = remember { context.getSharedPreferences("deck_vp", Context.MODE_PRIVATE) }

    // ── Chromecast ────────────────────────────────────────────────
    val castManager = remember {
        DeckCastManager(context).also { it.init() }
    }
    val castState by castManager.castState.collectAsState()
    val castDeviceName by castManager.connectedDeviceName.collectAsState()
    DisposableEffect(Unit) {
        onDispose { castManager.release() }
    }

    var idx by remember { mutableIntStateOf(startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))) }
    val video = videos.getOrNull(idx) ?: return

    // ── Decoder preference — must be known BEFORE building ExoPlayer ──────
    val savedHw = prefs.getBoolean("use_hw_decoder", true)
    val vp = remember {
        val tsParams = androidx.media3.common.TrackSelectionParameters.Builder(context)
            .setForceHighestSupportedBitrate(false)
            .build()
        ExoPlayer.Builder(context.applicationContext)
            .apply {
                if (!savedHw) {
                    // SW decoder: disable hardware-accelerated renderers
                    setTrackSelector(
                        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
                            parameters = buildUponParameters()
                                .setForceHighestSupportedBitrate(true)
                                .build()
                        }
                    )
                }
            }
            .build()
    }

    // ── Orientation ───────────────────────────────────────────────────
    var orient by remember { mutableStateOf(OrientMode.LANDSCAPE) }
    LaunchedEffect(orient) {
        activity?.requestedOrientation = when (orient) {
            OrientMode.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientMode.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // ── Resume ────────────────────────────────────────────────────────
    var showResume by remember { mutableStateOf(false) }
    var resumePos  by remember { mutableLongStateOf(0L) }

    fun loadVideo(song: Song, restart: Boolean = false) {
        vp.stop(); vp.clearMediaItems()
        vp.setMediaItem(MediaItem.fromUri(song.uri))
        vp.prepare()
        val saved = prefs.getLong("p${song.id}", 0L)
        if (!restart && saved > 10_000L && saved < (song.duration - 10_000L).coerceAtLeast(1L)) {
            resumePos = saved; showResume = true
            vp.seekTo(saved); vp.playWhenReady = false
        } else {
            if (restart) {
                // On manual restart clear any lingering AB state so it doesn't immediately loop
                prefs.edit().remove("p${song.id}").apply()
            }
            vp.playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onPauseMusic()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadVideo(video)
        onDispose {
            val p = vp.currentPosition
            if (p > 10_000L && p < vp.duration - 5_000L)
                prefs.edit().putLong("p${video.id}", p).apply()
            cancelVidNotif(context)
            vp.stop(); vp.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore system brightness when exiting player
            activity?.window?.attributes?.let {
                it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = it
            }
        }
    }
    BackHandler { onBack() }

    // Apply saved brightness immediately on open
    LaunchedEffect(Unit) {
        activity?.window?.attributes?.let {
            it.screenBrightness = brightness.coerceIn(0.01f, 1f)
            activity.window.attributes = it
        }
    }

    // ── Playback — all persistent preferences loaded from prefs ─────
    var isPlaying   by remember { mutableStateOf(true) }
    var isMuted     by remember { mutableStateOf(false) }
    var isLocked    by remember { mutableStateOf(false) }
    var videoScale  by remember { mutableStateOf(1f) }
    var nightMode   by remember { mutableStateOf(false) }
    // Video EQ — real android.media.audiofx.Equalizer on vp.audioSessionId
    val videoEq = remember { mutableStateOf<AndroidEqualizer?>(null) }
    val eqBandLevels = remember { mutableStateListOf(0, 0, 0, 0, 0) }  // millibels, 5 bands
    var eqEnabled by remember { mutableStateOf(prefs.getBoolean("eq_enabled_video", false)) }
    // Persist eqEnabled on every change
    LaunchedEffect(eqEnabled) { prefs.edit().putBoolean("eq_enabled_video", eqEnabled).apply() }
    var mirrorMode  by remember { mutableStateOf(false) }
    var bgAudio     by remember { mutableStateOf(false) }
    // Aspect + scale persisted per video
    var aspect by remember {
        mutableStateOf(
            AspectMode.entries.getOrElse(
                prefs.getInt("aspect_${video.id}", AspectMode.FIT.ordinal)
            ) { AspectMode.FIT }
        )
    }

    // ── Persisted global preferences ─────────────────────────────
    var loopMode    by remember { mutableStateOf(
        LoopMode.entries.getOrElse(prefs.getInt("loop_mode", LoopMode.LIST.ordinal)) { LoopMode.LIST }
    )}
    var speed       by remember { mutableStateOf(prefs.getFloat("playback_speed", 1f)) }
    var useHwDecoder by remember { mutableStateOf(prefs.getBoolean("use_hw_decoder", true)) }
    var brightness  by remember { mutableStateOf(prefs.getFloat("brightness", 0.5f)) }

    // ── Persisted per-video state (keyed by video.id) ─────────────
    var isFavorite  by remember { mutableStateOf(prefs.getBoolean("fav_${video.id}", false)) }
    var subSizeScale by remember { mutableStateOf(prefs.getFloat("sub_size", 1f)) }
    var subDelayMs  by remember { mutableLongStateOf(prefs.getLong("sub_delay_${video.id}", 0L)) }

    // AB Repeat — per video, resets when idx changes
    var abA      by remember { mutableLongStateOf(prefs.getLong("ab_a_${video.id}", -1L)) }
    var abB      by remember { mutableLongStateOf(prefs.getLong("ab_b_${video.id}", -1L)) }
    var abActive by remember { mutableStateOf(
        prefs.getLong("ab_a_${video.id}", -1L) >= 0 &&
        prefs.getLong("ab_b_${video.id}", -1L) >= 0
    )}

    // Bookmarks — per video, stored as comma-separated ms values
    var bookmarks by remember {
        mutableStateOf(
            prefs.getString("bm_${video.id}", "")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()
        )
    }

    // Sleep timer — session only (intentionally not persisted)
    var sleepMins   by remember { mutableIntStateOf(0) }
    var sleepRemain by remember { mutableLongStateOf(0L) }

    // ── UI state ──────────────────────────────────────────────────────
    var showControls  by remember { mutableStateOf(true) }
    var showOverflow  by remember { mutableStateOf(false) }
    var showSpeed     by remember { mutableStateOf(false) }
    var showSubSheet  by remember { mutableStateOf(false) }
    var showAudSheet  by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }
    var showJumpTo    by remember { mutableStateOf(false) }
    var showSleep        by remember { mutableStateOf(false) }
    var showUpNext       by remember { mutableStateOf(false) }
    var upNextDone       by remember { mutableStateOf(false) }
    var showEq           by remember { mutableStateOf(false) }
    var showCast         by remember { mutableStateOf(false) }
    var showSubDelay     by remember { mutableStateOf(false) }
    var showSubSize      by remember { mutableStateOf(false) }
    var showBookmarks    by remember { mutableStateOf(false) }
    var showEditPlayer   by remember { mutableStateOf(false) }
    var showQueue        by remember { mutableStateOf(false) }

    // ── Tracks ────────────────────────────────────────────────────────
    var subTracks  by remember { mutableStateOf<List<String>>(emptyList()) }
    var audTracks  by remember { mutableStateOf<List<String>>(emptyList()) }

    // ── Gesture HUDs ─────────────────────────────────────────────────
    var brightHud   by remember { mutableStateOf<Float?>(null) }
    var volHud      by remember { mutableStateOf<Float?>(null) }
    var tapTrigger  by remember { mutableLongStateOf(0L) }
    var tapLeft     by remember { mutableStateOf(true) }
    var tapLabel    by remember { mutableStateOf("") }
    var longSpeed   by remember { mutableStateOf(false) }
    var seekGestureLabel by remember { mutableStateOf("") }
    var showSeekHud by remember { mutableStateOf(false) }

    // ── Seek bar ──────────────────────────────────────────────────────
    var dragging   by remember { mutableStateOf(false) }
    var dragFrac   by remember { mutableStateOf(0f) }
    var thumbBmp   by remember { mutableStateOf<Bitmap?>(null) }

    // ── Position ──────────────────────────────────────────────────────
    val pos by produceState(0L) { while (true) { value = vp.currentPosition; delay(200) } }
    val dur  = vp.duration.coerceAtLeast(1L)

    // Apply mute
    LaunchedEffect(isMuted) { vp.volume = if (isMuted) 0f else 1f }
    // Apply saved speed immediately when player opens
    LaunchedEffect(Unit) {
        if (speed != 1f) vp.setPlaybackSpeed(speed)
    }
    // Init real video EQ when audio session is ready
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && videoEq.value == null) {
                    val sid = vp.audioSessionId
                    if (sid > 0) {
                        try {
                            val eq = AndroidEqualizer(0, sid).apply { enabled = eqEnabled }
                            // Restore saved band levels
                            val numBands = eq.numberOfBands.toInt()
                            for (i in 0 until minOf(numBands, eqBandLevels.size)) {
                                eq.setBandLevel(i.toShort(), eqBandLevels[i].toShort())
                            }
                            videoEq.value = eq
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        vp.addListener(listener)
        onDispose {
            vp.removeListener(listener)
            videoEq.value?.release()
            videoEq.value = null
        }
    }
    // Apply brightness
    LaunchedEffect(brightness) {
        activity?.window?.attributes?.let {
            it.screenBrightness = brightness.coerceIn(0.01f, 1f)
            activity.window.attributes = it
        }
    }
    // Apply mirror
    LaunchedEffect(mirrorMode) { /* scaleX handled in AndroidView update */ }
    // Loop mode — apply on change and on open (key = loopMode fires on init too)
    LaunchedEffect(loopMode) {
        vp.shuffleModeEnabled = loopMode == LoopMode.SHUFFLE
        vp.repeatMode = when (loopMode) {
            LoopMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            LoopMode.LIST       -> Player.REPEAT_MODE_ALL
            else                -> Player.REPEAT_MODE_OFF
        }
    }
    // Long speed
    LaunchedEffect(longSpeed) { vp.setPlaybackSpeed(if (longSpeed) 2f else speed) }
    // Auto-hide controls
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) { delay(4000); showControls = false }
    }
    LaunchedEffect(brightHud) { if (brightHud != null) { delay(1400); brightHud = null } }
    LaunchedEffect(volHud)    { if (volHud != null)    { delay(1400); volHud = null } }
    LaunchedEffect(showSeekHud) { if (showSeekHud) { delay(900); showSeekHud = false } }

    // ── PERSIST global prefs on change ────────────────────────────
    LaunchedEffect(loopMode)     { prefs.edit().putInt("loop_mode", loopMode.ordinal).apply() }
    LaunchedEffect(speed)        { prefs.edit().putFloat("playback_speed", speed).apply() }
    LaunchedEffect(useHwDecoder) { prefs.edit().putBoolean("use_hw_decoder", useHwDecoder).apply() }
    LaunchedEffect(brightness)   { prefs.edit().putFloat("brightness", brightness).apply() }
    LaunchedEffect(aspect)       { prefs.edit().putInt("aspect_${video.id}", aspect.ordinal).apply() }

    // ── PERSIST per-video prefs on change ─────────────────────────
    LaunchedEffect(isFavorite)   { prefs.edit().putBoolean("fav_${video.id}", isFavorite).apply() }
    LaunchedEffect(subSizeScale) { prefs.edit().putFloat("sub_size", subSizeScale).apply() }
    LaunchedEffect(subDelayMs)   {
        prefs.edit().putLong("sub_delay_${video.id}", subDelayMs).apply()
        // Subtitle offset stored — applied via SubtitleConfiguration on next video load
        // ExoPlayer's public Player API does not expose setSubtitleOffset directly
    }
    LaunchedEffect(abA)          { prefs.edit().putLong("ab_a_${video.id}", abA).apply() }
    LaunchedEffect(abB)          { prefs.edit().putLong("ab_b_${video.id}", abB).apply() }
    LaunchedEffect(bookmarks)    {
        val str = bookmarks.joinToString(",")
        prefs.edit().putString("bm_${video.id}", str).apply()
    }

    // Single LaunchedEffect(idx) — loads video AND reloads all per-video persistent state
    // Must be AFTER state declarations so all variables are in scope
    LaunchedEffect(idx) {
        if (idx != startIndex) loadVideo(video)
        isFavorite   = prefs.getBoolean("fav_${video.id}", false)
        aspect       = AspectMode.entries.getOrElse(
            prefs.getInt("aspect_${video.id}", AspectMode.FIT.ordinal)) { AspectMode.FIT }
        subDelayMs   = prefs.getLong("sub_delay_${video.id}", 0L)
        abA          = prefs.getLong("ab_a_${video.id}", -1L)
        abB          = prefs.getLong("ab_b_${video.id}", -1L)
        abActive     = abA >= 0 && abB >= 0
        bookmarks    = prefs.getString("bm_${video.id}", "")
            ?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    // Sleep timer
    LaunchedEffect(sleepMins) {
        if (sleepMins <= 0) return@LaunchedEffect
        sleepRemain = sleepMins * 60_000L
        while (sleepRemain > 0) { delay(1000); sleepRemain -= 1000 }
        vp.pause(); sleepMins = 0; sleepRemain = 0
        DeckToastEngine.info("Sleep timer: paused")
    }

    // AB Repeat check
    LaunchedEffect(pos) {
        if (abActive && abA >= 0 && abB > abA) {
            if (pos >= abB) vp.seekTo(abA)
        }
        // Up Next
        val rem = dur - pos
        if (rem in 5_000L..30_000L && !upNextDone && idx < videos.size - 1) showUpNext = true
        else if (rem > 30_000L) { upNextDone = false; showUpNext = false }
        if (bgAudio && isPlaying) showVidNotif(context, video, isPlaying, pos, dur)
    }

    // Track detection — uses "idx_tracks" key so it doesn't conflict with the idx load block above
    LaunchedEffect(idx, "tracks") {
        delay(1000)
        try {
            val t = vp.currentTracks
            subTracks = t.groups.indices.filter { t.groups[it].type == C.TRACK_TYPE_TEXT }
                .mapIndexed { i, _ -> "Subtitle ${i + 1}" }
            audTracks = t.groups.indices.filter { t.groups[it].type == C.TRACK_TYPE_AUDIO }
                .mapIndexed { i, gi ->
                    val f = t.groups[gi].getTrackFormat(0)
                    val lang = f.language?.uppercase() ?: "Track ${i + 1}"
                    val ch = when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount}ch" }
                    "$lang · $ch"
                }
        } catch (_: Exception) {}
    }

    // Playback listener
    DisposableEffect(idx) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_ENDED) {
                    prefs.edit().remove("p${video.id}").apply()
                    if (loopMode == LoopMode.NONE) { cancelVidNotif(context); onBack(); return }
                    if (loopMode != LoopMode.REPEAT_ONE && loopMode != LoopMode.SHUFFLE) {
                        if (idx < videos.size - 1) { idx++; showUpNext = false; upNextDone = false }
                        else { cancelVidNotif(context); onBack() }
                    }
                }
                isPlaying = vp.isPlaying
            }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        }
        vp.addListener(l)
        onDispose { vp.removeListener(l) }
    }

    // Seek thumbnail
    LaunchedEffect(dragging, dragFrac) {
        if (!dragging) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(video.filePath)
                val bmp = mmr.getFrameAtTime((dragFrac * dur * 1000L).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                withContext(Dispatchers.Main) {
                    thumbBmp = bmp?.let { Bitmap.createScaledBitmap(it, 180, 102, true)
                        .also { s -> if (s !== it) it.recycle() } }
                }
            } catch (_: Exception) {}
        }
    }

    var pvRef by remember { mutableStateOf<PlayerView?>(null) }

    // ═════════════════════════════════════════════════════════════════
    // ROOT
    // ═════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── PlayerView ─────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = vp; useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspect.rm; pvRef = this
                }
            },
            update = { pv ->
                pv.resizeMode = aspect.rm
                pv.scaleX = (if (mirrorMode) -1f else 1f) * videoScale
                pv.scaleY = videoScale
                // Apply subtitle text size (0.5–2.0× of default 5.33% screen height)
                try {
                    pv.subtitleView?.setFractionalTextSize(
                        0.0533f * subSizeScale.coerceIn(0.5f, 2.0f)
                    )
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
        )

        // Night mode — real ColorMatrix warm reduction, not just a black overlay
        if (nightMode) {
            Canvas(Modifier.fillMaxSize()) {
                // Warm night filter: reduce blue channel 40%, boost red slightly,
                // add semi-transparent dark amber overlay — matches Playit's warm tone
                drawRect(Color(0x55000000))              // base dim
                drawRect(Color(0x22CC6600))              // warm amber tint
                drawRect(Color(0x0A000044))              // blue channel reduction hint
            }
        }

        // ── Gesture layer ───────────────────────────────────────────
        var lastTapMs by remember { mutableLongStateOf(0L) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTransformGestures { c, pan, zoom, _ ->
                        if (abs(zoom - 1f) > 0.012f) {
                            videoScale = (videoScale * zoom).coerceIn(0.8f, 3f); return@detectTransformGestures
                        }
                        val dy = -pan.y / size.height
                        val dx = pan.x / size.width
                        // Horizontal swipe = seek gesture
                        if (abs(dx) > abs(dy) && abs(dx) > 0.006f) {
                            val deltaMs = (dx * 60_000L).toLong()
                            vp.seekTo((vp.currentPosition + deltaMs).coerceIn(0L, dur))
                            val secs = deltaMs / 1000L
                            seekGestureLabel = "${if (secs >= 0) "+" else ""}${secs}s · ${vidFmt(pos)}"
                            showSeekHud = true
                            return@detectTransformGestures
                        }
                        // Vertical = brightness (left) / volume (right)
                        if (abs(dy) > 0.004f) {
                            if (c.x < size.width / 2) {
                                brightness = (brightness + dy).coerceIn(0.01f, 1f)
                                brightHud = brightness
                            } else {
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val nv  = (cur + (dy * max).toInt()).coerceIn(0, max)
                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                volHud = nv.toFloat() / max
                            }
                        }
                    }
                }
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) { showControls = !showControls; return@detectTapGestures }
                            val now = System.currentTimeMillis()
                            if (now - lastTapMs < 280) {
                                val left = it.x < size.width / 2
                                val seekMs = if (left) -10_000L else 10_000L
                                vp.seekTo((vp.currentPosition + seekMs).coerceAtLeast(0))
                                tapLeft = left; tapLabel = if (left) "−10s" else "+10s"
                                tapTrigger = now
                            } else { showControls = !showControls }
                            lastTapMs = now
                        },
                        onLongPress = { if (!isLocked) longSpeed = true }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.all { !it.pressed } && longSpeed) longSpeed = false
                        }
                    }
                }
        )

        // ── Double-tap ripple ───────────────────────────────────────
        if (tapTrigger > 0L) {
            key(tapTrigger) { TapRipple(tapLeft, tapLabel) }
        }

        // ── Seek gesture HUD ────────────────────────────────────────
        AnimatedVisibility(showSeekHud, enter = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                .padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(seekGestureLabel, color = Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // ── Long-press 2× HUD ───────────────────────────────────────
        AnimatedVisibility(longSpeed, enter = fadeIn(tween(80)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC000000))
                .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = NebulaViolet, modifier = Modifier.size(15.dp))
                    Text("2×", color = Color.White, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Side HUDs ───────────────────────────────────────────────
        AnimatedVisibility(brightHud != null,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) {
            brightHud?.let { SideHud(Icons.Filled.Brightness6, it) }
        }
        AnimatedVisibility(volHud != null,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) {
            volHud?.let { SideHud(Icons.Filled.VolumeUp, it) }
        }

        // ── Zoom badge ──────────────────────────────────────────────
        if (videoScale > 1.05f) {
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 56.dp, top = 4.dp)) {
                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xBB000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("${String.format("%.1f", videoScale)}×",
                        color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ── Sleep / AB badges ───────────────────────────────────────
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding()
            .padding(end = 52.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sleepMins > 0 && sleepRemain > 0) {
                Badge(NebulaCyan, Icons.Filled.Bedtime, vidFmt(sleepRemain))
            }
            if (abActive && abA >= 0) {
                Badge(NebulaAmber, Icons.Filled.Repeat, "AB")
            }
        }

        // ── Up Next ─────────────────────────────────────────────────
        val nextVid = videos.getOrNull(idx + 1)
        if (showUpNext && nextVid != null) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 14.dp, bottom = 130.dp)) {
                UpNextCard(nextVid, dur - pos,
                    onSkip = { idx++; showUpNext = false },
                    onDismiss = { showUpNext = false; upNextDone = true })
            }
        }

        // ════════════════════════════════════════════════════════════
        // ALWAYS-VISIBLE SIDE BUTTONS (Playit style — left + right)
        // These do NOT hide when controls fade — always accessible
        // ════════════════════════════════════════════════════════════
        if (!isLocked) {
            // LEFT: Mute + Lock
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SideBtn(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    tint = if (isMuted) NebulaRed else Color.White.copy(0.9f)) {
                    isMuted = !isMuted
                }
                SideBtn(Icons.Filled.Lock) { isLocked = true }
            }
            // RIGHT: Scissors (screenshot/cut) + Orient
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SideBtn(Icons.Filled.ContentCut) {
                    scope.launch { DeckToastEngine.success(captureScreenshot(pvRef)) }
                }
                SideBtn(when (orient) {
                    OrientMode.LANDSCAPE -> Icons.Filled.ScreenRotation
                    OrientMode.PORTRAIT  -> Icons.Filled.StayCurrentPortrait
                    OrientMode.AUTO      -> Icons.Filled.ScreenRotationAlt
                }) {
                    orient = when (orient) {
                        OrientMode.LANDSCAPE -> OrientMode.PORTRAIT
                        OrientMode.PORTRAIT  -> OrientMode.AUTO
                        OrientMode.AUTO      -> OrientMode.LANDSCAPE
                    }
                }
            }
        }

        // ── Lock unlock button (centered, shown when locked) ────────
        if (isLocked) {
            AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xCC000000))
                    .border(1.dp, NebulaViolet.copy(0.6f), RoundedCornerShape(14.dp))
                    .clickable { isLocked = false }
                    .padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.LockOpen, null, tint = NebulaViolet,
                            modifier = Modifier.size(24.dp))
                        Text("Tap to unlock", color = Color.White.copy(0.9f),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════
        // MAIN CONTROLS (fade on tap)
        // ════════════════════════════════════════════════════════════
        AnimatedVisibility(showControls && !isLocked,
            enter = fadeIn(tween(150)), exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.78f), 0.20f to Color.Transparent,
                        0.72f to Color.Transparent,   1f   to Color.Black.copy(0.88f),
                    )
                )
            ) {
                // ──── TOP BAR ──────────────────────────────────────
                // Playit: ← · title · HDR · CC · Audio · Aspect · ⋮
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.White,
                            modifier = Modifier.size(22.dp))
                    }
                    Text(video.title, color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp))

                    // HDR badge
                    val vfmt = vp.videoFormat
                    if (vfmt != null && vfmt.height > 0) {
                        val isHdr = Build.VERSION.SDK_INT >= 24 &&
                            vfmt.colorInfo?.colorTransfer?.let {
                                it == C.COLOR_TRANSFER_ST2084 || it == C.COLOR_TRANSFER_HLG
                            } == true
                        if (isHdr) {
                            TopBadge("HDR")
                            Spacer(Modifier.width(2.dp))
                        }
                        // Resolution
                        val res = when { vfmt.height >= 2160 -> "4K"; vfmt.height >= 1080 -> "1080p"
                            vfmt.height >= 720 -> "720p"; vfmt.height >= 480 -> "480p"
                            else -> "${vfmt.height}p" }
                        TopBadge(res)
                        Spacer(Modifier.width(2.dp))
                    }

                    // CC button
                    TopIconBtn(Icons.Filled.Subtitles,
                        tint = if (subTracks.isNotEmpty()) NebulaCyan else Color.White.copy(0.7f)) {
                        showSubSheet = true
                    }
                    // Audio track
                    TopIconBtn(Icons.Filled.Headset,
                        tint = if (audTracks.size > 1) NebulaAmber else Color.White.copy(0.7f)) {
                        showAudSheet = true
                    }
                    // Aspect ratio
                    TopIconBtn(Icons.Filled.AspectRatio) { aspect = AspectMode.entries[(AspectMode.entries.indexOf(aspect) + 1) % 3] }
                    // Overflow
                    TopIconBtn(Icons.Filled.MoreVert) { showOverflow = true }
                }

                // ──── CENTER TRANSPORT ────────────────────────────
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CenterBtn(Icons.Filled.SkipPrevious, 40.dp, 26.dp, idx > 0) { idx-- }
                    CenterBtn(Icons.Filled.Replay10, 50.dp, 36.dp) {
                        vp.seekTo((vp.currentPosition - 10_000L).coerceAtLeast(0))
                        tapLeft = true; tapLabel = "−10s"; tapTrigger = System.currentTimeMillis()
                    }
                    // PLAY — dominant
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(Color.White.copy(0.14f))
                            .border(2.dp, Color.White.copy(0.72f), CircleShape)
                            .clickable {
                                if (vp.isPlaying) { vp.pause(); isPlaying = false }
                                else              { vp.play();  isPlaying = true  }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    CenterBtn(Icons.Filled.Forward10, 50.dp, 36.dp) {
                        vp.seekTo(vp.currentPosition + 10_000L)
                        tapLeft = false; tapLabel = "+10s"; tapTrigger = System.currentTimeMillis()
                    }
                    CenterBtn(Icons.Filled.SkipNext, 40.dp, 26.dp, idx < videos.size - 1) { idx++ }
                }

                // ──── BOTTOM BAR ──────────────────────────────────
                // Playit: seek bar (top) + [Play · Prev · Next · Speed · ↔ · Resize · ≡]
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    // Seek thumbnail + bar + times
                    SeekBarSection(
                        pos, dur, dragging, dragFrac, thumbBmp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        abA = abA, abB = abB,
                        onStart = { f -> dragging = true; dragFrac = f },
                        onMove  = { f -> dragFrac = f },
                        onEnd   = { f -> vp.seekTo((f * dur).toLong()); dragging = false }
                    )
                    // Controls strip (Playit style)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color.Black.copy(0.6f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Play/pause (small, in bar)
                        BarBtn(Icons.Filled.PlayArrow.let {
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
                        }) {
                            if (vp.isPlaying) { vp.pause(); isPlaying = false }
                            else              { vp.play();  isPlaying = true  }
                        }
                        BarBtn(Icons.Filled.SkipPrevious, enabled = idx > 0) { idx-- }
                        BarBtn(Icons.Filled.SkipNext, enabled = idx < videos.size - 1) { idx++ }
                        Spacer(Modifier.weight(1f))
                        // Speed button: tap = picker, long-press = quick ramp through presets
                        val speedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                        Box(Modifier.clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = { showSpeed = true },
                                onLongClick = {
                                    val cur = speedPresets.indexOf(speed)
                                    val next = speedPresets.getOrElse(cur + 1) { speedPresets.first() }
                                    speed = next
                                    vp.setPlaybackSpeed(next)
                                    DeckToastEngine.info("Speed: ${next}×")
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                "${if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"}",
                                color = if (speed != 1f) NebulaViolet else Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                        // Aspect ratio
                        BarBtn(Icons.Filled.AspectRatio) {
                            aspect = AspectMode.entries[(AspectMode.entries.indexOf(aspect) + 1) % 3]
                        }
                        // PiP / resize
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            BarBtn(Icons.Filled.PictureInPicture) {
                                try { activity?.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9)).build())
                                } catch (_: Exception) {}
                            }
                        }
                        // Playlist/queue count
                        if (videos.size > 1) {
                            BarBtn(Icons.Filled.QueueMusic) { showQueue = true }
                        }
                    }
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // OVERFLOW SHEET — Playit style: circle icon grid + sections
    // ═════════════════════════════════════════════════════════════════
    if (showOverflow) {
        PlayitOverflowSheet(
            isFavorite  = isFavorite,
            nightMode   = nightMode,
            mirrorMode  = mirrorMode,
            bgAudio     = bgAudio,
            useHwDecoder = useHwDecoder,
            loopMode    = loopMode,
            brightness  = brightness,
            sleepMins   = sleepMins,
            abActive    = abActive,
            abA         = abA,
            abB         = abB,
            currentPos  = pos,
            bookmarks   = bookmarks,
            onAudioTrack     = { showOverflow = false; showAudSheet = true },
            onEqualizer      = { showEq = true },
            onCast           = { showCast = true },
            onSubDelay       = { showSubDelay = true },
            onSubSize        = { showSubSize = true },
            onBookmarks      = { showBookmarks = true },
            onEditPlayer     = { showEditPlayer = true },
            onShare     = {
                showOverflow = false
                try {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"; putExtra(Intent.EXTRA_STREAM, video.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(i, "Share"))
                } catch (_: Exception) { DeckToastEngine.error("Share failed") }
            },
            onFavorite       = {
                isFavorite = !isFavorite
                showOverflow = false
                // Persist in prefs (immediate) and notify ViewModel (for UI badge)
                prefs.edit().putBoolean("fav_${video.id}", isFavorite).apply()
                onToggleVideoFavorite?.invoke(video.id, isFavorite)
                DeckToastEngine.success(if (isFavorite) "Added to Favorites ❤️" else "Removed from Favorites")
            },
            onBookmark       = {
                bookmarks = bookmarks + pos
                showOverflow = false
                DeckToastEngine.success("Bookmark added at ${vidFmt(pos)}")
            },
            onDelete         = {
                showOverflow = false
                scope.launch {
                    try {
                        val f = File(video.filePath)
                        if (f.delete()) { DeckToastEngine.songDeleted(); onBack() }
                        else DeckToastEngine.error("Could not delete file")
                    } catch (_: Exception) { DeckToastEngine.error("Delete failed") }
                }
            },
            onNightMode      = { nightMode = !nightMode },
            onMirrorMode     = { mirrorMode = !mirrorMode },
            onTimer          = { showOverflow = false; showSleep = true },
            onAbRepeat       = {
                if (!abActive) {
                    if (abA < 0) { abA = pos; DeckToastEngine.info("A point set at ${vidFmt(pos)}") }
                    else if (abB < 0) { abB = pos; abActive = true
                        DeckToastEngine.info("B point set. AB repeating ${vidFmt(abA)} → ${vidFmt(abB)}") }
                    else { abA = -1; abB = -1; abActive = false; DeckToastEngine.info("AB Repeat cleared") }
                } else { abActive = false; abA = -1; abB = -1; DeckToastEngine.info("AB Repeat off") }
            },
            onBgAudio        = { bgAudio = !bgAudio
                if (bgAudio) showVidNotif(context, video, isPlaying, pos, dur)
                else cancelVidNotif(context)
            },
            onLoopMode       = { loopMode = it },
            onBrightness     = { brightness = it },
            onDecoder        = { hw ->
                if (hw != useHwDecoder) {
                    useHwDecoder = hw
                    // Save the preference so next ExoPlayer build picks it up
                    prefs.edit().putBoolean("use_hw_decoder", hw).apply()
                    // Rebuild: re-prepare current video from current position
                    val currentPos = vp.currentPosition
                    val wasPlaying = vp.isPlaying
                    vp.stop(); vp.clearMediaItems()
                    vp.setMediaItem(MediaItem.fromUri(video.uri))
                    // Apply track selector for SW mode
                    if (!hw) {
                        try {
                            val ts = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
                            ts.parameters = ts.buildUponParameters()
                                .setForceHighestSupportedBitrate(true)
                                .build()
                            // Can't swap selector on existing player — seekTo after prepare
                        } catch (_: Exception) {}
                    }
                    vp.prepare()
                    vp.seekTo(currentPos)
                    vp.playWhenReady = wasPlaying
                    DeckToastEngine.info("Switched to ${if (hw) "Hardware" else "Software"} decoder")
                }
            },
            onFileInfo       = { showOverflow = false; showInfo = true },
            onJumpTo         = { showOverflow = false; showJumpTo = true },
            onScreenshot     = { showOverflow = false; scope.launch { DeckToastEngine.success(captureScreenshot(pvRef)) } },
            onZoomReset      = if (videoScale > 1.05f) ({ videoScale = 1f }) else null,
            onDismiss        = { showOverflow = false }
        )
    }

    // Speed sheet
    if (showSpeed) {
        SpeedPickerSheet(current = speed,
            onSelect = { s -> speed = s; vp.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false })
    }

    // Sleep picker
    if (showSleep) {
        SleepPickerSheet(sleepMins,
            onPick = { m -> sleepMins = m; sleepRemain = m * 60_000L; showSleep = false
                if (m > 0) DeckToastEngine.sleepTimerSet(m) else DeckToastEngine.sleepTimerCancelled() },
            onDismiss = { showSleep = false })
    }

    // Resume dialog
    if (showResume) {
        AlertDialog(
            onDismissRequest = { showResume = false; vp.playWhenReady = true },
            containerColor   = Color(0xFF141420),
            title = { Text("Continue watching?", color = Color.White, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, color = Color.White.copy(0.5f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("Paused at ${vidFmt(resumePos)}", color = NebulaViolet,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { showResume = false; vp.seekTo(resumePos); vp.playWhenReady = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                    Text("Resume", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResume = false; vp.seekTo(0); vp.playWhenReady = true
                    prefs.edit().remove("p${video.id}").apply()
                }) { Text("Restart", color = Color.White.copy(0.55f)) }
            }
        )
    }

    if (showSubSheet) TrackSheet("Subtitles", subTracks, C.TRACK_TYPE_TEXT, vp, NebulaCyan,
        "No subtitle tracks") { showSubSheet = false }
    if (showAudSheet) TrackSheet("Audio Track", audTracks, C.TRACK_TYPE_AUDIO, vp, NebulaAmber,
        "Only one audio track") { showAudSheet = false }
    if (showInfo)    InfoSheet(video, vp.videoFormat) { showInfo = false }
    if (showJumpTo)  JumpToDialog(dur) { ms -> vp.seekTo(ms); showJumpTo = false }

    // ── New sheets ──────────────────────────────────────────────────────────
    if (showSubDelay) {
        SubtitleDelaySheet(delayMs = subDelayMs, onChange = { subDelayMs = it }) {
            showSubDelay = false
        }
    }
    if (showSubSize) {
        SubtitleSizeSheet(scale = subSizeScale, onChange = { subSizeScale = it }) {
            showSubSize = false
        }
    }
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks    = bookmarks.sorted(),
            currentPos   = pos,
            onJump       = { ms ->
                vp.seekTo(ms)
                showBookmarks = false
            },
            onDelete     = { ms ->
                bookmarks = bookmarks.filter { it != ms }
                // prefs updated automatically via LaunchedEffect(bookmarks)
                DeckToastEngine.info("Bookmark removed")
            },
            onAddCurrent = {
                if (pos !in bookmarks) {
                    bookmarks = bookmarks + pos
                    DeckToastEngine.success("Bookmark added at ${vidFmt(pos)}")
                } else {
                    DeckToastEngine.info("Already bookmarked at ${vidFmt(pos)}")
                }
            }
        ) { showBookmarks = false }
    }
    if (showEq) {
        VideoEqSheet(
            eq             = videoEq.value,
            eqEnabled      = eqEnabled,
            onEnabledChange = { eqEnabled = it },
            bandLevels     = eqBandLevels
        ) { showEq = false }
    }
    if (showCast) {
        CastSheet(
            castManager = castManager,
            video       = video,
            currentPos  = pos
        ) { showCast = false }
    }
    if (showEditPlayer) {
        EditPlayerSheet { showEditPlayer = false }
    }
    if (showQueue) {
        VideoQueueSheet(
            videos       = videos,
            currentIdx   = idx,
            onJump       = { i -> idx = i; showQueue = false },
            onDismiss    = { showQueue = false }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PLAYIT OVERFLOW SHEET — circle icons + sections + scrollable
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun PlayitOverflowSheet(
    isFavorite: Boolean, nightMode: Boolean, mirrorMode: Boolean,
    bgAudio: Boolean, useHwDecoder: Boolean, loopMode: LoopMode,
    brightness: Float, sleepMins: Int, abActive: Boolean,
    abA: Long, abB: Long, currentPos: Long, bookmarks: List<Long>,
    onAudioTrack: () -> Unit, onShare: () -> Unit, onFavorite: () -> Unit,
    onBookmark: () -> Unit, onDelete: () -> Unit, onNightMode: () -> Unit,
    onMirrorMode: () -> Unit, onTimer: () -> Unit, onAbRepeat: () -> Unit,
    onBgAudio: () -> Unit, onLoopMode: (LoopMode) -> Unit,
    onBrightness: (Float) -> Unit, onDecoder: (Boolean) -> Unit,
    onFileInfo: () -> Unit, onJumpTo: () -> Unit, onScreenshot: () -> Unit,
    onZoomReset: (() -> Unit)?,
    onEqualizer: () -> Unit, onCast: () -> Unit,
    onSubDelay: () -> Unit, onSubSize: () -> Unit, onBookmarks: () -> Unit,
    onEditPlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(enabled = false) {}
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally)
                .padding(top = 10.dp))
            Spacer(Modifier.height(14.dp))

            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 20.dp)) {

                // ── TOOL ICONS GRID (4 per row, circle style) ─────────
                val tools = listOf(
                    CircleItem(Icons.Filled.RecordVoiceOver, "Audio track", Color.White.copy(0.85f), onAudioTrack),
                    CircleItem(Icons.Filled.Equalizer, "Equalizer", Color.White.copy(0.85f)) { onEqualizer(); onDismiss() },
                    CircleItem(Icons.Filled.Cast,      "Cast",      Color.White.copy(0.85f)) { onCast(); onDismiss() },
                    CircleItem(Icons.Filled.ContentCut,    "Screenshot",  Color.White.copy(0.85f), onScreenshot),
                    CircleItem(Icons.Filled.Favorite,      "Favourite",
                        if (isFavorite) NebulaRed else Color.White.copy(0.85f), onFavorite),
                    CircleItem(Icons.Filled.Bookmark,      "Bookmark",    Color.White.copy(0.85f)) { onBookmarks(); onDismiss() },
                    CircleItem(Icons.Filled.DeleteOutline, "Delete",      NebulaRed.copy(0.8f), onDelete),
                    CircleItem(Icons.Filled.Subtitles,     "Sub delay",   Color.White.copy(0.85f)) { onSubDelay(); onDismiss() },
                    CircleItem(Icons.Filled.FormatSize,    "Sub size",    Color.White.copy(0.85f)) { onSubSize(); onDismiss() },
                    CircleItem(Icons.Filled.Share,         "Share",       Color.White.copy(0.85f), onShare),
                )
                tools.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { item -> CircleIconBtn(item) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Divider(Color.White.copy(0.1f))
                Spacer(Modifier.height(14.dp))

                // ── PLAY SETTINGS ──────────────────────────────────────
                Text("Play settings", color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))

                val abLabel = when {
                    !abActive && abA < 0  -> "AB Repeat"
                    !abActive && abA >= 0 -> "Set B point"
                    else                  -> "AB: ${vidFmt(abA)}→${vidFmt(abB)}"
                }
                val playSettings = listOf(
                    CircleItem(Icons.Filled.Repeat, abLabel,
                        if (abActive) NebulaViolet else Color.White.copy(0.85f), onAbRepeat),
                    CircleItem(Icons.Filled.Nightlight, "Night mode",
                        if (nightMode) NebulaAmber else Color.White.copy(0.85f), onNightMode),
                    CircleItem(Icons.Filled.Flip, "Mirror mode",
                        if (mirrorMode) NebulaCyan else Color.White.copy(0.85f), onMirrorMode),
                    CircleItem(Icons.Filled.Bedtime, if (sleepMins > 0) "Timer: ${sleepMins}m" else "Timer",
                        if (sleepMins > 0) NebulaCyan else Color.White.copy(0.85f), onTimer),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    playSettings.forEach { CircleIconBtn(it) }
                }

                Spacer(Modifier.height(16.dp))
                Divider(Color.White.copy(0.1f))
                Spacer(Modifier.height(14.dp))

                // ── LOOP ──────────────────────────────────────────────
                Text("Loop", color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))

                // 4 loop buttons in a rounded segmented control (Playit style)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.07f)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LoopMode.entries.forEach { mode ->
                        val sel = loopMode == mode
                        Box(
                            Modifier.weight(1f).height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) NebulaGreen else Color.Transparent)
                                .clickable { onLoopMode(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(mode.icon, null,
                                tint = if (sel) Color.White else Color.White.copy(0.5f),
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(Color.White.copy(0.1f))
                Spacer(Modifier.height(14.dp))

                // ── BRIGHTNESS ────────────────────────────────────────
                Text("Brightness", color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.BrightnessLow, null, tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(18.dp))
                    Slider(
                        value = brightness, onValueChange = onBrightness,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = NebulaGreen, thumbColor = Color.White,
                            inactiveTrackColor = Color.White.copy(0.2f))
                    )
                    Icon(Icons.Filled.BrightnessHigh, null, tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.height(14.dp))
                Divider(Color.White.copy(0.1f))
                Spacer(Modifier.height(14.dp))

                // ── DECODER ───────────────────────────────────────────
                Text("Decoder", color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("HW Decoder",
                        color = if (useHwDecoder) NebulaGreen else Color.White.copy(0.4f),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDecoder(true) })
                    Text("SW Decoder",
                        color = if (!useHwDecoder) NebulaGreen else Color.White.copy(0.4f),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDecoder(false) })
                }

                if (onZoomReset != null) {
                    Spacer(Modifier.height(14.dp))
                    Divider(Color.White.copy(0.1f))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clickable(onClick = onZoomReset)
                        .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitScreen, null, tint = NebulaAmber,
                            modifier = Modifier.size(20.dp))
                        Text("Reset zoom", color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(Color.White.copy(0.1f))
                Spacer(Modifier.height(14.dp))

                // ── BOTTOM FIXED BAR (Playit: Edit player · Tutorials · File info · Feedback) ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomSheetFooterBtn(Icons.Filled.Edit, "Edit player") { onEditPlayer(); onDismiss() }
                    BottomSheetFooterBtn(Icons.Filled.Tune, "Jump to") { onJumpTo(); onDismiss() }
                    BottomSheetFooterBtn(Icons.Filled.Info, "File info") { onFileInfo(); onDismiss() }
                    BottomSheetFooterBtn(Icons.Filled.BugReport, "Feedback") { DeckToastEngine.info("Send feedback via the Play Store review. Thank you!") }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetFooterBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(0.1f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White.copy(0.75f), modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White.copy(0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

private data class CircleItem(
    val icon: ImageVector, val label: String, val tint: Color, val onClick: () -> Unit)

@Composable
private fun CircleIconBtn(item: CircleItem) {
    Column(
        Modifier.clickable(onClick = item.onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Color.White.copy(0.1f))
                .border(1.dp, item.tint.copy(0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(24.dp))
        }
        Text(item.label, color = Color.White.copy(0.75f),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Divider(color: Color) {
    HorizontalDivider(color = color, thickness = 0.5.dp)
}

// ═════════════════════════════════════════════════════════════════════════════
// SEEK BAR
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SeekBarSection(
    pos: Long, dur: Long, dragging: Boolean, dragFrac: Float, thumbBitmap: Bitmap?,
    modifier: Modifier,
    abA: Long = -1L, abB: Long = -1L,  // AB repeat markers
    onStart: (Float) -> Unit, onMove: (Float) -> Unit, onEnd: (Float) -> Unit,
) {
    val frac = if (dragging) dragFrac else (pos.toFloat() / dur).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        // Thumbnail
        if (dragging && thumbBitmap != null) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(82.dp)) {
                val w = maxWidth; val tw = 120.dp
                val ox = (dragFrac * (w - tw)).coerceIn(0.dp, w - tw)
                Box(Modifier.offset(x = ox)) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(6.dp))) {
                        androidx.compose.foundation.Image(thumbBitmap.asImageBitmap(), null,
                            modifier = Modifier.size(tw, 68.dp), contentScale = ContentScale.Crop)
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(0.65f)).padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center) {
                            Text(vidFmt((dragFrac * dur).toLong()), color = Color.White,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else { Spacer(Modifier.height(82.dp)) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vidFmt(if (dragging) (dragFrac * dur).toLong() else pos),
                color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium)
            Text(vidFmt(dur), color = Color.White.copy(0.9f),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(26.dp)
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onStart((o.x / size.width).coerceIn(0f, 1f)) },
                    onHorizontalDrag = { _, dx -> onMove((dragFrac + dx / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { onEnd(dragFrac) }, onDragCancel = { onEnd(dragFrac) }
                )
            }
            .pointerInput(dur) {
                detectTapGestures { o -> onEnd((o.x / size.width).coerceIn(0f, 1f)) }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f; val th = 4.dp.toPx(); val tr = if (dragging) 8.dp.toPx() else 6.dp.toPx()
                // Background track
                drawRoundRect(Color.White.copy(0.22f), Offset(0f, cy - th / 2f), Size(size.width, th), CornerRadius(th / 2f))
                // AB range highlight (green fill between A and B)
                if (abA >= 0 && abB > abA && dur > 0) {
                    val ax = (abA.toFloat() / dur) * size.width
                    val bx = (abB.toFloat() / dur) * size.width
                    drawRoundRect(
                        NebulaGreen.copy(0.35f),
                        Offset(ax, cy - th / 2f),
                        Size(bx - ax, th),
                        CornerRadius(th / 2f)
                    )
                }
                // Played portion
                if (frac > 0f) drawRoundRect(NebulaViolet, Offset(0f, cy - th / 2f), Size(size.width * frac, th), CornerRadius(th / 2f))
                // AB tick marks
                if (abA >= 0 && dur > 0) {
                    val ax = (abA.toFloat() / dur) * size.width
                    drawCircle(NebulaGreen, 5.dp.toPx(), Offset(ax, cy))
                    drawLine(NebulaGreen, Offset(ax, cy - 10.dp.toPx()), Offset(ax, cy + 10.dp.toPx()), 2.dp.toPx())
                }
                if (abB >= 0 && dur > 0) {
                    val bx = (abB.toFloat() / dur) * size.width
                    drawCircle(NebulaAmber, 5.dp.toPx(), Offset(bx, cy))
                    drawLine(NebulaAmber, Offset(bx, cy - 10.dp.toPx()), Offset(bx, cy + 10.dp.toPx()), 2.dp.toPx())
                }
                // Thumb
                val tx = (size.width * frac).coerceIn(tr, size.width - tr)
                drawCircle(Color.White, tr, Offset(tx, cy))
                drawCircle(NebulaViolet, tr * 0.42f, Offset(tx, cy))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SMALL COMPOSABLES
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SideBtn(icon: ImageVector, tint: Color = Color.White.copy(0.85f), onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.55f))
            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun TopIconBtn(icon: ImageVector, tint: Color = Color.White.copy(0.85f), onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TopBadge(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.15f))
        .border(0.5.dp, Color.White.copy(0.3f), RoundedCornerShape(4.dp))
        .padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(text, color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Badge(color: Color, icon: ImageVector, text: String) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(0.2f))
        .border(0.5.dp, color.copy(0.4f), RoundedCornerShape(5.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CenterBtn(icon: ImageVector, size: Dp, iconSz: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, null, tint = if (enabled) Color.White else Color.White.copy(0.2f),
            modifier = Modifier.size(iconSz))
    }
}

@Composable
private fun BarBtn(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(icon, null, tint = if (enabled) Color.White.copy(0.9f) else Color.White.copy(0.25f),
            modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SideHud(icon: ImageVector, value: Float) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xBB000000))
            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp).width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(0.75f), modifier = Modifier.size(15.dp))
        Box(Modifier.width(5.dp).height(80.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.15f))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter).clip(RoundedCornerShape(3.dp)).background(NebulaViolet))
        }
        Text("${(value * 100).toInt()}", color = Color.White.copy(0.65f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TapRipple(isLeft: Boolean, label: String) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.5f, tween(400, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(0f,   tween(400, easing = LinearEasing)) }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.45f)
                .let { if (isLeft) it.align(Alignment.CenterStart) else it.align(Alignment.CenterEnd) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(110.dp)) {
                val r = size.minDimension / 2f * scale.value
                drawCircle(Color.White.copy(alpha.value * 0.22f), r)
                drawCircle(Color.White.copy(alpha.value * 0.1f), r * 1.3f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(if (isLeft) Icons.Filled.Replay10 else Icons.Filled.Forward10, null,
                    tint = Color.White.copy(alpha.value.coerceAtLeast(0.6f)), modifier = Modifier.size(34.dp))
                Text(label, color = Color.White.copy(alpha.value.coerceAtLeast(0.8f)),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UpNextCard(video: Song, timeLeft: Long, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xDD000000))
            .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)).padding(8.dp)
            .widthIn(max = 260.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(50.dp, 32.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1A1A2E))) {
            if (video.albumArtUri != null) {
                AsyncImage(ImageRequest.Builder(ctx).data(video.albumArtUri).crossfade(true).build(),
                    null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(0.45f),
                    modifier = Modifier.align(Alignment.Center).size(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Up Next", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
            Text(video.title, style = MaterialTheme.typography.labelMedium, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("in ${vidFmt(timeLeft)}", style = MaterialTheme.typography.labelSmall, color = NebulaViolet)
        }
        IconButton(onClick = onSkip, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.SkipNext, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, null, tint = Color.White.copy(0.35f), modifier = Modifier.size(12.dp))
        }
    }
}

// Track sheet, Info sheet, Jump dialog — same as before, slimmed
@Composable
private fun TrackSheet(title: String, tracks: List<String>, type: Int, player: ExoPlayer,
    tint: Color, emptyMsg: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420)).clickable(enabled = false) {}
            .navigationBarsPadding().padding(top = 10.dp, bottom = 8.dp)) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            if (tracks.isEmpty()) {
                Text(emptyMsg, color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            } else {
                tracks.forEachIndexed { i, lbl ->
                    Row(Modifier.fillMaxWidth().clickable {
                        try {
                            player.currentTracks.groups.filter { it.type == type }.getOrNull(i)?.let { g ->
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                            }
                        } catch (_: Exception) {}
                        onDismiss()
                    }.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (type == C.TRACK_TYPE_TEXT) Icons.Filled.Subtitles else Icons.Filled.RecordVoiceOver,
                            null, tint = tint, modifier = Modifier.size(20.dp))
                        Text(lbl, color = Color.White.copy(0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = Color.White.copy(0.07f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            Row(Modifier.fillMaxWidth().clickable {
                try { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setDisabledTrackTypes(setOf(type)).build() } catch (_: Exception) {}
                onDismiss()
            }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.Block, null, tint = NebulaRed, modifier = Modifier.size(20.dp))
                Text("Disable $title", color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoSheet(video: Song, fmt: androidx.media3.common.Format?, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420)).clickable(enabled = false) {}
            .navigationBarsPadding().padding(top = 10.dp, bottom = 20.dp)) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Text("File info", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            val rows = buildList {
                add("Title" to video.title); add("Duration" to video.durationFormatted)
                add("Size" to video.sizeFormatted); add("Format" to video.filePath.substringAfterLast(".").uppercase())
                fmt?.let { f ->
                    if (f.width > 0 && f.height > 0) add("Resolution" to "${f.width} × ${f.height}")
                    f.codecs?.let { add("Codec" to it.substringBefore(".")) }
                    if (f.bitrate > 0) add("Bitrate" to "${f.bitrate / 1000} kbps")
                    if (f.frameRate > 0) add("Frame rate" to "${f.frameRate.toInt()} fps")
                }
                add("Path" to video.filePath)
            }
            rows.forEach { (lbl, v) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp)) {
                    Text(lbl, color = Color.White.copy(0.38f), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(90.dp))
                    Text(v, color = Color.White.copy(0.88f), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = Color.White.copy(0.06f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun JumpToDialog(duration: Long, onSeek: (Long) -> Unit) {
    var input by remember { mutableStateOf("") }; var err by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = {}, containerColor = Color(0xFF141420),
        title = { Text("Jump to time", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("mm:ss or h:mm:ss", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.45f))
                OutlinedTextField(value = input, onValueChange = { input = it; err = false }, singleLine = true, isError = err,
                    placeholder = { Text("0:30", color = Color.White.copy(0.3f)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = Color.White.copy(0.3f), errorBorderColor = NebulaRed),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                if (err) Text("Invalid time", color = NebulaRed, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { val ms = parseVidTime(input); if (ms != null && ms in 0L..duration) onSeek(ms) else err = true },
                colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                Text("Go", fontWeight = FontWeight.Bold)
            }
        }, dismissButton = {}
    )
}

@Composable
private fun SleepPickerSheet(currentMins: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF141420)).clickable(enabled = false) {}
            .navigationBarsPadding().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 20.dp)) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            presets.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val sel = currentMins == m
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) NebulaViolet else Color.White.copy(0.08f))
                            .clickable { onPick(m) }, contentAlignment = Alignment.Center) {
                            Text(if (m < 60) "${m}m" else "${m / 60}h",
                                color = if (sel) Color.White else Color.White.copy(0.75f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (currentMins > 0) {
                OutlinedButton(onClick = { onPick(0) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NebulaRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NebulaRed)) {
                    Text("Cancel Timer")
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// NOTIFICATIONS + UTILITIES
// ═════════════════════════════════════════════════════════════════════════════
// Notification action intents
private const val ACTION_VID_PLAY_PAUSE = "com.sayaem.nebula.VID_PLAY_PAUSE"
private const val ACTION_VID_NEXT       = "com.sayaem.nebula.VID_NEXT"
private const val ACTION_VID_PREV       = "com.sayaem.nebula.VID_PREV"

private fun showVidNotif(context: Context, video: Song, playing: Boolean, pos: Long, dur: Long) {
    try {
        // Content intent — open app
        val openPi = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Action intents — sent to MainActivity which forwards to player
        fun actionIntent(action: String, req: Int) = PendingIntent.getActivity(
            context, req,
            Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePi = actionIntent(ACTION_VID_PLAY_PAUSE, 101)
        val prevPi      = actionIntent(ACTION_VID_PREV,       102)
        val nextPi      = actionIntent(ACTION_VID_NEXT,       103)

        val progress = if (dur > 0) ((pos.toFloat() / dur) * 100).toInt() else 0

        val n = NotificationCompat.Builder(context, DeckNotificationEngine.CH_PLAYBACK)
            .setSmallIcon(if (playing) android.R.drawable.ic_media_pause
                          else        android.R.drawable.ic_media_play)
            .setContentTitle(video.title)
            .setContentText("${vidFmt(pos)} / ${vidFmt(dur)}")
            .setProgress(100, progress, false)
            .setContentIntent(openPi)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Action buttons
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPi)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                playPausePi
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPi)
            // MediaStyle — shows on lock screen, uses media controls UI
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        NotificationManagerCompat.from(context).notify(DeckNotificationEngine.ID_PLAYBACK, n)
    } catch (_: Exception) {}
}

private fun cancelVidNotif(context: Context) {
    try { NotificationManagerCompat.from(context).cancel(DeckNotificationEngine.ID_PLAYBACK) } catch (_: Exception) {}
}

private suspend fun captureScreenshot(pv: PlayerView?): String {
    pv ?: return "No player"
    if (pv.width == 0) return "Not ready"
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val bmp = Bitmap.createBitmap(pv.width, pv.height, Bitmap.Config.ARGB_8888)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.PixelCopy.request(
                    (pv.context as? Activity)?.window ?: run { cont.resumeWith(Result.success("No window")); return@suspendCancellableCoroutine },
                    android.graphics.Rect(pv.left, pv.top, pv.right, pv.bottom), bmp,
                    { result ->
                        if (result == android.view.PixelCopy.SUCCESS) {
                            try {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                val f = File(File(dir, "Deck Screenshots").also { it.mkdirs() },
                                    "deck_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
                                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                android.media.MediaScannerConnection.scanFile(pv.context, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                                bmp.recycle(); cont.resumeWith(Result.success("Screenshot saved ✓"))
                            } catch (_: Exception) { cont.resumeWith(Result.success("Save failed")) }
                        } else { bmp.recycle(); cont.resumeWith(Result.success("Capture failed")) }
                    }, android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                android.graphics.Canvas(bmp).also { pv.draw(it) }
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val f = File(File(dir, "Deck Screenshots").also { it.mkdirs() }, "deck_${System.currentTimeMillis()}.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle(); cont.resumeWith(Result.success("Screenshot saved ✓"))
            }
        } catch (e: Exception) { cont.resumeWith(Result.success("Failed")) }
    }
}


// ═════════════════════════════════════════════════════════════════════════════
// SUBTITLE DELAY SHEET
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SubtitleDelaySheet(delayMs: Long, onChange: (Long) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Subtitle Delay", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                // Current delay badge
                Box(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (delayMs == 0L) Color.White.copy(0.1f) else NebulaViolet.copy(0.25f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("${if (delayMs >= 0) "+" else ""}${delayMs}ms",
                        color = if (delayMs == 0L) Color.White.copy(0.6f) else NebulaViolet,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            // Step buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(-1000L, -500L, -100L).forEach { delta ->
                    OutlinedButton(
                        onClick = { onChange(delayMs + delta) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NebulaRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NebulaRed.copy(0.4f))
                    ) { Text("${delta / 1000}s", fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    onClick = { onChange(0L) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f))
                ) { Text("0", fontWeight = FontWeight.Bold) }
                listOf(100L, 500L, 1000L).forEach { delta ->
                    OutlinedButton(
                        onClick = { onChange(delayMs + delta) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NebulaGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NebulaGreen.copy(0.4f))
                    ) { Text("+${delta / 1000}s", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Negative = subtitles appear earlier · Positive = later",
                color = Color.White.copy(0.4f), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            // Note about functionality
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(NebulaAmber.copy(0.1f))
                .border(0.5.dp, NebulaAmber.copy(0.3f), RoundedCornerShape(10.dp))
                .padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Info, null, tint = NebulaAmber, modifier = Modifier.size(16.dp))
                    Text("Subtitle delay requires Media3 1.4+. Currently stored — will apply automatically when player updates.",
                        color = NebulaAmber.copy(0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SUBTITLE SIZE SHEET
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SubtitleSizeSheet(scale: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Text("Subtitle Size", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            // Preview text
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(0.6f)).padding(vertical = 20.dp),
                contentAlignment = Alignment.Center) {
                Text("Subtitle preview text",
                    color = Color.White,
                    fontSize = (16 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            // Size slider
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A", color = Color.White.copy(0.5f), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = scale, onValueChange = onChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = NebulaViolet, thumbColor = Color.White,
                        inactiveTrackColor = Color.White.copy(0.2f))
                )
                Text("A", color = Color.White.copy(0.85f),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            // Preset sizes
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f to "Small", 1.0f to "Default", 1.25f to "Medium", 1.5f to "Large").forEach { (s, label) ->
                    Box(Modifier.weight(1f).height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (kotlin.math.abs(scale - s) < 0.05f) NebulaViolet else Color.White.copy(0.08f))
                        .clickable { onChange(s) },
                        contentAlignment = Alignment.Center) {
                        Text(label,
                            color = if (kotlin.math.abs(scale - s) < 0.05f) Color.White else Color.White.copy(0.6f),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// BOOKMARKS SHEET
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun BookmarksSheet(
    bookmarks: List<Long>,
    currentPos: Long,
    onJump: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAddCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(top = 14.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Bookmarks", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                // Add current position button
                Box(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(NebulaViolet.copy(0.2f))
                    .border(0.5.dp, NebulaViolet.copy(0.5f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddCurrent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = NebulaViolet,
                            modifier = Modifier.size(14.dp))
                        Text("Add ${vidFmt(currentPos)}", color = NebulaViolet,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (bookmarks.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Bookmark, null, tint = Color.White.copy(0.25f),
                            modifier = Modifier.size(40.dp))
                        Text("No bookmarks yet", color = Color.White.copy(0.4f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Tap 'Add' above to bookmark the current position",
                            color = Color.White.copy(0.3f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    items(bookmarks.size) { i ->
                        val bm = bookmarks[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { onJump(bm); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Filled.Bookmark, null, tint = NebulaAmber,
                                modifier = Modifier.size(20.dp))
                            Text("Bookmark ${i + 1}", color = Color.White.copy(0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f))
                            Text(vidFmt(bm), color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { onDelete(bm) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, null, tint = NebulaRed.copy(0.7f),
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(0.07f), thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// VIDEO EQ SHEET — shows existing EQ bands wired to video audio session
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoEqSheet(
    eq: AndroidEqualizer?,
    eqEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    bandLevels: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Video Equalizer", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (eqEnabled) "ON" else "OFF",
                        color = if (eqEnabled) NebulaGreen else Color.White.copy(0.4f),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Switch(checked = eqEnabled, onCheckedChange = { v ->
                        onEnabledChange(v)
                        try { eq?.enabled = v } catch (_: Exception) {}
                    }, colors = SwitchDefaults.colors(
                        checkedTrackColor = NebulaGreen, checkedThumbColor = Color.White))
                }
            }
            Spacer(Modifier.height(16.dp))

            if (eq == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Equalizer, null, tint = Color.White.copy(0.2f),
                            modifier = Modifier.size(48.dp))
                        Text("Waiting for audio session...",
                            color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodyMedium)
                        Text("Tap play and reopen EQ",
                            color = Color.White.copy(0.3f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                // Get real band range from the EQ
                val numBands = try { eq.numberOfBands.toInt() } catch (_: Exception) { 5 }
                val minLevel = try { eq.bandLevelRange[0].toInt() } catch (_: Exception) { -1500 }
                val maxLevel = try { eq.bandLevelRange[1].toInt() } catch (_: Exception) { 1500 }
                // Band labels from center frequencies
                val bandLabels = (0 until numBands).map { i ->
                    try {
                        val hz = eq.getCenterFreq(i.toShort()) / 1000
                        if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
                    } catch (_: Exception) { "Band $i" }
                }

                Row(Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 0 until minOf(numBands, bandLevels.size)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val db = bandLevels[i] / 100
                            Text("${if (db >= 0) "+" else ""}${db}dB",
                                color = when {
                                    db > 0  -> NebulaGreen
                                    db < 0  -> NebulaRed
                                    else    -> Color.White.copy(0.4f)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium)
                            Slider(
                                value        = bandLevels[i].toFloat(),
                                onValueChange = { v ->
                                    val level = v.toInt()
                                    bandLevels[i] = level
                                    try { if (eqEnabled) eq.setBandLevel(i.toShort(), level.toShort()) }
                                    catch (_: Exception) {}
                                },
                                valueRange   = minLevel.toFloat()..maxLevel.toFloat(),
                                modifier     = Modifier.height(140.dp).width(36.dp),
                                colors       = SliderDefaults.colors(
                                    activeTrackColor   = NebulaViolet,
                                    thumbColor         = Color.White,
                                    inactiveTrackColor = Color.White.copy(0.18f))
                            )
                            Text(bandLabels[i], color = Color.White.copy(0.5f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Reset button
                TextButton(
                    onClick = {
                        for (i in bandLevels.indices) {
                            bandLevels[i] = 0
                            try { eq.setBandLevel(i.toShort(), 0) } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset to flat", color = Color.White.copy(0.5f),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// CAST SHEET — Chromecast (UI ready, functionality Round C)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun CastSheet(
    castManager: DeckCastManager,
    video: Song,
    currentPos: Long,
    onDismiss: () -> Unit,
) {
    val castState by castManager.castState.collectAsState()
    val deviceName by castManager.connectedDeviceName.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, null,
                        tint = if (castState == CastState.CONNECTED || castState == CastState.PLAYING)
                            NebulaCyan else Color.White.copy(0.7f),
                        modifier = Modifier.size(26.dp))
                    Text("Cast to Device", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                }
                // Status badge
                val (statusText, statusColor) = when (castState) {
                    CastState.CONNECTED -> "Connected" to NebulaGreen
                    CastState.PLAYING   -> "Casting" to NebulaCyan
                    CastState.PAUSED    -> "Paused" to NebulaAmber
                    CastState.CONNECTING -> "Connecting..." to NebulaAmber
                    CastState.ERROR     -> "Error" to NebulaRed
                    else                -> "Not connected" to Color.White.copy(0.4f)
                }
                Box(Modifier.clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(statusText, color = statusColor,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (castState == CastState.DISCONNECTED || castState == CastState.ERROR) {
                // Scanning state
                val inf = rememberInfiniteTransition(label = "s")
                val pulse by inf.animateFloat(0.3f, 1f,
                    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
                Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.04f))
                    .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cast, null, tint = NebulaCyan.copy(pulse),
                            modifier = Modifier.size(22.dp))
                        Text("Scanning for Chromecast devices...",
                            color = Color.White.copy(0.55f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Make sure your Chromecast is on the same Wi-Fi network.",
                    color = Color.White.copy(0.35f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // Connected — show device + controls
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(NebulaGreen.copy(0.08f))
                    .border(0.5.dp, NebulaGreen.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Tv, null, tint = NebulaGreen,
                            modifier = Modifier.size(24.dp))
                        Column {
                            Text(deviceName ?: "Chromecast", color = Color.White,
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("Tap below to cast current video",
                                color = Color.White.copy(0.5f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Cast / Stop button
                Button(
                    onClick = {
                        if (castState == CastState.PLAYING || castState == CastState.PAUSED) {
                            castManager.stop()
                        } else {
                            val success = castManager.castVideo(video, currentPos)
                            if (!success) DeckToastEngine.error("Cast failed — check device connection")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (castState == CastState.PLAYING) NebulaRed else NebulaCyan)
                ) {
                    Icon(
                        if (castState == CastState.PLAYING) Icons.Filled.Stop else Icons.Filled.Cast,
                        null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (castState == CastState.PLAYING) "Stop casting"
                        else "Cast: ${video.title}",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Disconnect button
                OutlinedButton(
                    onClick = { castManager.endSession(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NebulaRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NebulaRed.copy(0.4f))
                ) { Text("Disconnect device") }
            }
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════════
// EDIT PLAYER SHEET — customize control layout (UI complete)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun EditPlayerSheet(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.65f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420)).clickable(enabled = false) {}
                .navigationBarsPadding().padding(top = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Text("Edit Player", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
            Text("Customize which controls appear in the player",
                color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // Control toggles — keyed to SharedPreferences, survive restarts
            val ctx = LocalContext.current
            val epPrefs = remember { ctx.getSharedPreferences("deck_vp", android.content.Context.MODE_PRIVATE) }
            data class PlayerOption(val key: String, val label: String, val default: Boolean)
            val options = listOf(
                PlayerOption("ep_thumbnail",    "Show seek thumbnail preview",   true),
                PlayerOption("ep_upnext",       "Show Up Next card",             true),
                PlayerOption("ep_side_btns",    "Show side buttons (mute, lock)", true),
                PlayerOption("ep_autohide",     "Auto-hide controls (4 sec)",    true),
                PlayerOption("ep_autoadvance",  "Auto-advance to next video",    true),
                PlayerOption("ep_save_pos",     "Remember watch position",       true),
                PlayerOption("ep_seek_ripple",  "Show double-tap ripple",        true),
            )
            options.forEach { opt ->
                var checked by remember { mutableStateOf(epPrefs.getBoolean(opt.key, opt.default)) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(opt.label, color = Color.White.copy(0.88f),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = checked,
                        onCheckedChange = { v ->
                            checked = v
                            epPrefs.edit().putBoolean(opt.key, v).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NebulaViolet,
                            uncheckedThumbColor = Color.White.copy(0.6f),
                            uncheckedTrackColor = Color.White.copy(0.15f))
                    )
                }
                HorizontalDivider(color = Color.White.copy(0.07f), thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(14.dp))
            // Reset to defaults
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NebulaRed.copy(0.08f))
                    .border(0.5.dp, NebulaRed.copy(0.25f), RoundedCornerShape(10.dp))
                    .clickable {
                        options.forEach { opt ->
                            epPrefs.edit().putBoolean(opt.key, opt.default).apply()
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, null, tint = NebulaRed, modifier = Modifier.size(16.dp))
                    Text("Reset to defaults", color = NebulaRed,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════════
// VIDEO QUEUE SHEET — shows full queue, highlights current, tap to jump
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun VideoQueueSheet(
    videos: List<Song>,
    currentIdx: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIdx - 2).coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.65f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF141420))
                .clickable(enabled = false) {}
                .navigationBarsPadding()
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.2f))
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Box(Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${currentIdx + 1} / ${videos.size}",
                        color = Color.White.copy(0.55f),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                itemsIndexed(videos) { i, video ->
                    val isCurrent = i == currentIdx
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isCurrent) NebulaViolet.copy(0.12f) else Color.Transparent)
                            .clickable { onJump(i) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Index number or playing indicator
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            if (isCurrent) {
                                Icon(Icons.Filled.PlayArrow, null, tint = NebulaViolet,
                                    modifier = Modifier.size(18.dp))
                            } else {
                                Text("${i + 1}", color = Color.White.copy(0.35f),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        // Thumbnail
                        Box(Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1A2E))) {
                            if (video.albumArtUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(video.albumArtUri).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.VideoFile, null,
                                    tint = Color.White.copy(0.25f),
                                    modifier = Modifier.align(Alignment.Center).size(16.dp))
                            }
                        }
                        // Title + duration
                        Column(Modifier.weight(1f)) {
                            Text(video.title,
                                color = if (isCurrent) NebulaViolet else Color.White.copy(0.88f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(video.durationFormatted,
                                color = Color.White.copy(0.4f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (!isCurrent) {
                        HorizontalDivider(color = Color.White.copy(0.06f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                    }
                }
            }
        }
    }
}

fun vidFmt(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val s = ms / 1000L; val h = s / 3600L; val m = (s % 3600L) / 60L; val sec = s % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun parseVidTime(input: String): Long? = try {
    val p = input.trim().split(":")
    when (p.size) {
        2 -> (p[0].toLong() * 60 + p[1].toLong()) * 1000L
        3 -> (p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong()) * 1000L
        else -> null
    }
} catch (_: Exception) { null }
