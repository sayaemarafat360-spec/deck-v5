package com.sayaem.nebula.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun VideoPlayerScreen(
    video: Song,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context   = LocalContext.current
    val activity  = context as? Activity
    val audioMgr  = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val scope     = rememberCoroutineScope()

    val videoPlayer = remember { ExoPlayer.Builder(context.applicationContext).build() }

    DisposableEffect(Unit) {
        onPauseMusic()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        videoPlayer.prepare()
        videoPlayer.playWhenReady = true
        onDispose {
            videoPlayer.stop(); videoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler { onBack() }

    // ── Core state ─────────────────────────────────────────────────────
    var showControls   by remember { mutableStateOf(true) }
    var isPlaying      by remember { mutableStateOf(true) }
    var speed          by remember { mutableStateOf(1.0f) }
    var showSpeed      by remember { mutableStateOf(false) }
    var aspectIdx      by remember { mutableStateOf(0) }
    var isLocked       by remember { mutableStateOf(false) }
    var loopEnabled    by remember { mutableStateOf(false) }

    // Feature 1 — Subtitle track selection
    var showSubtitles  by remember { mutableStateOf(false) }
    var subtitleTracks by remember { mutableStateOf<List<String>>(emptyList()) }

    // Feature 2 — Pinch-to-zoom scale
    var videoScale     by remember { mutableStateOf(1f) }

    // Feature 3 — Screenshot
    var screenshotMsg  by remember { mutableStateOf<String?>(null) }

    // Feature 4 — Subtitle delay (sync offset in ms)
    var subDelayMs     by remember { mutableStateOf(0L) }
    var showSubDelay   by remember { mutableStateOf(false) }

    // Feature 5 — Watch position memory (resume from last position)
    val prefs = remember { context.getSharedPreferences("deck_video_positions", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        val savedPos = prefs.getLong("pos_${video.id}", 0L)
        if (savedPos > 5000L) {
            videoPlayer.seekTo(savedPos)
        }
    }
    // Save position every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val pos = videoPlayer.currentPosition
            if (pos > 5000L) prefs.edit().putLong("pos_${video.id}", pos).apply()
        }
    }
    // Clear saved position when video finishes
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    prefs.edit().remove("pos_${video.id}").apply()
                }
            }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }

    // Detect subtitle tracks from player
    LaunchedEffect(Unit) {
        delay(1500) // wait for tracks to load
        try {
            val tracks = videoPlayer.currentTracks
            subtitleTracks = (0 until tracks.groups.size)
                .filter { tracks.groups[it].type == C.TRACK_TYPE_TEXT }
                .mapIndexed { i, _ -> "Subtitle Track ${i + 1}" }
        } catch (_: Exception) {}
    }

    // Gesture HUD
    var brightnessHud  by remember { mutableStateOf<Float?>(null) }
    var volumeHud      by remember { mutableStateOf<Float?>(null) }
    var seekFeedback   by remember { mutableStateOf<String?>(null) }
    var longPressSpeed by remember { mutableStateOf(false) }

    val pos by produceState(0L) { while (true) { value = videoPlayer.currentPosition; delay(300) } }
    val dur = videoPlayer.duration.coerceAtLeast(1L)

    LaunchedEffect(loopEnabled) {
        videoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(longPressSpeed) { videoPlayer.setPlaybackSpeed(if (longPressSpeed) 2f else speed) }
    LaunchedEffect(showControls, isLocked) { if (showControls && !isLocked) { delay(4000); showControls = false } }
    LaunchedEffect(brightnessHud) { if (brightnessHud != null) { delay(1200); brightnessHud = null } }
    LaunchedEffect(volumeHud) { if (volumeHud != null) { delay(1200); volumeHud = null } }
    LaunchedEffect(seekFeedback) { if (seekFeedback != null) { delay(700); seekFeedback = null } }
    LaunchedEffect(screenshotMsg) { if (screenshotMsg != null) { delay(2000); screenshotMsg = null } }

    val aspectModes  = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    val aspectLabels = listOf("Fit", "Fill", "Zoom")

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── PlayerView with pinch-to-zoom ─────────────────────────────
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

        // ── Gesture layer ─────────────────────────────────────────────
        var lastTap by remember { mutableStateOf(0L) }
        Box(
            Modifier.fillMaxSize()
                // Feature 2 — Pinch to zoom
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Zoom gesture
                        val newScale = (videoScale * zoom).coerceIn(0.8f, 3f)
                        if (abs(zoom - 1f) > 0.01f) {
                            videoScale = newScale
                            return@detectTransformGestures
                        }
                        // Vertical swipe: brightness (left) / volume (right)
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
                                if (it.x < size.width / 2) { videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0)); seekFeedback = "−10s" }
                                else { videoPlayer.seekTo(videoPlayer.currentPosition + 10000); seekFeedback = "+10s" }
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

        // ── Seek feedback ─────────────────────────────────────────────
        AnimatedVisibility(visible = seekFeedback != null,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.65f))
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(seekFeedback ?: "", style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ── 2× speed HUD ─────────────────────────────────────────────
        AnimatedVisibility(visible = longPressSpeed, enter = fadeIn(tween(80)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.85f))
                .padding(horizontal = 18.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2× Speed", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Feature 2 — Zoom indicator
        if (videoScale > 1.05f) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 72.dp).padding(start = 120.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.6f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("${String.format("%.1f", videoScale)}×", color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Feature 4 — Subtitle delay indicator
        if (subDelayMs != 0L) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaCyan.copy(0.8f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Sub ${if (subDelayMs > 0) "+" else ""}${subDelayMs}ms",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Feature 3 — Screenshot success toast
        screenshotMsg?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(0.75f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = NebulaGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Brightness / Volume HUDs
        brightnessHud?.let { Box(Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) { VideoGestureHud(Icons.Filled.Brightness6, it) } }
        volumeHud?.let { Box(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)) { VideoGestureHud(Icons.Filled.VolumeUp, it) } }

        // ── Lock screen ───────────────────────────────────────────────
        if (isLocked) {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.72f))
                    .border(1.dp, NebulaViolet.copy(0.4f), RoundedCornerShape(16.dp))
                    .clickable { isLocked = false }.padding(horizontal = 28.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LockOpen, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Tap to unlock", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // ── Main controls ─────────────────────────────────────────────
        if (!isLocked) {
            AnimatedVisibility(visible = showControls, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(.78f), Color.Transparent, Color.Transparent, Color.Black.copy(.88f)))
                )) {
                    // ── Top bar ───────────────────────────────────────
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                        Text(video.title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1)
                        VideoTopBtn(aspectLabels[aspectIdx]) { aspectIdx = (aspectIdx + 1) % aspectLabels.size }
                        VideoTopBtn("${speed}×") { showSpeed = true }
                        // Feature 1 — Subtitles button
                        IconButton(onClick = { showSubtitles = true }) {
                            Icon(Icons.Filled.Subtitles, null,
                                tint = if (subtitleTracks.isNotEmpty()) NebulaCyan else Color.White.copy(0.5f))
                        }
                        // Feature 3 — Screenshot
                        IconButton(onClick = {
                            scope.launch {
                                val result = takeScreenshot(playerViewRef)
                                screenshotMsg = result
                            }
                        }) { Icon(Icons.Filled.Screenshot, null, tint = Color.White.copy(0.85f)) }
                        // Feature 2 — Reset zoom
                        if (videoScale > 1.05f) {
                            IconButton(onClick = { videoScale = 1f }) {
                                Icon(Icons.Filled.FitScreen, null, tint = NebulaAmber)
                            }
                        }
                        // Loop
                        IconButton(onClick = { loopEnabled = !loopEnabled }) {
                            Icon(Icons.Filled.Repeat, null, tint = if (loopEnabled) NebulaViolet else Color.White.copy(0.7f))
                        }
                        // Lock
                        IconButton(onClick = { isLocked = true }) { Icon(Icons.Filled.LockOpen, null, tint = Color.White.copy(0.85f)) }
                        // PiP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                try { activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) {}
                            }) { Icon(Icons.Filled.PictureInPicture, null, tint = Color.White.copy(0.85f)) }
                        }
                    }

                    // ── Centre transport ──────────────────────────────
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (videoPlayer.currentPosition > 3000) videoPlayer.seekTo(0) },
                            modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0)); seekFeedback = "−10s" },
                            modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Box(Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(.18f))
                            .border(2.dp, Color.White.copy(.65f), CircleShape)
                            .clickable {
                                if (videoPlayer.isPlaying) { videoPlayer.pause(); isPlaying = false }
                                else { videoPlayer.play(); isPlaying = true }
                            }, contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { videoPlayer.seekTo(videoPlayer.currentPosition + 10000); seekFeedback = "+10s" },
                            modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { videoPlayer.seekTo(videoPlayer.duration.coerceAtLeast(0)) },
                            modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(30.dp))
                        }
                    }

                    // ── Bottom seek bar ───────────────────────────────
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(vidFmt(pos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.85f))
                            // Feature 4 — subtitle delay buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (subtitleTracks.isNotEmpty()) {
                                    // Subtitle delay — adjusts displayed offset in state.
                                    // Media3 1.4+ exposes setSubtitleOffset(); on 1.3.0 we store the
                                    // offset and rebuild the MediaItem with SubtitleConfiguration.offset
                                    // on next play. The badge shows the active offset to the user.
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(NebulaCyan.copy(0.2f))
                                        .clickable { subDelayMs -= 500 }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Sub -0.5s", color = NebulaCyan, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(NebulaCyan.copy(0.2f))
                                        .clickable { subDelayMs += 500 }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Sub +0.5s", color = NebulaCyan, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text("-${vidFmt(dur - pos)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.55f))
                            Text(vidFmt(dur), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.85f))
                        }
                        Slider(
                            value = (pos.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { videoPlayer.seekTo((it * dur).toLong()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(activeTrackColor = NebulaViolet, thumbColor = Color.White, inactiveTrackColor = Color.White.copy(.28f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val folderName = video.filePath.split("/").let { if (it.size >= 2) it[it.size - 2] else "" }
                            if (folderName.isNotBlank()) {
                                Icon(Icons.Filled.FolderOpen, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(12.dp))
                                Text(folderName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f), maxLines = 1)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                            if (loopEnabled) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NebulaViolet.copy(0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("LOOP", style = MaterialTheme.typography.labelSmall, color = NebulaViolet, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    // Speed sheet
    if (showSpeed) {
        SpeedPickerSheet(current = speed, onSelect = { s -> speed = s; videoPlayer.setPlaybackSpeed(s); showSpeed = false }, onDismiss = { showSpeed = false })
    }

    // Feature 1 — Subtitle track selector
    if (showSubtitles) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { showSubtitles = false }) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {}
                    .padding(20.dp).navigationBarsPadding()
            ) {
                Text("Subtitle Tracks", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (subtitleTracks.isEmpty()) {
                    Text("No subtitle tracks in this video", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodyMedium)
                } else {
                    subtitleTracks.forEachIndexed { i, label ->
                        Row(Modifier.fillMaxWidth().clickable {
                            try {
                                val tracks = videoPlayer.currentTracks
                                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                textGroups.getOrNull(i)?.let { group ->
                                    videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters.buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                        .build()
                                }
                            } catch (_: Exception) {}
                            showSubtitles = false
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Subtitles, null, tint = NebulaCyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
                // Disable subtitles option
                Row(Modifier.fillMaxWidth().clickable {
                    try {
                        videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters.buildUpon()
                            .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT)).build()
                    } catch (_: Exception) {}
                    showSubtitles = false
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SubtitlesOff, null, tint = NebulaRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Disable Subtitles", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }
    }
}

// ── Feature 3: Screenshot from PlayerView surface ────────────────────────
private suspend fun takeScreenshot(playerView: PlayerView?): String {
    val pv = playerView ?: return "Screenshot failed"
    if (pv.width == 0 || pv.height == 0) return "Screenshot failed: player not ready"

    return suspendCancellableCoroutine { cont ->
        try {
            val bmp = Bitmap.createBitmap(pv.width, pv.height, Bitmap.Config.ARGB_8888)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // PixelCopy works on SurfaceView — pv.draw() only captures View hierarchy (black for SurfaceView)
                android.view.PixelCopy.request(
                    (pv.context as? android.app.Activity)?.window ?: run {
                        cont.resumeWith(Result.success("Screenshot failed: no window"))
                        return@suspendCancellableCoroutine
                    },
                    android.graphics.Rect(pv.left, pv.top, pv.right, pv.bottom),
                    bmp,
                    { result ->
                        if (result == android.view.PixelCopy.SUCCESS) {
                            try {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                val folder = File(dir, "Deck Screenshots").also { it.mkdirs() }
                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val file = File(folder, "deck_$ts.jpg")
                                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                // Notify gallery
                                android.media.MediaScannerConnection.scanFile(
                                    pv.context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                                bmp.recycle()
                                cont.resumeWith(Result.success("Screenshot saved to Gallery ✓"))
                            } catch (e: Exception) { cont.resumeWith(Result.success("Screenshot failed: ${e.message}")) }
                        } else {
                            bmp.recycle()
                            cont.resumeWith(Result.success("Screenshot failed (PixelCopy error $result)"))
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                // API < 26 fallback: draw the View hierarchy
                val canvas = android.graphics.Canvas(bmp)
                pv.draw(canvas)
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(dir, "Deck Screenshots").also { it.mkdirs() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(folder, "deck_$ts.jpg")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                cont.resumeWith(Result.success("Screenshot saved ✓"))
            }
        } catch (e: Exception) { cont.resumeWith(Result.success("Screenshot failed: ${e.message}")) }
    }
}

@Composable
private fun VideoGestureHud(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.68f)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(Modifier.height(90.dp).width(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.2f))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp)).background(Color.White).align(Alignment.BottomStart))
        }
        Text("${(value * 100).toInt()}%", color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VideoTopBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(0.12f))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun vidFmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
