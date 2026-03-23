package com.sayaem.nebula.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun MoreScreen(
    isDark: Boolean,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    isPremium: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onToggleTheme: () -> Unit,
    onEqualizerClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onStatsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onRescan: () -> Unit,
    onGaplessChanged: (Boolean) -> Unit,
    onSmartSkipChanged: (Boolean) -> Unit,
    onCrossfadeChanged: (Float) -> Unit,
    onVolumeNormChanged: (Boolean) -> Unit,
    initialGapless: Boolean,
    initialSmartSkip: Boolean,
    initialCrossfade: Float,
    initialVolumeNorm: Boolean,
) {
    val context  = LocalContext.current
    val appColors = LocalAppColors.current
    val prefs    = remember { context.getSharedPreferences("deck_data", android.content.Context.MODE_PRIVATE) }

    var gapless   by remember { mutableStateOf(initialGapless) }
    var smartSkip by remember { mutableStateOf(initialSmartSkip) }
    var crossfade by remember { mutableStateOf(initialCrossfade) }
    var volNorm   by remember { mutableStateOf(initialVolumeNorm) }
    var fadeOnPause by remember { mutableStateOf(prefs.getBoolean("fade_on_pause", true)) }
    var showLyrics  by remember { mutableStateOf(prefs.getBoolean("show_lyrics", true)) }
    var haptic      by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
    var notifExpanded by remember { mutableStateOf(prefs.getBoolean("notif_expanded", true)) }
    var showMinDurDialog by remember { mutableStateOf(false) }
    var minDuration by remember { mutableIntStateOf(prefs.getInt("min_duration_sec", 30)) }

    LazyColumn(
        Modifier.fillMaxSize().background(appColors.bg),
        contentPadding = PaddingValues(bottom = 180.dp)
    ) {
        item {
            Spacer(Modifier.statusBarsPadding().height(8.dp))
            Text("More", style = MaterialTheme.typography.headlineLarge,
                color = appColors.textPrimary, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }

        // ── Account ──────────────────────────────────────────────────
        item { SSection("Account") }
        item {
            if (currentUser != null && !currentUser.isAnonymous) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape)
                        .background(NebulaViolet.copy(0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = NebulaViolet, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentUser.displayName ?: "User", style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall,
                            color = appColors.textTertiary)
                    }
                    if (isPremium) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaViolet.copy(0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("PRO", style = MaterialTheme.typography.labelSmall,
                                color = NebulaViolet, fontWeight = FontWeight.Bold)
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
                            color = appColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Sync playlists & favorites", style = MaterialTheme.typography.bodySmall,
                            color = appColors.textTertiary)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary)
                }
            }
        }

        // ── Premium banner ────────────────────────────────────────────
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
                            Text("No ads · Cloud sync · Per-song EQ · Stats",
                                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
                        }
                        Text("Upgrade →", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Quick actions ─────────────────────────────────────────────
        item { SSection("Quick") }
        item { STile("Equalizer", Icons.Filled.Equalizer, NebulaCyan, "10-band EQ + Bass Boost",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary) },
            onClick = onEqualizerClick) }
        item { STile("Sleep Timer", Icons.Filled.Bedtime, NebulaCyan,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary) },
            onClick = onSleepTimerClick) }
        item { STile("Listening Stats", Icons.Filled.BarChart, NebulaViolet, "Your listening history",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary) },
            onClick = onStatsClick) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Appearance ────────────────────────────────────────────────
        item { SSection("Appearance") }
        item { STile("Dark Mode", Icons.Filled.DarkMode, NebulaViolet,
            trailing = { Switch(checked = isDark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Playback ─────────────────────────────────────────────────
        item { SSection("Playback") }
        item { STile("Gapless Playback", Icons.Filled.GraphicEq, NebulaGreen, "No silence between tracks",
            trailing = { Switch(checked = gapless, onCheckedChange = { gapless = it; onGaplessChanged(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Volume Normalization", Icons.Filled.VolumeUp, NebulaGreen, "Balance loudness across tracks",
            trailing = { Switch(checked = volNorm, onCheckedChange = { volNorm = it; onVolumeNormChanged(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Fade on Pause", Icons.Filled.VolumeDown, NebulaVioletLight, "Smooth audio transitions",
            trailing = { Switch(checked = fadeOnPause, onCheckedChange = {
                fadeOnPause = it; prefs.edit().putBoolean("fade_on_pause", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(NebulaVioletLight.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SyncAlt, null, tint = NebulaVioletLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Crossfade", style = MaterialTheme.typography.bodyMedium, color = appColors.textPrimary)
                            Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s",
                                style = MaterialTheme.typography.bodySmall, color = appColors.textTertiary)
                        }
                    }
                    Text(if (crossfade == 0f) "Off" else "${crossfade.toInt()}s",
                        style = MaterialTheme.typography.labelMedium, color = NebulaVioletLight)
                }
                Slider(value = crossfade, onValueChange = { crossfade = it; onCrossfadeChanged(it) },
                    valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(activeTrackColor = NebulaVioletLight,
                        thumbColor = Color.White, inactiveTrackColor = appColors.border),
                    modifier = Modifier.padding(start = 50.dp))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Smart ─────────────────────────────────────────────────────
        item { SSection("Smart Features") }
        item { STile("Smart Skip", Icons.Filled.Psychology, NebulaPink,
            if (smartSkip) "Auto-skips frequently skipped songs" else "Off",
            trailing = { Switch(checked = smartSkip, onCheckedChange = { smartSkip = it; onSmartSkipChanged(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Synced Lyrics", Icons.Filled.Lyrics, NebulaCyan, "Show .lrc lyrics in Now Playing",
            trailing = { Switch(checked = showLyrics, onCheckedChange = {
                showLyrics = it; prefs.edit().putBoolean("show_lyrics", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { STile("Haptic Feedback", Icons.Filled.Vibration, NebulaViolet, "Vibrate on interactions",
            trailing = { Switch(checked = haptic, onCheckedChange = {
                haptic = it; prefs.edit().putBoolean("haptic", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── Library ───────────────────────────────────────────────────
        item { SSection("Library") }
        item { STile("Rescan Media", Icons.Filled.Refresh, NebulaGreen, "Find new files on device",
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary) },
            onClick = onRescan) }
        item { STile("Min Track Duration", Icons.Filled.Timelapse, NebulaAmber,
            "Ignore files shorter than ${minDuration}s",
            trailing = { Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NebulaAmber.copy(0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("${minDuration}s", style = MaterialTheme.typography.labelMedium, color = NebulaAmber)
            }},
            onClick = { showMinDurDialog = true }) }
        item { STile("Notification Controls", Icons.Filled.NotificationsActive, NebulaGreen,
            "Show media controls in notification shade",
            trailing = { Switch(checked = notifExpanded, onCheckedChange = {
                notifExpanded = it; prefs.edit().putBoolean("notif_expanded", it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NebulaViolet)) }) }
        item { Spacer(Modifier.height(8.dp)) }

        // ── About ─────────────────────────────────────────────────────
        item { SSection("About") }
        item { STile("Version", Icons.Filled.Info, NebulaViolet, "Deck v1.0.0") }
        item { STile("Rate on Play Store", Icons.Filled.Star, NebulaAmber,
            trailing = { Icon(Icons.Filled.OpenInNew, null, tint = appColors.textTertiary) },
            onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.sayaem.nebula")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) {}
            }) }
        item { STile("Share Deck", Icons.Filled.Share, NebulaGreen,
            trailing = { Icon(Icons.Filled.ChevronRight, null, tint = appColors.textTertiary) },
            onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Check out Deck — the best media player!")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) }
    }

    if (showMinDurDialog) {
        val appColors2 = LocalAppColors.current
        AlertDialog(
            onDismissRequest = { showMinDurDialog = false },
            containerColor = appColors2.card,
            title = { Text("Min Track Duration", color = appColors2.textPrimary, fontWeight = FontWeight.Bold) },
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
                                color = if (minDuration == secs) NebulaViolet else appColors2.textPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMinDurDialog = false }) { Text("Done", color = NebulaViolet) } }
        )
    }
}
