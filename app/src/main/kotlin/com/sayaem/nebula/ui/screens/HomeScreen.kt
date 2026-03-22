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
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import com.sayaem.nebula.ui.components.AdMobBanner
import java.util.Calendar

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
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (isPremium) 160.dp else 210.dp)
    ) {

        // ── Header ──────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 56.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSecondary)
                    Text("Deck", style = MaterialTheme.typography.displaySmall,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBox(Icons.Filled.BarChart, NebulaViolet, onClick = onStatsClick)
                    if (!isPremium) {
                        IconBox(Icons.Filled.Star, NebulaAmber, onClick = onPremiumClick)
                    }
                }
            }
        }

        // ── Stats row ───────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill("${songs.size}", "Songs", NebulaViolet, Icons.Filled.MusicNote, Modifier.weight(1f))
                StatPill("${videos.size}", "Videos", NebulaRed, Icons.Filled.VideoFile, Modifier.weight(1f))
                StatPill("${(songs + videos).groupBy { it.album }.size}", "Albums", NebulaCyan, Icons.Filled.Album, Modifier.weight(1f))
            }
        }

        // ── FEATURED VIDEO — hero card ───────────────────────────────
        if (videos.isNotEmpty()) {
            item {
                SectionHeader("Featured Video", Icons.Filled.PlayCircle, NebulaRed)
            }
            item {
                FeaturedVideoCard(
                    video = videos.first(),
                    onClick = { onVideoClick(videos.first()) },
                    onMoreClick = { onMoreVideoClick?.invoke(videos.first()) }
                )
            }
        }

        // ── ALL VIDEOS grid ─────────────────────────────────────────
        if (videos.size > 1) {
            item {
                SectionHeader(
                    "All Videos", Icons.Filled.VideoLibrary, NebulaRed,
                    badge = "${videos.size}"
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos.drop(1).take(20)) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onMoreClick = { onMoreVideoClick?.invoke(video) }
                        )
                    }
                }
            }
        }

        // ── Recently Added ───────────────────────────────────────────
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
                        AlbumCard(song = song, onClick = { onSongClick(song) },
                            onMoreClick = { onMoreClick?.invoke(song) })
                    }
                }
            }
        }

        // ── Recently Played ──────────────────────────────────────────
        if (recentSongs.isNotEmpty()) {
            item { SectionHeader("Recently Played", Icons.Filled.History, NebulaViolet) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentSongs.take(10)) { song ->
                        AlbumCard(song = song, onClick = { onSongClick(song) },
                            onMoreClick = { onMoreClick?.invoke(song) })
                    }
                }
            }
        }

        // ── Songs quick list ─────────────────────────────────────────
        if (songs.isNotEmpty()) {
            item {
                SectionHeader("Songs", Icons.Filled.MusicNote, NebulaViolet,
                    badge = "${songs.size} tracks")
            }
            items(songs.take(6)) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { onMoreClick?.invoke(song) }
                )
            }
            if (songs.size > 6) {
                item {
                    Box(Modifier.fillMaxWidth().clickable {}.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center) {
                        Text("See all ${songs.size} songs in Library →",
                            style = MaterialTheme.typography.labelLarge, color = NebulaViolet)
                    }
                }
            }
        }

        // ── AdMob for free users
        if (!isPremium) {
            item {
                Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) {
                    AdMobBanner()
                }
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────
@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    badge: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        }
        if (badge != null) {
            Text(badge, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
        }
    }
}

// ── IconBox helper ──────────────────────────────────────────────────────
@Composable
private fun IconBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.12f))
            .border(0.5.dp, color.copy(0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
    }
}

// ── FEATURED VIDEO — large hero card ───────────────────────────────────
@Composable
private fun FeaturedVideoCard(
    video: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF120A0A))
            .border(0.5.dp, NebulaRed.copy(0.2f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail or gradient
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(NebulaRed.copy(0.15f), Color(0xFF120A0A))
                    )
                )
        )
        // Big play button
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(Color.Black.copy(0.6f))
                .border(2.dp, Color.White.copy(0.85f), CircleShape)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(34.dp))
        }
        // Bottom info row
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Featured", style = MaterialTheme.typography.labelSmall,
                    color = NebulaRed, fontWeight = FontWeight.Bold)
                Text(video.title, style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.6f))
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White.copy(0.8f))
            }
        }
    }
}

// ── VIDEO CARD (smaller, for the row) ──────────────────────────────────
@Composable
private fun VideoCard(
    video: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(Modifier.width(180.dp)) {
        Box(
            Modifier.width(180.dp).height(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF180A0A))
                .border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        ) {
            // Gradient bg
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(NebulaRed.copy(0.10f), Color(0xFF180A0A)))))
            // Play overlay
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.55f))
                    .border(1.5.dp, Color.White.copy(0.7f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Duration badge
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary)
            }
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = LocalAppColors.current.textTertiary,
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── ALBUM CARD (for music rows) ─────────────────────────────────────────
@Composable
private fun AlbumCard(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color  = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Column(Modifier.width(116.dp)) {
        Box(
            Modifier.size(116.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(0.14f))
                .border(0.5.dp, color.copy(0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = color, modifier = Modifier.size(38.dp))
            }
            // 3-dot overlay top-right
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(0.45f))
                    .align(Alignment.TopEnd).offset((-6).dp, 6.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            // Play button bottom-right
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color)
                    .align(Alignment.BottomEnd).offset((-6).dp, (-6).dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── SONG ROW ────────────────────────────────────────────────────────────
@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
) {
    val colors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color  = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall,
                color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(song.durationFormatted, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
        IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(17.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 82.dp), color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
}

// ── Stat pill ────────────────────────────────────────────────────────────
@Composable
private fun StatPill(
    value: String, label: String, color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(color.copy(0.08f))
            .border(0.5.dp, color.copy(0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelLarge,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
    }
}
