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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var groupByFolder by remember { mutableStateOf(false) }
    val folderGroups  = remember(sorted, groupByFolder) {
        if (!groupByFolder) emptyMap()
        else sorted.groupBy { it.filePath.split("/").let { p -> if (p.size >= 2) p[p.size - 2] else "Root" } }
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { onRefresh(); kotlinx.coroutines.delay(1200); pullState.endRefresh() }
    }
    val refreshAlpha by animateFloatAsState(
        targetValue = if (pullState.progress > 0f || pullState.isRefreshing) 1f else 0f,
        animationSpec = tween(150), label = "ptr"
    )

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
                // Folder group toggle
                IconButton(onClick = { groupByFolder = !groupByFolder }) {
                    Icon(
                        if (groupByFolder) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        null,
                        tint = if (groupByFolder) NebulaViolet else appColors.textTertiary,
                        modifier = Modifier.size(22.dp)
                    )
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
        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (groupByFolder && folderGroups.isNotEmpty()) {
                    folderGroups.forEach { (folder, folderVideos) ->
                        item(key = "header_$folder") {
                            FolderHeaderChip(
                                name    = folder,
                                count   = folderVideos.size,
                                onClick = { onFolderClick?.invoke(folder, folderVideos) }
                            )
                        }
                        items(folderVideos, key = { "v_${it.id}" }) { video ->
                            VideoFeedCard(video, { onVideoClick(video) }, { onMoreClick(video) })
                        }
                    }
                } else {
                    items(sorted, key = { it.id }) { video ->
                        VideoFeedCard(video, { onVideoClick(video) }, { onMoreClick(video) })
                    }
                }
            }

            PullToRefreshContainer(
                state = pullState, modifier = Modifier.align(Alignment.TopCenter).alpha(refreshAlpha),
                contentColor = NebulaViolet, containerColor = appColors.card,
            )
        }
    }
}

// ── Folder group header chip ──────────────────────────────────────────────
@Composable
private fun FolderHeaderChip(name: String, count: Int, onClick: () -> Unit = {}) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NebulaViolet.copy(0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.FolderOpen, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge,
            color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("$count videos", style = MaterialTheme.typography.labelSmall, color = appColors.textTertiary)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary, modifier = Modifier.size(16.dp))
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
