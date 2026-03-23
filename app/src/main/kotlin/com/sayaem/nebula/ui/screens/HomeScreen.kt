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
// Fix: PullToRefreshBox is Material3 1.3.0 (BOM ≥ 2024.06).
// BOM 2024.02.00 ships Material3 1.2.x — use PullToRefreshContainer + nestedScroll instead.
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
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import com.sayaem.nebula.ui.components.AdMobBanner
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    videos: List<Song>,
    recentSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onPremiumClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditTag: ((Song) -> Unit)? = null,
    onMoreClick: ((Song) -> Unit)? = null,
    onMoreVideoClick: ((Song) -> Unit)? = null,
    isPremium: Boolean = false,
    recentlyAdded: List<Song> = emptyList(),
    // Fix #5 — wired callbacks, was missing before
    onSeeAllSongs: () -> Unit = {},
    onSeeAllVideos: () -> Unit = {},
    // Fix #2 — pull to refresh
    onRefresh: (() -> Unit)? = null,
) {
    val hour     = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening"
    }

    // Fix: BOM 2024.02.00 API — rememberPullToRefreshState + PullToRefreshContainer
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh?.invoke()
            kotlinx.coroutines.delay(1400)
            pullState.endRefresh()
        }
    }

    val appColors = LocalAppColors.current

    Column(Modifier.fillMaxSize()) {

        // ── Fix #8 — Static branding: extracted ABOVE LazyColumn so it never scrolls away ──
        Column(
            Modifier.fillMaxWidth()
                .background(color = appColors.bg)
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textSecondary)
                    Text("Deck", style = MaterialTheme.typography.displaySmall,
                        color = appColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBox(Icons.Filled.BarChart, NebulaViolet, onClick = onStatsClick)
                    if (!isPremium) IconBox(Icons.Filled.Star, NebulaAmber, onClick = onPremiumClick)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill("${songs.size}",  "Songs",  NebulaViolet, Icons.Filled.MusicNote,  Modifier.weight(1f))
                StatPill("${videos.size}", "Videos", NebulaRed,    Icons.Filled.VideoFile,  Modifier.weight(1f))
                StatPill("${(songs + videos).groupBy { it.album }.size}",
                    "Albums", NebulaCyan, Icons.Filled.Album, Modifier.weight(1f))
            }
            HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)
        }

        // Fix #2 — Pull-to-refresh with BOM 2024.02.00 compatible API
        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (isPremium) 160.dp else 210.dp)
            ) {

                // ── Fix #3 — VIDEO FIRST ──────────────────────────────────────
                if (videos.isNotEmpty()) {
                    item {
                        SectionHeader("Videos", Icons.Filled.VideoLibrary, NebulaRed,
                            badge = "${videos.size}",
                            onSeeAll = if (videos.size > 1) onSeeAllVideos else null)
                    }
                    item {
                        FeaturedVideoCard(
                            video       = videos.first(),
                            onClick     = { onVideoClick(videos.first()) },
                            onMoreClick = { onMoreVideoClick?.invoke(videos.first()) }
                        )
                    }
                    if (videos.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(videos.drop(1).take(20)) { video ->
                                    VideoCard(
                                        video       = video,
                                        onClick     = { onVideoClick(video) },
                                        onMoreClick = { onMoreVideoClick?.invoke(video) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Recently Added ────────────────────────────────────────────
                if (recentlyAdded.isNotEmpty()) {
                    item {
                        SectionHeader("Recently Added", Icons.Filled.FiberNew, NebulaGreen,
                            badge = "Last 7 days")
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentlyAdded.take(10)) { song ->
                                AlbumCard(song, onClick = { onSongClick(song) },
                                    onMoreClick = { onMoreClick?.invoke(song) })
                            }
                        }
                    }
                }

                // ── Recently Played ───────────────────────────────────────────
                if (recentSongs.isNotEmpty()) {
                    item { SectionHeader("Recently Played", Icons.Filled.History, NebulaViolet) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentSongs.take(10)) { song ->
                                AlbumCard(song, onClick = { onSongClick(song) },
                                    onMoreClick = { onMoreClick?.invoke(song) })
                            }
                        }
                    }
                }

                // ── Songs vertical list ───────────────────────────────────────
                if (songs.isNotEmpty()) {
                    item {
                        SectionHeader("Songs", Icons.Filled.MusicNote, NebulaViolet,
                            badge = "${songs.size} tracks",
                            onSeeAll = if (songs.size > 8) onSeeAllSongs else null)  // Fix #5
                    }
                    items(songs.take(8)) { song ->
                        SongRow(song,
                            onClick     = { onSongClick(song) },
                            onMoreClick = { onMoreClick?.invoke(song) })
                    }
                    // Fix #5 — See All is now a real clickable button
                    if (songs.size > 8) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(color = NebulaViolet.copy(alpha = 0.08f))
                                    .border(0.5.dp, NebulaViolet.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .clickable(onClick = onSeeAllSongs)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("See all ${songs.size} songs in Library",
                                    style = MaterialTheme.typography.labelLarge, color = NebulaViolet)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = NebulaViolet,
                                    modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // ── AdMob ─────────────────────────────────────────────────────
                if (!isPremium) {
                    item { Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) { AdMobBanner() } }
                }
            }

            // Fix: PullToRefreshContainer placed on top, aligned to TopCenter in the Box
            PullToRefreshContainer(
                state          = pullState,
                modifier       = Modifier.align(Alignment.TopCenter),
                contentColor   = NebulaViolet,
                containerColor = appColors.card,
            )
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    badge: String? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                color = appColors.textPrimary, fontWeight = FontWeight.Bold)
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(badge, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
            }
        }
        if (onSeeAll != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("See all", style = MaterialTheme.typography.labelMedium, color = color)
                Icon(Icons.Filled.ChevronRight, null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── IconBox ───────────────────────────────────────────────────────────────
@Composable
private fun IconBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(color = color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
}

// ── Fix #4 — Featured Video: real thumbnail via AsyncImage ────────────────
// Fix #4 for AsyncImage: Coil 2.x AsyncImage has no composable `error` lambda.
// Use Box-stacking: icon rendered first, AsyncImage on top (transparent on fail) = icon shows through.
@Composable
private fun FeaturedVideoCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color = Color(0xFF120A0A))
            .border(0.5.dp, NebulaRed.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Fallback gradient always rendered beneath thumbnail
        Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(NebulaRed.copy(alpha = 0.25f), Color(0xFF120A0A)))))

        // Fix: AsyncImage on top — transparent/invisible on error, gradient shows through
        if (video.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.albumArtUri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Scrim
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        // Play button
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(color = Color.Black.copy(alpha = 0.6f))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }

        // Duration badge
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp)
                .clip(RoundedCornerShape(6.dp)).background(color = Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White) }

        // Bottom info
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color = NebulaRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("VIDEO", style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(video.title, style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

// ── Fix #4 — VideoCard: real thumbnail ───────────────────────────────────
@Composable
private fun VideoCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(Modifier.width(180.dp)) {
        Box(
            Modifier.width(180.dp).height(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color = Color(0xFF180A0A))
                .border(0.5.dp, appColors.border, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        ) {
            // Fallback gradient first
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(NebulaRed.copy(alpha = 0.15f), Color(0xFF180A0A)))))
            // Real thumbnail on top (transparent on error = fallback shows through)
            if (video.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(color = Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(4.dp)).background(color = Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) { Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.labelMedium,
                    color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                    color = appColors.textTertiary)
            }
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Fix #4 — AlbumCard: Box-stack fallback for album art ─────────────────
@Composable
private fun AlbumCard(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val colors    = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color     = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Column(Modifier.width(116.dp)) {
        Box(
            Modifier.size(116.dp).clip(RoundedCornerShape(14.dp))
                .background(color = color.copy(alpha = 0.14f))
                .border(0.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Fix: icon rendered first as fallback, AsyncImage on top transparent on error
            Icon(Icons.Filled.MusicNote, null, tint = color, modifier = Modifier.size(38.dp))
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            }
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                    .background(color = Color.Black.copy(alpha = 0.45f))
                    .align(Alignment.TopEnd).offset((-6).dp, 6.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color = color)
                    .align(Alignment.BottomEnd).offset((-6).dp, (-6).dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.labelMedium,
            color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── SongRow ────────────────────────────────────────────────────────────────
@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit = {}) {
    val appColors = LocalAppColors.current
    val colors    = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color     = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color = color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            // Icon as fallback beneath AsyncImage
            Icon(Icons.Filled.MusicNote, null, tint = color, modifier = Modifier.size(22.dp))
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(song.durationFormatted, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
        IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(17.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 82.dp), color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ── StatPill ──────────────────────────────────────────────────────────────
@Composable
private fun StatPill(
    value: String, label: String, color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(color = color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelLarge,
            color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
    }
}
