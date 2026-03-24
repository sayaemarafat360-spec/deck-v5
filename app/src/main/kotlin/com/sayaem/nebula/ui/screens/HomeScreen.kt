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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.PlaybackState
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.AdMobBanner
// MusicArtBox is in SongOptionsSheet (same package — no import needed)
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    videos: List<Song>,
    recentSongs: List<Song>,
    recentlyAdded: List<Song>,
    playbackState: PlaybackState,
    playlists: List<Playlist> = emptyList(),
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
    onMoreVideo: (Song) -> Unit,
    onResumeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onPlayPlaylist: (Playlist) -> Unit = {},
    onFolderClick: ((String, List<Song>) -> Unit)? = null,
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

    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            kotlinx.coroutines.delay(1200)
            isRefreshing = false
        }
    }

    // Sub-tab pager state
    val subTabs = listOf("Videos", "Folders", "Playlists")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { subTabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(appColors.bg)) {

        // ── Top bar ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(greeting, style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textTertiary)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Animated Sonix logo mark — pulsing waveform dots
                    SonixLogoMark()
                    Text("Sonix", style = MaterialTheme.typography.headlineLarge,
                        color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
                }
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = NebulaViolet,
                )
            }
        ) {
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

                // ── Recently Played history ─────────────────────────────
                if (recentSongs.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeSectionHeader("History", Icons.Filled.History, NebulaViolet)
                            TextButton(onClick = onClearHistory) {
                                Text("Clear all", style = MaterialTheme.typography.labelSmall,
                                    color = appColors.textTertiary)
                            }
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentSongs, key = { "hist_${it.id}" }) { song ->
                                HistoryCard(
                                    song    = song,
                                    onClick = { if (song.isVideo) onVideoClick(song) else onSongClick(song) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Recently Added ─────────────────────────────────────
                if (recentlyAdded.isNotEmpty()) {
                    item {
                        HomeSectionHeader("Recently Added", Icons.Filled.FiberNew, NebulaGreen)
                    }
                    items(recentlyAdded.take(6), key = { "added_${it.id}" }) { song ->
                        HomeRecentSongRow(song, { onSongClick(song) }, { onMoreSong(song) })
                        HorizontalDivider(Modifier.padding(start = 82.dp),
                            color = appColors.borderSubtle, thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // ── Sub-tabs: Videos / Folders / Playlists ─────────────
                item {
                    Spacer(Modifier.height(8.dp))
                    // Pill tab row
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subTabs.forEachIndexed { idx, label ->
                            val selected = pagerState.currentPage == idx
                            val targetColor = when (idx) {
                                0 -> NebulaRed; 1 -> NebulaCyan; else -> NebulaViolet
                            }
                            val bgColor by animateColorAsState(
                                if (selected) targetColor else targetColor.copy(0.08f),
                                animationSpec = tween(220), label = "tab_bg_$idx"
                            )
                            val textColor by animateColorAsState(
                                if (selected) Color.White else targetColor.copy(0.75f),
                                animationSpec = tween(220), label = "tab_txt_$idx"
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(bgColor)
                                    .border(0.5.dp, targetColor.copy(if (selected) 0f else 0.25f), RoundedCornerShape(50))
                                    .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                // ── HorizontalPager content ────────────────────────────
                item {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        userScrollEnabled = true,
                    ) { page ->
                        when (page) {
                            0 -> HomeVideosTab(
                                videos = videos,
                                onVideoClick = onVideoClick,
                                onMoreVideo = onMoreVideo,
                                onFolderClick = onFolderClick
                            )
                            1 -> HomeFoldersTab(
                                songs = songs,
                                videos = videos,
                                onFolderClick = onFolderClick
                            )
                            2 -> HomePlaylistsTab(
                                playlists = playlists,
                                songs = songs,
                                onPlayPlaylist = onPlayPlaylist,
                                onCreatePlaylist = onCreatePlaylist
                            )
                        }
                    }
                }

                // ── AdMob ──────────────────────────────────────────────
                if (!isPremium) {
                    item { Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) { AdMobBanner() } }
                }
            }
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


// ── History card — compact horizontal card for both songs and videos ──────
@Composable
private fun HistoryCard(song: Song, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(
        Modifier.width(90.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Thumbnail / art box
        Box(
            Modifier.size(90.dp).clip(RoundedCornerShape(10.dp))
                .background(appColors.card)
        ) {
            if (song.isVideo) {
                // Video thumbnail
                if (song.albumArtUri != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.VideoFile, null, tint = NebulaRed,
                            modifier = Modifier.size(28.dp))
                    }
                }
                // Video badge
                Box(Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.size(10.dp))
                    }
                }
                // Duration badge
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(0.7f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text(song.durationFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White)
                    }
                }
            } else {
                // Music art box
                MusicArtBox(song = song, size = 90.dp)
            }
        }
        // Title
        Text(song.title,
            style = MaterialTheme.typography.labelSmall,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium)
        // Artist or folder
        Text(
            if (song.isVideo) song.filePath.substringBeforeLast("/").substringAfterLast("/")
            else song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Sonix animated logo mark ──────────────────────────────────────────────
@Composable
private fun SonixLogoMark() {
    val inf = rememberInfiniteTransition(label = "logo")
    val bars = listOf(0.4f, 0.7f, 1f, 0.6f, 0.85f)
    Row(
        Modifier.size(22.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        bars.forEachIndexed { i, base ->
            val height by inf.animateFloat(
                initialValue = base * 0.5f,
                targetValue  = base,
                animationSpec = infiniteRepeatable(
                    tween(400 + i * 80, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NebulaViolet)
            )
        }
    }
}

// ── Home Videos sub-tab ───────────────────────────────────────────────────
@Composable
private fun HomeVideosTab(
    videos: List<Song>,
    onVideoClick: (Song) -> Unit,
    onMoreVideo: (Song) -> Unit,
    onFolderClick: ((String, List<Song>) -> Unit)?,
) {
    if (videos.isEmpty()) {
        HomeEmptyState("No videos found", Icons.Filled.VideoLibrary, NebulaRed)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 1800.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(videos.take(20), key = { "home_vid_${it.id}" }) { video ->
            VideoFeedCard(
                video       = video,
                onClick     = { onVideoClick(video) },
                onMoreClick = { onMoreVideo(video) }
            )
        }
    }
}

// ── Home Folders sub-tab ──────────────────────────────────────────────────
@Composable
private fun HomeFoldersTab(
    songs: List<Song>,
    videos: List<Song>,
    onFolderClick: ((String, List<Song>) -> Unit)?,
) {
    val appColors = LocalAppColors.current
    val allMedia  = songs + videos
    val folders   = remember(allMedia) {
        allMedia.groupBy { it.filePath.substringBeforeLast("/").substringAfterLast("/") }
            .entries.sortedByDescending { it.value.size }
    }
    if (folders.isEmpty()) {
        HomeEmptyState("No folders found", Icons.Filled.FolderOpen, NebulaCyan)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 1800.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false
    ) {
        items(folders, key = { "folder_${it.key}" }) { (name, items) ->
            val songCount  = items.count { !it.isVideo }
            val videoCount = items.count { it.isVideo }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appColors.card)
                    .border(0.5.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onFolderClick?.invoke(name, items) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                        .background(NebulaCyan.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FolderOpen, null, tint = NebulaCyan,
                        modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            if (songCount > 0) append("$songCount song${if (songCount > 1) "s" else ""}")
                            if (songCount > 0 && videoCount > 0) append("  ·  ")
                            if (videoCount > 0) append("$videoCount video${if (videoCount > 1) "s" else ""}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.textTertiary
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Home Playlists sub-tab ────────────────────────────────────────────────
@Composable
private fun HomePlaylistsTab(
    playlists: List<com.sayaem.nebula.data.models.Playlist>,
    songs: List<Song>,
    onPlayPlaylist: (com.sayaem.nebula.data.models.Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    val appColors = LocalAppColors.current
    var showCreate by remember { mutableStateOf(false) }
    var newName    by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Create new playlist button
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NebulaViolet.copy(0.1f))
                .border(0.5.dp, NebulaViolet.copy(0.25f), RoundedCornerShape(14.dp))
                .clickable { showCreate = !showCreate }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
            Text("New playlist", style = MaterialTheme.typography.bodyMedium,
                color = NebulaViolet, fontWeight = FontWeight.SemiBold)
        }
        // Inline name entry
        androidx.compose.animation.AnimatedVisibility(showCreate) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NebulaViolet,
                        unfocusedBorderColor = appColors.borderSubtle,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary
                    )
                )
                IconButton(onClick = {
                    if (newName.isNotBlank()) {
                        onCreatePlaylist(newName.trim())
                        newName = ""; showCreate = false
                    }
                }) {
                    Icon(Icons.Filled.Check, null, tint = NebulaViolet)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            HomeEmptyState("No playlists yet", Icons.Filled.QueueMusic, NebulaViolet)
        } else {
            playlists.forEach { playlist ->
                val songMap = remember(songs) { songs.associateBy { it.id } }
                val cover   = playlist.songIds.firstNotNullOfOrNull { songMap[it] }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.card)
                        .border(0.5.dp, appColors.borderSubtle, RoundedCornerShape(14.dp))
                        .clickable { onPlayPlaylist(playlist) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(NebulaViolet.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cover?.albumArtUri != null) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(cover.albumArtUri).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(Icons.Filled.QueueMusic, null, tint = NebulaViolet,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors.textTertiary)
                    }
                    Icon(Icons.Filled.PlayArrow, null, tint = NebulaViolet,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Generic empty state for sub-tabs ─────────────────────────────────────
@Composable
private fun HomeEmptyState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Box(
        Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color.copy(0.3f), modifier = Modifier.size(44.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textTertiary)
        }
    }
}
