package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
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


// ─── Settings Screen — advanced, all real ────────────────────────────────

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
    val prefs     = remember { context.getSharedPreferences("deck_data", android.content.Context.MODE_PRIVATE) }

    // All settings backed by real SharedPreferences
    var gapless      by remember { mutableStateOf(initialGapless) }
    var smartSkip    by remember { mutableStateOf(initialSmartSkip) }
    var crossfade    by remember { mutableStateOf(initialCrossfade) }
    var volNorm      by remember { mutableStateOf(initialVolumeNorm) }
    var fadeOnPause  by remember { mutableStateOf(prefs.getBoolean("fade_on_pause", true)) }
    var showLyrics   by remember { mutableStateOf(prefs.getBoolean("show_lyrics", true)) }
    var haptic       by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
    var notifExpanded by remember { mutableStateOf(prefs.getBoolean("notif_expanded", true)) }
    var showMinDurDialog by remember { mutableStateOf(false) }
    var minDuration  by remember { mutableIntStateOf(prefs.getInt("min_duration_sec", 30)) }

    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.statusBarsPadding().height(8.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }

        // ── Account ──────────────────────────────────────────────────────
        item { SSection("Account") }
        item {
            if (currentUser != null && !currentUser.isAnonymous) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(NebulaViolet.copy(0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentUser.displayName ?: "User", style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textTertiary)
                    }
                    if (isPremium) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("PRO", style = MaterialTheme.typography.labelSmall, color = NebulaViolet, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Sign out", color = NebulaRed, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().clickable(onClick = onSignIn)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(NebulaViolet.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Login, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sign in with Google", style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Sync favorites & playlists across devices",
                            style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary)
                }
            }
        }
        if (!isPremium) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
                    .clickable(onClick = onPremiumClick).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Deck Premium", style = MaterialTheme.typography.titleSmall,
                                color = Color.White, fontWeight = FontWeight.Bold)
                            Text("No ads · Cloud sync · Themes",
                                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
                        }
                        Text("Upgrade →", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Appearance ──────────────────────────────────────────────────
        item { SSection("Appearance") }
        item { STile("Dark Mode", Icons.Filled.DarkMode, NebulaViolet,
            trailing = { Switch(checked = isDark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Playback ─────────────────────────────────────────────────────
        item { SSection("Playback") }
        item { STile("Equalizer", Icons.Filled.Equalizer, NebulaCyan, "10-band EQ + Bass Boost",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = onEqualizerClick) }
        item { STile("Gapless Playback", Icons.Filled.GraphicEq, NebulaGreen, "No silence between tracks",
            trailing = { Switch(checked = gapless, onCheckedChange = { gapless = it; onGaplessChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Volume Normalization", Icons.Filled.VolumeUp, NebulaGreen, "Balance loudness across tracks",
            trailing = { Switch(checked = volNorm, onCheckedChange = { volNorm = it; onVolumeNormChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Fade on Pause/Resume", Icons.Filled.VolumeDown, NebulaVioletLight, "Smooth audio transitions",
            trailing = { Switch(checked = fadeOnPause, onCheckedChange = {
                fadeOnPause = it; prefs.edit().putBoolean("fade_on_pause", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        // Crossfade slider
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(NebulaVioletLight.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CompareArrows, null, tint = NebulaVioletLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Crossfade", style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
                            Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s transition",
                                style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                        }
                    }
                    Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s",
                        style = MaterialTheme.typography.labelMedium, color = NebulaVioletLight)
                }
                Slider(value = crossfade, onValueChange = { crossfade = it; onCrossfadeChanged?.invoke(it) },
                    valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(activeTrackColor = NebulaVioletLight,
                        thumbColor = Color.White, inactiveTrackColor = LocalAppColors.current.border),
                    modifier = Modifier.padding(start = 50.dp))
            }
        }
        item { STile("Sleep Timer", Icons.Filled.Bedtime, NebulaCyan,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onSleepTimerClick?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Library ──────────────────────────────────────────────────────
        item { SSection("Library") }
        item { STile("Rescan Media", Icons.Filled.Refresh, NebulaGreen, "Find new audio & video files",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onRescan?.invoke() }) }
        item { STile("Min Track Duration", Icons.Filled.Timelapse, NebulaAmber,
            "Skip files shorter than ${minDuration}s",
            trailing = { Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaAmber.copy(0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("${minDuration}s", style = MaterialTheme.typography.labelMedium, color = NebulaAmber)
            }},
            onClick = { showMinDurDialog = true }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Notifications ─────────────────────────────────────────────────
        item { SSection("Notifications") }
        item { STile("Media Notification Controls", Icons.Filled.NotificationsActive, NebulaGreen,
            "Show playback controls in notification shade",
            trailing = { Switch(checked = notifExpanded, onCheckedChange = {
                notifExpanded = it; prefs.edit().putBoolean("notif_expanded", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Smart & Extras ────────────────────────────────────────────────
        item { SSection("Smart Features") }
        item { STile("Smart Skip", Icons.Filled.Psychology, NebulaPink,
            if (smartSkip) "Auto-skips songs you keep skipping" else "Off",
            trailing = { Switch(checked = smartSkip, onCheckedChange = { smartSkip = it; onSmartSkipChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Lyrics", Icons.Filled.Lyrics, NebulaCyan, "Synced lyrics in Now Playing",
            trailing = { Switch(checked = showLyrics, onCheckedChange = {
                showLyrics = it; prefs.edit().putBoolean("show_lyrics", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Haptic Feedback", Icons.Filled.Vibration, NebulaViolet, "Vibrate on interactions",
            trailing = { Switch(checked = haptic, onCheckedChange = {
                haptic = it; prefs.edit().putBoolean("haptic", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Listening Stats", Icons.Filled.BarChart, NebulaViolet, "View your listening history",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = onStatsClick) }
        item { STile("Driving Mode", Icons.Filled.DirectionsCar, NebulaGreen, "Large controls for safe driving",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onDrivingMode?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── About ─────────────────────────────────────────────────────────
        item { SSection("About") }
        item { STile("Version", Icons.Filled.Info, NebulaViolet, "Deck v1.0.0") }
        item { STile("Rate on Play Store", Icons.Filled.Star, NebulaAmber,
            trailing = { Icon(Icons.Filled.OpenInNew, null, tint = LocalAppColors.current.textTertiary) },
            onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.sayaem.nebula")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) {}
            }) }
        item { STile("Share Deck", Icons.Filled.Share, NebulaGreen,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Check out Deck — best media player!")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) }
    }

    // Min duration picker
    if (showMinDurDialog) {
        AlertDialog(
            onDismissRequest = { showMinDurDialog = false },
            containerColor = LocalAppColors.current.card,
            title = { Text("Min Track Duration", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(10, 20, 30, 45, 60, 90, 120).forEach { secs ->
                        Row(Modifier.fillMaxWidth().clickable {
                            minDuration = secs; prefs.edit().putInt("min_duration_sec", secs).apply()
                            showMinDurDialog = false
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = minDuration == secs, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = NebulaViolet))
                            Spacer(Modifier.width(10.dp))
                            Text("${secs}s", style = MaterialTheme.typography.bodyMedium,
                                color = if (minDuration == secs) NebulaViolet else LocalAppColors.current.textPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMinDurDialog = false }) { Text("Done", color = NebulaViolet) } }
        )
    }
}

@Composable
fun SSection(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.textTertiary, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
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
