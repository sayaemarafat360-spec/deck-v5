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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ── Orientation modes ─────────────────────────────────────────────────────
private enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

@Composable
fun VideoPlayerScreen(
    videos: List<Song>,          // full queue
    startIndex: Int = 0,         // which video to start with
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val audioMgr = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val scope    = rememberCoroutineScope()
    val prefs    = remember { context.getSharedPreferences("deck_video_positions", android.content.Context.MODE_PRIVATE) }

    // ── Queue state ────────────────────────────────────────────────────
    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))) }
    val video = videos.getOrNull(currentIndex) ?: videos.firstOrNull() ?: return

    val videoPlayer = remember { ExoPlayer.Builder(context.applicationContext).build() }

    // ── Orientation ────────────────────────────────────────────────────
    var orientMode by remember { mutableStateOf(OrientationMode.LANDSCAPE) }
    LaunchedEffect(orientMode) {
        activity?.requestedOrientation = when (orientMode) {
            OrientationMode.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // ── Resume dialog ─────────────────────────────────────────────────
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumePosition   by remember { mutableLongStateOf(0L) }

    fun loadVideo(song: Song, forceRestart: Boolean = false) {
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        videoPlayer.setMediaItem(MediaItem.fromUri(song.uri))
        videoPlayer.prepare()
        val saved = prefs.getLong("pos_${song.id}", 0L)
        if (!forceRestart && saved > 10_000L && saved < (song.duration - 10_000L)) {
            resumePosition = saved
            showResumeDialog = true
            videoPlayer.seekTo(saved)
            videoPlayer.playWhenReady = false
        } else {
            prefs.edit().remove("pos_${song.id}").apply()
            videoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onPauseMusic()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadVideo(video)
        onDispose {
            // Save position before releasing
            val pos = videoPlayer.currentPosition
            if (pos > 10_000L && pos < videoPlayer.duration - 5_000L) {
                prefs.edit().putLong("pos_${video.id}", pos).apply()
            }
            videoPlayer.stop(); videoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Reload player when currentIndex changes
    LaunchedEffect(currentIndex) {
        if (currentIndex != startIndex) loadVideo(video)
    }

    BackHandler { onBack() }

    // ── Core playback state ────────────────────────────────────────────
    var isPlaying      by remember { mutableStateOf(true) }
    var showControls   by remember { mutableStateOf(true) }
    var isLocked       by remember { mutableStateOf(false) }
    var loopEnabled    by remember { mutableStateOf(false) }
    var speed          by remember { mutableStateOf(1.0f) }
    var showSpeed      by remember { mutableStateOf(false) }
    var aspectIdx      by remember { mutableIntStateOf(0) }
    val aspectModes    = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    val aspectLabels   = listOf("Fit", "Fill", "Zoom")

    // ── Advanced features state ────────────────────────────────────────
    var videoScale       by remember { mutableStateOf(1f) }
    var nightMode        by remember { mutableStateOf(false) }
    var subtitleTracks   by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioTracks      by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSubtitles    by remember { mutableStateOf(false) }
    var showAudioTracks  by remember { mutableStateOf(false) }
    var subDelayMs       by remember { mutableLongStateOf(0L) }
    var showVideoInfo    by remember { mutableStateOf(false) }
    var showJumpTo       by remember { mutableStateOf(false) }

    // ── Up Next card ─────────────────────────────────────────────────
    var showUpNext       by remember { mutableStateOf(false) }
    var upNextDismissed  by remember { mutableStateOf(false) }

    // ── Gesture HUD ───────────────────────────────────────────────────
    var brightnessHud    by remember { mutableStateOf<Float?>(null) }
    var volumeHud        by remember { mutableStateOf<Float?>(null) }
    var seekFeedback     by remember { mutableStateOf<String?>(null) }
    var seekFeedbackLeft by remember { mutableStateOf(false) }
    var longPressSpeed   by remember { mutableStateOf(false) }

    // ── Seek thumbnail ────────────────────────────────────────────────
    var isDragging          by remember { mutableStateOf(false) }
    var dragProgress        by remember { mutableStateOf(0f) }
    var seekThumbBitmap     by remember { mutableStateOf<Bitmap?>(null) }

    // ── Position tracking ─────────────────────────────────────────────
    val pos by produceState(0L) { while (true) { value = videoPlayer.currentPosition; delay(300) } }
    val dur = videoPlayer.duration.coerceAtLeast(1L)

    // Auto-hide controls
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) { delay(4000); showControls = false }
    }
    LaunchedEffect(brightnessHud) { if (brightnessHud != null) { delay(1300); brightnessHud = null } }
    LaunchedEffect(volumeHud)     { if (volumeHud != null) { delay(1300); volumeHud = null } }
    LaunchedEffect(seekFeedback)  { if (seekFeedback != null) { delay(700); seekFeedback = null } }
    LaunchedEffect(longPressSpeed){ videoPlayer.setPlaybackSpeed(if (longPressSpeed) 2f else speed) }
    LaunchedEffect(loopEnabled)   { videoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    // Track detection
    LaunchedEffect(currentIndex) {
        delay(1200)
        try {
            val tracks = videoPlayer.currentTracks
            subtitleTracks = tracks.groups.indices
                .filter { tracks.groups[it].type == C.TRACK_TYPE_TEXT }
                .mapIndexed { i, _ -> "Subtitle ${i + 1}" }
            audioTracks = tracks.groups.indices
                .filter { tracks.groups[it].type == C.TRACK_TYPE_AUDIO }
                .mapIndexed { i, origIdx ->
                    // origIdx is the original index into tracks.groups
                    val fmt  = tracks.groups[origIdx].getTrackFormat(0)
                    val lang = fmt.language?.uppercase() ?: "Track ${i + 1}"
                    val ch   = if (fmt.channelCount == 1) "Mono"
                               else if (fmt.channelCount == 2) "Stereo"
                               else "${fmt.channelCount}ch"
                    "$lang · $ch"
                }
        } catch (_: Exception) {}
    }

    // Position save + Up Next trigger
    LaunchedEffect(pos) {
        if (pos > 10_000L) prefs.edit().putLong("pos_${video.id}", pos).apply()
        // Show "Up Next" card 30 seconds before end
        val remaining = dur - pos
        if (remaining in 5_000L..30_000L && !upNextDismissed &&
            currentIndex < videos.size - 1 && !loopEnabled) {
            showUpNext = true
        } else if (remaining > 30_000L) {
            upNextDismissed = false
            showUpNext = false
        }
    }

    // Auto-next on video end
    DisposableEffect(currentIndex) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (loopEnabled) return
                    prefs.edit().remove("pos_${video.id}").apply()
                    if (currentIndex < videos.size - 1) {
                        currentIndex++
                        showUpNext = false
                        upNextDismissed = false
                    } else {
                        // Last video — go back
                        onBack()
                    }
                }
                isPlaying = videoPlayer.isPlaying
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }

    // Seek thumbnail generator
    LaunchedEffect(isDragging, dragProgress) {
        if (!isDragging) return@LaunchedEffect
        val targetMs = (dragProgress * dur).toLong()
        scope.launch(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(video.filePath)
                val bmp = mmr.getFrameAtTime(
                    targetMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                mmr.release()
                withContext(Dispatchers.Main) {
                    seekThumbBitmap = bmp?.let {
                        Bitmap.createScaledBitmap(it, 160, 90, true)
                            .also { s -> if (s !== it) it.recycle() }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // ── Root Box ──────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = videoPlayer; useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectModes[aspectIdx]
                    playerViewRef = this
                }
            },
            update = { pv ->
                pv.resizeMode = aspectModes[aspectIdx]
                pv.scaleX = videoScale; pv.scaleY = videoScale
            },
            modifier = Modifier.fillMaxSize()
        )

        // Night mode overlay
        if (nightMode) {
            Box(Modifier.fillMaxSize().background(Color(0x55000000)))
            Box(Modifier.fillMaxSize().background(Color(0x15FF8C00)))
        }

        // ── Gesture layer ─────────────────────────────────────────────
        var lastTap by remember { mutableLongStateOf(0L) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (abs(zoom - 1f) > 0.01f) {
                            videoScale = (videoScale * zoom).coerceIn(0.8f, 3f)
                            return@detectTransformGestures
                        }
                        val dy = -pan.y / size.height
                        if (abs(dy) > 0.004f) {
                            if (centroid.x < size.width / 2) {
                                val lp = activity?.window?.attributes ?: return@detectTransformGestures
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur + dy).coerceIn(0.01f, 1f)
                                activity.window.attributes = lp
                                brightnessHud = lp.screenBrightness
                            } else {
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (cur + (dy * max).toInt()).coerceIn(0, max)
                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                volumeHud = newVol.toFloat() / max
                            }
                        }
                    }
                }
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) { showControls = !showControls; return@detectTapGestures }
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 350) {
                                val left = it.x < size.width / 2
                                val seekMs = if (left) -10_000L else 10_000L
                                videoPlayer.seekTo((videoPlayer.currentPosition + seekMs).coerceAtLeast(0))
                                seekFeedback = if (left) "−10s" else "+10s"
                                seekFeedbackLeft = left
                            } else { showControls = !showControls }
                            lastTap = now
                        },
                        onLongPress = { if (!isLocked) longPressSpeed = true }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.all { !it.pressed } && longPressSpeed) longPressSpeed = false
                        }
                    }
                }
        )

        // ── Seek ripple animation ─────────────────────────────────────
        if (seekFeedback != null) {
            SeekRipple(
                isLeft = seekFeedbackLeft,
                label  = seekFeedback ?: "",
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Long-press 2× HUD ─────────────────────────────────────────
        AnimatedVisibility(visible = longPressSpeed, enter = fadeIn(tween(80)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.85f))
                .padding(horizontal = 18.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2× Speed", color = Color.White, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Zoom indicator ────────────────────────────────────────────
        if (videoScale > 1.05f) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.65f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("${String.format("%.1f", videoScale)}×", color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Brightness / Volume HUDs ──────────────────────────────────
        brightnessHud?.let { Box(Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) { VideoGestureHud(Icons.Filled.Brightness6, it) } }
        volumeHud?.let    { Box(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))     { VideoGestureHud(Icons.Filled.VolumeUp, it) } }

        // ── Up Next card ──────────────────────────────────────────────
        val nextVideo = videos.getOrNull(currentIndex + 1)
        if (showUpNext && nextVideo != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 120.dp)) {
                UpNextCard(
                    title    = nextVideo.title,
                    timeLeft = dur - pos,
                    onSkip   = { currentIndex++; showUpNext = false },
                    onDismiss = { showUpNext = false; upNextDismissed = true }
                )
            }
        }

        // ── Lock screen ───────────────────────────────────────────────
        if (isLocked) {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.72f))
                    .border(1.dp, NebulaViolet.copy(0.4f), RoundedCornerShape(16.dp))
                    .clickable { isLocked = false }
                    .padding(horizontal = 28.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LockOpen, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Tap to unlock", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // ── Main controls (unlocked) ─────────────────────────────────
        if (!isLocked) {
            AnimatedVisibility(visible = showControls, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(.82f), Color.Transparent, Color.Transparent, Color.Black.copy(.92f)))
                )) {

                    // ── Top bar ───────────────────────────────────────
                    Row(Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {

                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium,
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                maxLines = 1)
                            // Queue position
                            if (videos.size > 1) {
                                Text("${currentIndex + 1} / ${videos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.55f))
                            }
                        }

                        // Resolution badge
                        val videoFmt = videoPlayer.videoFormat
                        val resBadge = when {
                            videoFmt == null -> ""
                            videoFmt.height >= 2160 -> "4K"
                            videoFmt.height >= 1080 -> "1080p"
                            videoFmt.height >= 720  -> "720p"
                            videoFmt.height >= 480  -> "480p"
                            else -> "${videoFmt.height}p"
                        }
                        if (resBadge.isNotEmpty()) {
                            Box(Modifier.clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Text(resBadge, style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.85f), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        VideoTopBtn(aspectLabels[aspectIdx]) { aspectIdx = (aspectIdx + 1) % aspectLabels.size }
                        VideoTopBtn("${speed}×") { showSpeed = true }

                        // Subtitle button
                        IconButton(onClick = { showSubtitles = true }) {
                            Icon(Icons.Filled.Subtitles, null,
                                tint = if (subtitleTracks.isNotEmpty()) NebulaCyan else Color.White.copy(0.5f),
                                modifier = Modifier.size(22.dp))
                        }
                        // Audio track button
                        if (audioTracks.size > 1) {
                            IconButton(onClick = { showAudioTracks = true }) {
                                Icon(Icons.Filled.RecordVoiceOver, null,
                                    tint = NebulaAmber, modifier = Modifier.size(22.dp))
                            }
                        }
                        // Night mode
                        IconButton(onClick = { nightMode = !nightMode }) {
                            Icon(Icons.Filled.Nightlight, null,
                                tint = if (nightMode) NebulaAmber else Color.White.copy(0.7f),
                                modifier = Modifier.size(20.dp))
                        }
                        // Orientation
                        IconButton(onClick = {
                            orientMode = when (orientMode) {
                                OrientationMode.LANDSCAPE -> OrientationMode.AUTO
                                OrientationMode.AUTO      -> OrientationMode.PORTRAIT
                                OrientationMode.PORTRAIT  -> OrientationMode.LANDSCAPE
                            }
                        }) {
                            val icon = when (orientMode) {
                                OrientationMode.LANDSCAPE -> Icons.Filled.ScreenRotation
                                OrientationMode.PORTRAIT  -> Icons.Filled.StayCurrentPortrait
                                OrientationMode.AUTO      -> Icons.Filled.ScreenRotationAlt
                            }
                            Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp))
                        }
                        // Screenshot
                        IconButton(onClick = {
                            scope.launch {
                                val result = takeVideoScreenshot(playerViewRef)
                                DeckToastEngine.success(result)
                            }
                        }) { Icon(Icons.Filled.Screenshot, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp)) }
                        // Zoom reset
                        if (videoScale > 1.05f) {
                            IconButton(onClick = { videoScale = 1f }) {
                                Icon(Icons.Filled.FitScreen, null, tint = NebulaAmber, modifier = Modifier.size(20.dp))
                            }
                        }
                        // Loop
                        IconButton(onClick = { loopEnabled = !loopEnabled }) {
                            Icon(Icons.Filled.Repeat, null,
                                tint = if (loopEnabled) NebulaViolet else Color.White.copy(0.7f),
                                modifier = Modifier.size(20.dp))
                        }
                        // Lock
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Filled.LockOpen, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp))
                        }
                        // PiP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                try { activity?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                                } catch (_: Exception) {}
                            }) { Icon(Icons.Filled.PictureInPicture, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp)) }
                        }
                    }

                    // ── Centre transport ──────────────────────────────
                    Row(Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // Prev video
                        if (currentIndex > 0) {
                            IconButton(onClick = { currentIndex-- }, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(28.dp))
                            }
                        } else { Spacer(Modifier.size(42.dp)) }

                        IconButton(onClick = { videoPlayer.seekTo((videoPlayer.currentPosition - 10_000).coerceAtLeast(0)); seekFeedback = "−10s"; seekFeedbackLeft = true },
                            modifier = Modifier.size(50.dp)) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Box(Modifier.size(68.dp).clip(CircleShape).background(Color.White.copy(.18f))
                            .border(2.dp, Color.White.copy(.65f), CircleShape)
                            .clickable {
                                if (videoPlayer.isPlaying) { videoPlayer.pause(); isPlaying = false }
                                else { videoPlayer.play(); isPlaying = true }
                            }, contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { videoPlayer.seekTo(videoPlayer.currentPosition + 10_000); seekFeedback = "+10s"; seekFeedbackLeft = false },
                            modifier = Modifier.size(50.dp)) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        // Next video
                        if (currentIndex < videos.size - 1) {
                            IconButton(onClick = { currentIndex++ }, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(28.dp))
                            }
                        } else { Spacer(Modifier.size(42.dp)) }
                    }

                    // ── Bottom bar ────────────────────────────────────
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {

                        // Time row + subtitle delay
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            // Jump-to button
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.1f))
                                .clickable { showJumpTo = true }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(vidFmt(pos), style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(.85f))
                            }
                            // Subtitle delay
                            if (subtitleTracks.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(NebulaCyan.copy(0.18f))
                                        .clickable { subDelayMs -= 500 }.padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("Sub -0.5s", color = NebulaCyan, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (subDelayMs != 0L) {
                                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(NebulaCyan.copy(0.25f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                                            Text("${if (subDelayMs > 0) "+" else ""}${subDelayMs}ms",
                                                color = NebulaCyan, style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(NebulaCyan.copy(0.18f))
                                        .clickable { subDelayMs += 500 }.padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("Sub +0.5s", color = NebulaCyan, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(vidFmt(dur), style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(.85f))
                        }

                        // Seek slider with thumbnail preview
                        Box(Modifier.fillMaxWidth()) {
                            Slider(
                                value = if (isDragging) dragProgress else (pos.toFloat() / dur).coerceIn(0f, 1f),
                                onValueChange = { v ->
                                    isDragging = true; dragProgress = v
                                },
                                onValueChangeFinished = {
                                    videoPlayer.seekTo((dragProgress * dur).toLong())
                                    isDragging = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor   = NebulaViolet,
                                    thumbColor         = Color.White,
                                    inactiveTrackColor = Color.White.copy(.28f)
                                )
                            )
                            // Seek thumbnail above thumb
                            if (isDragging && seekThumbBitmap != null) {
                                val thumbX = dragProgress
                                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                                    Box(
                                        Modifier.padding(
                                            start = (thumbX * 300).dp.coerceIn(0.dp, 260.dp)
                                        ).offset(y = (-70).dp)
                                    ) {
                                        androidx.compose.foundation.Image(
                                            bitmap = seekThumbBitmap!!.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(120.dp, 68.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(6.dp))
                                        )
                                        // Time label on thumbnail
                                        Box(Modifier.align(Alignment.BottomCenter)
                                            .background(Color.Black.copy(0.7f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)) {
                                            Text(vidFmt((dragProgress * dur).toLong()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom meta row
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val folder = video.filePath.split("/").let { if (it.size >= 2) it[it.size - 2] else "" }
                            if (folder.isNotBlank()) {
                                Icon(Icons.Filled.FolderOpen, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(12.dp))
                                Text(folder, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f), maxLines = 1)
                            }
                            Spacer(Modifier.weight(1f))
                            if (loopEnabled) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NebulaViolet.copy(0.3f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)) {
                                    Text("LOOP", style = MaterialTheme.typography.labelSmall,
                                        color = NebulaViolet, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    // ── Speed sheet ───────────────────────────────────────────────────────
    if (showSpeed) {
        SpeedPickerSheet(current = speed,
            onSelect = { s -> speed = s; videoPlayer.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false })
    }

    // ── Resume dialog ─────────────────────────────────────────────────────
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false; videoPlayer.playWhenReady = true },
            containerColor = Color(0xFF1A1A2E),
            title = {
                Text("Continue watching?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(video.title, color = Color.White.copy(0.7f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Text("Paused at ${vidFmt(resumePosition)}",
                        color = NebulaViolet, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showResumeDialog = false
                    videoPlayer.seekTo(resumePosition)
                    videoPlayer.playWhenReady = true
                }, colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                    Text("Resume", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    videoPlayer.seekTo(0)
                    videoPlayer.playWhenReady = true
                    prefs.edit().remove("pos_${video.id}").apply()
                }) { Text("Restart", color = Color.White.copy(0.7f)) }
            }
        )
    }

    // ── Subtitle track sheet ──────────────────────────────────────────────
    if (showSubtitles) {
        TrackSelectorSheet(
            title  = "Subtitles",
            tracks = subtitleTracks,
            type   = C.TRACK_TYPE_TEXT,
            player = videoPlayer,
            tint   = NebulaCyan,
            onDismiss = { showSubtitles = false }
        )
    }

    // ── Audio track sheet ─────────────────────────────────────────────────
    if (showAudioTracks) {
        TrackSelectorSheet(
            title  = "Audio Track",
            tracks = audioTracks,
            type   = C.TRACK_TYPE_AUDIO,
            player = videoPlayer,
            tint   = NebulaAmber,
            onDismiss = { showAudioTracks = false }
        )
    }

    // ── Video info sheet ──────────────────────────────────────────────────
    if (showVideoInfo) {
        val fmt = videoPlayer.videoFormat
        VideoInfoSheet(
            video = video, fmt = fmt, onDismiss = { showVideoInfo = false }
        )
    }

    // ── Jump to time dialog ───────────────────────────────────────────────
    if (showJumpTo) {
        JumpToDialog(
            current = pos, duration = dur,
            onSeek = { ms -> videoPlayer.seekTo(ms); showJumpTo = false },
            onDismiss = { showJumpTo = false }
        )
    }
}

// ── Seek ripple animation ─────────────────────────────────────────────────
@Composable
private fun SeekRipple(isLeft: Boolean, label: String, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "ripple")
    val scale by inf.animateFloat(0.6f, 1.2f,
        infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "s")
    val alpha by inf.animateFloat(0.8f, 0f,
        infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "a")

    Box(modifier) {
        val boxAlign = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
        Box(Modifier.align(boxAlign).padding(horizontal = 40.dp)) {
            Canvas(Modifier.size(100.dp)) {
                val r = size.minDimension / 2 * scale
                drawCircle(Color.White.copy(alpha = alpha * 0.3f), radius = r)
                drawCircle(Color.White.copy(alpha = alpha * 0.15f), radius = r * 1.3f)
            }
            Column(Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isLeft) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                    null, tint = Color.White.copy(0.9f), modifier = Modifier.size(32.dp)
                )
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Up Next card ──────────────────────────────────────────────────────────
@Composable
private fun UpNextCard(title: String, timeLeft: Long, onSkip: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(0.82f))
            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Up Next", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.55f))
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text("in ${vidFmt(timeLeft)}", style = MaterialTheme.typography.labelSmall,
                color = NebulaViolet)
        }
        IconButton(onClick = onSkip, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.SkipNext, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
        }
    }
}

// ── Shared track selector sheet (subtitles + audio) ───────────────────────
@Composable
private fun TrackSelectorSheet(
    title: String, tracks: List<String>, type: Int,
    player: ExoPlayer, tint: Color, onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1A1A2E))
                .clickable(enabled = false) {}
                .padding(20.dp).navigationBarsPadding()
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (tracks.isEmpty()) {
                Text("No ${title.lowercase()} tracks found", color = Color.White.copy(0.6f),
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                tracks.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clickable {
                        try {
                            val allTracks = player.currentTracks
                            allTracks.groups.filter { it.type == type }
                                .getOrNull(i)?.let { group ->
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                            .build()
                                }
                        } catch (_: Exception) {}
                        onDismiss()
                    }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (type == C.TRACK_TYPE_TEXT) Icons.Filled.Subtitles else Icons.Filled.RecordVoiceOver,
                            null, tint = tint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                    HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                }
            }
            // Disable option
            Row(Modifier.fillMaxWidth().clickable {
                try {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setDisabledTrackTypes(setOf(type)).build()
                } catch (_: Exception) {}
                onDismiss()
            }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Block, null, tint = NebulaRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Disable ${title}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))
            }
        }
    }
}

// ── Video info sheet ──────────────────────────────────────────────────────
@Composable
private fun VideoInfoSheet(
    video: Song,
    fmt: androidx.media3.common.Format?,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1A1A2E))
                .clickable(enabled = false) {}
                .padding(20.dp).navigationBarsPadding()
        ) {
            Text("Video Info", style = MaterialTheme.typography.titleMedium,
                color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            val rows = buildList {
                add("Title" to video.title)
                add("Duration" to video.durationFormatted)
                add("File Size" to video.sizeFormatted)
                add("Format" to video.filePath.substringAfterLast(".").uppercase())
                if (fmt != null) {
                    if (fmt.width > 0 && fmt.height > 0)
                        add("Resolution" to "${fmt.width}×${fmt.height}")
                    fmt.codecs?.let { add("Codec" to it.substringBefore(".")) }
                    if (fmt.bitrate > 0)
                        add("Bitrate" to "${fmt.bitrate / 1000} kbps")
                    if (fmt.frameRate > 0)
                        add("Frame Rate" to "${fmt.frameRate.toInt()} fps")
                }
                add("Path" to video.filePath.substringAfterLast("/"))
            }
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.45f), modifier = Modifier.width(90.dp))
                    Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White,
                        modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = Color.White.copy(0.06f), thickness = 0.5.dp)
            }
        }
    }
}

// ── Jump to time dialog ───────────────────────────────────────────────────
@Composable
private fun JumpToDialog(current: Long, duration: Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text("Jump to time", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter time (mm:ss or hh:mm:ss)", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.6f))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input, onValueChange = { input = it; error = false },
                    singleLine = true, placeholder = { Text("1:23", color = Color.White.copy(0.35f)) },
                    isError = error,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = Color.White.copy(0.3f),
                        errorBorderColor = NebulaRed
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
                if (error) Text("Invalid time format", color = NebulaRed,
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val ms = parseTimeInput(input)
                if (ms != null && ms in 0L..duration) onSeek(ms)
                else error = true
            }, colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                Text("Go", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.6f)) } }
    )
}

private fun parseTimeInput(input: String): Long? {
    return try {
        val parts = input.trim().split(":")
        when (parts.size) {
            2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000L
            3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000L
            else -> input.toLongOrNull()?.times(1000L)
        }
    } catch (_: Exception) { null }
}

// ── Screenshot ────────────────────────────────────────────────────────────
private suspend fun takeVideoScreenshot(playerView: PlayerView?): String {
    val pv = playerView ?: return "No player view"
    if (pv.width == 0 || pv.height == 0) return "Player not ready"
    return suspendCancellableCoroutine { cont ->
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
                                val folder = File(dir, "Deck Screenshots").also { it.mkdirs() }
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val file = File(folder, "deck_$ts.jpg")
                                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                android.media.MediaScannerConnection.scanFile(pv.context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                                bmp.recycle()
                                cont.resumeWith(Result.success("Screenshot saved to Gallery ✓"))
                            } catch (e: Exception) { cont.resumeWith(Result.success("Screenshot failed")) }
                        } else { bmp.recycle(); cont.resumeWith(Result.success("PixelCopy failed")) }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                val canvas = android.graphics.Canvas(bmp); pv.draw(canvas)
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(dir, "Deck Screenshots").also { it.mkdirs() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(folder, "deck_$ts.jpg")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle(); cont.resumeWith(Result.success("Screenshot saved ✓"))
            }
        } catch (e: Exception) { cont.resumeWith(Result.success("Screenshot failed: ${e.message}")) }
    }
}

// ── Shared UI helpers ─────────────────────────────────────────────────────
@Composable
private fun VideoGestureHud(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.68f)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(Modifier.height(90.dp).width(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.2f))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp)).background(Color.White).align(Alignment.BottomStart))
        }
        Text("${(value * 100).toInt()}%", color = Color.White.copy(0.85f),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VideoTopBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(0.12f))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White,
            fontWeight = FontWeight.SemiBold)
    }
}

fun vidFmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
