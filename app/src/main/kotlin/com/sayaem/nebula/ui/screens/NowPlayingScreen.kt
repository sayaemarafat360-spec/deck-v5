package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import android.graphics.drawable.BitmapDrawable
import coil.request.SuccessResult
import coil.ImageLoader
import androidx.palette.graphics.Palette
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.PlaybackState
import com.sayaem.nebula.data.models.RepeatMode
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.PlayingIndicator
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    currentSpeed: Float,
    sleepTimerState: SleepTimerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onClose: () -> Unit,
    audioSessionId: Int = 0,
    onEqualizerClick: () -> Unit,
    onSleepTimer: () -> Unit,
    onSpeedClick: () -> Unit,
    onShare: (Song) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (Song) -> Unit,
    onQueueSeekTo: ((Int) -> Unit)? = null,
    onAddBookmark: (() -> Unit)? = null,
    onBookmarks: (() -> List<com.sayaem.nebula.data.local.LocalDataStore.Bookmark>)? = null,
    onSeekToBookmark: ((Long) -> Unit)? = null,
    onDeleteBookmark: ((Long) -> Unit)? = null,
    onEditTag: ((Song) -> Unit)? = null,
) {
    val song = state.currentSong

    val bgAnim by animateColorAsState(
        if (state.isPlaying) NebulaViolet.copy(alpha = 0.25f) else LocalAppColors.current.bgSecondary,
        animationSpec = tween(800), label = "bg"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "rot"
    )

    var isFav by remember(isFavorite) { mutableStateOf(isFavorite) }
    var showQueue  by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Dynamic background color from album art
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    val currentSong = state.currentSong
    LaunchedEffect(currentSong?.id) {
        val artUri = currentSong?.albumArtUri ?: run { dominantColor = null; return@LaunchedEffect }
        try {
            val loader = ImageLoader(context)
            val req    = ImageRequest.Builder(context).data(artUri).allowHardware(false).build()
            val result = (loader.execute(req) as? SuccessResult)?.drawable
            val bmp    = (result as? BitmapDrawable)?.bitmap
            bmp?.let { b ->
                Palette.from(b).generate { palette ->
                    val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch
                    swatch?.let { sw ->
                        dominantColor = androidx.compose.ui.graphics.Color(sw.rgb).copy(alpha = 0.35f)
                    }
                }
            }
        } catch (_: Exception) { dominantColor = null }
    }

    Box(modifier = Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(bgAnim, LocalAppColors.current.bg)))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))

            // Top bar
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(Icons.Filled.KeyboardArrowDown, onClick = onClose)
                Text("Now Playing", style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.textSecondary)
                CircleBtn(Icons.Filled.QueueMusic, onClick = { showQueue = true })
            }

            Spacer(Modifier.height(32.dp))

            // Vinyl disc with real-time audio visualizer behind it
            Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
                // Real-time FFT visualizer ring behind disc
                if (audioSessionId != 0 && state.isPlaying) {
                    AudioVisualizer(
                        audioSessionId = audioSessionId,
                        barCount       = 40,
                        color1         = NebulaViolet,
                        color2         = NebulaPink,
                        modifier       = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.size(290.dp).clip(CircleShape)
                    .background(NebulaViolet.copy(alpha = if (state.isPlaying) 0.12f else 0.04f)))
                Box(
                    modifier = Modifier.size(270.dp).clip(CircleShape)
                        .background(Color(0xFF0A0A14))
                        .rotate(if (state.isPlaying) vinylRotation else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    // Real album art in center of vinyl
                    if (state.currentSong?.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.currentSong?.albumArtUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(160.dp).clip(CircleShape)
                        )
                    }
                    for (r in listOf(0.95f, 0.8f, 0.65f, 0.5f)) {
                        Box(Modifier.size((270 * r).dp).clip(CircleShape)
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape))
                    }
                    Box(Modifier.size(150.dp).clip(CircleShape)
                        .border(3.dp, NebulaViolet.copy(alpha = 0.2f), CircleShape))
                    Box(Modifier.size(120.dp).clip(CircleShape)
                        .background(NebulaViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        if (state.isPlaying) PlayingIndicator(NebulaVioletLight)
                        else Icon(Icons.Filled.MusicNote, null,
                            tint = NebulaViolet, modifier = Modifier.size(44.dp))
                    }
                    Box(Modifier.size(18.dp).clip(CircleShape).background(LocalAppColors.current.bg))
                }
            }

            Spacer(Modifier.height(28.dp))

            // Song info
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.displaySmall,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(song?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge, color = LocalAppColors.current.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val favScale by animateFloatAsState(if (isFav) 1.3f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "fav")
                IconButton(onClick = {
                    isFav = !isFav
                    song?.let { onToggleFavorite(it) }
                }) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null,
                        tint = if (isFav) NebulaPink else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp).scale(favScale))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Waveform seek bar
            WaveformSeekBar(
                progress = state.progress,
                onSeek   = onSeek,
                position = state.position,
                duration = state.duration,
                songId   = state.currentSong?.id ?: 0L,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Main controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Shuffle, null,
                        tint = if (state.isShuffled) NebulaViolet else TextSecondaryDark,
                        modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier.size(70.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(state.isPlaying, label = "pp") { playing ->
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
                    Icon(
                        when (state.repeatMode) {
                            RepeatMode.ONE  -> Icons.Filled.RepeatOne
                            else            -> Icons.Filled.Repeat
                        }, null,
                        tint = if (state.repeatMode != RepeatMode.NONE) NebulaViolet else TextSecondaryDark,
                        modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Extra actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ExtraBtn(Icons.Filled.Equalizer, "EQ",    onClick = onEqualizerClick)
                ExtraBtn(Icons.Filled.Timer,
                    if (sleepTimerState.isActive) sleepTimerState.remainingFormatted else "Sleep",
                    tint = if (sleepTimerState.isActive) NebulaCyan else null,
                    onClick = onSleepTimer)
                ExtraBtn(Icons.Filled.Speed, "${currentSpeed}x", onClick = onSpeedClick)
                ExtraBtn(Icons.Filled.Share, "Share",
                    onClick = { song?.let { onShare(it) } })
                ExtraBtn(Icons.Filled.Lyrics, "Lyrics",    onClick = { showLyrics = true })
                ExtraBtn(Icons.Filled.Bookmark, "Bookmark", onClick = { onAddBookmark?.invoke() })
                ExtraBtn(Icons.Filled.Edit, "Edit Tags", onClick = { state.currentSong?.let { onEditTag?.invoke(it) } })
            }
        }
    }
    // Lyrics sheet (real .lrc file reader)
    if (showLyrics) {
        state.currentSong?.let { song ->
            LyricsSheet(
                song       = song,
                positionMs = state.position,
                onDismiss  = { showLyrics = false }
            )
        }
    }

    // Queue sheet overlay
    if (showQueue) {
        QueueSheet(
            queue      = state.queue,
            currentIdx = state.queueIndex,
            onSeekTo   = { idx -> onQueueSeekTo?.invoke(idx); showQueue = false },
            onDismiss  = { showQueue = false }
        )
    }

}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ExtraBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint ?: Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val m = (ms / 60000).toString().padStart(2, '0')
    val s = ((ms % 60000) / 1000).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
fun QueueSheet(
    queue: List<com.sayaem.nebula.data.models.Song>,
    currentIdx: Int,
    onSeekTo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.BottomCenter)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalAppColors.current.bgSecondary)
                .clickable(enabled = false) {}
        ) {
            // Handle + header
            Box(Modifier.width(36.dp).height(4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                .background(LocalAppColors.current.border)
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp))

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Up Next", style = MaterialTheme.typography.headlineSmall,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Text("${queue.size} songs", style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textTertiary)
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(queue.size) { i ->
                    val song    = queue[i]
                    val isCurr  = i == currentIdx
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isCurr) NebulaViolet.copy(0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSeekTo(i) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Index or playing indicator
                        Box(Modifier.width(28.dp)) {
                            if (isCurr) {
                                Icon(Icons.Filled.VolumeUp, null, tint = NebulaViolet,
                                    modifier = Modifier.size(18.dp))
                            } else {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = LocalAppColors.current.textTertiary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurr) NebulaViolet else TextPrimaryDark,
                                fontWeight = if (isCurr) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textTertiary,
                                maxLines = 1)
                        }
                        Text(song.durationFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalAppColors.current.textTertiary)
                    }
                    if (i < queue.size - 1) {
                        HorizontalDivider(
                            Modifier.padding(start = 60.dp),
                            color = LocalAppColors.current.borderSubtle,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    position: Long,
    duration: Long,
    songId: Long,
    modifier: Modifier = Modifier,
) {
    // Generate pseudo-waveform bars seeded by songId so each song looks unique
    val bars = remember(songId) {
        val rng = java.util.Random(songId)
        List(60) { 0.15f + rng.nextFloat() * 0.85f }
    }

    Column(modifier = modifier) {
        // Waveform canvas with drag-to-seek
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableStateOf(progress) }
        val displayProgress = if (isDragging) dragProgress else progress

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            isDragging   = true
                            dragProgress = (pos.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { _, drag ->
                            dragProgress = (dragProgress + drag.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek(dragProgress)
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            val appColors = LocalAppColors.current
            Canvas(Modifier.fillMaxSize()) {
                val barW    = size.width / bars.size
                val centerY = size.height / 2f
                val maxH    = size.height * 0.9f

                bars.forEachIndexed { i, amp ->
                    val x       = i * barW + barW / 2f
                    val barFrac = x / size.width
                    val h       = amp * maxH
                    val played  = barFrac <= displayProgress

                    // Bar color: played = violet, unplayed = dark
                    val color = if (played) NebulaViolet else appColors.border

                    drawRoundRect(
                        color       = color,
                        topLeft     = androidx.compose.ui.geometry.Offset(x - barW * 0.3f, centerY - h / 2),
                        size        = androidx.compose.ui.geometry.Size(barW * 0.55f, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.3f),
                    )
                }

                // Playhead thumb
                val thumbX = displayProgress * size.width
                drawCircle(
                    color  = Color.White,
                    radius = 6.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
                )
            }
        }

        // Time labels
        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(position), style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary)
            if (isDragging) {
                Text(formatMs((dragProgress * duration).toLong()),
                    style = MaterialTheme.typography.labelSmall, color = NebulaViolet,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(formatMs(duration), style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary)
        }
    }
}
