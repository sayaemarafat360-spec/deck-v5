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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════
// DISCOVER SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DiscoverScreen(
    songs: List<Song>,
    videos: List<Song>,
    recentlyAdded: List<Song>,
    topSongs: List<Pair<Song, Int>>,
    onSongClick: (Song) -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    onVideoClick: (Song) -> Unit,
    onSearchClick: () -> Unit,
) {
    val appColors = LocalAppColors.current

    // Precompute mood buckets from genre/title heuristics
    val moodBuckets = remember(songs) { buildMoodBuckets(songs) }

    // Artist radio buckets
    val artistBuckets = remember(songs) {
        songs.groupBy { it.artist }
            .filter { it.key.isNotBlank() && it.key != "Unknown Artist" && it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(8)
    }

    // Decade time machine
    val decadeBuckets = remember(songs) {
        songs.filter { it.year > 0 }
            .groupBy { (it.year / 10) * 10 }
            .entries.sortedByDescending { it.key }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(appColors.bg),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {

        // ── Header ─────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Discover", style = MaterialTheme.typography.headlineLarge,
                        color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
                    Text("What's your vibe today?",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.textTertiary)
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, null, tint = appColors.textPrimary,
                        modifier = Modifier.size(24.dp))
                }
            }
            HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)
        }

        // ── 1. Mood Mixes ──────────────────────────────────────────────
        item { DiscoverSectionHeader("Mood Mixes", Icons.Filled.AutoAwesome, NebulaViolet) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(moodBuckets, key = { "mood_${it.name}" }) { mood ->
                    MoodCard(mood = mood, onPlay = { onPlaySongs(mood.songs.shuffled()) })
                }
                // "Surprise me" — always last
                item {
                    MoodCard(
                        mood = MoodBucket(
                            name = "Surprise Me",
                            emoji = "🎲",
                            songs = songs,
                            gradient = listOf(NebulaAmber, NebulaPink)
                        ),
                        onPlay = { onPlaySongs(songs.shuffled()) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── 2. Artist Radio ────────────────────────────────────────────
        if (artistBuckets.isNotEmpty()) {
            item { DiscoverSectionHeader("Artist Radio", Icons.Filled.Radio, NebulaCyan) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(artistBuckets, key = { "artist_${it.key}" }) { (artist, artistSongs) ->
                        ArtistRadioCard(
                            artist   = artist,
                            songs    = artistSongs,
                            onPlay   = { onPlaySongs(artistSongs.shuffled()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── 3. Daily Mix (from top-played + recently played) ───────────
        if (topSongs.isNotEmpty()) {
            item { DiscoverSectionHeader("Daily Mix", Icons.Filled.Today, NebulaGreen) }
            item {
                val dailySongs = remember(topSongs) {
                    topSongs.map { it.first }.take(12)
                }
                DailyMixCard(songs = dailySongs, onPlay = { onPlaySongs(dailySongs) })
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── 4. Top Played Chart ────────────────────────────────────────
        if (topSongs.size >= 3) {
            item { DiscoverSectionHeader("Top Played", Icons.Filled.BarChart, NebulaAmber) }
            items(topSongs.take(5), key = { "top_${it.first.id}" }) { (song, plays) ->
                TopPlayedRow(song = song, plays = plays, rank = topSongs.indexOf(Pair(song, plays)) + 1,
                    onClick = { onSongClick(song) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── 5. Recently Added Spotlight ────────────────────────────────
        if (recentlyAdded.isNotEmpty()) {
            item { DiscoverSectionHeader("Just Added", Icons.Filled.FiberNew, NebulaGreen) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentlyAdded.take(10), key = { "new_${it.id}" }) { song ->
                        RecentlyAddedCard(song = song, onClick = { onSongClick(song) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── 6. Decade Time Machine ─────────────────────────────────────
        if (decadeBuckets.isNotEmpty()) {
            item { DiscoverSectionHeader("Time Machine", Icons.Filled.AccessTime, NebulaRed) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(decadeBuckets, key = { "decade_${it.key}" }) { (decade, decadeSongs) ->
                        DecadeCard(
                            decade = decade,
                            songs  = decadeSongs,
                            onPlay = { onPlaySongs(decadeSongs.shuffled()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── 7. Empty state (no library) ────────────────────────────────
        if (songs.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.LibraryMusic, null,
                        tint = appColors.textTertiary.copy(0.3f),
                        modifier = Modifier.size(64.dp))
                    Text("Your library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.textSecondary,
                        fontWeight = FontWeight.Bold)
                    Text("Add music to your device to start discovering",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textTertiary,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─── Mood buckets ─────────────────────────────────────────────────────────────
data class MoodBucket(
    val name: String,
    val emoji: String,
    val songs: List<Song>,
    val gradient: List<Color>,
)

private fun buildMoodBuckets(songs: List<Song>): List<MoodBucket> {
    fun match(s: Song, vararg keywords: String) =
        keywords.any { k ->
            s.genre.lowercase().contains(k) ||
            s.title.lowercase().contains(k) ||
            s.album.lowercase().contains(k)
        }

    val chill   = songs.filter { match(it, "chill", "lofi", "lo-fi", "ambient", "sleep", "relax", "jazz", "acoustic", "slow") }
    val focus   = songs.filter { match(it, "focus", "study", "classical", "instrumental", "piano", "orchestra") }
    val hype    = songs.filter { match(it, "hype", "workout", "gym", "energy", "power", "bass", "edm", "dance", "rap", "hip-hop", "rock", "metal") }
    val vibes   = songs.filter { match(it, "rnb", "r&b", "soul", "neo", "smooth", "groove") }
    val party   = songs.filter { match(it, "party", "pop", "club", "dance", "disco") }

    // Fallback pools if heuristics yield nothing
    val fallback = songs.shuffled()

    return buildList {
        if ((chill.ifEmpty { fallback.take(8) }).isNotEmpty())
            add(MoodBucket("Chill", "🌙", chill.ifEmpty { fallback.take(8) },
                listOf(NebulaCyan, Color(0xFF0A3D62))))
        if ((focus.ifEmpty { fallback.drop(8).take(8) }).isNotEmpty())
            add(MoodBucket("Focus", "🎯", focus.ifEmpty { fallback.drop(8).take(8) },
                listOf(NebulaViolet, Color(0xFF1A0035))))
        if ((hype.ifEmpty { fallback.drop(16).take(8) }).isNotEmpty())
            add(MoodBucket("Hype", "⚡", hype.ifEmpty { fallback.drop(16).take(8) },
                listOf(NebulaRed, NebulaAmber)))
        if ((vibes.ifEmpty { fallback.drop(24).take(8) }).isNotEmpty())
            add(MoodBucket("Vibes", "✨", vibes.ifEmpty { fallback.drop(24).take(8) },
                listOf(NebulaPink, NebulaViolet)))
        if ((party.ifEmpty { fallback.drop(32).take(8) }).isNotEmpty())
            add(MoodBucket("Party", "🎉", party.ifEmpty { fallback.drop(32).take(8) },
                listOf(NebulaAmber, NebulaGreen)))
    }
}

// ─── Section header ──────────────────────────────────────────────────────────
@Composable
private fun DiscoverSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(color.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
    }
}

// ─── Mood card ───────────────────────────────────────────────────────────────
@Composable
private fun MoodCard(mood: MoodBucket, onPlay: () -> Unit) {
    Box(
        Modifier
            .width(140.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(mood.gradient))
            .clickable(onClick = onPlay)
    ) {
        // Art collage background (up to 4 thumbnails)
        val arts = mood.songs.filter { it.albumArtUri != null }.take(4)
        if (arts.isNotEmpty()) {
            val gridSize = 70.dp
            Box(Modifier.align(Alignment.CenterEnd).size(gridSize).alpha(0.35f)) {
                arts.take(4).forEachIndexed { i, song ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(gridSize / 2)
                            .offset(
                                x = if (i % 2 == 0) 0.dp else gridSize / 2,
                                y = if (i < 2) 0.dp else gridSize / 2
                            )
                    )
                }
            }
        }
        // Gradient overlay left-to-right so text is always readable
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                listOf(mood.gradient.first().copy(0.95f), Color.Transparent)
            )
        ))
        Column(
            Modifier.align(Alignment.CenterStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(mood.emoji, style = MaterialTheme.typography.titleLarge)
            Text(mood.name, style = MaterialTheme.typography.titleSmall,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("${mood.songs.size} songs",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.7f))
        }
    }
}

// ─── Artist radio card ────────────────────────────────────────────────────────
@Composable
private fun ArtistRadioCard(artist: String, songs: List<Song>, onPlay: () -> Unit) {
    val appColors = LocalAppColors.current
    val cover     = songs.firstOrNull { it.albumArtUri != null }
    // Pick a deterministic accent color from the artist name
    val accentColors = listOf(NebulaViolet, NebulaCyan, NebulaAmber, NebulaGreen, NebulaPink, NebulaRed)
    val accent = accentColors[abs(artist.hashCode()) % accentColors.size]

    Column(
        Modifier.width(90.dp).clickable(onClick = onPlay),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(90.dp).clip(CircleShape)
                .background(accent.copy(0.18f))
                .border(1.5.dp, accent.copy(0.4f), CircleShape)
        ) {
            if (cover?.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(cover.albumArtUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, null, tint = accent,
                    modifier = Modifier.size(36.dp).align(Alignment.Center))
            }
            // Radio badge
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .size(20.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Radio, null, tint = Color.White,
                    modifier = Modifier.size(11.dp))
            }
        }
        Text(artist, style = MaterialTheme.typography.labelSmall,
            color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        Text("${songs.size} tracks",
            style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary)
    }
}

// ─── Daily mix card ──────────────────────────────────────────────────────────
@Composable
private fun DailyMixCard(songs: List<Song>, onPlay: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(NebulaGreen.copy(0.18f), NebulaCyan.copy(0.12f))))
            .border(0.5.dp, NebulaGreen.copy(0.2f), RoundedCornerShape(20.dp))
            .clickable(onClick = onPlay)
    ) {
        // Thumbnail strip
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy((-16).dp)
        ) {
            songs.filter { it.albumArtUri != null }.take(5).reversed().forEachIndexed { i, song ->
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, appColors.bg, CircleShape)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            Text("Daily Mix", style = MaterialTheme.typography.titleMedium,
                color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold)
            Text("${songs.size} songs · Built for you",
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NebulaGreen)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                        modifier = Modifier.size(14.dp))
                    Text("Play", style = MaterialTheme.typography.labelMedium,
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Top played row ───────────────────────────────────────────────────────────
@Composable
private fun TopPlayedRow(song: Song, plays: Int, rank: Int, onClick: () -> Unit) {
    val appColors  = LocalAppColors.current
    val rankColors = listOf(NebulaAmber, Color(0xFFC0C0C0), Color(0xFFCD7F32))
    val rankColor  = rankColors.getOrElse(rank - 1) { appColors.textTertiary }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("$rank", style = MaterialTheme.typography.titleSmall,
            color = rankColor, fontWeight = FontWeight.Black,
            modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
        MusicArtBox(song = song, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = appColors.textTertiary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$plays", style = MaterialTheme.typography.labelLarge,
                color = NebulaAmber, fontWeight = FontWeight.Bold)
            Text("plays", style = MaterialTheme.typography.labelSmall,
                color = appColors.textTertiary)
        }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp),
        color = appColors.borderSubtle, thickness = 0.5.dp)
}

// ─── Recently added card ─────────────────────────────────────────────────────
@Composable
private fun RecentlyAddedCard(song: Song, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(
        Modifier.width(90.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)).background(appColors.card)) {
            MusicArtBox(song = song, size = 90.dp)
            // "New" badge
            Box(
                Modifier.align(Alignment.TopStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NebulaGreen)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("NEW", style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Black,
                    fontSize = 8.sp)
            }
        }
        Text(song.title, style = MaterialTheme.typography.labelSmall,
            color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium)
        Text(song.artist, style = MaterialTheme.typography.labelSmall,
            color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Decade card ──────────────────────────────────────────────────────────────
@Composable
private fun DecadeCard(decade: Int, songs: List<Song>, onPlay: () -> Unit) {
    val decadeColors = mapOf(
        1950 to listOf(Color(0xFF6B4C3B), Color(0xFF3E2723)),
        1960 to listOf(Color(0xFF7B5EA7), Color(0xFF311B92)),
        1970 to listOf(Color(0xFFBF8040), Color(0xFF4E342E)),
        1980 to listOf(Color(0xFFE040FB), Color(0xFF1A237E)),
        1990 to listOf(Color(0xFF00BCD4), Color(0xFF1B5E20)),
        2000 to listOf(Color(0xFFFF5722), Color(0xFF880E4F)),
        2010 to listOf(Color(0xFF2196F3), Color(0xFF0D47A1)),
        2020 to listOf(NebulaViolet, NebulaCyan),
    )
    val gradient = decadeColors.getOrElse(decade) { listOf(NebulaViolet, NebulaCyan) }

    Box(
        Modifier
            .width(110.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("${decade}s", style = MaterialTheme.typography.titleLarge,
                color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text("${songs.size} songs",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.75f))
        }
    }
}
