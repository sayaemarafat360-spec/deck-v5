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
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.AdMobBanner
import com.sayaem.nebula.ui.components.VideoThumbnail
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ── Date bucket label ─────────────────────────────────────────────────────
private fun dateBucket(addedEpochSec: Long): String {
    val nowSec = System.currentTimeMillis() / 1000
    val diff   = nowSec - addedEpochSec
    return when {
        diff < TimeUnit.DAYS.toSeconds(1)   -> "Today"
        diff < TimeUnit.DAYS.toSeconds(2)   -> "Yesterday"
        diff < TimeUnit.DAYS.toSeconds(7)   -> "This Week"
        diff < TimeUnit.DAYS.toSeconds(30)  -> "This Month"
        diff < TimeUnit.DAYS.toSeconds(365) -> "This Year"
        else                                -> "Older"
    }
}

// Tab indices
private const val TAB_VIDEOS = 0
private const val TAB_MUSIC  = 1
private const val TAB_FOLDERS= 2

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
    onSeeAllSongs: () -> Unit = {},
    onSeeAllVideos: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
) {
    val hour     = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) { in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val appColors = LocalAppColors.current

    // Pull-to-refresh (BOM 2024.02.00 API)
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh?.invoke()
            kotlinx.coroutines.delay(1400)
            pullState.endRefresh()
        }
    }

    var selectedTab by remember { mutableStateOf(TAB_VIDEOS) }

    Column(Modifier.fillMaxSize()) {

        // ── Fix #8 — STATIC header, never scrolls ────────────────────────
        Column(
            Modifier.fillMaxWidth()
                .background(color = appColors.bg)
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, color = appColors.textSecondary)
                    Text("Deck", style = MaterialTheme.typography.displaySmall,
                        color = appColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeIconBox(Icons.Filled.BarChart, NebulaViolet, onClick = onStatsClick)
                    if (!isPremium) HomeIconBox(Icons.Filled.Star, NebulaAmber, onClick = onPremiumClick)
                }
            }

            // Stat strip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeStatPill("${songs.size}",  "Songs",  NebulaViolet, Icons.Filled.MusicNote,  Modifier.weight(1f))
                HomeStatPill("${videos.size}", "Videos", NebulaRed,    Icons.Filled.VideoFile,  Modifier.weight(1f))
                HomeStatPill(
                    "${(songs + videos).groupBy { it.album.ifBlank { it.filePath.substringBeforeLast("/") } }.size}",
                    "Albums", NebulaCyan, Icons.Filled.Album, Modifier.weight(1f)
                )
            }

            // Tabs
            val tabLabels = listOf("Videos", "Music", "Folders")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.Transparent,
                contentColor     = NebulaViolet,
                edgePadding      = 20.dp,
                divider          = {}
            ) {
                tabLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(label, style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == i) NebulaViolet else appColors.textTertiary,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }
            HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)
        }

        // ── Scrollable body with pull-to-refresh ──────────────────────────
        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            when (selectedTab) {
                TAB_VIDEOS  -> VideosTab(videos, onVideoClick, onMoreVideoClick, onSeeAllVideos, isPremium)
                TAB_MUSIC   -> MusicTab(songs, recentSongs, recentlyAdded, onSongClick, onMoreClick, onSeeAllSongs, isPremium)
                TAB_FOLDERS -> FoldersTab(songs, videos, onSongClick, onVideoClick)
            }

            // Fix #3 — PullToRefreshContainer only visible when progress > 0 or refreshing
            // alpha trick: transparent when idle so no permanent circle
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (pullState.progress > 0f || pullState.isRefreshing) 1f else 0f,
                animationSpec = tween(150), label = "ptr"
            )
            PullToRefreshContainer(
                state          = pullState,
                modifier       = Modifier.align(Alignment.TopCenter).alpha(indicatorAlpha),
                contentColor   = NebulaViolet,
                containerColor = appColors.card,
            )
        }
    }
}

// ── TAB 1: VIDEOS — vertical feed, date-grouped ───────────────────────────
@Composable
private fun VideosTab(
    videos: List<Song>,
    onVideoClick: (Song) -> Unit,
    onMoreClick: ((Song) -> Unit)?,
    onSeeAll: () -> Unit,
    isPremium: Boolean,
) {
    if (videos.isEmpty()) {
        HomeEmpty("No videos found", "Add video files to your device storage", Icons.Filled.VideoFile)
        return
    }

    // Date-based grouping
    val grouped = remember(videos) {
        videos.groupBy { video ->
            dateBucket(video.id) // id approximates date_added ordering
        }.entries.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {
        // Hero — first video full-width
        item {
            Spacer(Modifier.height(16.dp))
            HeroVideoCard(
                video       = videos.first(),
                onClick     = { onVideoClick(videos.first()) },
                onMoreClick = { onMoreClick?.invoke(videos.first()) }
            )
        }

        // Vertical video feed — each card fills width like a feed
        videos.drop(1).forEachIndexed { idx, video ->
            // Date section header when bucket changes
            if (idx == 0 || dateBucket(video.id) != dateBucket(videos[idx].id)) {
                item(key = "header_${video.id}") {
                    DateHeader(dateBucket(video.id))
                }
            }
            item(key = "video_${video.id}") {
                FeedVideoCard(
                    video       = video,
                    onClick     = { onVideoClick(video) },
                    onMoreClick = { onMoreClick?.invoke(video) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (videos.size > 1) {
            item {
                SeeAllButton("See all ${videos.size} videos", NebulaRed, onSeeAll)
            }
        }
        if (!isPremium) {
            item { Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) { AdMobBanner() } }
        }
    }
}

// ── TAB 2: MUSIC — recently played + recently added + all songs ───────────
@Composable
private fun MusicTab(
    songs: List<Song>,
    recentSongs: List<Song>,
    recentlyAdded: List<Song>,
    onSongClick: (Song) -> Unit,
    onMoreClick: ((Song) -> Unit)?,
    onSeeAll: () -> Unit,
    isPremium: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {
        if (recentSongs.isNotEmpty()) {
            item { HomeSectionHeader("Recently Played", Icons.Filled.History, NebulaViolet) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentSongs.take(12)) { song ->
                        MusicAlbumCard(song, { onSongClick(song) }, { onMoreClick?.invoke(song) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { HomeSectionHeader("Recently Added", Icons.Filled.FiberNew, NebulaGreen, "Last 7 days") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyAdded.take(12)) { song ->
                        MusicAlbumCard(song, { onSongClick(song) }, { onMoreClick?.invoke(song) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (songs.isNotEmpty()) {
            item { HomeSectionHeader("All Songs", Icons.Filled.MusicNote, NebulaViolet, "${songs.size} tracks") }
            items(songs.take(10), key = { it.id }) { song ->
                HomeSongRow(song, { onSongClick(song) }, { onMoreClick?.invoke(song) })
            }
            if (songs.size > 10) {
                item { SeeAllButton("See all ${songs.size} songs", NebulaViolet, onSeeAll) }
            }
        }

        if (!isPremium) {
            item { Box(Modifier.fillMaxWidth().padding(0.dp, 4.dp)) { AdMobBanner() } }
        }
    }
}

// ── TAB 3: FOLDERS — grouped by folder path ───────────────────────────────
@Composable
private fun FoldersTab(
    songs: List<Song>,
    videos: List<Song>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
) {
    val allMedia = remember(songs, videos) { songs + videos }
    val folderMap = remember(allMedia) {
        allMedia.groupBy { item ->
            val parts = item.filePath.split("/")
            if (parts.size >= 2) parts[parts.size - 2] else "Root"
        }.toSortedMap()
    }

    if (folderMap.isEmpty()) {
        HomeEmpty("No folders found", "Grant storage permission to scan your files", Icons.Filled.FolderOpen)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("${folderMap.size} folders", style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        items(folderMap.entries.toList(), key = { it.key }) { (folderName, items) ->
            FolderCard(
                name       = folderName,
                count      = items.size,
                videoCount = items.count { it.isVideo },
                sampleItem = items.firstOrNull(),
                onClick    = {
                    // Play first song in folder
                    items.firstOrNull { !it.isVideo }?.let { onSongClick(it) }
                        ?: items.firstOrNull { it.isVideo }?.let { onVideoClick(it) }
                }
            )
        }
    }
}

// ── Folder card ───────────────────────────────────────────────────────────
@Composable
private fun FolderCard(
    name: String, count: Int, videoCount: Int,
    sampleItem: Song?, onClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color = appColors.card)
            .border(0.5.dp, appColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder art — small thumbnail or icon
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                .background(color = NebulaViolet.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = NebulaViolet, modifier = Modifier.size(28.dp))
            if (sampleItem?.albumArtUri != null && !sampleItem.isVideo) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(sampleItem.albumArtUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val musicCount = count - videoCount
                if (musicCount > 0) {
                    FolderChip("$musicCount songs", NebulaViolet)
                }
                if (videoCount > 0) {
                    FolderChip("$videoCount videos", NebulaRed)
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FolderChip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color = color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ── Hero video card (first item, large) ───────────────────────────────────
@Composable
private fun HeroVideoCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(0.5.dp, NebulaRed.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Fix #2 — Real video frame thumbnail
        VideoThumbnail(filePath = video.filePath, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Box(
            Modifier.size(68.dp).clip(CircleShape)
                .background(color = Color.Black.copy(alpha = 0.6f))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp)
                .clip(RoundedCornerShape(6.dp)).background(color = Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp)
                .clip(RoundedCornerShape(6.dp)).background(color = NebulaRed)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text("FEATURED", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold) }
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
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

// ── Feed video card — vertical full-width with real thumbnail ─────────────
@Composable
private fun FeedVideoCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color = appColors.card)
            .border(0.5.dp, appColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            // Fix #2 — Real video frame extraction
            VideoThumbnail(filePath = video.filePath, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(color = Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(color = Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) { Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall,
                    color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
                    val folderName = video.filePath.split("/").let { if (it.size >= 2) it[it.size - 2] else "" }
                    if (folderName.isNotBlank()) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(color = appColors.textTertiary))
                        Text(folderName, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Music album card ──────────────────────────────────────────────────────
@Composable
private fun MusicAlbumCard(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val colors    = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color     = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Column(Modifier.width(116.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(116.dp).clip(RoundedCornerShape(14.dp))
                .background(color = color.copy(alpha = 0.14f))
                .border(0.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
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
                    .align(Alignment.TopEnd).offset((-6).dp, 6.dp).clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color = color)
                    .align(Alignment.BottomEnd).offset((-6).dp, (-6).dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.labelMedium, color = appColors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Song row ──────────────────────────────────────────────────────────────
@Composable
private fun HomeSongRow(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val colors    = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color     = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color = color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
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

// ── Shared helpers ────────────────────────────────────────────────────────

@Composable
private fun HomeSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, badge: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(badge, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color = NebulaRed))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalAppColors.current.textSecondary,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
    }
}

@Composable
private fun SeeAllButton(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color = color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HomeEmpty(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = LocalAppColors.current.textTertiary)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textTertiary)
        }
    }
}

@Composable
private fun HomeIconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(color = color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun HomeStatPill(value: String, label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
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
        Text(value, style = MaterialTheme.typography.labelLarge, color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
    }
}

