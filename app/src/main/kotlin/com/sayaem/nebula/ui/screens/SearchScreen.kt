package com.sayaem.nebula.ui.screens

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

private const val PREFS_KEY        = "sonix_search_prefs"
private const val HISTORY_KEY      = "search_history"
private const val MAX_HISTORY      = 10

private fun loadHistory(ctx: Context): List<String> {
    val raw = ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        .getString(HISTORY_KEY, "") ?: ""
    return if (raw.isBlank()) emptyList()
    else raw.split("|||").filter { it.isNotBlank() }
}

private fun saveHistory(ctx: Context, query: String, existing: List<String>) {
    val updated = (listOf(query.trim()) + existing.filter { it != query.trim() })
        .take(MAX_HISTORY)
    ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        .edit().putString(HISTORY_KEY, updated.joinToString("|||")).apply()
}

private fun clearHistory(ctx: Context) {
    ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        .edit().remove(HISTORY_KEY).apply()
}

// ── Filter chip labels ─────────────────────────────────────────────────────
private val FILTERS = listOf("All", "Songs", "Videos", "Folders")

// ── Smart suggestions (shown on empty state) ──────────────────────────────
private val SUGGESTIONS = listOf(
    "Top played today", "Recently added", "Favorites",
    "Videos by folder", "Longest songs", "Shortest tracks"
)

// ═══════════════════════════════════════════════════════════════════════════
// MAIN SEARCH SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun SearchScreen(
    songs: List<Song>,
    videos: List<Song>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onMoreSong: (Song) -> Unit,
    onMoreVideo: (Song) -> Unit,
    onDismiss: () -> Unit,
) {
    val context       = LocalContext.current
    val appColors     = LocalAppColors.current
    val keyboard      = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var query         by remember { mutableStateOf("") }
    var activeFilter  by remember { mutableIntStateOf(0) }   // 0=All 1=Songs 2=Videos 3=Folders
    var history       by remember { mutableStateOf(loadHistory(context)) }

    // Voice search launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) query = spoken
    }

    // Auto-focus + keyboard on open
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
        keyboard?.show()
    }

    // Compute results
    val allMedia  = remember(songs, videos) { songs + videos }
    val folders   = remember(allMedia) {
        allMedia.groupBy {
            it.filePath.substringBeforeLast("/").substringAfterLast("/")
        }.entries.toList()
    }

    data class SearchResults(
        val songs: List<Song>,
        val videos: List<Song>,
        val folders: List<Map.Entry<String, List<Song>>>,
    )

    val results = remember(query, activeFilter, allMedia, folders) {
        if (query.isBlank()) return@remember SearchResults(emptyList(), emptyList(), emptyList())
        val q = query.trim().lowercase()
        val matchedSongs  = songs.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
        val matchedVideos = videos.filter {
            it.title.lowercase().contains(q) ||
            it.filePath.lowercase().contains(q)
        }
        val matchedFolders = folders.filter { it.key.lowercase().contains(q) }
        when (activeFilter) {
            1 -> SearchResults(matchedSongs, emptyList(), emptyList())
            2 -> SearchResults(emptyList(), matchedVideos, emptyList())
            3 -> SearchResults(emptyList(), emptyList(), matchedFolders)
            else -> SearchResults(matchedSongs, matchedVideos, matchedFolders)
        }
    }

    val totalResults = results.songs.size + results.videos.size + results.folders.size

    // ─── Commit query to history on search
    fun commitQuery(q: String) {
        if (q.isBlank()) return
        history = (listOf(q.trim()) + history.filter { it != q.trim() }).take(MAX_HISTORY)
        saveHistory(context, q, history)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appColors.bg)
    ) {

        // ── Search bar ─────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Back
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, null, tint = appColors.textPrimary,
                    modifier = Modifier.size(22.dp))
            }

            // Input field
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(appColors.card)
                    .border(1.dp,
                        if (query.isNotBlank()) NebulaViolet.copy(0.5f) else appColors.borderSubtle,
                        RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = appColors.textPrimary
                    ),
                    cursorBrush = SolidColor(NebulaViolet),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { commitQuery(query); keyboard?.hide() }
                    ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search songs, videos, folders…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = appColors.textTertiary)
                        }
                        inner()
                    }
                )
            }

            // Clear button (visible when typing)
            AnimatedVisibility(query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, null, tint = appColors.textTertiary,
                        modifier = Modifier.size(20.dp))
                }
            }

            // Voice search
            IconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you're looking for…")
                        }
                        voiceLauncher.launch(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Mic, null,
                    tint = if (query.isBlank()) NebulaViolet else appColors.textTertiary,
                    modifier = Modifier.size(22.dp))
            }
        }

        // ── Filter chips ───────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            itemsIndexed(FILTERS) { idx, label ->
                val selected = activeFilter == idx
                val colors = listOf(NebulaViolet, NebulaViolet, NebulaRed, NebulaCyan, NebulaAmber)
                val chipColor = colors.getOrElse(idx) { NebulaViolet }
                FilterChip(
                    selected = selected,
                    onClick  = { activeFilter = idx },
                    label    = { Text(label, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(0.18f),
                        selectedLabelColor     = chipColor,
                        containerColor         = appColors.card,
                        labelColor             = appColors.textTertiary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled  = true,
                        selected = selected,
                        selectedBorderColor = chipColor.copy(0.4f),
                        borderColor = appColors.borderSubtle
                    )
                )
            }
        }

        HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)

        // ── Content area ───────────────────────────────────────────────
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {

            // EMPTY STATE — no query yet
            if (query.isBlank()) {
                // Recent history
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp, top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent searches",
                                style = MaterialTheme.typography.labelMedium,
                                color = appColors.textTertiary,
                                fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {
                                clearHistory(context); history = emptyList()
                            }) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall,
                                    color = appColors.textTertiary)
                            }
                        }
                    }
                    items(history, key = { "hist_$it" }) { term ->
                        SearchHistoryRow(
                            term    = term,
                            onClick = { query = term },
                            onDelete = {
                                history = history.filter { it != term }
                                clearHistory(context)
                                history.forEach { saveHistory(context, it, emptyList()) }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Suggestions
                item {
                    Text("Suggestions",
                        style = MaterialTheme.typography.labelMedium,
                        color = appColors.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                items(SUGGESTIONS, key = { "sug_$it" }) { suggestion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { query = suggestion }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.TrendingUp, null,
                            tint = NebulaViolet.copy(0.6f),
                            modifier = Modifier.size(18.dp))
                        Text(suggestion, style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textSecondary)
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 52.dp),
                        color = appColors.borderSubtle, thickness = 0.5.dp
                    )
                }
                return@LazyColumn
            }

            // RESULTS STATE
            if (totalResults == 0) {
                item { SearchNoResults(query) }
                return@LazyColumn
            }

            // Results count header
            item {
                Text(
                    "$totalResults result${if (totalResults != 1) "s" else ""} for \"$query\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = appColors.textTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            // Songs section
            if (results.songs.isNotEmpty()) {
                item {
                    SearchSectionHeader("Songs", Icons.Filled.MusicNote, NebulaViolet,
                        results.songs.size)
                }
                items(results.songs, key = { "sr_song_${it.id}" }) { song ->
                    SearchSongRow(
                        song        = song,
                        query       = query,
                        onClick     = { commitQuery(query); onSongClick(song) },
                        onMoreClick = { onMoreSong(song) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Videos section
            if (results.videos.isNotEmpty()) {
                item {
                    SearchSectionHeader("Videos", Icons.Filled.VideoLibrary, NebulaRed,
                        results.videos.size)
                }
                items(results.videos, key = { "sr_vid_${it.id}" }) { video ->
                    SearchVideoRow(
                        video       = video,
                        query       = query,
                        onClick     = { commitQuery(query); onVideoClick(video) },
                        onMoreClick = { onMoreVideo(video) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Folders section
            if (results.folders.isNotEmpty()) {
                item {
                    SearchSectionHeader("Folders", Icons.Filled.FolderOpen, NebulaCyan,
                        results.folders.size)
                }
                items(results.folders, key = { "sr_folder_${it.key}" }) { (name, items) ->
                    SearchFolderRow(
                        name    = name,
                        count   = items.size,
                        query   = query,
                        onClick = { commitQuery(query) }
                    )
                }
            }
        }
    }
}

// ─── Section header ─────────────────────────────────────────────────────────
@Composable
private fun SearchSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    count: Int,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", style = MaterialTheme.typography.labelSmall,
                color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Song result row ─────────────────────────────────────────────────────────
@Composable
private fun SearchSongRow(
    song: Song, query: String, onClick: () -> Unit, onMoreClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MusicArtBox(song = song, size = 46.dp)
        Column(Modifier.weight(1f)) {
            HighlightedText(song.title, query, appColors.textPrimary, appColors.textTertiary,
                MaterialTheme.typography.bodyMedium)
            Text("${song.artist}  ·  ${song.durationFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(28.dp).clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary,
                modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 80.dp),
        color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ─── Video result row ─────────────────────────────────────────────────────────
@Composable
private fun SearchVideoRow(
    video: Song, query: String, onClick: () -> Unit, onMoreClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(72.dp, 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
        ) {
            if (video.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.albumArtUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(0.7f),
                modifier = Modifier.size(18.dp).align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            HighlightedText(video.title, query, appColors.textPrimary, appColors.textTertiary,
                MaterialTheme.typography.bodyMedium)
            Text(video.durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary)
        }
        Box(Modifier.size(28.dp).clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, null, tint = appColors.textTertiary,
                modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 106.dp),
        color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ─── Folder result row ─────────────────────────────────────────────────────
@Composable
private fun SearchFolderRow(
    name: String, count: Int, query: String, onClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                .background(NebulaCyan.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = NebulaCyan,
                modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            HighlightedText(name, query, appColors.textPrimary, appColors.textTertiary,
                MaterialTheme.typography.bodyMedium)
            Text("$count item${if (count != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary,
            modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(Modifier.padding(start = 80.dp),
        color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ─── History row ────────────────────────────────────────────────────────────
@Composable
private fun SearchHistoryRow(term: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Filled.History, null, tint = appColors.textTertiary,
            modifier = Modifier.size(18.dp))
        Text(term, style = MaterialTheme.typography.bodyMedium,
            color = appColors.textSecondary, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Tap to delete this entry
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = appColors.textTertiary,
                modifier = Modifier.size(14.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 52.dp),
        color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ─── No results state ────────────────────────────────────────────────────────
@Composable
private fun SearchNoResults(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.SearchOff, null,
            tint = LocalAppColors.current.textTertiary.copy(0.4f),
            modifier = Modifier.size(56.dp))
        Text("No results for \"$query\"",
            style = MaterialTheme.typography.titleSmall,
            color = LocalAppColors.current.textSecondary,
            fontWeight = FontWeight.SemiBold)
        Text("Try a different spelling or search by artist, album, or folder name.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ─── Highlighted text (bolds the matched portion) ────────────────────────────
@Composable
private fun HighlightedText(
    text: String,
    query: String,
    textColor: Color,
    highlightColor: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    if (query.isBlank()) {
        Text(text, style = style, color = textColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val lower  = text.lowercase()
    val qLower = query.trim().lowercase()
    val start  = lower.indexOf(qLower)
    if (start < 0) {
        Text(text, style = style, color = textColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val end = start + qLower.length
    val annotated = buildAnnotatedString {
        if (start > 0) append(
            androidx.compose.ui.text.AnnotatedString(
                text.substring(0, start),
                spanStyles = listOf(
                    androidx.compose.ui.text.AnnotatedString.Range(
                        androidx.compose.ui.text.SpanStyle(color = textColor), 0, start
                    )
                )
            )
        )
        append(
            androidx.compose.ui.text.AnnotatedString(
                text.substring(start, end),
                spanStyles = listOf(
                    androidx.compose.ui.text.AnnotatedString.Range(
                        androidx.compose.ui.text.SpanStyle(
                            color = NebulaViolet,
                            fontWeight = FontWeight.Bold
                        ), 0, end - start
                    )
                )
            )
        )
        if (end < text.length) append(
            androidx.compose.ui.text.AnnotatedString(
                text.substring(end),
                spanStyles = listOf(
                    androidx.compose.ui.text.AnnotatedString.Range(
                        androidx.compose.ui.text.SpanStyle(color = textColor), 0, text.length - end
                    )
                )
            )
        )
    }
    Text(annotated, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
