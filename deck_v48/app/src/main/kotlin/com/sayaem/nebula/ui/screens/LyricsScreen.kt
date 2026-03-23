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
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import java.io.File

// ── LRC parser ────────────────────────────────────────────────────────
data class LrcLine(val timeMs: Long, val text: String)

fun parseLrc(filePath: String): List<LrcLine> {
    return try {
        val lrcPath = filePath.replaceAfterLast(".", "lrc")
        val file    = File(lrcPath)
        if (!file.exists()) return emptyList()

        val lines = file.readLines()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        lines.mapNotNull { line ->
            regex.matchEntire(line.trim())?.let { m ->
                val min  = m.groupValues[1].toLong()
                val sec  = m.groupValues[2].toLong()
                val ms   = m.groupValues[3].padEnd(3, '0').take(3).toLong()
                val text = m.groupValues[4].trim()
                if (text.isNotEmpty()) LrcLine((min * 60 + sec) * 1000 + ms, text)
                else null
            }
        }.sortedBy { it.timeMs }
    } catch (_: Exception) { emptyList() }
}

@Composable
fun LyricsSheet(
    song: Song,
    positionMs: Long,
    onDismiss: () -> Unit,
) {
    val lyrics = remember(song.filePath) { parseLrc(song.filePath) }
    val listState = rememberLazyListState()

    // Find currently active line
    val activeIndex = remember(positionMs, lyrics) {
        if (lyrics.isEmpty()) return@remember -1
        var idx = 0
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= positionMs) idx = i else break
        }
        idx
    }

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(
                (activeIndex - 2).coerceAtLeast(0),
                scrollOffset = 0
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(0.7f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalAppColors.current.bgSecondary)
                .clickable(enabled = false) {}
        ) {
            // Handle
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(LocalAppColors.current.border).align(Alignment.CenterHorizontally).padding(top = 0.dp))

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Lyrics", style = MaterialTheme.typography.headlineSmall,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                    Text(song.title, style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSecondary)
                }
                Icon(Icons.Filled.Close, null, tint = LocalAppColors.current.textTertiary,
                    modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

            if (lyrics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicOff, null, tint = LocalAppColors.current.textTertiary,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("No lyrics found", style = MaterialTheme.typography.headlineSmall,
                            color = LocalAppColors.current.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Add a .lrc file with the same name\nas the song in the same folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textTertiary, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lyrics) { i, line ->
                        val isActive = i == activeIndex
                        val scale by animateFloatAsState(
                            targetValue  = if (isActive) 1.05f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label        = "lyric_scale"
                        )
                        Text(
                            text      = line.text,
                            style     = MaterialTheme.typography.bodyLarge,
                            fontSize  = if (isActive) 20.sp else 16.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color     = when {
                                isActive            -> TextPrimaryDark
                                i < activeIndex     -> TextTertiaryDark.copy(alpha = 0.5f)
                                else                -> TextSecondaryDark
                            },
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                                .scale(scale)
                                .alpha(if (i < activeIndex - 3) 0.3f else 1f)
                        )
                    }
                }
            }
        }
    }
}
