package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.sayaem.nebula.MainViewModel
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors
import kotlin.math.*

data class EqState(
    val enabled: Boolean = true,
    val bands: MutableList<Float> = MutableList(10) { 0f },
    val bassBoost: Float = 0f,
    val preset: String = "Flat",
)

// Fix #1 — Advanced Equalizer UI/UX
@Composable
fun EqualizerScreen(
    eqState: EqState,
    onBandChanged: (Int, Float) -> Unit,
    onPresetChanged: (String) -> Unit,
    onToggleEq: () -> Unit,
    onBack: () -> Unit,
    onSaveForSong: (() -> Unit)? = null,
    currentSongTitle: String? = null,
) {
    val presets = MainViewModel.EQ_PRESETS.keys.toList()
    val freqLabels = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")

    // Animated enable/disable
    val enableAlpha by animateFloatAsState(
        targetValue = if (eqState.enabled) 1f else 0.38f,
        animationSpec = tween(300), label = "eqAlpha"
    )

    // Spectrum animation — subtle glow pulse when enabled
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        Modifier.fillMaxSize()
            .background(LocalAppColors.current.bg)
    ) {
        // ── Top bar ──────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = LocalAppColors.current.textPrimary)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Equalizer", style = MaterialTheme.typography.headlineMedium,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                if (currentSongTitle != null) {
                    Text(currentSongTitle, style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary, maxLines = 1)
                }
            }
            if (onSaveForSong != null) {
                TextButton(onClick = onSaveForSong) {
                    Icon(Icons.Filled.Save, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", color = NebulaViolet, style = MaterialTheme.typography.labelSmall)
                }
            }
            // Power toggle with animated color
            val toggleColor by animateColorAsState(
                targetValue = if (eqState.enabled) NebulaViolet else LocalAppColors.current.border,
                animationSpec = tween(250), label = "toggleColor"
            )
            Box(
                Modifier.padding(end = 8.dp).size(40.dp)
                    .clip(CircleShape)
                    .background(toggleColor.copy(0.15f))
                    .border(1.5.dp, toggleColor, CircleShape)
                    .clickable(onClick = onToggleEq),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Power, null, tint = toggleColor, modifier = Modifier.size(20.dp))
            }
        }

        // ── ON/OFF status strip ───────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (eqState.enabled) NebulaViolet.copy(0.1f) else LocalAppColors.current.card
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated dot
            val dotColor by animateColorAsState(
                targetValue = if (eqState.enabled) NebulaGreen else LocalAppColors.current.textTertiary,
                animationSpec = tween(300), label = "dot"
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Text(
                if (eqState.enabled) "Equalizer Active · ${eqState.preset}" else "Equalizer Disabled",
                style = MaterialTheme.typography.labelMedium,
                color = if (eqState.enabled) LocalAppColors.current.textPrimary else LocalAppColors.current.textTertiary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (eqState.enabled) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (eqState.enabled) NebulaViolet else TextTertiaryDark,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Preset chips ─────────────────────────────────────────────
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets.size) { i ->
                val p = presets[i]
                val selected = eqState.preset == p
                val chipColor by animateColorAsState(
                    targetValue = if (selected) NebulaViolet else LocalAppColors.current.card,
                    animationSpec = tween(200), label = "chip"
                )
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(chipColor)
                        .border(1.dp,
                            if (selected) NebulaViolet.copy(glowPulse) else LocalAppColors.current.border,
                            RoundedCornerShape(20.dp))
                        .clickable { onPresetChanged(p) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(p, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else LocalAppColors.current.textSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        // ── EQ Curve visualizer (decorative spline) ──────────────────
        Box(
            Modifier.fillMaxWidth().height(60.dp)
                .padding(horizontal = 20.dp)
                .alpha(enableAlpha)
        ) {
            val bands = eqState.bands
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bandCount = bands.size.coerceAtLeast(1)
                val points = bands.mapIndexed { i, v ->
                    val x = w * i / (bandCount - 1)
                    val y = h / 2f - (v / 12f) * (h / 2f - 4.dp.toPx())
                    Offset(x, y)
                }
                if (points.size >= 2) {
                    val path = Path()
                    path.moveTo(points[0].x, points[0].y)
                    for (j in 1 until points.size) {
                        val prev = points[j - 1]
                        val curr = points[j]
                        val cpX = (prev.x + curr.x) / 2f
                        path.cubicTo(cpX, prev.y, cpX, curr.y, curr.x, curr.y)
                    }
                    // Glow under curve
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        fillPath,
                        brush = Brush.verticalGradient(
                            listOf(NebulaViolet.copy(0.28f * glowPulse), Color.Transparent)
                        )
                    )
                    // Curve line
                    drawPath(
                        path,
                        color = NebulaViolet.copy(if (eqState.enabled) 0.9f else 0.3f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                // Center baseline
                drawLine(
                    color = LocalAppColors.current.border.copy(0.5f),
                    start = Offset(0f, h / 2f), end = Offset(w, h / 2f),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }

        // ── 10-band bars ─────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .alpha(enableAlpha)
        ) {
            // dB scale on left
            Column(
                Modifier.align(Alignment.CenterStart).width(28.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("+12", "+6", "0", "-6", "-12").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary,
                        modifier = Modifier.align(Alignment.End))
                }
            }

            // Band bars
            Row(
                Modifier.fillMaxSize().padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(10) { bandIdx ->
                    EqBand(
                        bandIndex = bandIdx,
                        value     = eqState.bands.getOrElse(bandIdx) { 0f },
                        label     = freqLabels.getOrElse(bandIdx) { "" },
                        enabled   = eqState.enabled,
                        glowPulse = glowPulse,
                        onChanged = { v -> onBandChanged(bandIdx, v) },
                        modifier  = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // ── Bass Boost slider ─────────────────────────────────────────
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalAppColors.current.card)
                .padding(16.dp)
                .alpha(enableAlpha)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bass Boost", style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(NebulaViolet.copy(0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${(eqState.bassBoost * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium, color = NebulaViolet,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = eqState.bassBoost,
                onValueChange = { /* parent handles via onBandChanged pattern */ },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    activeTrackColor = NebulaViolet,
                    thumbColor = Color.White,
                    inactiveTrackColor = LocalAppColors.current.border,
                )
            )
        }

        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

// ── Single EQ band — vertical drag with glow effect ──────────────────────
@Composable
private fun EqBand(
    bandIndex: Int,
    value: Float,
    label: String,
    enabled: Boolean,
    glowPulse: Float,
    onChanged: (Float) -> Unit,
    modifier: Modifier,
) {
    val normalised = (value + 12f) / 24f  // 0..1, 0.5 = centre
    val animNorm by animateFloatAsState(targetValue = normalised, animationSpec = spring(), label = "norm")

    // Pick a colour from the spectrum based on band index (cool EQ rainbow)
    val bandColors = listOf(
        Color(0xFF7B2FFF), Color(0xFF8B3FFF), Color(0xFF9B4FFF),
        Color(0xFF6B6FFF), Color(0xFF5B8FFF), Color(0xFF4B9FFF),
        Color(0xFF3BAFFF), Color(0xFF2BBFFF), Color(0xFF1BCFFF), Color(0xFF0BDFFF)
    )
    val bandColor = bandColors.getOrElse(bandIndex) { NebulaViolet }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // dB label
        val dbText = when {
            value > 0f  -> "+${value.toInt()}"
            value < 0f  -> "${value.toInt()}"
            else        -> "0"
        }
        val labelColor by animateColorAsState(
            targetValue = if (value != 0f) bandColor else LocalAppColors.current.textTertiary,
            animationSpec = tween(200), label = "dbColor"
        )
        Text(dbText, style = MaterialTheme.typography.labelSmall,
            color = labelColor, modifier = Modifier.height(18.dp), textAlign = TextAlign.Center)

        Spacer(Modifier.height(4.dp))

        // Drag zone
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { _, drag ->
                        val delta = -drag.y / size.height * 24f
                        onChanged((value + delta).coerceIn(-12f, 12f))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val trackW  = size.width * 0.32f
                val trackX  = (size.width - trackW) / 2
                val centerY = size.height / 2
                val thumbY  = size.height * (1f - animNorm)

                // Track shadow/glow
                if (enabled && value != 0f) {
                    drawRoundRect(
                        color = bandColor.copy(0.2f * glowPulse),
                        topLeft = Offset(trackX - 4.dp.toPx(), 0f),
                        size = Size(trackW + 8.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(trackW / 2)
                    )
                }
                // Track background
                drawRoundRect(
                    color = LocalAppColors.current.border,
                    topLeft = Offset(trackX, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2)
                )
                // Active fill
                val fillTop    = minOf(centerY, thumbY)
                val fillBottom = maxOf(centerY, thumbY)
                if (fillBottom > fillTop + 1f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(bandColor, bandColor.copy(0.75f)),
                            startY = fillTop, endY = fillBottom
                        ),
                        topLeft = Offset(trackX, fillTop),
                        size = Size(trackW, fillBottom - fillTop),
                        cornerRadius = CornerRadius(trackW / 2)
                    )
                }
                // Centre tick
                drawLine(
                    color = LocalAppColors.current.border.copy(0.8f),
                    start = Offset(trackX - 3.dp.toPx(), centerY),
                    end   = Offset(trackX + trackW + 3.dp.toPx(), centerY),
                    strokeWidth = 0.5.dp.toPx()
                )
                // Outer thumb glow
                if (enabled) {
                    drawCircle(
                        color = bandColor.copy(0.25f * glowPulse),
                        radius = trackW * 1.3f,
                        center = Offset(size.width / 2, thumbY)
                    )
                }
                // Thumb white ring
                drawCircle(color = Color.White, radius = trackW * 0.88f,
                    center = Offset(size.width / 2, thumbY))
                // Thumb inner fill
                drawCircle(color = bandColor, radius = trackW * 0.52f,
                    center = Offset(size.width / 2, thumbY))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            modifier = Modifier.height(16.dp), textAlign = TextAlign.Center)
    }
}
