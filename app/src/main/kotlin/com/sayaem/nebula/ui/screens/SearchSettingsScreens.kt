package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.SongTile
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors





@Composable
fun SearchScreen(
    query: String, onQueryChange: (String) -> Unit,
    results: List<Song>, currentSong: Song?, isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
) {
    data class Cat(val label: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val cats = listOf(
        Cat("Trending",   NebulaRed,        Icons.Filled.Whatshot),
        Cat("Pop",        NebulaPink,        Icons.Filled.Star),
        Cat("Hip-Hop",    NebulaViolet,      Icons.Filled.Mic),
        Cat("Electronic", NebulaCyan,        Icons.Filled.GraphicEq),
        Cat("Rock",       NebulaAmber,       Icons.Filled.LibraryMusic),
        Cat("Classical",  NebulaGreen,       Icons.Filled.MusicNote),
        Cat("R&B",        NebulaVioletLight, Icons.Filled.Audiotrack),
        Cat("K-Pop",      NebulaPink,        Icons.Filled.Public),
    )

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(52.dp))
        Text("Search", style = MaterialTheme.typography.displaySmall,
            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp)).background(LocalAppColors.current.card)
            .border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.textPrimary),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Songs, artists, albums, videos…",
                        style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textTertiary)
                    inner()
                })
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (query.isEmpty()) {
            Text("Browse categories", style = MaterialTheme.typography.headlineSmall,
                color = LocalAppColors.current.textSecondary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                items(cats) { cat ->
                    Row(modifier = Modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(cat.color.copy(alpha = 0.15f))
                        .border(0.5.dp, cat.color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .clickable { onQueryChange(cat.label) }
                        .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.label, style = MaterialTheme.typography.labelLarge, color = LocalAppColors.current.textPrimary)
                    }
                }
            }
        } else {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.SearchOff, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No results for \"$query\"",
                            style = MaterialTheme.typography.headlineSmall, color = LocalAppColors.current.textTertiary)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                    items(results, key = { it.id }) { song ->
                        SongTile(title = song.title, artist = song.artist,
                            duration = song.durationFormatted,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = { onSongClick(song) })
                    }
                }
            }
        }
    }
}

// ─── Settings Screen ─────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    isDark: Boolean,
    currentUser: com.google.firebase.auth.FirebaseUser? = null,
    isPremium: Boolean = false,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onToggleTheme: () -> Unit,
    onDynColorChanged: ((Boolean) -> Unit)? = null,
    onEqualizerClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onStatsClick: () -> Unit,
    onSleepTimerClick: (() -> Unit)? = null,
    onRescan: (() -> Unit)? = null,
    onDrivingMode: (() -> Unit)? = null,
    onGaplessChanged: ((Boolean) -> Unit)? = null,
    onSmartSkipChanged: ((Boolean) -> Unit)? = null,
    onCrossfadeChanged: ((Float) -> Unit)? = null,
    onVolumeNormChanged: ((Boolean) -> Unit)? = null,
    initialVolumeNorm: Boolean = false,
    initialGapless: Boolean = true,
    initialSmartSkip: Boolean = false,
    initialCrossfade: Float = 0f,
) {
    val context   = LocalContext.current
    var gapless   by remember { mutableStateOf(initialGapless) }
    var smartSkip by remember { mutableStateOf(initialSmartSkip) }
    var dynColor  by remember { mutableStateOf(false) }
    var crossfade  by remember { mutableStateOf(initialCrossfade) }
    var volNorm    by remember { mutableStateOf(initialVolumeNorm) }

    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.height(52.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))
        }

        // Premium banner
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
                .clickable(onClick = onPremiumClick).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DECK PREMIUM", style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(6.dp))
                        Text("Unlock EQ, themes & no ads",
                            style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Upgrade", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Appearance
        // ── Account section ──────────────────────────────────────────────
        item { SSection("Account") }
        item {
            if (currentUser != null && !currentUser.isAnonymous) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(NebulaViolet.copy(0.2f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentUser.displayName ?: "Signed in", style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textTertiary)
                            if (isPremium) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("PREMIUM", style = MaterialTheme.typography.labelSmall, color = NebulaViolet)
                                }
                            }
                        }
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Sign out", color = NebulaRed, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().clickable(onClick = onSignIn)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NebulaViolet.copy(0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Login, null, tint = NebulaViolet, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sign in with Google", style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Sync premium, playlists & favorites across devices",
                            style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }

        item { SSection("Appearance") }
        item { STile("Dark Mode", Icons.Filled.DarkMode, NebulaViolet,
            trailing = { Switch(checked = isDark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Dynamic Colors", Icons.Filled.AutoAwesome, NebulaAmber, "Tint UI from album art",
            trailing = { Switch(checked = dynColor, onCheckedChange = { dynColor = it; onDynColorChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // Playback
        item { SSection("Playback") }
        item { STile("Equalizer", Icons.Filled.Equalizer, NebulaCyan, "10-band EQ",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = onEqualizerClick) }
        item {
            STile("Gapless Playback", Icons.Filled.GraphicEq, NebulaGreen, "No silence between tracks",
                trailing = { Switch(checked = gapless, onCheckedChange = {
                    gapless = it
                    onGaplessChanged?.invoke(it)
                }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) })
        }
        item {
            // Real crossfade slider wired to callback
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(NebulaVioletLight.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Shuffle, null, tint = NebulaVioletLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Crossfade", style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
                            Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s",
                                style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                        }
                    }
                    Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s",
                        style = MaterialTheme.typography.labelMedium, color = NebulaVioletLight)
                }
                Slider(value = crossfade, onValueChange = {
                    crossfade = it
                    onCrossfadeChanged?.invoke(it)
                }, valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(activeTrackColor = NebulaVioletLight,
                        thumbColor = Color.White, inactiveTrackColor = LocalAppColors.current.border),
                    modifier = Modifier.padding(start = 50.dp))
            }
        }
        item { STile("Sleep Timer", Icons.Filled.Timer, NebulaCyan,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onSleepTimerClick?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // Library
        item { SSection("Library") }
        item { STile("Driving Mode", Icons.Filled.DirectionsCar, NebulaGreen,
                "Large controls for safe listening while driving",
                trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
                onClick = { onDrivingMode?.invoke() }) }
        item { STile("Rescan Media", Icons.Filled.Refresh, NebulaGreen, "Find new files on device",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onRescan?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // Smart Features
        item { SSection("Smart Features") }
        item {
            STile("Smart Skip", Icons.Filled.Psychology, NebulaPink,
                if (smartSkip) "Auto-skips songs you repeatedly skip" else "Off — tap to enable",
                trailing = { Switch(checked = smartSkip, onCheckedChange = {
                    smartSkip = it
                    onSmartSkipChanged?.invoke(it)
                }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) })
        }
        item { STile("Listening Stats", Icons.Filled.BarChart, NebulaViolet, "Your Deck Wrapped",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = onStatsClick) }
        item { Spacer(Modifier.height(8.dp)) }

        // About
        item { SSection("About") }
        item { STile("Version", Icons.Filled.Info, NebulaViolet, "Deck v1.0.0") }
        item {
            STile("Rate on Play Store", Icons.Filled.Star, NebulaAmber,
                trailing = { Icon(Icons.Filled.OpenInNew, null, tint = LocalAppColors.current.textTertiary) },
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.sayaem.nebula"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                })
        }
        item {
            STile("Share Deck", Icons.Filled.Share, NebulaGreen,
                trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
                onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Check out Deck — the ultimate media player! Download it on Google Play.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(i, "Share via")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
        }
    }
}

@Composable
fun SSection(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.textTertiary, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
}

@Composable
fun STile(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color, subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
            if (subtitle != null)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
        }
        trailing?.invoke()
    }
}
