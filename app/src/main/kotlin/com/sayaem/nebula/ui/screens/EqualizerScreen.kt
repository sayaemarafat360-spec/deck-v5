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
import androidx.compose.ui.unit.*
import com.sayaem.nebula.MainViewModel
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors



data class EqState(
    val enabled: Boolean = true,
    val bands: MutableList<Float> = MutableList(10) { 0f },
    val bassBoost: Float = 0f,
    val preset: String = "Flat",
)

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

    Column(Modifier.fillMaxSize().background(LocalAppColors.current.bg)) {

        // ── Top bar ──────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = LocalAppColors.current.textPrimary)
            }
            if (onSaveForSong != null && currentSongTitle != null) {
                TextButton(onClick = onSaveForSong) {
                    Icon(Icons.Filled.Save, null, tint = NebulaViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save for song", color = NebulaViolet, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("Equalizer", style = MaterialTheme.typography.headlineMedium,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text(if (eqState.enabled) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (eqState.enabled) NebulaViolet else TextTertiaryDark,
                modifier = Modifier.padding(end = 8.dp))
            Switch(
                checked = eqState.enabled,
                onCheckedChange = { onToggleEq() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NebulaViolet,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = LocalAppColors.current.border,
                )
            )
        }

        // ── Preset chips ─────────────────────────────────────────────
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets.size) { i ->
                val p = presets[i]
                val selected = eqState.preset == p
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) NebulaViolet else LocalAppColors.current.card)
                        .border(0.5.dp, if (selected) NebulaViolet else LocalAppColors.current.border, RoundedCornerShape(20.dp))
                        .clickable { onPresetChanged(p) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(p, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextSecondaryDark)
                }
            }
        }

        // ── EQ bands — vertical drag bars ────────────────────────────
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (eqState.enabled) 1f else 0.4f)
        ) {
            // dB labels on left
            Column(
                Modifier.align(Alignment.CenterStart).width(28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("+12", "+6", "0", "-6", "-12").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary, modifier = Modifier.padding(bottom = 0.dp))
                }
            }

            // The 10 band bars
            Row(
                Modifier.fillMaxSize().padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(10) { bandIdx ->
                    EqBand(
                        bandIndex  = bandIdx,
                        value      = eqState.bands.getOrElse(bandIdx) { 0f },
                        label      = freqLabels.getOrElse(bandIdx) { "" },
                        enabled    = eqState.enabled,
                        onChanged  = { v -> onBandChanged(bandIdx, v) },
                        modifier   = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // ── Bass Boost ───────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bass Boost", style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("${(eqState.bassBoost * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium, color = NebulaViolet)
            }
            Spacer(Modifier.height(6.dp))
            Slider(
                value = eqState.bassBoost,
                onValueChange = { /* handled by parent */ },
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

@Composable
private fun EqBand(
    bandIndex: Int,
    value: Float,         // -12 to +12 dB
    label: String,
    enabled: Boolean,
    onChanged: (Float) -> Unit,
    modifier: Modifier,
) {
    val normalised = (value + 12f) / 24f  // 0..1, 0.5 = center

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // dB value label
        Text(
            text = if (value == 0f) "0" else if (value > 0) "+${value.toInt()}" else "${value.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (value != 0f) NebulaViolet else TextTertiaryDark,
            modifier = Modifier.height(18.dp)
        )

        Spacer(Modifier.height(4.dp))

        // Vertical drag bar
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { _, drag ->
                        val delta = -drag.y / size.height * 24f
                        onChanged((value + delta).coerceIn(-12f, 12f))
                    }
                }
        ) {
            val appColors = LocalAppColors.current
            Canvas(Modifier.fillMaxSize()) {
                val trackW  = size.width * 0.35f
                val trackX  = (size.width - trackW) / 2
                val centerY = size.height / 2

                // Track background
                drawRoundRect(
                    color  = appColors.border,
                    topLeft = Offset(trackX, 0f),
                    size   = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2)
                )

                // Active fill from center to thumb
                val thumbY = size.height * (1f - normalised)
                val fillTop    = minOf(centerY, thumbY)
                val fillBottom = maxOf(centerY, thumbY)
                drawRoundRect(
                    color  = NebulaViolet,
                    topLeft = Offset(trackX, fillTop),
                    size   = Size(trackW, (fillBottom - fillTop).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(trackW / 2)
                )

                // Thumb circle
                drawCircle(
                    color  = Color.White,
                    radius = trackW * 0.85f,
                    center = Offset(size.width / 2, thumbY)
                )
                drawCircle(
                    color  = NebulaViolet,
                    radius = trackW * 0.5f,
                    center = Offset(size.width / 2, thumbY)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Frequency label
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary, modifier = Modifier.height(16.dp))
    }
}
