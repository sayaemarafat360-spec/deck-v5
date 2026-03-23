package com.sayaem.nebula.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.VideoThumbnail
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun FolderContentScreen(
    folderName: String,
    songs: List<Song>,
    videos: List<Song>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
    onMoreVideo: (Song) -> Unit,
    onBack: () -> Unit,
) {
    val appColors = LocalAppColors.current
    var selectedTab by remember { mutableStateOf(if (songs.isNotEmpty()) 0 else 1) }

    Column(Modifier.fillMaxSize().background(appColors.bg)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = appColors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(folderName, style = MaterialTheme.typography.titleLarge,
                    color = appColors.textPrimary, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (songs.isNotEmpty()) append("${songs.size} songs")
                        if (songs.isNotEmpty() && videos.isNotEmpty()) append("  ·  ")
                        if (videos.isNotEmpty()) append("${videos.size} videos")
                    },
                    style = MaterialTheme.typography.bodySmall, color = appColors.textTertiary
                )
            }
        }

        // Tabs (only if both types exist)
        if (songs.isNotEmpty() && videos.isNotEmpty()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.Transparent,
                contentColor     = NebulaViolet,
                divider          = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = {
                        Text("Songs (${songs.size})", style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == 0) NebulaViolet else appColors.textTertiary)
                    })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        Text("Videos (${videos.size})", style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == 1) NebulaViolet else appColors.textTertiary)
                    })
            }
            HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)
        }

        when {
            selectedTab == 0 && songs.isNotEmpty() -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 200.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        FolderSongRow(song, { onSongClick(song) }, { onMoreSong(song) })
                        HorizontalDivider(
                            Modifier.padding(start = 82.dp),
                            color = appColors.borderSubtle, thickness = 0.5.dp
                        )
                    }
                }
            }
            selectedTab == 1 && videos.isNotEmpty() -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos, key = { it.id }) { video ->
                        FolderVideoRow(video, { onVideoClick(video) }, { onMoreVideo(video) })
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No media in this folder", color = appColors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun FolderSongRow(song: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicArtBox(song = song, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary, maxLines = 1)
        }
        Text(song.durationFormatted, style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary)
        IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FolderVideoRow(video: Song, onClick: () -> Unit, onMoreClick: () -> Unit) {
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
        Box(
            Modifier.width(120.dp).fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
        ) {
            VideoThumbnail(filePath = video.filePath, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)))
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.5f))
                    .border(1.dp, Color.White.copy(0.7f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(video.sizeFormatted, style = MaterialTheme.typography.bodySmall, color = appColors.textTertiary)
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
