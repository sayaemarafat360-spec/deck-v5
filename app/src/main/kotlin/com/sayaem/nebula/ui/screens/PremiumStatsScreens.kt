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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.components.StatCard
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors




// ─── Premium Screen ───────────────────────────────────────────────────
@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    isPremium: Boolean = false,
    premiumPlan: String = "none",
    prices: com.sayaem.nebula.backend.PriceConfig = com.sayaem.nebula.backend.PriceConfig(),
    onPurchase: (String) -> Unit = {},
) {
    var selectedPlan by remember { mutableStateOf(1) }
    val plans = listOf(
        Triple("Monthly",  "\$${prices.monthly}",  "per month · \$${String.format("%.2f", prices.monthly.toFloatOrNull()?.times(12) ?: 23.88f)}/yr"),
        Triple("Yearly",   "\$${prices.yearly}",  "per year · save ${prices.yearlySavings}%"),
        Triple("Lifetime", "\$${prices.lifetime}", "one-time · = ${prices.lifetimeYears} yrs yearly"),
    )
    val features = listOf(
        Triple("Pro 10-Band Equalizer",   Icons.Filled.Equalizer,    NebulaCyan),
        Triple("All Premium Themes",      Icons.Filled.Palette,      NebulaPink),
        Triple("Zero Ads — Forever",      Icons.Filled.Block,        NebulaGreen),
        Triple("Advanced Stats & Wrapped",Icons.Filled.BarChart,     NebulaViolet),
        Triple("Smart AI Skip",           Icons.Filled.Psychology,   NebulaPink),
        Triple("Spatial Audio & Reverb",  Icons.Filled.Speaker,      NebulaCyan),
        Triple("Cloud Playlist Backup",   Icons.Filled.CloudUpload,  NebulaAmber),
    )

    Column(Modifier.fillMaxSize().background(LocalAppColors.current.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, null, tint = LocalAppColors.current.textPrimary)
            }
        }

        if (isPremium) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NebulaGreen.copy(0.15f))
                .border(1.dp, NebulaGreen.copy(0.3f), RoundedCornerShape(14.dp))
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = NebulaGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("You have Deck Premium", style = MaterialTheme.typography.titleSmall,
                            color = NebulaGreen, fontWeight = FontWeight.Bold)
                        Text("Plan: ${premiumPlan.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSecondary)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Star, null, tint = NebulaAmber, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            Text("DECK PREMIUM", style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Unlock the full universe of sound & vision",
                style = MaterialTheme.typography.bodyLarge, color = LocalAppColors.current.textSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
        }
        Spacer(Modifier.height(32.dp))

        // Plan cards
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plans.forEachIndexed { i, (label, price, period) ->
                val sel = i == selectedPlan
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Brush.linearGradient(listOf(NebulaViolet, NebulaPink))
                        else Brush.linearGradient(listOf(Color.White.copy(0.06f), Color.White.copy(0.06f))))
                    .border(if (!sel) 0.5.dp else 0.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .clickable { selectedPlan = i }.padding(14.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        if (i == 1) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("BEST VALUE", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            Spacer(Modifier.height(4.dp))
                        } else Spacer(Modifier.height(22.dp))
                        Text(price, style = MaterialTheme.typography.headlineLarge,
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.8f))
                        Text(period, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))

        // Features
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            features.forEach { (title, icon, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(0.12f)),
                        contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.CheckCircle, null, tint = NebulaGreen, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
            .clickable { if (!isPremium) onPurchase(listOf("monthly","yearly","lifetime")[selectedPlan]) }.padding(vertical = 18.dp),
            contentAlignment = Alignment.Center) {
            Text("Start ${plans[selectedPlan].first} — ${plans[selectedPlan].second}",
                style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
        // Promo banner from Remote Config
        if (prices.showPromoBanner && prices.promoText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NebulaAmber.copy(0.15f))
                .border(1.dp, NebulaAmber.copy(0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalOffer, null, tint = NebulaAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(prices.promoText, style = MaterialTheme.typography.labelMedium,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Cancel anytime • Secure payment • Premium never expires for lifetime",
            style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}

// ─── Stats / Wrapped Screen ───────────────────────────────────────────
@Composable
fun StatsScreen(
    songs: List<Song>,
    stats: List<Pair<Song, Long>>,
    topSongs: List<Pair<Song, Int>>,
    totalMinutes: Int,
    onBack: () -> Unit,
) {
    val totalSongs  = stats.size
    val topArtist   = topSongs.groupBy { it.first.artist }.maxByOrNull { it.value.sumOf { p -> p.second } }?.key ?: "—"
    val topGenre    = "Mixed"

    Column(Modifier.fillMaxSize().background(LocalAppColors.current.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = LocalAppColors.current.textPrimary) }
            Text("Deck Wrapped", style = MaterialTheme.typography.headlineLarge,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
        }
        Text("Your listening story", style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp, bottom = 20.dp))

        // Hero stats row
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${totalMinutes}m", "Listened",
                icon = { Icon(Icons.Filled.AccessTime, null, tint = NebulaViolet, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f))
            StatCard("$totalSongs", "Tracks",
                icon = { Icon(Icons.Filled.LibraryMusic, null, tint = NebulaPink, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(topArtist.take(12), "Top Artist",
                icon = { Icon(Icons.Filled.Person, null, tint = NebulaAmber, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f))
            StatCard(topGenre, "Top Genre",
                icon = { Icon(Icons.Filled.MusicNote, null, tint = NebulaCyan, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        // Top tracks
        if (topSongs.isNotEmpty()) {
            Text("Most Played", style = MaterialTheme.typography.headlineSmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            topSongs.take(5).forEachIndexed { i, (song, count) ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i+1}", style = MaterialTheme.typography.headlineSmall,
                        color = if (i == 0) NebulaViolet else TextTertiaryDark,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(NebulaViolet.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, null, tint = NebulaViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall,
                            color = LocalAppColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
                    }
                    Text("$count plays", style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BarChart, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Play some music to build your stats!",
                        style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textTertiary,
                        textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // Weekly activity bars (simulated from recent plays)
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp))
            .background(LocalAppColors.current.card).border(0.5.dp, LocalAppColors.current.border, RoundedCornerShape(20.dp)).padding(20.dp)) {
            Column {
                Text("Weekly Activity", style = MaterialTheme.typography.headlineSmall,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                val days = listOf("M","T","W","T","F","S","S")
                val heights = remember(stats) {
                    // Distribute recent plays across days
                    val counts = IntArray(7) { 0 }
                    stats.forEach { (_, ts) ->
                        val dayOfWeek = ((ts / 86400000) % 7).toInt()
                        counts[dayOfWeek] = (counts[dayOfWeek] + 1).coerceAtMost(10)
                    }
                    val max = counts.maxOrNull()?.takeIf { it > 0 } ?: 1
                    counts.map { it.toFloat() / max }
                }
                Row(Modifier.fillMaxWidth().height(100.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    days.forEachIndexed { i, day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(28.dp).fillMaxHeight(heights[i].coerceAtLeast(0.05f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (heights[i] == heights.max()) NebulaViolet else LocalAppColors.current.border))
                            Spacer(Modifier.height(6.dp))
                            Text(day, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textTertiary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // Personality card
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(NebulaViolet, NebulaPink)))
            .padding(24.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Your Sound Personality", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.8f))
                }
                Spacer(Modifier.height(12.dp))
                Text(when {
                    totalMinutes > 300 -> "The Audiophile"
                    totalMinutes > 100 -> "The Night Explorer"
                    totalMinutes > 30  -> "The Casual Listener"
                    else               -> "Just Getting Started"
                }, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(when {
                    totalMinutes > 300 -> "You live and breathe music. With $totalMinutes minutes logged, your dedication to sound is unmatched."
                    totalMinutes > 100 -> "You explore music deeply and love discovering new sounds across genres."
                    totalMinutes > 30  -> "You enjoy good music when the moment calls for it."
                    else               -> "Start listening to discover your sound personality!"
                }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.75f))
            }
        }
        Spacer(Modifier.height(60.dp))
    }
}
