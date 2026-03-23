package com.sayaem.nebula.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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

// Fix #7 — Advanced video playback with gestures, lock, brightness/volume HUD
@Composable
fun VideoPlayerScreen(
    video: Song,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    val videoPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build()
    }

    DisposableEffect(Unit) {
        onPauseMusic()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val item = MediaItem.fromUri(video.uri)
        videoPlayer.setMediaItem(item)
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

    var showControls  by remember { mutableStateOf(true) }
    var isPlaying     by remember { mutableStateOf(true) }
    var speed         by remember { mutableStateOf(1.0f) }
    var showSpeed     by remember { mutableStateOf(false) }
    var aspectIdx     by remember { mutableStateOf(0) }
    var isLocked      by remember { mutableStateOf(false) }  // Fix #7 lock feature
    var brightnessHud by remember { mutableStateOf<Float?>(null) } // brightness gesture HUD
    var volumeHud     by remember { mutableStateOf<Float?>(null) }  // volume gesture HUD
    var longPressSpeed by remember { mutableStateOf(false) }
    var seekFeedback  by remember { mutableStateOf<String?>(null) }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )
    val aspectLabels = listOf("Fit", "Fill", "Zoom")

    // Fix #7 — 2× speed on long press
    LaunchedEffect(longPressSpeed) {
        if (longPressSpeed) {
            videoPlayer.setPlaybackSpeed(2f)
        } else {
            videoPlayer.setPlaybackSpeed(speed)
        }
    }

    // Auto-hide controls after 3.5 s
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) {
            delay(3500)
            showControls = false
        }
    }

    // Hide gesture HUDs after 1 s
    LaunchedEffect(brightnessHud) {
        if (brightnessHud != null) { delay(1000); brightnessHud = null }
    }
    LaunchedEffect(volumeHud) {
        if (volumeHud != null) { delay(1000); volumeHud = null }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) { delay(700); seekFeedback = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── PlayerView ─────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = videoPlayer
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectModes[0]
                }
            },
            update = { pv -> pv.resizeMode = aspectModes[aspectIdx] },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture overlay ────────────────────────────────────────────
        var lastTap by remember { mutableStateOf(0L) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        // While locked only single-tap to show lock UI
                        detectTapGestures(onTap = { showControls = !showControls })
                        return@pointerInput
                    }
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Vertical swipe: left side = brightness, right side = volume
                        val isLeft = centroid.x < size.width / 2
                        val dy = -pan.y / size.height
                        if (kotlin.math.abs(dy) > 0.005f) {
                            if (isLeft) {
                                val lp = activity?.window?.attributes
                                if (lp != null) {
                                    lp.screenBrightness = (lp.screenBrightness + dy).coerceIn(0.05f, 1f)
                                    activity.window.attributes = lp
                                    brightnessHud = lp.screenBrightness
                                }
                            } else {
                                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val newVol = (cur + (dy * max).toInt()).coerceIn(0, max)
                                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                                volumeHud = newVol.toFloat() / max
                            }
                        }
                    }
                }
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 350) {
                                val isLeft = it.x < size.width / 2
                                if (isLeft) {
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
                        onLongPress = {
                            longPressSpeed = true
                        }
                    )
                }
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

        // ── Seek feedback overlay (+10s / -10s) ────────────────────────
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(tween(100)),
            exit  = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.65f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(seekFeedback ?: "", style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ── Long-press 2× speed HUD ────────────────────────────────────
        AnimatedVisibility(
            visible = longPressSpeed,
            enter = fadeIn(tween(100)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(NebulaViolet.copy(0.8f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2× Speed", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Brightness HUD ─────────────────────────────────────────────
        brightnessHud?.let { bv ->
            Box(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                GestureHud(Icons.Filled.Brightness6, bv, "Brightness")
            }
        }

        // ── Volume HUD ────────────────────────────────────────────────
        volumeHud?.let { vv ->
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) {
                GestureHud(Icons.Filled.VolumeUp, vv, "Volume")
            }
        }

        // ── Lock screen indicator ─────────────────────────────────────
        if (isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(0.7f))
                        .border(1.dp, NebulaViolet.copy(0.4f), RoundedCornerShape(16.dp))
                        .clickable { isLocked = false }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Lock, null, tint = NebulaViolet, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to unlock", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ── Controls (hidden when locked) ──────────────────────────────
        if (!isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter   = fadeIn(tween(150)),
                exit    = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(
                            listOf(Color.Black.copy(.75f), Color.Transparent, Color.Black.copy(.85f))
                        ))
                ) {
                    // ── Top bar ─────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            maxLines = 1
                        )
                        // Aspect ratio toggle
                        TextButton(onClick = { aspectIdx = (aspectIdx + 1) % aspectLabels.size }) {
                            Text(aspectLabels[aspectIdx], color = Color.White,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        // Speed button
                        TextButton(onClick = { showSpeed = true }) {
                            Text("${speed}×", color = Color.White,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        // Fix #7 — Lock button
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Filled.LockOpen, null, tint = Color.White)
                        }
                        // PiP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                try {
                                    activity?.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9)).build()
                                    )
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Filled.PictureInPicture, null, tint = Color.White)
                            }
                        }
                    }

                    // ── Centre controls ─────────────────────────────────
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        IconButton(onClick = { videoPlayer.seekTo(0) },
                            modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(0.85f),
                                modifier = Modifier.size(32.dp))
                        }
                        // Seek -10
                        IconButton(onClick = {
                            videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            seekFeedback = "−10s"
                        }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White,
                                modifier = Modifier.size(38.dp))
                        }
                        // Play/pause
                        Box(
                            Modifier.size(68.dp).clip(CircleShape)
                                .background(Color.White.copy(.2f))
                                .border(2.dp, Color.White.copy(.6f), CircleShape)
                                .clickable {
                                    if (videoPlayer.isPlaying) {
                                        videoPlayer.pause(); isPlaying = false
                                    } else {
                                        videoPlayer.play(); isPlaying = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(32.dp)
                            )
                        }
                        // Seek +10
                        IconButton(onClick = {
                            videoPlayer.seekTo(videoPlayer.currentPosition + 10000)
                            seekFeedback = "+10s"
                        }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White,
                                modifier = Modifier.size(38.dp))
                        }
                        // Next / end
                        IconButton(onClick = {
                            videoPlayer.seekTo(videoPlayer.duration)
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(0.85f),
                                modifier = Modifier.size(32.dp))
                        }
                    }

                    // ── Bottom seek bar ─────────────────────────────────
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val pos by produceState(0L) {
                            while (true) {
                                value = videoPlayer.currentPosition
                                delay(500)
                            }
                        }
                        val dur = videoPlayer.duration.coerceAtLeast(1L)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatVideoMs(pos), style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(.8f))
                            Text(formatVideoMs(dur), style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(.8f))
                        }
                        Slider(
                            value         = (pos.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { videoPlayer.seekTo((it * dur).toLong()) },
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = SliderDefaults.colors(
                                activeTrackColor   = NebulaViolet,
                                thumbColor         = Color.White,
                                inactiveTrackColor = Color.White.copy(.3f)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showSpeed) {
        SpeedPickerSheet(
            current   = speed,
            onSelect  = { s -> speed = s; videoPlayer.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false }
        )
    }
}

// ── Gesture HUD (brightness / volume) ────────────────────────────────────
@Composable
private fun GestureHud(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    label: String,
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(0.65f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Box(
            Modifier.height(80.dp).width(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.2f))
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp)).background(Color.White)
                    .align(Alignment.BottomStart)
            )
        }
        Text("${(value * 100).toInt()}%", color = Color.White,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatVideoMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
