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
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

// ═══════════════════════════════════════════════════════════════════════════
// AUDIO OPTIONS SHEET — music-specific actions
// ═══════════════════════════════════════════════════════════════════════════
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

    var showFileInfo        by remember { mutableStateOf(false) }
    var showPlaylistPicker  by remember { mutableStateOf(false) }
    var showDeleteConfirm   by remember { mutableStateOf(false) }
    var newPlaylistName     by remember { mutableStateOf("") }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onDelete()
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
        }
        showDeleteConfirm = false
        onDismiss()
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.bgSecondary)
                .clickable(enabled = false) {}
                .navigationBarsPadding()
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(colors.border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            // Header — static music thumbnail (#5 fix)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MusicArtBox(song = song, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary, maxLines = 1)
                }
            }

            HorizontalDivider(color = colors.border, thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // Audio-specific options
            SheetOption(Icons.Filled.PlayArrow,           "Play Now",             NebulaViolet) { onPlayNow(); onDismiss() }
            SheetOption(Icons.Filled.SkipNext,            "Play Next",            NebulaViolet) { onPlayNext(); Toast.makeText(context,"Playing next",Toast.LENGTH_SHORT).show(); onDismiss() }
            SheetOption(Icons.Filled.AddToQueue,          "Add to Queue",         NebulaViolet) { onAddToQueue(); Toast.makeText(context,"Added to queue",Toast.LENGTH_SHORT).show(); onDismiss() }
            SheetOption(Icons.Filled.PlaylistAdd,         "Add to Playlist",      NebulaCyan)   { showPlaylistPicker = true }
            SheetOption(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Remove Favorite" else "Add to Favorites",
                NebulaPink
            ) { onToggleFavorite(); onDismiss() }
            SheetOption(Icons.Filled.NotificationsActive, "Set as Ringtone",      NebulaGreen)  { scope.launch { setRingtone(context, song) }; onDismiss() }
            SheetOption(Icons.Filled.Edit,                "Edit Tags",            NebulaAmber)  { onEditTags(); onDismiss() }
            SheetOption(Icons.Filled.Share,               "Share",                NebulaCyan)   { onShare(); onDismiss() }
            SheetOption(Icons.Filled.Info,                "File Info",            TextSecondaryDark) { showFileInfo = true }
            SheetOption(Icons.Filled.Delete,              "Delete from Device",   NebulaRed)    { showDeleteConfirm = true }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPlaylistPicker) PlaylistPickerSheet(song, playlists, newPlaylistName,
        onNameChange = { newPlaylistName = it },
        onCreatePlaylist = { onCreateAndAddPlaylist(it); Toast.makeText(context,"Added to playlist",Toast.LENGTH_SHORT).show(); showPlaylistPicker = false; onDismiss() },
        onAddToExisting = { pid -> onAddToPlaylist(pid); Toast.makeText(context,"Added to playlist",Toast.LENGTH_SHORT).show(); showPlaylistPicker = false; onDismiss() },
        onDismiss = { showPlaylistPicker = false }
    )

    if (showFileInfo) FileInfoDialog(song) { showFileInfo = false }

    if (showDeleteConfirm) DeleteConfirmDialog(song,
        onConfirm = {
            scope.launch { performDelete(context, song,
                onSuccess = { onDelete(); showDeleteConfirm = false; onDismiss() },
                onNeedsPermission = { sender -> deleteIntentLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
                onFailure = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show(); showDeleteConfirm = false }
            )}
        },
        onDismiss = { showDeleteConfirm = false }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// VIDEO OPTIONS SHEET — video-specific actions (Fix #4)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun VideoOptionsSheet(
    video: Song,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val colors  = LocalAppColors.current

    var showFileInfo      by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onDelete()
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
        }
        showDeleteConfirm = false
        onDismiss()
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.bgSecondary)
                .clickable(enabled = false) {}
                .navigationBarsPadding()
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(colors.border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            // Header — video thumbnail
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(NebulaRed.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.VideoFile, null, tint = NebulaRed, modifier = Modifier.size(26.dp))
                    if (video.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(video.albumArtUri).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(video.durationFormatted, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        Text(video.sizeFormatted, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                    }
                }
            }

            HorizontalDivider(color = colors.border, thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // Video-specific options
            SheetOption(Icons.Filled.PlayArrow,    "Play Video",           NebulaRed)   { onPlayNow(); onDismiss() }
            SheetOption(Icons.Filled.Share,        "Share",                NebulaCyan)  { onShare(); onDismiss() }
            SheetOption(Icons.Filled.Info,         "File Info",            TextSecondaryDark) { showFileInfo = true }
            SheetOption(Icons.Filled.ContentCopy,  "Copy Path",            NebulaViolet) {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", video.filePath))
                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            SheetOption(Icons.Filled.OpenInNew,    "Open With",            NebulaAmber) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(video.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { Toast.makeText(context, "No app found", Toast.LENGTH_SHORT).show() }
                onDismiss()
            }
            SheetOption(Icons.Filled.Delete,       "Delete from Device",   NebulaRed)   { showDeleteConfirm = true }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showFileInfo) FileInfoDialog(video) { showFileInfo = false }

    if (showDeleteConfirm) DeleteConfirmDialog(video,
        onConfirm = {
            scope.launch { performDelete(context, video,
                onSuccess = { onDelete(); showDeleteConfirm = false; onDismiss() },
                onNeedsPermission = { sender -> deleteIntentLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
                onFailure = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show(); showDeleteConfirm = false }
            )}
        },
        onDismiss = { showDeleteConfirm = false }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Fix #5 — Music Art Box: static gradient + album art overlay
// Displayed everywhere audio art is shown — consistent, never blank
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun MusicArtBox(song: Song, size: Dp = 0.dp, modifier: Modifier = Modifier) {
    val colors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaAmber, NebulaGreen)
    val color  = colors[(song.id % colors.size).toInt().let { if (it < 0) -it else it }]
    // When size == 0.dp, the modifier is expected to define the dimensions (e.g. fillMaxWidth)
    val radius = if (size > 0.dp) size * 0.25f else 14.dp
    val sizeModifier = if (size > 0.dp) modifier.size(size) else modifier
    Box(
        sizeModifier.clip(RoundedCornerShape(radius))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(color.copy(0.35f), color.copy(0.12f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Static music note always rendered first
        Icon(Icons.Filled.MusicNote, null, tint = color.copy(0.7f),
            modifier = Modifier.size(size * 0.45f))
        // Real album art on top — transparent on error so gradient shows through
        if (song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(radius))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared bottom-sheet components
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun PlaylistPickerSheet(
    song: Song,
    playlists: List<Playlist>,
    newPlaylistName: String,
    onNameChange: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddToExisting: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.bgSecondary)
                .clickable(enabled = false) {}
                .padding(20.dp).navigationBarsPadding()
        ) {
            Text("Add to Playlist", style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPlaylistName, onValueChange = onNameChange,
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("New playlist name", color = colors.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = NebulaViolet, unfocusedBorderColor = colors.border)
                )
                Button(onClick = { if (newPlaylistName.isNotBlank()) onCreatePlaylist(newPlaylistName.trim()) },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)) { Text("Create") }
            }
            if (playlists.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Existing", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(playlists) { pl ->
                        Row(Modifier.fillMaxWidth().clickable { onAddToExisting(pl.id) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QueueMusic, null, tint = NebulaPink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                Text("${pl.songCount} songs", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                            }
                            Icon(Icons.Filled.Add, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = colors.borderSubtle, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = NebulaViolet) } }
    )
}

@Composable
private fun DeleteConfirmDialog(song: Song, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.card,
        title = { Text("Delete?", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("\"${song.title}\" will be permanently deleted from your device.",
            color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = NebulaRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LocalAppColors.current.textSecondary) } }
    )
}

@Composable
fun SheetOption(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
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
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.textPrimary, modifier = Modifier.weight(1f))
    }
}

// ── Real delete with Android 11+ permission request ──────────────────────
private suspend fun performDelete(
    context: Context, song: Song,
    onSuccess: () -> Unit,
    onNeedsPermission: (android.content.IntentSender) -> Unit,
    onFailure: (String) -> Unit,
) = withContext(Dispatchers.Main) {
    withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(song.uri, null, null)
            if (deleted > 0) { withContext(Dispatchers.Main) { onSuccess() }; return@withContext }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                withContext(Dispatchers.Main) { onNeedsPermission(req.intentSender) }
            } else {
                withContext(Dispatchers.Main) { onFailure("Could not delete file.") }
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                    withContext(Dispatchers.Main) { onNeedsPermission(req.intentSender) }
                } catch (e2: Exception) { withContext(Dispatchers.Main) { onFailure("Cannot delete: ${e2.message}") } }
            } else { withContext(Dispatchers.Main) { onFailure("Cannot delete: ${e.message}") } }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onFailure("Delete failed: ${e.message}") } }
    }
}

// ── Real ringtone setter ──────────────────────────────────────────────────
private suspend fun setRingtone(context: Context, song: Song) = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return@withContext
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        try { context.contentResolver.update(song.uri, values, null, null) }
        catch (_: Exception) { context.contentResolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values,
            "${MediaStore.Audio.Media._ID}=?", arrayOf(song.id.toString())) }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
        withContext(Dispatchers.Main) { Toast.makeText(context, "Set as ringtone ✓", Toast.LENGTH_SHORT).show() }
    } catch (e: Exception) { e.printStackTrace() }
}

suspend fun deleteSong(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
    return@withContext try { context.contentResolver.delete(song.uri, null, null) > 0 }
    catch (_: Exception) { false }
}
