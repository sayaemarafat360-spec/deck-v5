package com.sayaem.nebula.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val colors  = LocalAppColors.current

    var showFileInfo       by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var newPlaylistName    by remember { mutableStateOf("") }

    // Fix #1 — Android 11+ delete: launch system permission dialog
    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // System granted deletion — notify ViewModel to rescan
            onDelete()
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
        }
        showDeleteConfirm = false
        onDismiss()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.bgSecondary)
                .clickable(enabled = false) {}
                .navigationBarsPadding()
        ) {
            // Handle
            Box(
                Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(colors.border).align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            // Song header — show real album art
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artColors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
                val artColor  = artColors[(song.id % artColors.size).toInt().let { if (it < 0) -it else it }]
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(artColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (song.isVideo) Icons.Filled.VideoFile else Icons.Filled.MusicNote,
                        null, tint = artColor, modifier = Modifier.size(24.dp)
                    )
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(song.albumArtUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(song.artist)
                            if (song.album.isNotBlank() && song.album != "Unknown Album") append(" · ${song.album}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary, maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = colors.border, thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // ── Menu items — ALL real ─────────────────────────────────────
            SongOption(Icons.Filled.PlayArrow, "Play Now", NebulaViolet) {
                onPlayNow(); onDismiss()
            }
            SongOption(Icons.Filled.SkipNext, "Play Next", NebulaViolet) {
                onPlayNext()
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            // Fix #1 — Add to Queue: shows toast confirming it was added
            SongOption(Icons.Filled.AddToQueue, "Add to Queue", NebulaViolet) {
                onAddToQueue()
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            SongOption(Icons.Filled.PlaylistAdd, "Add to Playlist", NebulaCyan) {
                showPlaylistPicker = true
            }
            SongOption(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                NebulaPink
            ) { onToggleFavorite(); onDismiss() }
            SongOption(Icons.Filled.Edit, "Edit Tags", NebulaAmber) {
                onEditTags(); onDismiss()
            }
            SongOption(Icons.Filled.NotificationsActive, "Set as Ringtone", NebulaGreen) {
                scope.launch { setRingtone(context, song) }
                onDismiss()
            }
            SongOption(Icons.Filled.Share, "Share", NebulaCyan) { onShare(); onDismiss() }
            SongOption(Icons.Filled.Info, "File Info", TextSecondaryDark) { showFileInfo = true }
            SongOption(Icons.Filled.Delete, "Delete from Device", NebulaRed) {
                showDeleteConfirm = true
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Playlist picker ───────────────────────────────────────────────────
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName, onValueChange = { newPlaylistName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New playlist name", color = LocalAppColors.current.textTertiary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            focusedBorderColor = NebulaViolet,
                            unfocusedBorderColor = LocalAppColors.current.border,
                        )
                    )
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreateAndAddPlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                                showPlaylistPicker = false
                                Toast.makeText(context, "Added to new playlist", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        enabled = newPlaylistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)
                    ) { Text("Create") }
                }
                Spacer(Modifier.height(14.dp))
                if (playlists.isNotEmpty()) {
                    Text("Existing playlists", style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(playlists) { pl ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onAddToPlaylist(pl.id)
                                    showPlaylistPicker = false
                                    Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.QueueMusic, null, tint = NebulaPink,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.bodyMedium,
                                        color = LocalAppColors.current.textPrimary)
                                    Text("${pl.songCount} songs", style = MaterialTheme.typography.labelSmall,
                                        color = LocalAppColors.current.textTertiary)
                                }
                                Icon(Icons.Filled.Add, null, tint = LocalAppColors.current.textTertiary,
                                    modifier = Modifier.size(18.dp))
                            }
                            HorizontalDivider(color = LocalAppColors.current.borderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // ── File info ─────────────────────────────────────────────────────────
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
                    if (song.year > 0) InfoRow("Year", song.year.toString())
                    InfoRow("Path",     song.filePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileInfo = false }) { Text("Close", color = NebulaViolet) }
            }
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = LocalAppColors.current.card,
            title = { Text("Delete File?", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${song.title}\" will be permanently deleted from your device.",
                    color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // Fix #1: Real delete with Android 11+ MediaStore.createDeleteRequest support
                        performDelete(context, song,
                            onSuccess = {
                                onDelete()
                                showDeleteConfirm = false
                                onDismiss()
                            },
                            onNeedsPermission = { intentSender ->
                                // Android 11+: launch system dialog asking user to approve deletion
                                deleteIntentLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            },
                            onFailure = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showDeleteConfirm = false
                            }
                        )
                    }
                }) { Text("Delete", color = NebulaRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

// ── Option row ────────────────────────────────────────────────────────────
@Composable
private fun SongOption(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary,
            modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textPrimary,
            modifier = Modifier.weight(1f))
    }
}

// ── Real delete — handles Android 10/11 SecurityException ────────────────
private suspend fun performDelete(
    context: Context,
    song: Song,
    onSuccess: () -> Unit,
    onNeedsPermission: (android.content.IntentSender) -> Unit,
    onFailure: (String) -> Unit,
) = withContext(Dispatchers.Main) {
    withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(song.uri, null, null)
            if (deleted > 0) { withContext(Dispatchers.Main) { onSuccess() }; return@withContext }
            // deleted == 0 means permission needed on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                withContext(Dispatchers.Main) { onNeedsPermission(req.intentSender) }
            } else {
                withContext(Dispatchers.Main) { onFailure("Could not delete file.") }
            }
        } catch (e: SecurityException) {
            // Android 10 RecoverableSecurityException path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                    withContext(Dispatchers.Main) { onNeedsPermission(req.intentSender) }
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) { onFailure("Cannot delete: ${e2.message}") }
                }
            } else {
                withContext(Dispatchers.Main) { onFailure("Cannot delete: ${e.message}") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onFailure("Delete failed: ${e.message}") }
        }
    }
}

// ── Real Ringtone setter ──────────────────────────────────────────────────
private suspend fun setRingtone(context: Context, song: Song) = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return@withContext
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        try {
            context.contentResolver.update(song.uri, values, null, null)
        } catch (_: Exception) {
            context.contentResolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values,
                "${MediaStore.Audio.Media._ID}=?", arrayOf(song.id.toString()))
        }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, "Set as ringtone", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { e.printStackTrace() }
}

// Used by ViewModel — kept here for compatibility
suspend fun deleteSong(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        context.contentResolver.delete(song.uri, null, null) > 0
    } catch (_: Exception) { false }
}
