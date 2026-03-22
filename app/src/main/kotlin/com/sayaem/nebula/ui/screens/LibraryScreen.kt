package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.SongTile
import com.sayaem.nebula.ui.components.SwipeableSongTile
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors





@Composable
fun LibraryScreen(
    songs: List<Song>,
    videos: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favorites: List<Song>,
    playlists: List<Playlist>,
    folders: Map<String, List<Song>>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit = {},
    onMoreVideoClick: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onPlayPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onReorderPlaylist: (String, List<Long>) -> Unit = { _, _ -> },
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Videos", "Albums", "Artists", "Playlists", "Favorites", "Folders")

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        }

        // Tab row
        ScrollableTabRow(selectedTabIndex = selectedTab,
            containerColor = Color.Transparent, contentColor = NebulaViolet,
            edgePadding = 16.dp, divider = {}) {
            tabs.forEachIndexed { i, tab ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = {
                        Text(tab, style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == i) NebulaViolet else TextTertiaryDark)
                    })
            }
        }
        HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

        when (selectedTab) {
            0 -> SongsTab(songs, currentSong, isPlaying, onSongClick, onMoreClick, onPlayNext, onAddToQueue)
            1 -> VideosTab(videos, onVideoClick, onMoreVideoClick)
            2 -> AlbumsTab(songs, onSongClick)
            3 -> ArtistsTab(songs, onSongClick)
            4 -> PlaylistsTab(songs, playlists, onPlayPlaylist, onCreatePlaylist,
                    onDeletePlaylist, onRenamePlaylist, onAddSongToPlaylist, onRemoveSongFromPlaylist, onReorderPlaylist)
            5 -> FavoritesTab(favorites, currentSong, isPlaying, onSongClick, onMoreClick)
            6 -> FoldersTab(folders, onSongClick)
        }
    }
}

// ─── Songs ───────────────────────────────────────────────────────────
@Composable
private fun SongsTab(songs: List<Song>, current: Song?, isPlaying: Boolean, onSongClick: (Song) -> Unit, onMoreClick: (Song) -> Unit = {}, onPlayNext: (Song) -> Unit = {}, onAddToQueue: (Song) -> Unit = {}) {
    var sortIdx by remember { mutableStateOf(0) }
    val sorts   = listOf("Recent", "A–Z", "Artist", "Duration")
    val sorted  = remember(songs, sortIdx) {
        when (sortIdx) {
            1 -> songs.sortedBy { it.title }
            2 -> songs.sortedBy { it.artist }
            3 -> songs.sortedBy { it.duration }
            else -> songs
        }
    }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sorts) { i, s ->
                SortChip(s, i == sortIdx) { sortIdx = i }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
            if (sorted.isEmpty()) {
                item { EmptyState("No songs found", "Grant storage permission to scan your music") }
            } else {
                items(sorted, key = { it.id }) { song ->
                    SwipeableSongTile(song.title, song.artist, song.durationFormatted,
                        albumArtUri  = song.albumArtUri,
                        isPlaying    = current?.id == song.id && isPlaying,
                        onClick      = { onSongClick(song) },
                        onMoreClick  = { onMoreClick(song) },
                        onPlayNext   = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) })
                    HorizontalDivider(Modifier.padding(start = 84.dp), color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── Videos ──────────────────────────────────────────────────────────
@Composable
private fun VideosTab(videos: List<Song>, onVideoClick: (Song) -> Unit, onMoreClick: (Song) -> Unit = {}) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (videos.isEmpty()) {
            item { EmptyState("No videos found", "Add video files to your device storage") }
        } else {
            items(videos, key = { it.id }) { video ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(LocalAppColors.current.card).border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(14.dp))
                    .clickable { onVideoClick(video) }.height(88.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(130.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
                        .background(NebulaRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayCircle, null, tint = NebulaRed, modifier = Modifier.size(36.dp))
                        Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.7f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(video.durationFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                    Column(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp).weight(1f)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall,
                            color = LocalAppColors.current.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(video.sizeFormatted, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                    }
                    IconButton(onClick = { onMoreClick(video) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.MoreVert, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─── Albums ──────────────────────────────────────────────────────────
@Composable
private fun AlbumsTab(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val albums = remember(songs) { songs.groupBy { it.album }.entries.toList() }
    val colors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    LazyVerticalGrid(columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(albums) { i, (album, albumSongs) ->
            val color = colors[i % colors.size]
            Column(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(LocalAppColors.current.card)
                .border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(16.dp))
                .clickable { albumSongs.firstOrNull()?.let { onSongClick(it) } }) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                    .background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    val artUri = albumSongs.firstOrNull()?.albumArtUri
                    if (artUri != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(artUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                        )
                    } else {
                        Icon(Icons.Filled.Album, null, tint = color, modifier = Modifier.size(40.dp))
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(album, style = MaterialTheme.typography.titleSmall,
                        color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold)
                    Text("${albumSongs.size} songs", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                }
            }
        }
    }
}

// ─── Artists ─────────────────────────────────────────────────────────
@Composable
private fun ArtistsTab(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val artists = remember(songs) { songs.groupBy { it.artist }.entries.toList().sortedBy { it.key } }
    val colors  = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        itemsIndexed(artists) { i, (artist, artistSongs) ->
            val color = colors[i % colors.size]
            Row(modifier = Modifier.fillMaxWidth().clickable { artistSongs.firstOrNull()?.let { onSongClick(it) } }
                .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(0.2f)),
                    contentAlignment = Alignment.Center) {
                    val artUri = artistSongs.firstOrNull()?.albumArtUri
                    if (artUri != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(artUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(artist.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall,
                            color = color, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, style = MaterialTheme.typography.titleMedium, color = LocalAppColors.current.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${artistSongs.size} songs", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(Modifier.padding(start = 86.dp), color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
        }
    }
}

// ─── Playlists (real) ─────────────────────────────────────────────────
@Composable
private fun PlaylistsTab(
    songs: List<Song>,
    playlists: List<Playlist>,
    onPlay: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onAdd: (String, Long) -> Unit,
    onRemove: (String, Long) -> Unit,
    onReorderPlaylist: (String, List<Long>) -> Unit = { _, _ -> },
) {
    var showCreateDialog  by remember { mutableStateOf(false) }
    var newPlaylistName   by remember { mutableStateOf("") }
    var renamingId        by remember { mutableStateOf<String?>(null) }
    var renameText        by remember { mutableStateOf("") }
    var expandedId        by remember { mutableStateOf<String?>(null) }
    var addingSongTo      by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        // Create new playlist button
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { showCreateDialog = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(NebulaViolet.copy(0.15f)).border(0.5.dp, NebulaViolet.copy(0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = NebulaViolet, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("New Playlist", style = MaterialTheme.typography.titleMedium,
                    color = NebulaViolet, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
        }

        if (playlists.isEmpty()) {
            item { EmptyState("No playlists yet", "Create your first playlist above") }
        } else {
            items(playlists, key = { it.id }) { pl ->
                val isExpanded = expandedId == pl.id

                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { expandedId = if (isExpanded) null else pl.id }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(NebulaPink.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.QueueMusic, null, tint = NebulaPink, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium,
                                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${pl.songCount} songs", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                        }
                        // Play button
                        if (pl.songCount > 0) {
                            IconButton(onClick = { onPlay(pl) }) {
                                Icon(Icons.Filled.PlayArrow, null, tint = NebulaViolet)
                            }
                        }
                        // More options
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, null, tint = LocalAppColors.current.textTertiary)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Add songs", color = LocalAppColors.current.textPrimary) },
                                    onClick = { addingSongTo = pl.id; showMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Add, null, tint = NebulaViolet) })
                                DropdownMenuItem(text = { Text("Rename", color = LocalAppColors.current.textPrimary) },
                                    onClick = { renamingId = pl.id; renameText = pl.name; showMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = NebulaCyan) })
                                DropdownMenuItem(text = { Text("Delete", color = NebulaRed) },
                                    onClick = { onDelete(pl.id); showMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = NebulaRed) })
                            }
                        }
                    }

                    // Expanded song list
                    if (isExpanded) {
                        val songMap    = songs.associateBy { it.id }
                        val plSongs    = pl.songIds.mapNotNull { songMap[it] }
                        if (plSongs.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 84.dp)) {
                                Text("No songs yet — tap ⋮ to add",
                                    style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                            }
                        } else {
                            plSongs.forEachIndexed { idx, song ->
                                Row(modifier = Modifier.fillMaxWidth()
                                    .padding(start = 84.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    // Drag handle
                                    Icon(Icons.Filled.DragHandle, null, tint = LocalAppColors.current.textTertiary,
                                        modifier = Modifier.size(18.dp).padding(end = 4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, style = MaterialTheme.typography.bodyMedium,
                                            color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                                    }
                                    // Move up
                                    if (idx > 0) {
                                        IconButton(onClick = {
                                            val ids = plSongs.map { it.id }.toMutableList()
                                            ids.add(idx - 1, ids.removeAt(idx))
                                            onReorderPlaylist(pl.id, ids)
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.KeyboardArrowUp, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    // Move down
                                    if (idx < plSongs.size - 1) {
                                        IconButton(onClick = {
                                            val ids = plSongs.map { it.id }.toMutableList()
                                            ids.add(idx + 1, ids.removeAt(idx))
                                            onReorderPlaylist(pl.id, ids)
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(onClick = { onRemove(pl.id, song.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.RemoveCircleOutline, null, tint = NebulaRed.copy(0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                    }
                }
                HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
            }
        }
    }

    // Create playlist dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
            tonalElevation = 0.dp,
            title = { Text("New Playlist", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name", color = LocalAppColors.current.textTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary, unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = LocalAppColors.current.border))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) { onCreate(newPlaylistName.trim()); newPlaylistName = "" }
                    showCreateDialog = false
                }) { Text("Create", color = NebulaViolet, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                    Text("Cancel", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    // Rename dialog
    renamingId?.let { rid ->
        AlertDialog(
            onDismissRequest = { renamingId = null },
            tonalElevation = 0.dp,
            title = { Text("Rename Playlist", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary, unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = LocalAppColors.current.border))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRename(rid, renameText.trim())
                    renamingId = null
                }) { Text("Save", color = NebulaViolet, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = null }) { Text("Cancel", color = LocalAppColors.current.textSecondary) }
            }
        )
    }

    // Add songs to playlist sheet
    addingSongTo?.let { pid ->
        AlertDialog(
            onDismissRequest = { addingSongTo = null },
            tonalElevation = 0.dp,
            title = { Text("Add Songs", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                val pl = playlists.find { it.id == pid }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(songs.filter { it.id !in (pl?.songIds ?: emptyList()) }) { song ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onAdd(pid, song.id) }
                            .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addingSongTo = null }) { Text("Done", color = NebulaViolet) }
            }
        )
    }
}

// ─── Favorites ───────────────────────────────────────────────────────
@Composable
private fun FavoritesTab(songs: List<Song>, current: Song?, isPlaying: Boolean, onSongClick: (Song) -> Unit, onMoreClick: (Song) -> Unit = {}) {
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        if (songs.isEmpty()) {
            item { EmptyState("No favorites yet", "Tap the heart on any song to save it here") }
        } else {
            items(songs, key = { it.id }) { song ->
                SongTile(song.title, song.artist, song.durationFormatted,
                    albumArtUri = song.albumArtUri,
                    accentColor = NebulaPink,
                    isPlaying = current?.id == song.id && isPlaying,
                    onClick = { onSongClick(song) },
                    onMoreClick = { onMoreClick(song) })
            }
        }
    }
}

// ─── Folders ─────────────────────────────────────────────────────────
@Composable
private fun FoldersTab(folders: Map<String, List<Song>>, onSongClick: (Song) -> Unit) {
    var expandedFolder by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        if (folders.isEmpty()) {
            item { EmptyState("No folders found", "Scan your media library first") }
        } else {
            folders.entries.forEach { (folder, folderSongs) ->
                val isExpanded = expandedFolder == folder
                item(key = folder) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            expandedFolder = if (isExpanded) null else folder }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(NebulaAmber.copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Folder, null, tint = NebulaAmber, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder, style = MaterialTheme.typography.titleMedium,
                                    color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${folderSongs.size} files", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                            }
                            Icon(if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(20.dp))
                        }

                        if (isExpanded) {
                            folderSongs.forEach { song ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { onSongClick(song) }
                                    .padding(start = 82.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (song.isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile,
                                        null, tint = if (song.isVideo) NebulaRed else NebulaViolet,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, style = MaterialTheme.typography.bodyMedium,
                                            color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.durationFormatted, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                                    }
                                }
                            }
                            HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                        }
                        HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(17.dp))
        .background(if (selected) NebulaViolet.copy(0.15f) else Color.Transparent)
        .border(if (selected) 1.dp else 0.5.dp,
            if (selected) NebulaViolet.copy(0.5f) else LocalAppColors.current.border, RoundedCornerShape(17.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) NebulaViolet else TextSecondaryDark)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LibraryMusic, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = LocalAppColors.current.textTertiary)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
        }
    }
}
