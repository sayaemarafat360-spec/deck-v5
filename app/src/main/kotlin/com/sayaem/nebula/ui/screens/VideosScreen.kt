package com.sayaem.nebula.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.EmptyState
import com.sayaem.nebula.ui.components.VideoThumbnail
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    videos: List<Song>,
    onVideoClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onFolderClick: ((String, List<Song>) -> Unit)? = null,
) {
    val appColors = LocalAppColors.current

    // Sort options
    var sortIdx by remember { mutableIntStateOf(0) }
    val sorts   = listOf("Recent", "Name", "Duration", "Size")
    val sorted  = remember(videos, sortIdx) {
        when (sortIdx) {
            1 -> videos.sortedBy { it.title }
            2 -> videos.sortedByDescending { it.duration }
            3 -> videos.sortedByDescending { it.size }
            else -> videos // date-added order (already sorted by MediaRepository)
        }
    }

    // Folder group toggle
    var groupByFolder by remember { mutableStateOf(true) }  // folders ON by default
    val folderGroups  = remember(sorted, groupByFolder) {
        if (!groupByFolder) emptyMap()
        else sorted.groupBy { it.filePath.split("/").let { p -> if (p.size >= 2) p[p.size - 2] else "Root" } }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { onRefresh(); kotlinx.coroutines.delay(1200); isRefreshing = false }
    }

    Column(Modifier.fillMaxSize().background(appColors.bg)) {

        // ── Top bar ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Videos", style = MaterialTheme.typography.headlineLarge,
                    color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
                Text("${videos.size} files", style = MaterialTheme.typography.bodySmall,
                    color = appColors.textTertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Toggle: Folder view / List view
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (groupByFolder) NebulaViolet.copy(0.15f) else appColors.card)
                        .border(0.5.dp,
                            if (groupByFolder) NebulaViolet else appColors.border,
                            RoundedCornerShape(10.dp))
                        .clickable { groupByFolder = !groupByFolder }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (groupByFolder) Icons.Filled.FolderOpen else Icons.Filled.ViewList,
                            null,
                            tint = if (groupByFolder) NebulaViolet else appColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (groupByFolder) "Folders" else "List",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (groupByFolder) NebulaViolet else appColors.textTertiary
                        )
                    }
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, null, tint = appColors.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        // ── Sort chips ─────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sorts) { i, s ->
                val sel = sortIdx == i
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) NebulaViolet else appColors.card)
                        .border(0.5.dp, if (sel) NebulaViolet else appColors.border, RoundedCornerShape(20.dp))
                        .clickable { sortIdx = i }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(s, style = MaterialTheme.typography.labelMedium,
                        color = if (sel) Color.White else appColors.textSecondary)
                }
            }
        }

        HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)

        if (videos.isEmpty()) {
            EmptyState("No videos found", "Add video files to your device storage")
            return@Column
        }

        // ── Video list ─────────────────────────────────────────────────
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (groupByFolder && folderGroups.isNotEmpty()) {
                    // Playit-style: ONLY show folder cards, NO inline videos
                    // Each card shows folder thumbnail from first video, name, count
                    // Tap → drill into folder (FolderContentScreen handles videos)
                    item(key = "folder_grid_header") { Spacer(Modifier.height(4.dp)) }
                    items(
                        items = folderGroups.entries.toList(),
                        key   = { "folder_${it.key}" }
                    ) { (folderName, folderVideos) ->
                        PlayitFolderCard(
                            name    = folderName,
                            videos  = folderVideos,
                            onClick = { onFolderClick?.invoke(folderName, folderVideos) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    items(sorted, key = { it.id }) { video ->
                        VideoFeedCard(video, { onVideoClick(video) }, { onMoreClick(video) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ── Playit-style folder card ──────────────────────────────────────────────
// Full-width card with thumbnail grid on left, folder info on right
@Composable
private fun PlayitFolderCard(name: String, videos: List<Song>, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.card)
            .border(0.5.dp, appColors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .height(80.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail grid (2x2) from first 4 videos, Playit style
        Box(
            Modifier.size(80.dp)
                .clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
        ) {
            when {
                videos.size >= 4 -> {
                    // 2x2 grid
                    Column {
                        Row(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                VideoThumbnail(videos[0].filePath, Modifier.fillMaxSize())
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                VideoThumbnail(videos[1].filePath, Modifier.fillMaxSize())
                            }
                        }
                        Row(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                VideoThumbnail(videos[2].filePath, Modifier.fillMaxSize())
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                VideoThumbnail(videos[3].filePath, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                videos.size == 2 || videos.size == 3 -> {
                    Row {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            VideoThumbnail(videos[0].filePath, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            videos.drop(1).take(2).forEach { v ->
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    VideoThumbnail(v.filePath, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                else -> VideoThumbnail(videos.first().filePath, Modifier.fillMaxSize())
            }
            // Dark overlay
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.18f)))
        }

        // Folder info
        Column(
            Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Folder, null, tint = NebulaAmber,
                    modifier = Modifier.size(14.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${videos.size} video${if (videos.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = appColors.textTertiary
            )
            // Total duration
            val totalDur = videos.sumOf { it.duration }
            if (totalDur > 0) {
                val h = totalDur / 3_600_000L
                val m = (totalDur % 3_600_000L) / 60_000L
                Text(
                    if (h > 0) "${h}h ${m}m" else "${m}m total",
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors.textTertiary
                )
            }
        }

        // Chevron
        Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary,
            modifier = Modifier.padding(end = 12.dp).size(18.dp))
    }
}

// ── Full-width video card with real thumbnail ─────────────────────────────
@Composable
fun VideoFeedCard(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(appColors.card)
            .border(0.5.dp, appColors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .height(86.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(128.dp).fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
        ) {
            VideoThumbnail(filePath = video.filePath, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.22f)))
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.5f))
                    .border(1.dp, Color.White.copy(0.7f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(video.sizeFormatted, style = MaterialTheme.typography.bodySmall,
                    color = appColors.textTertiary)
                val folder = video.filePath.split("/").let { if (it.size >= 2) it[it.size - 2] else "" }
                if (folder.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = appColors.textTertiary)
                    Text(folder, style = MaterialTheme.typography.bodySmall,
                        color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
