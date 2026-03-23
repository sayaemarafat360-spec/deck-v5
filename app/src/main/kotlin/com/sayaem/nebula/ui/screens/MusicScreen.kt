package com.sayaem.nebula.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.EmptyState
import com.sayaem.nebula.ui.components.SortChip
import com.sayaem.nebula.ui.components.SwipeableSongTile
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun MusicScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favorites: List<Song>,
    playlists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onSearchClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val chips = listOf("Songs", "Albums", "Artists", "Playlists", "Favorites")
    var selected by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(appColors.bg)) {

        // ── Top bar ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Music", style = MaterialTheme.typography.headlineLarge,
                    color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
                Text("${songs.size} songs", style = MaterialTheme.typography.bodySmall,
                    color = appColors.textTertiary)
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, null, tint = appColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        // ── Scrollable filter chips ────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(chips) { i, chip ->
                val chipColor by animateColorAsState(
                    targetValue = if (selected == i) NebulaViolet else appColors.card,
                    animationSpec = tween(180), label = "chip$i"
                )
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(chipColor)
                        .border(0.5.dp, if (selected == i) NebulaViolet else appColors.border, RoundedCornerShape(20.dp))
                        .clickable { selected = i }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(chip, style = MaterialTheme.typography.labelLarge,
                        color = if (selected == i) Color.White else appColors.textSecondary,
                        fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)

        // ── Content ────────────────────────────────────────────────────
        when (selected) {
            0 -> MusicSongsTab(songs, currentSong, isPlaying, onSongClick, onMoreClick, onPlayNext, onAddToQueue)
            1 -> MusicAlbumsTab(songs, onSongClick)
            2 -> MusicArtistsTab(songs, onSongClick)
            3 -> MusicPlaylistsTab(songs, playlists, onPlayPlaylist, onCreatePlaylist,
                onDeletePlaylist, onRenamePlaylist, onAddSongToPlaylist, onRemoveSongFromPlaylist)
            4 -> MusicFavoritesTab(favorites, currentSong, isPlaying, onSongClick, onMoreClick)
        }
    }
}

// ── Songs ─────────────────────────────────────────────────────────────────
@Composable
private fun MusicSongsTab(
    songs: List<Song>, current: Song?, isPlaying: Boolean,
    onSongClick: (Song) -> Unit, onMoreClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit, onAddToQueue: (Song) -> Unit,
) {
    var sortIdx by remember { mutableIntStateOf(0) }
    val sorts   = listOf("A–Z", "Recent", "Artist", "Duration")
    val sorted  = remember(songs, sortIdx) {
        when (sortIdx) {
            1 -> songs // MediaRepository returns in date order
            2 -> songs.sortedBy { it.artist }
            3 -> songs.sortedBy { it.duration }
            else -> songs.sortedBy { it.title }
        }
    }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sorts) { i, s -> SortChip(s, i == sortIdx) { sortIdx = i } }
        }
        if (sorted.isEmpty()) {
            EmptyState("No songs found", "Grant storage permission and tap Rescan in Settings")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
                items(sorted, key = { it.id }) { song ->
                    SwipeableSongTile(
                        title = song.title, artist = song.artist,
                        duration = song.durationFormatted, albumArtUri = song.albumArtUri,
                        isPlaying = current?.id == song.id && isPlaying,
                        onClick = { onSongClick(song) }, onMoreClick = { onMoreClick(song) },
                        onPlayNext = { onPlayNext(song) }, onAddToQueue = { onAddToQueue(song) }
                    )
                    HorizontalDivider(Modifier.padding(start = 84.dp),
                        color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Albums ────────────────────────────────────────────────────────────────
@Composable
private fun MusicAlbumsTab(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val albums    = remember(songs) { songs.groupBy { it.album }.entries.toList() }
    val appColors = LocalAppColors.current
    if (albums.isEmpty()) { EmptyState("No albums found", ""); return }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 200.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(albums, key = { it.key }) { (album, albumSongs) ->
            val sample = albumSongs.first()
            Column(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(appColors.card)
                    .border(0.5.dp, appColors.border, RoundedCornerShape(14.dp))
                    .clickable { albumSongs.firstOrNull()?.let { onSongClick(it) } }
            ) {
                MusicArtBox(song = sample, size = 0.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp)))
                Column(Modifier.padding(10.dp)) {
                    Text(album, style = MaterialTheme.typography.labelLarge,
                        color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold)
                    Text("${albumSongs.size} songs", style = MaterialTheme.typography.labelSmall,
                        color = appColors.textTertiary)
                }
            }
        }
    }
}

// ── Artists ───────────────────────────────────────────────────────────────
@Composable
private fun MusicArtistsTab(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val artists   = remember(songs) { songs.groupBy { it.artist }.entries.sortedBy { it.key }.toList() }
    val appColors = LocalAppColors.current
    if (artists.isEmpty()) { EmptyState("No artists found", ""); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
        items(artists, key = { it.key }) { (artist, artistSongs) ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { artistSongs.firstOrNull()?.let { onSongClick(it) } }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(NebulaViolet.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${artistSongs.size} songs", style = MaterialTheme.typography.bodySmall,
                        color = appColors.textTertiary)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(Modifier.padding(start = 80.dp), color = appColors.borderSubtle, thickness = 0.5.dp)
        }
    }
}

// ── Playlists ─────────────────────────────────────────────────────────────
@Composable
private fun MusicPlaylistsTab(
    songs: List<Song>, playlists: List<Playlist>,
    onPlayPlaylist: (Playlist) -> Unit, onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit, onRenamePlaylist: (String, String) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit, onRemoveSongFromPlaylist: (String, Long) -> Unit,
) {
    val appColors = LocalAppColors.current
    var showCreate by remember { mutableStateOf(false) }
    var newName    by remember { mutableStateOf("") }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${playlists.size} playlists", style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary)
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(NebulaViolet.copy(0.12f))
                    .clickable { showCreate = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Playlist", style = MaterialTheme.typography.labelMedium, color = NebulaViolet)
                }
            }
        }

        if (playlists.isEmpty()) {
            EmptyState("No playlists yet", "Tap 'New Playlist' to create one")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlayPlaylist(pl) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                                .background(NebulaViolet.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.QueueMusic, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.bodyMedium,
                                color = appColors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${pl.songCount} songs", style = MaterialTheme.typography.bodySmall,
                                color = appColors.textTertiary)
                        }
                        IconButton(onClick = { onDeletePlaylist(pl.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.DeleteOutline, null, tint = NebulaRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 80.dp), color = appColors.borderSubtle, thickness = 0.5.dp)
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; newName = "" },
            containerColor = appColors.card,
            title = { Text("New Playlist", color = appColors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Playlist name", color = appColors.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary, unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = appColors.border)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) { onCreatePlaylist(newName.trim()); newName = ""; showCreate = false }
                }, enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; newName = "" }) {
                    Text("Cancel", color = appColors.textSecondary)
                }
            }
        )
    }
}

// ── Favorites ─────────────────────────────────────────────────────────────
@Composable
private fun MusicFavoritesTab(
    favorites: List<Song>, current: Song?, isPlaying: Boolean,
    onSongClick: (Song) -> Unit, onMoreClick: (Song) -> Unit,
) {
    if (favorites.isEmpty()) {
        EmptyState("No favorites yet", "Tap ♥ on any song to add it here")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
        items(favorites, key = { it.id }) { song ->
            SwipeableSongTile(
                title = song.title, artist = song.artist,
                duration = song.durationFormatted, albumArtUri = song.albumArtUri,
                isPlaying = current?.id == song.id && isPlaying,
                onClick = { onSongClick(song) }, onMoreClick = { onMoreClick(song) }
            )
            HorizontalDivider(Modifier.padding(start = 84.dp),
                color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
        }
    }
}
