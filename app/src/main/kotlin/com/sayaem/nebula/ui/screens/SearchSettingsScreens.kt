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
    var gapless   by remember { mutableStateOf(initialGapless) }
    var smartSkip by remember { mutableStateOf(initialSmartSkip) }
    var dynColor  by remember { mutableStateOf(false) }
    var crossfade by remember { mutableStateOf(initialCrossfade) }
    var volNorm   by remember { mutableStateOf(initialVolumeNorm) }

    // Advanced real settings backed by SharedPreferences
    val prefs = remember { context.getSharedPreferences("deck_data", android.content.Context.MODE_PRIVATE) }
    var fadeOnPause     by remember { mutableStateOf(prefs.getBoolean("fade_on_pause", true)) }
    var replayGain      by remember { mutableStateOf(prefs.getBoolean("replay_gain", false)) }
    var skipSilence     by remember { mutableStateOf(prefs.getBoolean("skip_silence", false)) }
    var showLyrics      by remember { mutableStateOf(prefs.getBoolean("show_lyrics", true)) }
    var notifExpanded   by remember { mutableStateOf(prefs.getBoolean("notif_expanded", true)) }
    var lockScreenArt   by remember { mutableStateOf(prefs.getBoolean("lockscreen_art", true)) }
    var hapticFeedback  by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
    var highResArt      by remember { mutableStateOf(prefs.getBoolean("high_res_art", true)) }
    var minDuration     by remember { mutableIntStateOf(prefs.getInt("min_duration_sec", 30)) }
    var showMinDurationPicker by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.height(52.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))
        }

        // ── Premium banner ────────────────────────────────────────────
        item {
            if (!isPremium) {
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
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NebulaViolet.copy(alpha = 0.12f))
                    .border(1.dp, NebulaViolet.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = NebulaAmber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Premium Active", style = MaterialTheme.typography.titleSmall,
                            color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Account ────────────────────────────────────────────────────
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
                        Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textTertiary)
                    }
                    if (isPremium) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("PRO", style = MaterialTheme.typography.labelSmall, color = NebulaViolet)
                        }
                        Spacer(Modifier.width(8.dp))
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
                        Text("Sync premium, playlists & favorites",
                            style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Appearance ─────────────────────────────────────────────────
        item { SSection("Appearance") }
        item { STile("Dark Mode", Icons.Filled.DarkMode, NebulaViolet,
            trailing = { Switch(checked = isDark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Dynamic Colors", Icons.Filled.AutoAwesome, NebulaAmber, "Tint UI from album art",
            trailing = { Switch(checked = dynColor, onCheckedChange = { dynColor = it; onDynColorChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("High-Res Album Art", Icons.Filled.Image, NebulaCyan, "Load full-resolution covers",
            trailing = { Switch(checked = highResArt, onCheckedChange = {
                highResArt = it; prefs.edit().putBoolean("high_res_art", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Playback ────────────────────────────────────────────────────
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
        item { STile("Fade on Pause", Icons.Filled.VolumeDown, NebulaVioletLight, "Smoothly fade audio when pausing",
            trailing = { Switch(checked = fadeOnPause, onCheckedChange = {
                fadeOnPause = it; prefs.edit().putBoolean("fade_on_pause", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Skip Silence", Icons.Filled.FastForward, NebulaCyan, "Auto-skip silent segments",
            trailing = { Switch(checked = skipSilence, onCheckedChange = {
                skipSilence = it; prefs.edit().putBoolean("skip_silence", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("ReplayGain", Icons.Filled.Tune, NebulaAmber, "Use embedded gain tags for leveling",
            trailing = { Switch(checked = replayGain, onCheckedChange = {
                replayGain = it; prefs.edit().putBoolean("replay_gain", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        // Real crossfade slider
        item {
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
                Slider(value = crossfade, onValueChange = { crossfade = it; onCrossfadeChanged?.invoke(it) },
                    valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(activeTrackColor = NebulaVioletLight,
                        thumbColor = Color.White, inactiveTrackColor = LocalAppColors.current.border),
                    modifier = Modifier.padding(start = 50.dp))
            }
        }
        item { STile("Sleep Timer", Icons.Filled.Timer, NebulaCyan,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onSleepTimerClick?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Library ─────────────────────────────────────────────────────
        item { SSection("Library") }
        item { STile("Rescan Media", Icons.Filled.Refresh, NebulaGreen, "Find new files on device",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onRescan?.invoke() }) }
        // Minimum track duration — real pref backed setting
        item {
            STile("Min Track Duration", Icons.Filled.Timelapse, NebulaAmber,
                "Skip files shorter than ${minDuration}s",
                trailing = {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaAmber.copy(0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("${minDuration}s", style = MaterialTheme.typography.labelMedium, color = NebulaAmber)
                    }
                },
                onClick = { showMinDurationPicker = true })
        }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Notifications ───────────────────────────────────────────────
        item { SSection("Notifications") }
        item { STile("Expanded Notification", Icons.Filled.NotificationsActive, NebulaGreen,
            "Show controls in notification shade",
            trailing = { Switch(checked = notifExpanded, onCheckedChange = {
                notifExpanded = it; prefs.edit().putBoolean("notif_expanded", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Lock Screen Artwork", Icons.Filled.Lock, NebulaCyan, "Display album art on lock screen",
            trailing = { Switch(checked = lockScreenArt, onCheckedChange = {
                lockScreenArt = it; prefs.edit().putBoolean("lockscreen_art", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Smart Features ──────────────────────────────────────────────
        item { SSection("Smart Features") }
        item { STile("Smart Skip", Icons.Filled.Psychology, NebulaPink,
            if (smartSkip) "Auto-skips songs you repeatedly skip" else "Off — tap to enable",
            trailing = { Switch(checked = smartSkip, onCheckedChange = { smartSkip = it; onSmartSkipChanged?.invoke(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Lyrics", Icons.Filled.Lyrics, NebulaCyan, "Show synced lyrics on Now Playing",
            trailing = { Switch(checked = showLyrics, onCheckedChange = {
                showLyrics = it; prefs.edit().putBoolean("show_lyrics", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Haptic Feedback", Icons.Filled.Vibration, NebulaViolet, "Vibrate on play/pause",
            trailing = { Switch(checked = hapticFeedback, onCheckedChange = {
                hapticFeedback = it; prefs.edit().putBoolean("haptic", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Listening Stats", Icons.Filled.BarChart, NebulaViolet, "Your Deck Wrapped",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = onStatsClick) }
        item { STile("Driving Mode", Icons.Filled.DirectionsCar, NebulaGreen, "Large controls for safe driving",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
            onClick = { onDrivingMode?.invoke() }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── About ───────────────────────────────────────────────────────
        item { SSection("About") }
        item { STile("Version", Icons.Filled.Info, NebulaViolet, "Deck v1.0.0") }
        // ── Runtime SHA-1 diagnostic ────────────────────────────────────
        // Shows the ACTUAL SHA-1 of the installed APK that GMS reads.
        // Must match exactly what is in Firebase Console → Project Settings → SHA-1.
        // If they don't match → Google Sign-In error 10.
        item {
            val runtimeSHA1 = remember { getRuntimeSHA1(context) }
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NebulaViolet.copy(alpha = 0.08f))
                    .border(1.dp, NebulaViolet.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Fingerprint, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Runtime SHA-1 (for Firebase)", style = MaterialTheme.typography.labelMedium,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    runtimeSHA1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = NebulaViolet
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This SHA-1 must be in Firebase Console → Project Settings → Your App → Fingerprints",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary
                )
            }
        }
        item {
            STile("Rate on Play Store", Icons.Filled.Star, NebulaAmber,
                trailing = { Icon(Icons.Filled.OpenInNew, null, tint = LocalAppColors.current.textTertiary) },
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.sayaem.nebula")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                })
        }
        item {
            STile("Share Deck", Icons.Filled.Share, NebulaGreen,
                trailing = { Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.textTertiary) },
                onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out Deck — the ultimate media player!")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(i, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
        }
    }

    // Min duration picker dialog
    if (showMinDurationPicker) {
        val options = listOf(10, 20, 30, 45, 60, 90, 120)
        AlertDialog(
            onDismissRequest = { showMinDurationPicker = false },
            containerColor   = LocalAppColors.current.card,
            title = { Text("Min Track Duration", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { secs ->
                        Row(Modifier.fillMaxWidth().clickable {
                            minDuration = secs
                            prefs.edit().putInt("min_duration_sec", secs).apply()
                            showMinDurationPicker = false
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = minDuration == secs, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = NebulaViolet))
                            Spacer(Modifier.width(12.dp))
                            Text("${secs}s", style = MaterialTheme.typography.bodyMedium,
                                color = if (minDuration == secs) NebulaViolet else LocalAppColors.current.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMinDurationPicker = false }) { Text("Close", color = NebulaViolet) }
            }
        )
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

// ── Runtime SHA-1 of the installed APK ───────────────────────────────────
// This is what GMS reads at runtime — must match what's in Firebase Project Settings.
// If this differs from the SHA-1 you added to Firebase, Google Sign-In will give error 10.
private fun getRuntimeSHA1(context: android.content.Context): String {
    return try {
        @Suppress("DEPRECATION")
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo.apkContentsSigners
        } else {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures
        }
        val md    = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(sigs[0].toByteArray())
        bytes.joinToString(":") { "%02X".format(it) }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
