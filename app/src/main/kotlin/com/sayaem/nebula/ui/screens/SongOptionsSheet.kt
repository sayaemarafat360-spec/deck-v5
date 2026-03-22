package com.sayaem.nebula.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreateAndAddPlaylist: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var showFileInfo        by remember { mutableStateOf(false) }
    var showPlaylistPicker  by remember { mutableStateOf(false) }
    var showDeleteConfirm   by remember { mutableStateOf(false) }
    var newPlaylistName     by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalAppColors.current.bgSecondary)
                .clickable(enabled = false) {}
                .navigationBarsPadding()
        ) {
            // Drag handle
            Box(
                Modifier.width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.border)
                    .align(Alignment.CenterHorizontally).padding(top = 0.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Song header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(NebulaViolet.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = NebulaViolet, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSecondary, maxLines = 1)
                }
            }

            HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Menu items
            SongOption(Icons.Filled.PlayArrow,           "Play Now",            NebulaViolet) { onPlayNow(); onDismiss() }
            SongOption(Icons.Filled.SkipNext,            "Play Next",           NebulaViolet) { onPlayNext(); onDismiss() }
            SongOption(Icons.Filled.AddToQueue,          "Add to Queue",        NebulaViolet) { onAddToQueue(); onDismiss() }
            SongOption(Icons.Filled.PlaylistAdd,         "Add to Playlist",     NebulaCyan)   { showPlaylistPicker = true }
            SongOption(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                NebulaPink
            ) { onToggleFavorite(); onDismiss() }
            SongOption(Icons.Filled.Edit,                "Edit Tags",           NebulaAmber)  { onEditTags(); onDismiss() }
            SongOption(Icons.Filled.NotificationsActive, "Set as Ringtone",     NebulaGreen)  {
                scope.launch { setRingtone(context, song) }
                onDismiss()
            }
            SongOption(Icons.Filled.Share,               "Share",               NebulaCyan)   { onShare(); onDismiss() }
            SongOption(Icons.Filled.Info,                "File Info",           TextSecondaryDark) { showFileInfo = true }
            SongOption(Icons.Filled.Delete,              "Delete from Device",  NebulaRed)    { showDeleteConfirm = true }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Playlist picker
    if (showPlaylistPicker) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.6f))
                .clickable { showPlaylistPicker = false }
        ) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(LocalAppColors.current.bgSecondary)
                    .clickable(enabled = false) {}
                    .padding(20.dp).navigationBarsPadding()
            ) {
                Text("Add to Playlist", style = MaterialTheme.typography.headlineSmall,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                // New playlist input
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName, onValueChange = { newPlaylistName = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("New playlist name", color = LocalAppColors.current.textTertiary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppColors.current.textPrimary, unfocusedTextColor = LocalAppColors.current.textPrimary,
                            focusedBorderColor = NebulaViolet, unfocusedBorderColor = LocalAppColors.current.border,
                        )
                    )
                    Button(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreateAndAddPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showPlaylistPicker = false
                            onDismiss()
                        }
                    }, enabled = newPlaylistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) {
                        Text("Create")
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (playlists.isNotEmpty()) {
                    Text("Existing playlists", style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(playlists) { pl ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onAddToPlaylist(pl.id)
                                    showPlaylistPicker = false
                                    onDismiss()
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.QueueMusic, null, tint = NebulaPink, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
                                    Text("${pl.songCount} songs", style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
                                }
                                Icon(Icons.Filled.Add, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
                            }
                            HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // File info dialog
    if (showFileInfo) {
        AlertDialog(
            onDismissRequest = { showFileInfo = false },
            containerColor = LocalAppColors.current.card,
            title = { Text("File Info", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Title",    song.title)
                    InfoRow("Artist",   song.artist)
                    InfoRow("Album",    song.album)
                    InfoRow("Duration", song.durationFormatted)
                    InfoRow("Size",     song.sizeFormatted)
                    InfoRow("Format",   song.filePath.substringAfterLast(".").uppercase())
                    InfoRow("Path",     song.filePath)
                    if (song.year > 0) InfoRow("Year", song.year.toString())
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileInfo = false }) {
                    Text("Close", color = NebulaViolet)
                }
            }
        )
    }

    // Delete confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = LocalAppColors.current.card,
            title = { Text("Delete File?", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text("\"${song.title}\" will be permanently deleted from your device. This cannot be undone.",
                    color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false; onDismiss() }) {
                    Text("Delete", color = NebulaRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SongOption(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textPrimary,
            modifier = Modifier.weight(1f))
    }
}

// ── Real Ringtone setter ──────────────────────────────────────────────
private suspend fun setRingtone(context: Context, song: Song) = withContext(Dispatchers.IO) {
    try {
        // Check WRITE_SETTINGS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context)) {
            // Send user to settings to grant permission
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return@withContext
        }
        // Update MediaStore flags
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        // Try content URI first, fallback to external URI query
        val updated = try {
            context.contentResolver.update(song.uri, values, null, null)
        } catch (_: Exception) {
            context.contentResolver.update(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values,
                "${MediaStore.Audio.Media._ID}=?", arrayOf(song.id.toString())
            )
        }
        if (updated > 0) {
            RingtoneManager.setActualDefaultRingtoneUri(
                context, RingtoneManager.TYPE_RINGTONE, song.uri)
        }
    } catch (e: Exception) { e.printStackTrace() }
}

// ── Real file delete ──────────────────────────────────────────────────
suspend fun deleteSong(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val deleted = context.contentResolver.delete(song.uri, null, null)
        deleted > 0
    } catch (e: Exception) { e.printStackTrace(); false }
}
