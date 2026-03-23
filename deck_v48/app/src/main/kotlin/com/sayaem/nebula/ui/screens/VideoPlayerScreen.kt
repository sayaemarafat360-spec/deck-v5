package com.sayaem.nebula.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.notifications.DeckToastEngine
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private enum class OrientMode { AUTO, PORTRAIT, LANDSCAPE }
private enum class AspectMode(val label: String, val value: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO PLAYER SCREEN — Playit-inspired layout
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VideoPlayerScreen(
    videos: List<Song>,
    startIndex: Int = 0,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val audioMgr = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val scope    = rememberCoroutineScope()
    val prefs    = remember { context.getSharedPreferences("deck_video_pos", android.content.Context.MODE_PRIVATE) }

    // ── Queue ─────────────────────────────────────────────────────────────
    var idx by remember { mutableIntStateOf(startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))) }
    val video = videos.getOrNull(idx) ?: return

    val vp = remember { ExoPlayer.Builder(context.applicationContext).build() }

    // ── Orientation ───────────────────────────────────────────────────────
    var orientMode by remember { mutableStateOf(OrientMode.LANDSCAPE) }
    LaunchedEffect(orientMode) {
        activity?.requestedOrientation = when (orientMode) {
            OrientMode.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientMode.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // ── Resume dialog ─────────────────────────────────────────────────────
    var showResume by remember { mutableStateOf(false) }
    var resumePos  by remember { mutableLongStateOf(0L) }

    fun loadVideo(song: Song, restart: Boolean = false) {
        vp.stop(); vp.clearMediaItems()
        vp.setMediaItem(MediaItem.fromUri(song.uri))
        vp.prepare()
        val saved = prefs.getLong("p${song.id}", 0L)
        if (!restart && saved > 10_000L && saved < (song.duration - 10_000L)) {
            resumePos = saved; showResume = true
            vp.seekTo(saved); vp.playWhenReady = false
        } else {
            prefs.edit().remove("p${song.id}").apply()
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
            vp.stop(); vp.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(idx) { if (idx != startIndex) loadVideo(video) }
    BackHandler(onBack)

    // ── Playback state ────────────────────────────────────────────────────
    var isPlaying  by remember { mutableStateOf(true) }
    var isLocked   by remember { mutableStateOf(false) }
    var loopOn     by remember { mutableStateOf(false) }
    var speed      by remember { mutableStateOf(1f) }
    var aspect     by remember { mutableStateOf(AspectMode.FIT) }
    var nightMode  by remember { mutableStateOf(false) }
    var videoScale by remember { mutableStateOf(1f) }

    // ── UI visibility ─────────────────────────────────────────────────────
    var showControls  by remember { mutableStateOf(true) }
    var showOverflow  by remember { mutableStateOf(false) }
    var showSpeed     by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var showAudio     by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }
    var showJumpTo    by remember { mutableStateOf(false) }
    var showUpNext    by remember { mutableStateOf(false) }
    var upNextDone    by remember { mutableStateOf(false) }

    // ── Tracks ────────────────────────────────────────────────────────────
    var subTracks   by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioTracks by remember { mutableStateOf<List<String>>(emptyList()) }

    // ── Gesture HUD ───────────────────────────────────────────────────────
    var brightHud     by remember { mutableStateOf<Float?>(null) }
    var volHud        by remember { mutableStateOf<Float?>(null) }
    var seekLabel     by remember { mutableStateOf<String?>(null) }
    var seekLabelLeft by remember { mutableStateOf(true) }
    var longSpeed     by remember { mutableStateOf(false) }

    // ── Seek bar state ────────────────────────────────────────────────────
    var dragging    by remember { mutableStateOf(false) }
    var dragFrac    by remember { mutableStateOf(0f) }
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ── Position ──────────────────────────────────────────────────────────
    val pos by produceState(0L) { while (true) { value = vp.currentPosition; delay(250) } }
    val dur  = vp.duration.coerceAtLeast(1L)

    // Effects
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) { delay(4000); showControls = false }
    }
    LaunchedEffect(brightHud)  { if (brightHud != null)  { delay(1200); brightHud = null } }
    LaunchedEffect(volHud)     { if (volHud != null)     { delay(1200); volHud = null } }
    LaunchedEffect(seekLabel)  { if (seekLabel != null)  { delay(650);  seekLabel = null } }
    LaunchedEffect(loopOn)     { vp.repeatMode = if (loopOn) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(longSpeed)  { vp.setPlaybackSpeed(if (longSpeed) 2f else speed) }

    // Track detection
    LaunchedEffect(idx) {
        delay(1200)
        try {
            val t = vp.currentTracks
            subTracks = t.groups.indices.filter { t.groups[it].type == C.TRACK_TYPE_TEXT }
                .mapIndexed { i, _ -> "Subtitle ${i + 1}" }
            audioTracks = t.groups.indices.filter { t.groups[it].type == C.TRACK_TYPE_AUDIO }
                .mapIndexed { i, gi ->
                    val f = t.groups[gi].getTrackFormat(0)
                    val lang = f.language?.uppercase() ?: "Track ${i + 1}"
                    val ch = if (f.channelCount <= 1) "Mono" else if (f.channelCount == 2) "Stereo" else "${f.channelCount}ch"
                    "$lang · $ch"
                }
        } catch (_: Exception) {}
    }

    // Position save + Up Next
    LaunchedEffect(pos) {
        if (pos > 10_000L) prefs.edit().putLong("p${video.id}", pos).apply()
        val rem = dur - pos
        if (rem in 5_000L..30_000L && !upNextDone && idx < videos.size - 1 && !loopOn)
            showUpNext = true
        else if (rem > 30_000L) { upNextDone = false; showUpNext = false }
    }

    // Auto-next on end
    DisposableEffect(idx) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !loopOn) {
                    prefs.edit().remove("p${video.id}").apply()
                    if (idx < videos.size - 1) { idx++; showUpNext = false; upNextDone = false }
                    else onBack()
                }
                isPlaying = vp.isPlaying
            }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        }
        vp.addListener(listener)
        onDispose { vp.removeListener(listener) }
    }

    // Seek thumbnail
    LaunchedEffect(dragging, dragFrac) {
        if (!dragging) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(video.filePath)
                val bmp = mmr.getFrameAtTime(
                    (dragFrac * dur * 1000L).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                mmr.release()
                withContext(Dispatchers.Main) {
                    thumbBitmap = bmp?.let {
                        Bitmap.createScaledBitmap(it, 180, 102, true)
                            .also { s -> if (s !== it) it.recycle() }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var pvRef by remember { mutableStateOf<PlayerView?>(null) }

    // ════════════════════════════════════════════════════════════════════════
    // ROOT
    // ════════════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── PlayerView ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = vp; useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspect.value; pvRef = this
                }
            },
            update = { pv ->
                pv.resizeMode = aspect.value
                pv.scaleX = videoScale; pv.scaleY = videoScale
            },
            modifier = Modifier.fillMaxSize()
        )

        // Night mode overlay
        if (nightMode) {
            Box(Modifier.fillMaxSize().background(Color(0x40000000)))
            Box(Modifier.fillMaxSize().background(Color(0x12FF8A00)))
        }

        // ── Gesture layer ─────────────────────────────────────────────────
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
                        if (abs(dy) > 0.004f) {
                            if (c.x < size.width / 2) {
                                val lp = activity?.window?.attributes ?: return@detectTransformGestures
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur + dy).coerceIn(0.01f, 1f)
                                activity.window.attributes = lp; brightHud = lp.screenBrightness
                            } else {
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val nv = (cur + (dy * max).toInt()).coerceIn(0, max)
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
                            if (now - lastTapMs < 320) {
                                val left = it.x < size.width / 2
                                vp.seekTo((vp.currentPosition + if (left) -10_000L else 10_000L).coerceAtLeast(0))
                                seekLabel = if (left) "−10s" else "+10s"; seekLabelLeft = left
                            } else showControls = !showControls
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

        // ── Seek ripple ───────────────────────────────────────────────────
        if (seekLabel != null) {
            SeekRippleOverlay(isLeft = seekLabelLeft, label = seekLabel ?: "")
        }

        // ── 2× speed badge ────────────────────────────────────────────────
        AnimatedVisibility(visible = longSpeed,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
            Box(Modifier.clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(0.72f))
                .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = NebulaViolet,
                        modifier = Modifier.size(16.dp))
                    Text("2× Speed", color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Brightness HUD — left side slim bar ───────────────────────────
        brightHud?.let { SlimHud(Icons.Filled.Brightness6, it, Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) }
        // ── Volume HUD — right side slim bar ─────────────────────────────
        volHud?.let    { SlimHud(Icons.Filled.VolumeUp, it, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) }

        // ── Zoom badge ────────────────────────────────────────────────────
        if (videoScale > 1.05f) {
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding()
                .padding(start = 60.dp, top = 4.dp)) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.65f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${String.format("%.1f", videoScale)}×",
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Up Next card ──────────────────────────────────────────────────
        val nextVid = videos.getOrNull(idx + 1)
        if (showUpNext && nextVid != null) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 16.dp, bottom = 132.dp)) {
                UpNextCard(nextVid.title, dur - pos,
                    onSkip    = { idx++; showUpNext = false },
                    onDismiss = { showUpNext = false; upNextDone = true })
            }
        }

        // ── Lock overlay ──────────────────────────────────────────────────
        if (isLocked) {
            AnimatedVisibility(visible = showControls,
                enter = fadeIn(tween(120)), exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) {
                Box(Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.75f))
                    .border(1.dp, NebulaViolet.copy(0.5f), RoundedCornerShape(12.dp))
                    .clickable { isLocked = false }
                    .padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.LockOpen, null, tint = NebulaViolet,
                            modifier = Modifier.size(22.dp))
                        Text("Unlock", color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // MAIN CONTROLS — fade in/out together
        // ════════════════════════════════════════════════════════════════
        if (!isLocked) {
            AnimatedVisibility(
                visible  = showControls,
                enter    = fadeIn(tween(160)),
                exit     = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f   to Color.Black.copy(0.82f),
                            0.22f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f   to Color.Black.copy(0.90f),
                        )
                    )
                ) {
                    // ── TOP BAR — back + title + overflow ─────────────
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back
                        IconButton(onClick = onBack,
                            modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White,
                                modifier = Modifier.size(22.dp))
                        }

                        // Title + queue position
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(video.title, color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (videos.size > 1) {
                                Text("${idx + 1} / ${videos.size}",
                                    color = Color.White.copy(0.5f),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Resolution badge
                        val vfmt = vp.videoFormat
                        if (vfmt != null && vfmt.height > 0) {
                            val res = when {
                                vfmt.height >= 2160 -> "4K"
                                vfmt.height >= 1080 -> "1080p"
                                vfmt.height >= 720  -> "720p"
                                else                -> "${vfmt.height}p"
                            }
                            Box(Modifier.clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.14f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text(res, color = Color.White.copy(0.9f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        // ⋮ Overflow button — ONLY other top-bar item
                        IconButton(onClick = { showOverflow = true },
                            modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.MoreVert, null, tint = Color.White,
                                modifier = Modifier.size(22.dp))
                        }
                    }

                    // ── CENTER TRANSPORT ──────────────────────────────
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prev video (smaller)
                        VidBtn(
                            enabled = idx > 0,
                            size    = 38.dp,
                            icon    = Icons.Filled.SkipPrevious,
                            iconSz  = 24.dp,
                            onClick = { idx-- }
                        )

                        // −10s
                        VidBtn(
                            size   = 46.dp,
                            icon   = Icons.Filled.Replay10,
                            iconSz = 34.dp,
                            onClick = {
                                vp.seekTo((vp.currentPosition - 10_000L).coerceAtLeast(0))
                                seekLabel = "−10s"; seekLabelLeft = true
                            }
                        )

                        // PLAY / PAUSE — dominant 72dp circle
                        Box(
                            Modifier.size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.15f))
                                .border(2.dp, Color.White.copy(0.7f), CircleShape)
                                .clickable {
                                    if (vp.isPlaying) { vp.pause(); isPlaying = false }
                                    else { vp.play(); isPlaying = true }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // +10s
                        VidBtn(
                            size   = 46.dp,
                            icon   = Icons.Filled.Forward10,
                            iconSz = 34.dp,
                            onClick = {
                                vp.seekTo(vp.currentPosition + 10_000L)
                                seekLabel = "+10s"; seekLabelLeft = false
                            }
                        )

                        // Next video (smaller)
                        VidBtn(
                            enabled = idx < videos.size - 1,
                            size    = 38.dp,
                            icon    = Icons.Filled.SkipNext,
                            iconSz  = 24.dp,
                            onClick = { idx++ }
                        )
                    }

                    // ── BOTTOM SECTION ────────────────────────────────
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        // ── SEEK BAR ROW ──────────────────────────────
                        SeekBarSection(
                            pos       = pos,
                            dur       = dur,
                            dragging  = dragging,
                            dragFrac  = dragFrac,
                            thumb     = thumbBitmap,
                            onDragStart = { f -> dragging = true; dragFrac = f },
                            onDragMove  = { f -> dragFrac = f },
                            onDragEnd   = { f ->
                                vp.seekTo((f * dur).toLong()); dragging = false
                            }
                        )

                        Spacer(Modifier.height(6.dp))

                        // ── ACTION ICONS ROW ──────────────────────────
                        // Playit style: icon + label, always visible, evenly spaced
                        ActionIconsRow(
                            subTracks  = subTracks,
                            audioTracks = audioTracks,
                            aspect     = aspect,
                            orientMode = orientMode,
                            loopOn     = loopOn,
                            onSubtitles = { showSubtitles = true },
                            onAudio    = { showAudio = true },
                            onAspect   = { aspect = AspectMode.entries[(AspectMode.entries.indexOf(aspect) + 1) % AspectMode.entries.size] },
                            onOrient   = {
                                orientMode = when (orientMode) {
                                    OrientMode.LANDSCAPE -> OrientMode.AUTO
                                    OrientMode.AUTO      -> OrientMode.PORTRAIT
                                    OrientMode.PORTRAIT  -> OrientMode.LANDSCAPE
                                }
                            },
                            onLock     = { isLocked = true },
                            onLoop     = { loopOn = !loopOn }
                        )
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHEETS & DIALOGS
    // ════════════════════════════════════════════════════════════════════════

    // Overflow menu — all secondary features
    if (showOverflow) {
        OverflowMenu(
            speed      = speed,
            nightMode  = nightMode,
            videoScale = videoScale,
            onSpeed    = { showOverflow = false; showSpeed = true },
            onPip      = {
                showOverflow = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { activity?.enterPictureInPictureMode(
                        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                    } catch (_: Exception) {}
                }
            },
            onScreenshot = {
                showOverflow = false
                scope.launch { DeckToastEngine.success(takeVideoScreenshot(pvRef)) }
            },
            onNightMode  = { nightMode = !nightMode; showOverflow = false },
            onVideoInfo  = { showOverflow = false; showInfo = true },
            onJumpTo     = { showOverflow = false; showJumpTo = true },
            onZoomReset  = if (videoScale > 1.05f) ({ videoScale = 1f; showOverflow = false }) else null,
            onDismiss    = { showOverflow = false }
        )
    }

    // Speed sheet
    if (showSpeed) {
        SpeedPickerSheet(current = speed,
            onSelect  = { s -> speed = s; vp.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false })
    }

    // Resume dialog
    if (showResume) {
        AlertDialog(
            onDismissRequest = { showResume = false; vp.playWhenReady = true },
            containerColor   = Color(0xFF18182A),
            title = { Text("Continue watching?", color = Color.White, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, color = Color.White.copy(0.6f),
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
                TextButton(onClick = {
                    showResume = false; vp.seekTo(0); vp.playWhenReady = true
                    prefs.edit().remove("p${video.id}").apply()
                }) { Text("Restart", color = Color.White.copy(0.65f)) }
            }
        )
    }

    // Subtitle sheet
    if (showSubtitles) {
        VideoTrackSheet("Subtitles", subTracks, C.TRACK_TYPE_TEXT, vp, NebulaCyan, noTracksMsg = "No subtitle tracks found") {
            showSubtitles = false
        }
    }

    // Audio track sheet
    if (showAudio) {
        VideoTrackSheet("Audio Track", audioTracks, C.TRACK_TYPE_AUDIO, vp, NebulaAmber, noTracksMsg = "Only one audio track") {
            showAudio = false
        }
    }

    // Video info sheet
    if (showInfo) {
        VideoInfoSheet(video, vp.videoFormat) { showInfo = false }
    }

    // Jump to time dialog
    if (showJumpTo) {
        JumpToDialog(dur) { ms -> vp.seekTo(ms); showJumpTo = false }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEEK BAR SECTION — custom Canvas, thumbnail preview, time labels
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SeekBarSection(
    pos: Long, dur: Long,
    dragging: Boolean, dragFrac: Float,
    thumb: Bitmap?,
    onDragStart: (Float) -> Unit,
    onDragMove:  (Float) -> Unit,
    onDragEnd:   (Float) -> Unit,
) {
    val displayFrac = if (dragging) dragFrac else (pos.toFloat() / dur).coerceIn(0f, 1f)
    val density = LocalDensity.current

    Column(Modifier.fillMaxWidth()) {
        // Thumbnail preview
        if (dragging && thumb != null) {
            Box(Modifier.fillMaxWidth().height(80.dp)) {
                val thumbFrac = dragFrac
                Box(Modifier.align(Alignment.CenterStart)
                    // offset thumbnail to follow thumb position
                    .padding(start = with(density) {
                        ((thumbFrac * (340.dp.toPx() - 90.dp.toPx()))
                            .coerceIn(0f, (340.dp - 90.dp).toPx())).toDp()
                    })
                ) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(6.dp))) {
                        androidx.compose.foundation.Image(
                            bitmap = thumb.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.size(120.dp, 68.dp)
                        )
                        Box(Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().background(Color.Black.copy(0.6f))
                            .padding(vertical = 2.dp)) {
                            Text(vidFmt((dragFrac * dur).toLong()),
                                color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(80.dp))
        }

        // Time labels
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vidFmt(if (dragging) (dragFrac * dur).toLong() else pos),
                color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium)
            Text("-${vidFmt(dur - pos)}",
                color = Color.White.copy(0.5f), style = MaterialTheme.typography.labelMedium)
            Text(vidFmt(dur),
                color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(4.dp))

        // Custom thin seek bar
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> onDragStart((o.x / size.width).coerceIn(0f, 1f)) },
                        onDrag      = { _, dx ->
                            onDragMove((dragFrac + dx / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd   = { onDragEnd(dragFrac) },
                        onDragCancel = { onDragEnd(dragFrac) }
                    )
                }
                .pointerInput(dur) {
                    detectTapGestures { o ->
                        onDragEnd((o.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y      = size.height / 2
                val trackH = 4.dp.toPx()
                val thumbR = if (dragging) 7.dp.toPx() else 5.dp.toPx()
                val w      = size.width

                // Track background
                drawRoundRect(
                    color        = Color.White.copy(0.25f),
                    topLeft      = Offset(0f, y - trackH / 2),
                    size         = Size(w, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
                // Filled portion
                drawRoundRect(
                    color        = NebulaViolet,
                    topLeft      = Offset(0f, y - trackH / 2),
                    size         = Size(w * displayFrac, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
                // Thumb dot
                val thumbX = (w * displayFrac).coerceIn(thumbR, w - thumbR)
                drawCircle(color = Color.White, radius = thumbR, center = Offset(thumbX, y))
                drawCircle(color = NebulaViolet, radius = thumbR * 0.45f, center = Offset(thumbX, y))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTION ICONS ROW — Playit bottom row with icon + label
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ActionIconsRow(
    subTracks: List<String>, audioTracks: List<String>,
    aspect: AspectMode, orientMode: OrientMode, loopOn: Boolean,
    onSubtitles: () -> Unit, onAudio: () -> Unit, onAspect: () -> Unit,
    onOrient: () -> Unit, onLock: () -> Unit, onLoop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionBtn(
            icon  = Icons.Filled.Subtitles,
            label = "Subtitles",
            tint  = if (subTracks.isNotEmpty()) NebulaCyan else Color.White.copy(0.6f),
            onClick = onSubtitles
        )
        ActionBtn(
            icon  = Icons.Filled.RecordVoiceOver,
            label = "Audio",
            tint  = if (audioTracks.size > 1) NebulaAmber else Color.White.copy(0.6f),
            onClick = onAudio
        )
        ActionBtn(
            icon  = Icons.Filled.AspectRatio,
            label = aspect.label,
            tint  = Color.White.copy(0.85f),
            onClick = onAspect
        )
        ActionBtn(
            icon  = when (orientMode) {
                OrientMode.LANDSCAPE -> Icons.Filled.ScreenRotation
                OrientMode.PORTRAIT  -> Icons.Filled.StayCurrentPortrait
                OrientMode.AUTO      -> Icons.Filled.ScreenRotationAlt
            },
            label = when (orientMode) {
                OrientMode.LANDSCAPE -> "Landscape"
                OrientMode.PORTRAIT  -> "Portrait"
                OrientMode.AUTO      -> "Auto"
            },
            tint  = Color.White.copy(0.85f),
            onClick = onOrient
        )
        ActionBtn(
            icon  = Icons.Filled.Repeat,
            label = "Loop",
            tint  = if (loopOn) NebulaViolet else Color.White.copy(0.6f),
            onClick = onLoop
        )
        ActionBtn(
            icon  = Icons.Filled.Lock,
            label = "Lock",
            tint  = Color.White.copy(0.85f),
            onClick = onLock
        )
    }
}

@Composable
private fun ActionBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, tint: Color, onClick: () -> Unit
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint.copy(alpha = (tint.alpha * 0.9f).coerceIn(0.5f, 0.9f)),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OVERFLOW MENU — bottom sheet with all secondary features
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OverflowMenu(
    speed: Float, nightMode: Boolean, videoScale: Float,
    onSpeed: () -> Unit, onPip: () -> Unit, onScreenshot: () -> Unit,
    onNightMode: () -> Unit, onVideoInfo: () -> Unit, onJumpTo: () -> Unit,
    onZoomReset: (() -> Unit)?, onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF18182A))
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.25f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))

            val items = buildList {
                add(Triple(Icons.Filled.Speed,           "${speed}× Speed",     onSpeed))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    add(Triple(Icons.Filled.PictureInPicture, "Picture in Picture", onPip))
                add(Triple(Icons.Filled.Screenshot,      "Screenshot",          onScreenshot))
                add(Triple(Icons.Filled.Nightlight,
                    if (nightMode) "Night mode: ON" else "Night mode: OFF",     onNightMode))
                add(Triple(Icons.Filled.Info,            "Video info",          onVideoInfo))
                add(Triple(Icons.Filled.Timer,           "Jump to time",        onJumpTo))
                if (onZoomReset != null)
                    add(Triple(Icons.Filled.FitScreen,   "Reset zoom",          onZoomReset))
            }
            items.forEach { (icon, label, action) ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = action)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(icon, null, tint = Color.White.copy(0.85f),
                        modifier = Modifier.size(20.dp))
                    Text(label, color = Color.White.copy(0.9f),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEEK RIPPLE — animated circle + label on double-tap
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SeekRippleOverlay(isLeft: Boolean, label: String) {
    val inf = rememberInfiniteTransition(label = "r")
    val scale by inf.animateFloat(0.5f, 1.3f,
        infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "s")
    val alpha by inf.animateFloat(0.7f, 0f,
        infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "a")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.5f)
                .let { if (isLeft) it.align(Alignment.CenterStart) else it.align(Alignment.CenterEnd) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(110.dp)) {
                val r = size.minDimension / 2
                drawCircle(Color.White.copy(alpha * 0.3f), r * scale)
                drawCircle(Color.White.copy(alpha * 0.15f), r * scale * 1.35f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    if (isLeft) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                    null, tint = Color.White.copy(0.95f), modifier = Modifier.size(34.dp)
                )
                Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SLIM HUD — brightness / volume side bar (Playit style)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SlimHud(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(0.6f))
            .border(0.5.dp, Color.White.copy(0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .width(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(16.dp))
        // Vertical track
        Box(Modifier.width(4.dp).height(80.dp).clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(0.2f))) {
            Box(Modifier.fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(2.dp))
                .background(NebulaViolet))
        }
        Text("${(value * 100).toInt()}",
            color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UP NEXT CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UpNextCard(title: String, timeLeft: Long, onSkip: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(0.80f))
            .border(0.5.dp, Color.White.copy(0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 260.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Up Next", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.5f))
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Text("in ${vidFmt(timeLeft)}", style = MaterialTheme.typography.labelSmall,
                color = NebulaViolet)
        }
        IconButton(onClick = onSkip, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.SkipNext, null, tint = NebulaViolet, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Filled.Close, null, tint = Color.White.copy(0.45f),
                modifier = Modifier.size(13.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRACK SELECTOR SHEET (subtitles + audio)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VideoTrackSheet(
    title: String, tracks: List<String>, type: Int,
    player: ExoPlayer, tint: Color, noTracksMsg: String,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF18182A))
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.25f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))

            if (tracks.isEmpty()) {
                Text(noTracksMsg, color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            } else {
                tracks.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            try {
                                val all = player.currentTracks
                                all.groups.filter { it.type == type }.getOrNull(i)?.let { g ->
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                            .build()
                                }
                            } catch (_: Exception) {}
                            onDismiss()
                        }.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            if (type == C.TRACK_TYPE_TEXT) Icons.Filled.Subtitles
                            else Icons.Filled.RecordVoiceOver,
                            null, tint = tint, modifier = Modifier.size(20.dp)
                        )
                        Text(label, color = Color.White.copy(0.9f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = Color.White.copy(0.07f), thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            // Disable option
            Row(
                Modifier.fillMaxWidth().clickable {
                    try {
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .setDisabledTrackTypes(setOf(type)).build()
                    } catch (_: Exception) {}
                    onDismiss()
                }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.Block, null, tint = NebulaRed, modifier = Modifier.size(20.dp))
                Text("Disable $title", color = Color.White.copy(0.7f),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO INFO SHEET
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VideoInfoSheet(
    video: Song,
    fmt: androidx.media3.common.Format?,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF18182A))
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 16.dp)
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(0.25f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Text("Video Info", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))

            val rows = buildList {
                add("Title"    to video.title)
                add("Duration" to video.durationFormatted)
                add("Size"     to video.sizeFormatted)
                add("Format"   to video.filePath.substringAfterLast(".").uppercase())
                fmt?.let { f ->
                    if (f.width > 0 && f.height > 0) add("Resolution" to "${f.width}×${f.height}")
                    f.codecs?.let { add("Codec" to it.substringBefore(".")) }
                    if (f.bitrate > 0) add("Bitrate" to "${f.bitrate / 1000} kbps")
                    if (f.frameRate > 0) add("Frame rate" to "${f.frameRate.toInt()} fps")
                }
            }
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(label, color = Color.White.copy(0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(96.dp))
                    Text(value, color = Color.White.copy(0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = Color.White.copy(0.07f), thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JUMP TO TIME DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun JumpToDialog(duration: Long, onSeek: (Long) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        containerColor   = Color(0xFF18182A),
        title = { Text("Jump to time", color = Color.White, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter time (mm:ss or h:mm:ss)",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.55f))
                OutlinedTextField(
                    value = input, onValueChange = { input = it; error = false },
                    singleLine    = true,
                    isError       = error,
                    placeholder   = { Text("1:23", color = Color.White.copy(0.3f)) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor   = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = Color.White.copy(0.3f),
                        errorBorderColor   = NebulaRed,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                if (error) Text("Invalid time", color = NebulaRed,
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val ms = parseTime(input)
                if (ms != null && ms in 0L..duration) onSeek(ms) else error = true
            }, colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                Text("Go", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {}
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SMALL REUSABLE COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VidBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSz: Dp, size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(size)) {
        Icon(icon, null,
            tint = if (enabled) Color.White else Color.White.copy(0.25f),
            modifier = Modifier.size(iconSz))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UTILITIES
// ─────────────────────────────────────────────────────────────────────────────
fun vidFmt(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val s = ms / 1000L
    val h = s / 3600L; val m = (s % 3600L) / 60L; val sec = s % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun parseTime(input: String): Long? = try {
    val p = input.trim().split(":")
    when (p.size) {
        2 -> (p[0].toLong() * 60 + p[1].toLong()) * 1000L
        3 -> (p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong()) * 1000L
        else -> null
    }
} catch (_: Exception) { null }

private suspend fun takeVideoScreenshot(pv: PlayerView?): String {
    pv ?: return "No player"
    if (pv.width == 0) return "Player not ready"
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val bmp = Bitmap.createBitmap(pv.width, pv.height, Bitmap.Config.ARGB_8888)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.PixelCopy.request(
                    (pv.context as? Activity)?.window ?: run {
                        cont.resumeWith(Result.success("No window")); return@suspendCancellableCoroutine
                    },
                    android.graphics.Rect(pv.left, pv.top, pv.right, pv.bottom), bmp,
                    { result ->
                        if (result == android.view.PixelCopy.SUCCESS) {
                            try {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                val f = File(File(dir, "Deck Screenshots").also { it.mkdirs() },
                                    "deck_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
                                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                android.media.MediaScannerConnection.scanFile(
                                    pv.context, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                                bmp.recycle()
                                cont.resumeWith(Result.success("Screenshot saved to Gallery ✓"))
                            } catch (_: Exception) { cont.resumeWith(Result.success("Save failed")) }
                        } else { bmp.recycle(); cont.resumeWith(Result.success("Capture failed")) }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                android.graphics.Canvas(bmp).also { pv.draw(it) }
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val f = File(File(dir, "Deck Screenshots").also { it.mkdirs() },
                    "deck_${System.currentTimeMillis()}.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                cont.resumeWith(Result.success("Screenshot saved ✓"))
            }
        } catch (e: Exception) { cont.resumeWith(Result.success("Failed: ${e.message}")) }
    }
}
