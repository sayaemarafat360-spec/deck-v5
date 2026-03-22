package com.sayaem.nebula.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun DeckSplashScreen(onFinished: () -> Unit) {

    // ── Phase controller ──────────────────────────────────────────────
    var phase by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(80);  phase = 1   // particles + bg pulse starts
        delay(350); phase = 2   // logo disc erupts
        delay(280); phase = 3   // waveform draws
        delay(220); phase = 4   // title slides up
        delay(800); phase = 5   // everything fades out
        delay(420); onFinished()
    }

    // ── Infinite animations ───────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "inf")

    val arcRotation by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "arc"
    )
    val arcRotation2 by inf.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "arc2"
    )
    val pulse by inf.animateFloat(
        0.96f, 1.04f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulse"
    )
    val glowAlpha by inf.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow"
    )
    val waveOffset by inf.animateFloat(
        0f, 2 * PI.toFloat(),
        infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave"
    )

    // ── Phase-driven animations ───────────────────────────────────────
    val bgAlpha     by animateFloatAsState(if (phase >= 1) 1f else 0f,      tween(500),            label = "bgA")
    val logoScale   by animateFloatAsState(if (phase >= 2) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "ls")
    val logoAlpha   by animateFloatAsState(if (phase >= 2) 1f else 0f,      tween(300),            label = "la")
    val waveAlpha   by animateFloatAsState(if (phase >= 3) 1f else 0f,      tween(350),            label = "wa")
    val textOffsetY by animateFloatAsState(if (phase >= 4) 0f else 30f,     tween(400, easing = EaseOutCubic), label = "ty")
    val textAlpha   by animateFloatAsState(if (phase >= 4) 1f else 0f,      tween(400),            label = "ta")
    val exitAlpha   by animateFloatAsState(if (phase >= 5) 0f else 1f,      tween(380),            label = "ea")

    // ── Particle data ─────────────────────────────────────────────────
    val particles = remember {
        (0..28).map {
            ParticleData(
                angle  = (it * 137.508f) % 360f,          // golden angle spread
                radius = 80f + (it % 5) * 38f,
                size   = 2f + (it % 4) * 1.5f,
                speed  = 0.4f + (it % 3) * 0.3f,
                color  = when (it % 4) {
                    0 -> NebulaViolet
                    1 -> NebulaPink
                    2 -> NebulaCyan
                    else -> NebulaAmber
                }
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(exitAlpha)
            .background(Color(0xFF06050F)),
        contentAlignment = Alignment.Center
    ) {

        // ── Deep radial background glow ───────────────────────────────
        Canvas(Modifier.fillMaxSize().alpha(bgAlpha)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NebulaViolet.copy(alpha = 0.18f * glowAlpha),
                        NebulaPink.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(cx, cy)
            )
        }

        // ── Orbiting particles ────────────────────────────────────────
        Canvas(Modifier.size(380.dp).alpha(bgAlpha)) {
            val cx = size.width / 2f; val cy = size.height / 2f
            particles.forEach { p ->
                val angle = Math.toRadians((p.angle + arcRotation * p.speed).toDouble())
                val x = cx + cos(angle).toFloat() * p.radius
                val y = cy + sin(angle).toFloat() * p.radius
                drawCircle(
                    color  = p.color.copy(alpha = 0.55f),
                    radius = p.size,
                    center = Offset(x, y)
                )
                // Trail
                for (t in 1..3) {
                    val ta = Math.toRadians((p.angle + arcRotation * p.speed - t * 6.0).toDouble())
                    val tx = cx + cos(ta).toFloat() * p.radius
                    val ty = cy + sin(ta).toFloat() * p.radius
                    drawCircle(
                        color  = p.color.copy(alpha = 0.12f / t),
                        radius = p.size * (1f - t * 0.25f),
                        center = Offset(tx, ty)
                    )
                }
            }
        }

        // ── Rotating gradient arcs ────────────────────────────────────
        Canvas(Modifier.size(260.dp).rotate(arcRotation).alpha(logoAlpha * 0.9f)) {
            drawArc(
                brush = Brush.sweepGradient(listOf(
                    Color.Transparent, NebulaViolet.copy(0.9f),
                    NebulaPink.copy(0.6f), Color.Transparent
                )),
                startAngle = 0f, sweepAngle = 200f, useCenter = false,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
        Canvas(Modifier.size(230.dp).rotate(arcRotation2).alpha(logoAlpha * 0.7f)) {
            drawArc(
                brush = Brush.sweepGradient(listOf(
                    Color.Transparent, NebulaCyan.copy(0.7f),
                    NebulaAmber.copy(0.4f), Color.Transparent
                )),
                startAngle = 90f, sweepAngle = 160f, useCenter = false,
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
        }

        // ── Glow ring behind logo ─────────────────────────────────────
        Canvas(Modifier.size(150.dp).scale(pulse).alpha(logoAlpha)) {
            drawCircle(
                brush = Brush.radialGradient(listOf(
                    NebulaViolet.copy(0.5f), NebulaPink.copy(0.2f), Color.Transparent
                ))
            )
        }

        // ── Logo disc ────────────────────────────────────────────────
        Box(
            Modifier
                .size(108.dp)
                .scale(logoScale * pulse)
                .alpha(logoAlpha)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to NebulaViolet,
                        0.5f to Color(0xFF9B6BFF),
                        1f to NebulaPink
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner shadow ring
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(
                        Color.Transparent, Color.Black.copy(0.25f)
                    ))
                )
                // Highlight arc top-left
                drawArc(
                    color = Color.White.copy(0.25f),
                    startAngle = 200f, sweepAngle = 120f, useCenter = false,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    topLeft = Offset(8f, 8f),
                    size = Size(size.width - 16f, size.height - 16f)
                )
            }
            Text(
                "D",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Waveform equalizer bars below logo ────────────────────────
        Canvas(
            Modifier
                .offset(y = 80.dp)
                .size(width = 120.dp, height = 28.dp)
                .alpha(waveAlpha)
        ) {
            val barCount = 18
            val barW = size.width / (barCount * 1.7f)
            val gap  = size.width / barCount
            val midH = size.height / 2f
            for (i in 0 until barCount) {
                val x = i * gap
                val sinVal = sin(waveOffset + i * 0.55f).toFloat()
                val h = (midH * 0.35f + midH * 0.65f * ((sinVal + 1f) / 2f))
                    .coerceIn(3f, size.height * 0.92f)
                val color = lerp(NebulaViolet, NebulaPink, i.toFloat() / barCount)
                drawRoundRect(
                    color       = color.copy(alpha = 0.85f),
                    topLeft     = Offset(x + gap / 2f - barW / 2f, midH - h / 2f),
                    size        = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f)
                )
            }
        }

        // ── App name + tagline ────────────────────────────────────────
        Column(
            Modifier
                .offset(y = (112 + textOffsetY).dp)
                .alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "DECK",
                style = MaterialTheme.typography.headlineLarge.copy(
                    letterSpacing = 10.sp,
                    fontWeight    = FontWeight.Bold
                ),
                color = Color.White
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Your music universe",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = NebulaViolet.copy(0.75f)
            )
        }
    }
}

private data class ParticleData(
    val angle: Float,
    val radius: Float,
    val size: Float,
    val speed: Float,
    val color: Color,
)
