package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.*




data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val secondaryColor: Color,
    val illustration: @Composable (animProgress: Float) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ob")
    val pulse by infiniteTransition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val rotate by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    val wave by infiniteTransition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave")

    val pages = listOf(
        OnboardingPage(
            title    = "Everything plays.",
            subtitle = "Music, videos, podcasts — all in one beautifully crafted player.",
            accentColor   = NebulaViolet,
            secondaryColor = NebulaPink,
            illustration   = { p -> PlayIllustration(pulse, rotate) }
        ),
        OnboardingPage(
            title    = "Your sound, perfected.",
            subtitle = "10-band equalizer, spatial audio, and bass boost tuned exactly to your taste.",
            accentColor    = NebulaCyan,
            secondaryColor = NebulaViolet,
            illustration   = { p -> EqIllustration(wave, pulse) }
        ),
        OnboardingPage(
            title    = "Smart. Learns you.",
            subtitle = "Deck tracks what you love, skips what you don't, and gets better every day.",
            accentColor    = NebulaGreen,
            secondaryColor = NebulaCyan,
            illustration   = { p -> BrainIllustration(pulse, rotate) }
        ),
        OnboardingPage(
            title    = "Your music. Always.",
            subtitle = "Full offline library, folder browser, and playlists you actually control.",
            accentColor    = NebulaAmber,
            secondaryColor = NebulaGreen,
            illustration   = { p -> LibraryIllustration(pulse, wave) }
        ),
    )

    val pagerState = rememberPagerState { pages.size }

    Box(Modifier.fillMaxSize().background(LocalAppColors.current.bg)) {
        // Animated background blobs
        val page = pages[pagerState.currentPage]
        AnimatedContent(page.accentColor, transitionSpec = {
            fadeIn(tween(600)) togetherWith fadeOut(tween(400))
        }, label = "bgColor") { color ->
            val appColors = LocalAppColors.current
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = 0.06f + pulse * 0.03f),
                    radius = size.width * 0.7f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.2f))
                drawCircle(page.secondaryColor.copy(alpha = 0.04f + pulse * 0.02f),
                    radius = size.width * 0.5f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.75f))
            }
        }

        // Skip button
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)) {
            Text("Skip", style = MaterialTheme.typography.labelLarge,
                color = LocalAppColors.current.textSecondary)
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // Illustration area
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(300.dp)) { pageIdx ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    pages[pageIdx].illustration(pulse)
                }
            }

            Spacer(Modifier.height(40.dp))

            // Page indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                pages.indices.forEach { i ->
                    val selected = i == pagerState.currentPage
                    val width by animateDpAsState(if (selected) 24.dp else 8.dp,
                        spring(stiffness = Spring.StiffnessMedium), label = "dot")
                    Box(Modifier.height(8.dp).width(width).clip(RoundedCornerShape(4.dp))
                        .background(if (selected) page.accentColor else TextTertiaryDark.copy(0.4f)))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Text content with animation
            AnimatedContent(pagerState.currentPage, transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 2 } + fadeOut())
            }, label = "text") { idx ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pages[idx].title,
                        style = MaterialTheme.typography.displaySmall,
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(pages[idx].subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalAppColors.current.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // CTA button
            val isLast = pagerState.currentPage == pages.size - 1
            val scope  = rememberCoroutineScope()

            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(page.accentColor, page.secondaryColor)))
                .clickable {
                    if (isLast) {
                        onDone()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }
                .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(isLast, label = "btn") { last ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text(if (last) "Let's go" else "Next",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold)
                        if (!last) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ArrowForward, null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Illustration 1: Play / Media ─────────────────────────────────────
@Composable
fun PlayIllustration(pulse: Float, rotate: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val bar1 by infiniteTransition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(0.7f, 0.2f,
        infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(0.5f, 0.9f,
        infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "b3")
    val bar4 by infiniteTransition.animateFloat(0.2f, 0.8f,
        infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "b4")
    val bar5 by infiniteTransition.animateFloat(0.6f, 0.3f,
        infiniteRepeatable(tween(680), RepeatMode.Reverse), label = "b5")

    val appColors = LocalAppColors.current
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Outer glow ring
        drawCircle(NebulaViolet.copy(alpha = 0.08f + pulse * 0.06f),
            radius = 130f + pulse * 15f)
        drawCircle(NebulaViolet.copy(alpha = 0.04f), radius = 155f)

        // Vinyl disc
        drawCircle(Color(0xFF0F0F20), radius = 110f)
        for (r in listOf(90f, 72f, 55f, 38f)) {
            drawCircle(Color.White.copy(alpha = 0.04f), radius = r, style = Stroke(1f))
        }
        // Color band
        drawCircle(NebulaViolet.copy(alpha = 0.3f), radius = 50f, style = Stroke(8f))
        // Center hole
        drawCircle(appColors.bg, radius = 12f)
        drawCircle(NebulaViolet.copy(alpha = 0.6f), radius = 12f, style = Stroke(2f))

        // Spinning needle arm
        val needleAngle = Math.toRadians(-35.0)
        val nx = cx + 100f * cos(needleAngle).toFloat()
        val ny = cy - 80f + 100f * sin(needleAngle).toFloat()
        drawLine(TextTertiaryDark.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(cx + 85f, cy - 120f),
            end   = androidx.compose.ui.geometry.Offset(nx, ny), strokeWidth = 3f,
            cap   = StrokeCap.Round)
        drawCircle(NebulaViolet, radius = 6f,
            center = androidx.compose.ui.geometry.Offset(nx, ny))

        // EQ bars below
        val barColors = listOf(NebulaCyan, NebulaViolet, NebulaPink, NebulaAmber, NebulaGreen)
        val barHeights = listOf(bar1, bar2, bar3, bar4, bar5)
        val maxBarH = 60f
        val barW = 16f
        val spacing = 28f
        val startX = cx - (5 * spacing) / 2 + spacing / 2
        val baseY = cy + 130f

        barHeights.forEachIndexed { i, h ->
            val bx = startX + i * spacing
            val bh = maxBarH * h
            drawRoundRect(barColors[i].copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(bx - barW/2, baseY - bh),
                size    = androidx.compose.ui.geometry.Size(barW, bh),
                cornerRadius = CornerRadius(4f))
        }
    }
}

// ─── Illustration 2: Equalizer ───────────────────────────────────────
@Composable
fun EqIllustration(wave: Float, pulse: Float) {
    val appColors = LocalAppColors.current
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Background circle
        drawCircle(NebulaCyan.copy(alpha = 0.06f + pulse * 0.03f), radius = 130f)

        // Phone/device outline
        val ph = 180f; val pw = 110f
        drawRoundRect(DarkCard.copy(alpha = 0.8f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - pw/2, cy - ph/2),
            size    = androidx.compose.ui.geometry.Size(pw, ph),
            cornerRadius = CornerRadius(16f))
        drawRoundRect(DarkBorder.copy(alpha = 0.8f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - pw/2, cy - ph/2),
            size    = androidx.compose.ui.geometry.Size(pw, ph),
            cornerRadius = CornerRadius(16f),
            style   = Stroke(1.5f))

        // EQ bars inside device
        val bands = 5
        val bW = 12f; val bSpacing = 18f
        val maxH = 80f
        val startX = cx - (bands * bSpacing) / 2 + bSpacing / 2
        val baseY  = cy + 55f
        val colors = listOf(NebulaCyan, NebulaCyan, NebulaViolet, NebulaPink, NebulaPink)
        val waveHeights = (0 until bands).map { i ->
            val phase = i.toFloat() / bands + wave
            0.3f + 0.6f * ((sin(phase * 2 * PI.toFloat()) + 1) / 2)
        }

        waveHeights.forEachIndexed { i, h ->
            val bx = startX + i * bSpacing
            val bh = maxH * h
            drawRoundRect(colors[i].copy(alpha = 0.9f),
                topLeft = androidx.compose.ui.geometry.Offset(bx - bW/2, baseY - bh),
                size    = androidx.compose.ui.geometry.Size(bW, bh),
                cornerRadius = CornerRadius(4f))
        }

        // Headphones above device
        val hcx = cx; val hcy = cy - 145f
        drawArc(TextTertiaryDark.copy(alpha = 0.6f), startAngle = 180f, sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(hcx - 50f, hcy - 30f),
            size  = androidx.compose.ui.geometry.Size(100f, 60f),
            style = Stroke(6f, cap = StrokeCap.Round))
        drawCircle(NebulaCyan, radius = 16f,
            center = androidx.compose.ui.geometry.Offset(hcx - 50f, hcy))
        drawCircle(NebulaCyan, radius = 16f,
            center = androidx.compose.ui.geometry.Offset(hcx + 50f, hcy))
        drawCircle(appColors.bg, radius = 10f,
            center = androidx.compose.ui.geometry.Offset(hcx - 50f, hcy))
        drawCircle(appColors.bg, radius = 10f,
            center = androidx.compose.ui.geometry.Offset(hcx + 50f, hcy))
    }
}

// ─── Illustration 3: Smart Brain ────────────────────────────────────
@Composable
fun BrainIllustration(pulse: Float, rotate: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "nodes")
    val nodeAlpha by infiniteTransition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "na")

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Outer glow
        drawCircle(NebulaGreen.copy(alpha = 0.05f + pulse * 0.04f), radius = 140f)

        // Network nodes
        val nodes = listOf(
            Pair(cx, cy - 80f) to NebulaViolet,
            Pair(cx - 80f, cy - 20f) to NebulaCyan,
            Pair(cx + 80f, cy - 20f) to NebulaPink,
            Pair(cx - 50f, cy + 60f) to NebulaGreen,
            Pair(cx + 50f, cy + 60f) to NebulaAmber,
            Pair(cx, cy + 110f) to NebulaViolet,
        )

        // Draw connections first
        nodes.forEachIndexed { i, (pos, _) ->
            nodes.forEachIndexed { j, (pos2, _) ->
                if (j > i && (i + j) % 2 == 0) {
                    val alpha = 0.15f + pulse * 0.1f
                    drawLine(NebulaViolet.copy(alpha = alpha),
                        start = androidx.compose.ui.geometry.Offset(pos.first, pos.second),
                        end   = androidx.compose.ui.geometry.Offset(pos2.first, pos2.second),
                        strokeWidth = 1f)
                }
            }
        }

        // Animated data pulses along lines
        val pAngle = (rotate / 360f) * 2 * PI.toFloat()
        val px = cx + 80f * cos(pAngle)
        val py = cy + 80f * sin(pAngle)
        drawCircle(NebulaGreen.copy(alpha = 0.8f), radius = 5f,
            center = androidx.compose.ui.geometry.Offset(px, py))

        // Node circles
        nodes.forEach { (pos, color) ->
            drawCircle(color.copy(alpha = 0.2f), radius = 22f,
                center = androidx.compose.ui.geometry.Offset(pos.first, pos.second))
            drawCircle(color, radius = 13f,
                center = androidx.compose.ui.geometry.Offset(pos.first, pos.second))
            drawCircle(Color.White.copy(alpha = 0.3f), radius = 5f,
                center = androidx.compose.ui.geometry.Offset(pos.first - 4f, pos.second - 4f))
        }

        // Central "brain" hub
        drawCircle(NebulaViolet.copy(alpha = 0.15f + pulse * 0.1f), radius = 36f)
        drawCircle(DarkCard.copy(alpha = 0.9f), radius = 26f)
        drawCircle(NebulaViolet.copy(alpha = 0.6f), radius = 26f, style = Stroke(2f))

        // Music note in center
        drawCircle(NebulaViolet, radius = 8f,
            center = androidx.compose.ui.geometry.Offset(cx - 6f, cy + 4f))
        drawLine(NebulaViolet,
            start = androidx.compose.ui.geometry.Offset(cx + 2f, cy + 4f),
            end   = androidx.compose.ui.geometry.Offset(cx + 2f, cy - 12f), strokeWidth = 3f)
        drawLine(NebulaViolet,
            start = androidx.compose.ui.geometry.Offset(cx + 2f, cy - 12f),
            end   = androidx.compose.ui.geometry.Offset(cx + 12f, cy - 8f), strokeWidth = 3f)
    }
}

// ─── Illustration 4: Library ────────────────────────────────────────
@Composable
fun LibraryIllustration(pulse: Float, wave: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Background circle
        drawCircle(NebulaAmber.copy(alpha = 0.06f + pulse * 0.03f), radius = 135f)

        // Stack of "album" cards
        val cardW = 160f; val cardH = 44f
        val cardColors = listOf(NebulaViolet, NebulaPink, NebulaCyan, NebulaGreen, NebulaAmber)
        val labels = listOf("Music", "Videos", "Podcasts", "Playlists", "Folders")

        cardColors.forEachIndexed { i, color ->
            val yOff = -90f + i * 52f
            val xOff = (i - 2) * 4f  // slight fan effect
            val rotation = (i - 2) * 2f

            withTransform({
                translate(cx + xOff, cy + yOff)
                rotate(rotation)
            }) {
                drawRoundRect(DarkCard.copy(alpha = 0.95f),
                    topLeft = androidx.compose.ui.geometry.Offset(-cardW/2, -cardH/2),
                    size    = androidx.compose.ui.geometry.Size(cardW, cardH),
                    cornerRadius = CornerRadius(12f))
                drawRoundRect(DarkBorder.copy(alpha = 0.5f),
                    topLeft = androidx.compose.ui.geometry.Offset(-cardW/2, -cardH/2),
                    size    = androidx.compose.ui.geometry.Size(cardW, cardH),
                    cornerRadius = CornerRadius(12f),
                    style   = Stroke(1f))
                // Color strip on left
                drawRoundRect(color.copy(alpha = 0.8f),
                    topLeft = androidx.compose.ui.geometry.Offset(-cardW/2, -cardH/2),
                    size    = androidx.compose.ui.geometry.Size(6f, cardH),
                    cornerRadius = CornerRadius(12f, 12f))
                // Play icon placeholder
                drawCircle(color.copy(alpha = 0.15f), radius = 14f,
                    center = androidx.compose.ui.geometry.Offset(cardW/2 - 22f, 0f))
            }
        }

        // Floating music note above
        val noteY = cy - 150f + sin(wave * 2 * PI.toFloat()) * 8f
        drawCircle(NebulaAmber.copy(alpha = 0.9f), radius = 10f,
            center = androidx.compose.ui.geometry.Offset(cx - 8f, noteY))
        drawLine(NebulaAmber, start = androidx.compose.ui.geometry.Offset(cx + 2f, noteY),
            end = androidx.compose.ui.geometry.Offset(cx + 2f, noteY - 22f), strokeWidth = 3f)
        drawLine(NebulaAmber, start = androidx.compose.ui.geometry.Offset(cx + 2f, noteY - 22f),
            end = androidx.compose.ui.geometry.Offset(cx + 16f, noteY - 16f), strokeWidth = 3f)
    }
}
