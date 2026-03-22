package com.sayaem.nebula.ui.components

import androidx.compose.ui.*
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.*
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors


// ─── Gradient background ──────────────────────────────────────────────
@Composable
fun NebulaBackground(accentColor: Color = NebulaViolet, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.bg),
        content = content
    )
}

// ─── Section header ───────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, action: String = "", onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        if (action.isNotEmpty()) {
            Text(action, style = MaterialTheme.typography.labelMedium,
                color = NebulaViolet,
                modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

// ─── Song list tile ───────────────────────────────────────────────────
@Composable
fun SongTile(
    title: String,
    artist: String,
    duration: String,
    albumArtUri: android.net.Uri? = null,
    accentColor: Color = NebulaViolet,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real album art via Coil
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArtUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isPlaying) {
                Box(Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center) {
                    PlayingIndicator(color = Color.White)
                }
            } else if (albumArtUri == null) {
                Icon(Icons.Filled.MusicNote, contentDescription = null,
                    tint = accentColor, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) accentColor else TextPrimaryDark,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(duration, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Animated playing indicator ───────────────────────────────────────
@Composable
fun PlayingIndicator(color: Color = NebulaViolet) {
    val infiniteTransition = rememberInfiniteTransition(label = "playing")
    val bar1 by infiniteTransition.animateFloat(0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(0.7f, 0.2f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(0.5f, 0.9f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "b3")

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.size(20.dp)
    ) {
        listOf(bar1, bar2, bar3).forEach { h ->
            Box(Modifier.width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

// ─── Mini Player ──────────────────────────────────────────────────────
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit
) {
    val song = state.currentSong ?: return

    Surface(
        modifier   = Modifier.fillMaxWidth().clickable(onClick = onExpand),
        color      = LocalAppColors.current.surface,
        tonalElevation = 4.dp
    ) {
        Column {
            // Progress bar
            LinearProgressIndicator(
                progress   = { state.progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = NebulaViolet,
                trackColor = LocalAppColors.current.border
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real album art
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(NebulaViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.albumArtUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
                    }
                    if (state.isPlaying) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f))
                            .clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            PlayingIndicator()
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall,
                        color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Controls
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(26.dp)
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(NebulaViolet).clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ─── Mood chip ────────────────────────────────────────────────────────
@Composable
fun MoodChip(label: String, icon: @Composable () -> Unit, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg = if (selected) color.copy(alpha = 0.18f) else DarkCard
    val borderColor = if (selected) color.copy(alpha = 0.5f) else LocalAppColors.current.border

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(23.dp))
            .background(bg)
            .border(0.5.dp, borderColor, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else TextSecondaryDark)
    }
}

// ─── Premium banner ───────────────────────────────────────────────────
@Composable
fun PremiumBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Deck PREMIUM", style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(6.dp))
                Text("Unlock EQ, themes\n& ad-free experience",
                    style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Upgrade", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

// ─── Stat card ────────────────────────────────────────────────────────
@Composable
fun StatCard(value: String, label: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.card)
            .border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        icon()
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
    }
}

// ── Swipeable Song Tile (swipe right = Play Next, left = Add to Queue) ─
@Composable
fun SwipeableSongTile(
    title: String, artist: String, duration: String,
    albumArtUri: android.net.Uri? = null,
    accentColor: Color = NebulaViolet,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
) {
    var offsetX by remember { mutableStateOf(0f) }
    val animOffset by animateFloatAsState(offsetX, spring(stiffness = Spring.StiffnessMedium), label = "swipe")
    val threshold = 120f
    var triggered by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        // Background action hints
        if (animOffset > 20f && onPlayNext != null) {
            Box(Modifier.matchParentSize().background(NebulaViolet.copy(0.15f)),
                contentAlignment = Alignment.CenterStart) {
                Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SkipNext, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play Next", style = MaterialTheme.typography.labelMedium, color = NebulaViolet)
                }
            }
        }
        if (animOffset < -20f && onAddToQueue != null) {
            Box(Modifier.matchParentSize().background(NebulaCyan.copy(0.15f)),
                contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to Queue", style = MaterialTheme.typography.labelMedium, color = NebulaCyan)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.AddToQueue, null, tint = NebulaCyan, modifier = Modifier.size(20.dp))
                }
            }
        }

        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(animOffset.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX > threshold  -> { onPlayNext?.invoke(); triggered = true }
                                offsetX < -threshold -> { onAddToQueue?.invoke(); triggered = true }
                            }
                            offsetX = 0f; triggered = false
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + drag).coerceIn(-threshold * 1.5f, threshold * 1.5f)
                        }
                    )
                }
        ) {
            SongTile(title, artist, duration, albumArtUri, accentColor, isPlaying, onClick, onMoreClick)
        }
    }
}

// ── AdMob Banner (shown only for free users) ──────────────────────────
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            com.google.android.gms.ads.AdView(context).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                // Test ID for development — replace with real ID from AdMob console
                // Format: ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // AdMob test banner ID
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        },
        modifier = modifier.fillMaxWidth().height(50.dp)
    )
}
