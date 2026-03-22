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

@Composable
fun VideoPlayerScreen(
    video: Song,
    player: ExoPlayer?,      // unused — we create our own
    onBack: () -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    // Dedicated ExoPlayer using APPLICATION context — critical for content:// URIs
    val videoPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build()
    }

    DisposableEffect(Unit) {
        // Force landscape, keep screen on
        onPauseMusic()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Load and play immediately here — not in LaunchedEffect
        // This ensures the player is ready when the surface attaches
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

    var showControls by remember { mutableStateOf(true) }
    var isPlaying    by remember { mutableStateOf(true) }
    var speed        by remember { mutableStateOf(1.0f) }
    var showSpeed    by remember { mutableStateOf(false) }
    var aspectIdx    by remember { mutableStateOf(0) }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )
    val aspectLabels = listOf("Fit", "Fill", "Zoom")

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
    ) {
        // ── PlayerView — NO graphicsLayer, NO transformable modifier ──
        // SurfaceView renders directly to screen. Any layer transformation breaks it.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = videoPlayer
                    useController  = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectModes[0]
                }
            },
            update = { pv ->
                pv.resizeMode = aspectModes[aspectIdx]
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture overlay ────────────────────────────────────────────
        var lastTap by remember { mutableStateOf(0L) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 350) {
                                // Double-tap seek
                                if (it.x < size.width / 2) {
                                    videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                } else {
                                    videoPlayer.seekTo(videoPlayer.currentPosition + 10000)
                                }
                            } else {
                                showControls = !showControls
                            }
                            lastTap = now
                        }
                    )
                }
        )

        // ── Controls ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(
                        listOf(Color.Black.copy(.7f), Color.Transparent, Color.Black.copy(.8f))
                    ))
            ) {
                // Top bar
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
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1
                    )
                    TextButton(onClick = { aspectIdx = (aspectIdx + 1) % aspectLabels.size }) {
                        Text(aspectLabels[aspectIdx], color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { showSpeed = true }) {
                        Text("${speed}×", color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                    }
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

                // Center play/pause + seek
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                    }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Replay10, null, tint = Color.White,
                            modifier = Modifier.size(38.dp))
                    }
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
                    IconButton(onClick = {
                        videoPlayer.seekTo(videoPlayer.currentPosition + 10000)
                    }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Forward10, null, tint = Color.White,
                            modifier = Modifier.size(38.dp))
                    }
                }

                // Bottom seek bar
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

    if (showSpeed) {
        SpeedPickerSheet(
            current   = speed,
            onSelect  = { s -> speed = s; videoPlayer.setPlaybackSpeed(s); showSpeed = false },
            onDismiss = { showSpeed = false }
        )
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
