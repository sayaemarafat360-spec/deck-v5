package com.sayaem.nebula.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

// ── Fix #6 — Professional video player ───────────────────────────────────
// Features: brightness/volume swipe, lock screen, 2× long-press speed,
// loop toggle, seek feedback, PiP, aspect cycle, +10/-10 double-tap,
// chapter scrubbing hint, fully custom bottom scrubbar.
@Composable
fun VideoPlayerScreen(
    video: Song,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val audioMgr = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    val videoPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build()
    }

    DisposableEffect(Unit) {
        onPauseMusic()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        videoPlayer.prepare()
        videoPlayer.playWhenReady = true
        onDispose {
            videoPlayer.stop()
            videoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler { onBack() }

    // ── State ──────────────────────────────────────────────────────────
    var showControls    by remember { mutableStateOf(true) }
    var isPlaying       by remember { mutableStateOf(true) }
    var speed           by remember { mutableStateOf(1.0f) }
    var showSpeed       by remember { mutableStateOf(false) }
    var aspectIdx       by remember { mutableStateOf(0) }
    var isLocked        by remember { mutableStateOf(false) }
    var loopEnabled     by remember { mutableStateOf(false) }

    // Gesture HUD state
    var brightnessHud   by remember { mutableStateOf<Float?>(null) }
    var volumeHud       by remember { mutableStateOf<Float?>(null) }
    var seekFeedback    by remember { mutableStateOf<String?>(null) }
    var longPressSpeed  by remember { mutableStateOf(false) }

    // Live position tracking
    val pos by produceState(0L) {
        while (true) { value = videoPlayer.currentPosition; delay(300) }
    }
    val dur = remember(videoPlayer.duration) { videoPlayer.duration.coerceAtLeast(1L) }

    // Loop
    LaunchedEffect(loopEnabled) {
        videoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Long-press 2× speed
    LaunchedEffect(longPressSpeed) {
        videoPlayer.setPlaybackSpeed(if (longPressSpeed) 2f else speed)
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) { delay(4000); showControls = false }
    }

    // Auto-hide HUDs
    LaunchedEffect(brightnessHud) { if (brightnessHud != null) { delay(1200); brightnessHud = null } }
    LaunchedEffect(volumeHud) { if (volumeHud != null) { delay(1200); volumeHud = null } }
    LaunchedEffect(seekFeedback) { if (seekFeedback != null) { delay(700); seekFeedback = null } }

    val aspectModes  = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    val aspectLabels = listOf("Fit", "Fill", "Zoom")

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── PlayerView ─────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = videoPlayer; useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectModes[aspectIdx]
                }
            },
            update = { pv -> pv.resizeMode = aspectModes[aspectIdx] },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture overlay ────────────────────────────────────────────
        var lastTap by remember { mutableStateOf(0L) }

        Box(
            Modifier.fillMaxSize()
                // Vertical swipe: brightness (left) / volume (right)
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTransformGestures { centroid, pan, _, _ ->
                        val dy = -pan.y / size.height
                        if (abs(dy) > 0.004f) {
                            if (centroid.x < size.width / 2) {
                                // Brightness
                                val lp = activity?.window?.attributes ?: return@detectTransformGestures
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur + dy).coerceIn(0.01f, 1f)
                                activity.window.attributes = lp
                                brightnessHud = lp.screenBrightness
                            } else {
                                // Volume
                                val max = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (cur + (dy * max).toInt()).coerceIn(0, max)
                                audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                volumeHud = newVol.toFloat() / max
                            }
                        }
                    }
                }
                // Tap / double-tap / long-press
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) { showControls = !showControls; return@detectTapGestures }
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 350) {
                                // Double-tap seek
                                if (it.x < size.width / 2) {
                                    videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                    seekFeedback = "−10s"
                                } else {
                                    videoPlayer.seekTo(videoPlayer.currentPosition + 10000)
                                    seekFeedback = "+10s"
                                }
                            } else {
                                showControls = !showControls
                            }
                            lastTap = now
                        },
                        onLongPress = { if (!isLocked) longPressSpeed = true }
                    )
                }
                // Long-press release detection
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed } && longPressSpeed) {
                                longPressSpeed = false
                            }
                        }
                    }
                }
        )

        // ── Seek feedback overlay ──────────────────────────────────────
        AnimatedVisibility(visible = seekFeedback != null,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(0.65f))
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(seekFeedback ?: "", style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ── 2× speed HUD ──────────────────────────────────────────────
        AnimatedVisibility(visible = longPressSpeed,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
            Box(Modifier.clip(RoundedCornerShape(8.dp))
                .background(NebulaViolet.copy(0.85f)).padding(horizontal = 18.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2× Speed", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Brightness HUD ────────────────────────────────────────────
        brightnessHud?.let { bv ->
            Box(Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) {
                GestureHud(Icons.Filled.Brightness6, bv)
            }
        }

        // ── Volume HUD ────────────────────────────────────────────────
        volumeHud?.let { vv ->
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)) {
                GestureHud(Icons.Filled.VolumeUp, vv)
            }
        }

        // ── Locked screen ─────────────────────────────────────────────
        if (isLocked) {
            AnimatedVisibility(visible = showControls,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(0.72f))
                            .border(1.dp, NebulaViolet.copy(0.4f), RoundedCornerShape(16.dp))
                            .clickable { isLocked = false }
                            .padding(horizontal = 28.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LockOpen, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Tap to unlock", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // ── Main controls overlay ──────────────────────────────────────
        if (!isLocked) {
            AnimatedVisibility(visible = showControls,
                enter = fadeIn(tween(150)), exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(
                            listOf(Color.Black.copy(.78f), Color.Transparent, Color.Transparent, Color.Black.copy(.88f))
                        ))
                ) {
                    // ── Top bar ──────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text(video.title, style = MaterialTheme.typography.titleMedium,
                            color = Color.White, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1)

                        // Aspect ratio
                        VideoTopButton(aspectLabels[aspectIdx]) { aspectIdx = (aspectIdx + 1) % aspectLabels.size }
                        // Speed
                        VideoTopButton("${speed}×") { showSpeed = true }
                        // Loop toggle
                        IconButton(onClick = { loopEnabled = !loopEnabled }) {
                            Icon(Icons.Filled.Repeat, null,
                                tint = if (loopEnabled) NebulaViolet else Color.White.copy(0.7f))
                        }
                        // Lock
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Filled.LockOpen, null, tint = Color.White.copy(0.85f))
                        }
                        // PiP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                try {
                                    activity?.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                    )
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Filled.PictureInPicture, null, tint = Color.White.copy(0.85f))
                            }
                        }
                    }

                    // ── Centre transport ──────────────────────────────────
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous / seek to start
                        IconButton(onClick = {
                            if (videoPlayer.currentPosition > 3000) videoPlayer.seekTo(0)
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(30.dp))
                        }
                        // Seek -10
                        IconButton(onClick = {
                            videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            seekFeedback = "−10s"
                        }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        // Play / pause
                        Box(
                            Modifier.size(70.dp).clip(CircleShape)
                                .background(Color.White.copy(.18f))
                                .border(2.dp, Color.White.copy(.65f), CircleShape)
                                .clickable {
                                    if (videoPlayer.isPlaying) { videoPlayer.pause(); isPlaying = false }
                                    else { videoPlayer.play(); isPlaying = true }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(34.dp)
                            )
                        }
                        // Seek +10
                        IconButton(onClick = {
                            videoPlayer.seekTo(videoPlayer.currentPosition + 10000)
                            seekFeedback = "+10s"
                        }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        // Skip to end
                        IconButton(onClick = {
                            videoPlayer.seekTo(videoPlayer.duration.coerceAtLeast(0))
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(30.dp))
                        }
                    }

                    // ── Bottom seek bar + time ─────────────────────────────
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        // Time row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(vidFmt(pos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.85f))
                            // Remaining time
                            Text("-${vidFmt(dur - pos)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.55f))
                            Text(vidFmt(dur), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.85f))
                        }
                        // Seek slider — custom styled
                        Slider(
                            value = (pos.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { videoPlayer.seekTo((it * dur).toLong()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                activeTrackColor   = NebulaViolet,
                                thumbColor         = Color.White,
                                inactiveTrackColor = Color.White.copy(.28f)
                            )
                        )
                        // File info strip
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val folderName = video.filePath.split("/")
                                .let { if (it.size >= 2) it[it.size - 2] else "" }
                            if (folderName.isNotBlank()) {
                                Icon(Icons.Filled.FolderOpen, null, tint = Color.White.copy(0.4f),
                                    modifier = Modifier.size(12.dp))
                                Text(folderName, style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.4f), maxLines = 1)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.4f))
                            if (loopEnabled) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(NebulaViolet.copy(0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("LOOP", style = MaterialTheme.typography.labelSmall,
                                        color = NebulaViolet, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Speed picker sheet
    if (showSpeed) {
        SpeedPickerSheet(
            current   = speed,
            onSelect  = { s -> speed = s; videoPlayer.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false }
        )
    }
}

// ── Gesture HUD (brightness / volume vertical pill) ──────────────────────
@Composable
private fun GestureHud(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.68f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(
            Modifier.height(90.dp).width(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.2f))
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp)).background(Color.White)
                    .align(Alignment.BottomStart)
            )
        }
        Text("${(value * 100).toInt()}%", color = Color.White.copy(0.85f),
            style = MaterialTheme.typography.labelSmall)
    }
}

// ── Small top-bar text button ──────────────────────────────────────────────
@Composable
private fun VideoTopButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White,
            fontWeight = FontWeight.SemiBold)
    }
}

// ── Time formatter ────────────────────────────────────────────────────────
private fun vidFmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
