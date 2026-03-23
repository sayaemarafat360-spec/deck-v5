package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sayaem.nebula.data.models.PlaybackState
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.AdMobBanner
// MusicArtBox is in SongOptionsSheet (same package — no import needed)
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    videos: List<Song>,
    recentSongs: List<Song>,
    recentlyAdded: List<Song>,
    playbackState: PlaybackState,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
    onMoreVideo: (Song) -> Unit,
    onResumeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    isPremium: Boolean,
    isScanning: Boolean = false,
) {
    val appColors = LocalAppColors.current
    val hour      = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting  = when (hour) {
        in 5..11  -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            kotlinx.coroutines.delay(1200)
            pullState.endRefresh()
        }
    }

    val refreshAlpha by animateFloatAsState(
        targetValue = if (pullState.progress > 0f || pullState.isRefreshing) 1f else 0f,
        animationSpec = tween(150), label = "ptr"
    )

    Column(Modifier.fillMaxSize().background(appColors.bg)) {

        // ── Static top bar ────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(greeting, style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textTertiary)
                Text("Deck", style = MaterialTheme.typography.headlineLarge,
                    color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, null, tint = appColors.textPrimary,
                    modifier = Modifier.size(24.dp))
            }
        }

        // Scan progress banner
        androidx.compose.animation.AnimatedVisibility(visible = isScanning) {
            Row(
                Modifier.fillMaxWidth()
                    .background(NebulaViolet.copy(0.12f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = NebulaViolet,
                    trackColor = NebulaViolet.copy(0.2f)
                )
                Text("Scanning media…", style = MaterialTheme.typography.labelSmall,
                    color = NebulaViolet)
            }
        }
        HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)

        // ── Pull to refresh + scrollable body ─────────────────────────
        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 200.dp)
            ) {

                // ── Resume card — if something was playing ──────────────
                playbackState.currentSong?.let { current ->
                    item {
                        ResumeCard(
                            song     = current,
                            isPlaying = playbackState.isPlaying,
                            progress  = playbackState.progress,
                            onClick   = onResumeClick
                        )
                    }
                }

                // ── Stats strip ────────────────────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeStatChip("${songs.size}", "songs",  NebulaViolet, Icons.Filled.MusicNote, Modifier.weight(1f))
                        HomeStatChip("${videos.size}", "videos", NebulaRed,    Icons.Filled.VideoFile,  Modifier.weight(1f))
                        HomeStatChip(
                            "${(songs + videos).groupBy { it.filePath.substringBeforeLast("/") }.size}",
                            "folders", NebulaCyan, Icons.Filled.FolderOpen, Modifier.weight(1f)
                        )
                    }
                }

                // ── Recently Added ─────────────────────────────────────
                if (recentlyAdded.isNotEmpty()) {
                    item {
                        HomeSectionHeader("Recently Added", Icons.Filled.FiberNew, NebulaGreen)
                    }
                    items(recentlyAdded.take(10), key = { "added_${it.id}" }) { song ->
                        HomeRecentSongRow(song, { onSongClick(song) }, { onMoreSong(song) })
                        HorizontalDivider(Modifier.padding(start = 82.dp),
                            color = appColors.borderSubtle, thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // ── Recently Played ────────────────────────────────────
                if (recentSongs.isNotEmpty()) {
                    item {
                        HomeSectionHeader("Recently Played", Icons.Filled.History, NebulaViolet)
                    }
                    items(recentSongs.take(10), key = { "recent_${it.id}" }) { song ->
                        HomeRecentSongRow(song, { onSongClick(song) }, { onMoreSong(song) })
                        HorizontalDivider(Modifier.padding(start = 82.dp),
                            color = appColors.borderSubtle, thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // ── Recent Videos strip ────────────────────────────────
                if (videos.isNotEmpty()) {
                    item {
                        HomeSectionHeader("Recent Videos", Icons.Filled.VideoLibrary, NebulaRed)
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(videos.take(10), key = { it.id }) { video ->
                                HomeVideoCard(video, { onVideoClick(video) }, { onMoreVideo(video) })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // ── AdMob ──────────────────────────────────────────────
                if (!isPremium) {
                    item { Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) { AdMobBanner() } }
                }
            }

            PullToRefreshContainer(
                state          = pullState,
                modifier       = Modifier.align(Alignment.TopCenter).alpha(refreshAlpha),
                contentColor   = NebulaViolet,
                containerColor = appColors.card,
            )
        }
    }
}

// ── Resume Card ───────────────────────────────────────────────────────────
@Composable
private fun ResumeCard(song: Song, isPlaying: Boolean, progress: Float, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val colors    = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val accent    = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accent.copy(0.18f), accent.copy(0.06f))))
            .border(0.5.dp, accent.copy(0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MusicArtBox(song = song, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isPlaying) "Now Playing" else "Continue Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(song.title, style = MaterialTheme.typography.titleMedium,
                    color = appColors.textPrimary, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent, trackColor = accent.copy(0.2f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null, tint = Color.White, modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────
@Composable
private fun HomeSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
    }
}

// ── Album card ────────────────────────────────────────────────────────────
@Composable
private fun HomeAlbumCard(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(Modifier.width(108.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            MusicArtBox(song = song, size = 108.dp)
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(0.45f))
                    .align(Alignment.TopEnd).offset((-5).dp, 5.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(song.title, style = MaterialTheme.typography.labelMedium,
            color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold)
        Text(song.artist, style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Video card ────────────────────────────────────────────────────────────
@Composable
private fun HomeVideoCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(Modifier.width(170.dp)) {
        Box(
            Modifier.width(170.dp).height(98.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF180A0A))
                .clickable(onClick = onClick)
        ) {
            // Fallback gradient
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(NebulaRed.copy(0.2f), Color(0xFF180A0A)))))
            if (video.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)))
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(Color.Black.copy(0.55f))
                .border(1.5.dp, Color.White.copy(0.7f), CircleShape)
                .align(Alignment.Center), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.labelMedium,
                    color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                    color = appColors.textTertiary)
            }
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────
@Composable
private fun HomeStatChip(
    value: String, label: String, color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.08f))
            .border(0.5.dp, color.copy(0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelLarge,
            color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
    }
}


// ── Vertical recent song row for Home ────────────────────────────────────
@Composable
private fun HomeRecentSongRow(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicArtBox(song = song, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary, maxLines = 1)
        }
        Text(song.durationFormatted, style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(28.dp).clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary,
                modifier = Modifier.size(16.dp))
        }
    }
}
