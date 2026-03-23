package com.sayaem.nebula.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

// ── 2-page onboarding: fast, beautiful, one-time ─────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val scope  = rememberCoroutineScope()
    val pager  = rememberPagerState(pageCount = { 2 })

    val inf = rememberInfiniteTransition(label = "ob")
    val pulse by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val rotate by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "r")
    val wave by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "w")

    Box(Modifier.fillMaxSize().background(Color(0xFF08081A))) {

        // ── Ambient background glow ───────────────────────────────────
        val glowColor1 = lerp(NebulaViolet, NebulaPink, pager.currentPageOffsetFraction.coerceIn(0f, 1f))
        Box(
            Modifier.size(350.dp)
                .blur(120.dp)
                .background(glowColor1.copy(0.18f), CircleShape)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
        )
        Box(
            Modifier.size(250.dp)
                .blur(90.dp)
                .background(NebulaCyan.copy(0.12f), CircleShape)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
        )

        Column(Modifier.fillMaxSize()) {

            // ── Pager ─────────────────────────────────────────────────
            HorizontalPager(
                state  = pager,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage(pulse, rotate)
                    1 -> SoundPage(wave, pulse)
                }
            }

            // ── Bottom controls ────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).navigationBarsPadding().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { i ->
                        val isActive = pager.currentPage == i
                        val width by animateDpAsState(if (isActive) 24.dp else 8.dp, label = "dot$i")
                        Box(
                            Modifier.height(8.dp).width(width)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) NebulaViolet else Color.White.copy(0.25f))
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                // CTA button
                val isLast = pager.currentPage == 1
                val btnColor by animateColorAsState(
                    if (isLast) NebulaViolet else Color.White.copy(0.12f), label = "btn"
                )
                Button(
                    onClick = {
                        if (isLast) onDone()
                        else scope.launch { pager.animateScrollToPage(1) }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor)
                ) {
                    Text(
                        if (isLast) "Start Listening" else "Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isLast) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Skip (only on page 1)
                AnimatedVisibility(visible = !isLast) {
                    TextButton(onClick = onDone) {
                        Text("Skip", color = Color.White.copy(0.4f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ── Page 1: Welcome ───────────────────────────────────────────────────────
@Composable
private fun WelcomePage(pulse: Float, rotate: Float) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated disc illustration
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            // Outer glow ring
            Box(
                Modifier.size((220 + pulse * 20).dp)
                    .blur((24 + pulse * 8).dp)
                    .background(NebulaViolet.copy(0.22f + pulse * 0.1f), CircleShape)
            )
            // Spinning disc
            Box(
                Modifier.size(200.dp).clip(CircleShape)
                    .background(Color(0xFF12121E))
                    .rotate(rotate),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(200.dp)) {
                    val c = center
                    // Vinyl grooves
                    for (r in listOf(0.95f, 0.85f, 0.75f, 0.65f, 0.55f)) {
                        drawCircle(
                            color = Color.White.copy(0.04f),
                            radius = size.minDimension / 2 * r,
                            center = c, style = Stroke(1f)
                        )
                    }
                    // Label circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(NebulaViolet.copy(0.9f), NebulaPink.copy(0.7f)),
                            center = c, radius = size.minDimension * 0.22f
                        ),
                        radius = size.minDimension * 0.22f, center = c
                    )
                    // Center hole
                    drawCircle(color = Color(0xFF08081A), radius = 8.dp.toPx(), center = c)
                }
                // Music note in label
                Icon(Icons.Filled.MusicNote, null, tint = Color.White,
                    modifier = Modifier.size(32.dp))
            }
            // Floating note dots
            for ((offsetX, offsetY, color, sz) in listOf(
                arrayOf(-80f, -60f, NebulaViolet, 10f),
                arrayOf(85f, -40f, NebulaPink, 8f),
                arrayOf(-70f, 70f, NebulaCyan, 6f),
                arrayOf(75f, 55f, NebulaAmber, 9f),
            )) {
                @Suppress("UNCHECKED_CAST")
                val ox = offsetX as Float; val oy = offsetY as Float
                val col = color as Color; val s = (sz as Float).dp
                Box(
                    Modifier.offset(ox.dp, (oy + pulse * 6f).dp)
                        .size(s).clip(CircleShape).background(col.copy(0.8f))
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text("Everything plays.", style = MaterialTheme.typography.displaySmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text("Music, videos and podcasts — all in one beautifully crafted player.",
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(0.6f),
            textAlign = TextAlign.Center, lineHeight = 24.sp)
    }
}

// ── Page 2: Sound ─────────────────────────────────────────────────────────
@Composable
private fun SoundPage(wave: Float, pulse: Float) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated EQ waveform
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size((200 + pulse * 24).dp)
                    .blur(32.dp)
                    .background(NebulaCyan.copy(0.18f + pulse * 0.08f), CircleShape)
            )
            Canvas(Modifier.size(200.dp)) {
                val barCount = 20
                val barW     = size.width / (barCount * 2 - 1)
                val heights  = List(barCount) { i ->
                    val phase = (i.toFloat() / barCount + wave) * 2f * PI.toFloat()
                    val amp   = 0.25f + 0.55f * ((sin(phase) + 1f) / 2f)
                    amp * size.height * 0.85f
                }
                val colors   = listOf(NebulaViolet, NebulaPink, NebulaCyan)
                heights.forEachIndexed { i, h ->
                    val x     = i * barW * 2
                    val t     = i.toFloat() / barCount
                    val color = lerp(colors[0], lerp(colors[1], colors[2], t), t)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(color.copy(0.9f), color.copy(0.3f)),
                            startY = center.y - h / 2, endY = center.y + h / 2
                        ),
                        topLeft = Offset(x, center.y - h / 2),
                        size    = Size(barW * 0.75f, h),
                        cornerRadius = CornerRadius(barW * 0.35f)
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Text("Your sound, perfected.", style = MaterialTheme.typography.displaySmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text("10-band equalizer, per-song profiles, real-time visualizer. Your ears deserve it.",
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(0.6f),
            textAlign = TextAlign.Center, lineHeight = 24.sp)

        Spacer(Modifier.height(32.dp))

        // Feature chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Pair("10-Band EQ", NebulaViolet),
                Pair("Live Visualizer", NebulaCyan),
                Pair("Gapless", NebulaGreen),
            ).forEach { (label, color) ->
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(color.copy(0.15f))
                        .border(0.5.dp, color.copy(0.35f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}
