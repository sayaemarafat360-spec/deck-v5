package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun TagEditorScreen(
    song: Song,
    onSave: (title: String, artist: String, album: String) -> Unit,
    onBack: () -> Unit,
) {
    var title  by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album  by remember { mutableStateOf(song.album) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(LocalAppColors.current.bg)) {

        // ── Top bar ──────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = LocalAppColors.current.textPrimary)
            }
            Text(
                "Edit Tags",
                style = MaterialTheme.typography.headlineMedium,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        saving = true
                        onSave(title.trim(), artist.trim(), album.trim())
                    }
                },
                enabled = !saving && title.isNotBlank()
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = NebulaViolet,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Save", color = NebulaViolet,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // ── File info card ────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp)).background(LocalAppColors.current.card)
                .border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(NebulaViolet.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AudioFile, null, tint = NebulaViolet, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.filePath.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    song.sizeFormatted + " · " + song.durationFormatted,
                    style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Tag fields ────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TagField("Title",  title,  { title  = it }, Icons.Filled.Title)
            TagField("Artist", artist, { artist = it }, Icons.Filled.Person)
            TagField("Album",  album,  { album  = it }, Icons.Filled.Album)
        }

        Spacer(Modifier.height(24.dp))

        // ── Info note ─────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NebulaAmber.copy(0.08f))
                .border(0.5.dp, NebulaAmber.copy(0.2f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, null, tint = NebulaAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Changes are written to the device media database. Rescan to refresh all apps.",
                style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSecondary
            )
        }
    }
}

@Composable
private fun TagField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            textStyle     = MaterialTheme.typography.bodyLarge.copy(color = LocalAppColors.current.textPrimary),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = NebulaViolet,
                unfocusedBorderColor = LocalAppColors.current.border,
                focusedTextColor     = LocalAppColors.current.textPrimary,
                unfocusedTextColor   = LocalAppColors.current.textPrimary,
                cursorColor          = NebulaViolet,
            )
        )
    }
}
